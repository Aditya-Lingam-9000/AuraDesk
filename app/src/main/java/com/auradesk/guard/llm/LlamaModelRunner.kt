package com.auradesk.guard.llm

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.auradesk.guard.vision.PersonRadarDetector

sealed class LlamaState {
    object NotDownloaded : LlamaState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : LlamaState()
    object Unloaded : LlamaState() // Model is on disk (~352MB), but not resident in RAM
    object Loading : LlamaState()  // Loading / memory-mapping weights into RAM
    object Ready : LlamaState()    // Resident in RAM and ready for zero-latency inference
    data class Generating(val partialText: String) : LlamaState()
    data class Error(val message: String) : LlamaState()
}

class LlamaModelRunner private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LlamaModelRunner"
        const val MODEL_FILENAME = "qwen2-0_5b-instruct-q4_k_m.gguf"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q4_k_m.gguf"

        @Volatile
        private var instance: LlamaModelRunner? = null

        fun getInstance(context: Context): LlamaModelRunner {
            return instance ?: synchronized(this) {
                instance ?: LlamaModelRunner(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var llamaModel: LlamaModel? = null

    private val _llamaState = MutableStateFlow<LlamaState>(LlamaState.NotDownloaded)
    val llamaState: StateFlow<LlamaState> = _llamaState.asStateFlow()

    private val _lastGeneratedReply = MutableStateFlow<String>("")
    val lastGeneratedReply: StateFlow<String> = _lastGeneratedReply.asStateFlow()

    init {
        checkModelStatus()
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > 100_000_000L
    }

    fun isModelLoaded(): Boolean {
        return llamaModel != null && llamaModel?.isLoaded == true
    }

    private fun promoteTempFile(tempFile: File, targetFile: File): Boolean {
        return try {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Successfully renamed temp model file to target: ${targetFile.absolutePath}")
                true
            } else {
                Log.w(TAG, "renameTo returned false, attempting NIO copy/move...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    true
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Promote temp file error: ${e.message}")
            false
        }
    }

    fun getModelFile(): File {
        val extDir = context.getExternalFilesDir("models") ?: context.filesDir
        val intDir = File(context.filesDir, "models")

        // 1. Check if finalized target model already exists (>100MB)
        val extTarget = File(extDir, MODEL_FILENAME)
        if (extTarget.exists() && extTarget.length() > 100_000_000L) return extTarget

        val intTarget = File(intDir, MODEL_FILENAME)
        if (intTarget.exists() && intTarget.length() > 100_000_000L) return intTarget

        // 2. Check if a temporary file exists (>100MB) from previous download attempt
        val extTemp = File(extDir, "$MODEL_FILENAME.tmp")
        if (extTemp.exists() && extTemp.length() > 100_000_000L) {
            Log.i(TAG, "Found existing downloaded temp file (${extTemp.length() / (1024 * 1024)}MB). Promoting to target...")
            if (promoteTempFile(extTemp, extTarget) && extTarget.exists()) {
                return extTarget
            }
            // Even if rename failed, the tempFile has the complete model and can be loaded directly!
            return extTemp
        }

        val intTemp = File(intDir, "$MODEL_FILENAME.tmp")
        if (intTemp.exists() && intTemp.length() > 100_000_000L) {
            Log.i(TAG, "Found existing internal temp file (${intTemp.length() / (1024 * 1024)}MB). Promoting to target...")
            if (promoteTempFile(intTemp, intTarget) && intTarget.exists()) {
                return intTarget
            }
            return intTemp
        }

        return extTarget
    }

    fun checkModelStatus() {
        if (isModelDownloaded()) {
            if (isModelLoaded()) {
                _llamaState.value = LlamaState.Ready
            } else {
                // Do NOT auto-load into RAM on app start! Keep it unloaded until requested.
                _llamaState.value = LlamaState.Unloaded
            }
        } else {
            _llamaState.value = LlamaState.NotDownloaded
        }
    }

    fun unloadModel() {
        try {
            llamaModel?.close()
            llamaModel = null
            if (isModelDownloaded()) {
                _llamaState.value = LlamaState.Unloaded
            } else {
                _llamaState.value = LlamaState.NotDownloaded
            }
            Log.i(TAG, "🦙 Ejected Qwen2-0.5B model from RAM.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unloading llama model", e)
        }
    }

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded()) {
            _llamaState.value = LlamaState.Ready
            return@withContext true
        }

        val file = getModelFile()
        if (!file.exists() || file.length() < 100_000_000L) {
            _llamaState.value = LlamaState.NotDownloaded
            return@withContext false
        }

        _llamaState.value = LlamaState.Loading
        Log.i(TAG, "🦙 Initializing Qwen2-0.5B-Instruct INT4 from: ${file.absolutePath} (${file.length() / (1024 * 1024)} MB)")

        try {
            llamaModel?.close()
            llamaModel = LlamaModel.load(file.absolutePath) {
                contextSize = 2048
                batchSize = 2048
                threads = 4
                temperature = 0.25f
                maxTokens = 60
            }
            _llamaState.value = LlamaState.Ready
            Log.i(TAG, "✅ Qwen2-0.5B-Instruct successfully loaded in memory via llama.cpp NDK (batchSize=2048)!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load Qwen2-0.5B model", e)
            _llamaState.value = LlamaState.Error(e.message ?: "Model load error")
            false
        }
    }

    fun downloadModel(onComplete: (Boolean) -> Unit = {}) {
        if (_llamaState.value is LlamaState.Downloading) return

        // If a valid model or temp file already exists, don't re-download!
        if (isModelDownloaded()) {
            Log.i(TAG, "Model file already exists on disk. Marking as Unloaded...")
            _llamaState.value = LlamaState.Unloaded
            onComplete(true)
            return
        }

        scope.launch {
            val targetDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, MODEL_FILENAME)
            val tempFile = File(targetDir, "$MODEL_FILENAME.tmp")

            try {
                Log.i(TAG, "🌐 Initiating download of Qwen2-0.5B INT4 (~352MB) from Hugging Face...")
                val url = URL(MODEL_DOWNLOAD_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AuraDesk-Mobile-LLM-Downloader")
                }

                val totalLength = connection.contentLengthLong
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastProgressReport = 0

                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    val progress = if (totalLength > 0) ((downloaded * 100) / totalLength).toInt() else 0
                    if (progress != lastProgressReport) {
                        lastProgressReport = progress
                        _llamaState.value = LlamaState.Downloading(progress, downloaded, totalLength)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                // Promote temp file or load directly from tempFile
                promoteTempFile(tempFile, targetFile)
                val fileToLoad = if (targetFile.exists() && targetFile.length() > 100_000_000L) targetFile else tempFile

                if (fileToLoad.exists() && fileToLoad.length() > 100_000_000L) {
                    Log.i(TAG, "✅ Model download ready on disk at: ${fileToLoad.absolutePath}. Status set to Unloaded.")
                    _llamaState.value = LlamaState.Unloaded
                    onComplete(true)
                } else {
                    _llamaState.value = LlamaState.Error("Downloaded file incomplete")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _llamaState.value = LlamaState.Error(e.message ?: "Network error downloading model")
                onComplete(false)
            }
        }
    }

    /**
     * Phase 7 Prompt A: Auto-Reply System
     * High-speed, zero-hallucination topic extraction paired with deterministic focus dispatch.
     * Guarantees context-relevance without chatbot roleplay hallucinations.
     */
    suspend fun generateAutoReply(
        senderName: String,
        messageText: String,
        returnTime: String,
        userName: String = ""
    ): String = withContext(Dispatchers.IO) {
        val displayName = if (userName.isNotBlank()) userName.trim() else "the user"
        val safeMessage = if (messageText.length > 250) messageText.take(250) + "..." else messageText

        val model = llamaModel
        if (model != null && model.isLoaded) {
            try {
                PersonRadarDetector.isPausedForLlm = true
                val prompt = buildString {
                    append("<|im_start|>system\n")
                    append("Extract the subject or topic of the message as a short phrase (e.g. 'the auth PR', 'your resume', 'the design files'). If general, output 'your message'. Output ONLY the phrase.\n")
                    append("<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Alex: Can you review the payment auth PR before deployment?<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                    append("the payment auth PR<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Sarah: Where did you save the design files?<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                    append("the design files<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("$senderName: $safeMessage<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }

                Log.i(TAG, "🦙 Extracting topic with Qwen2-0.5B for $senderName (displayName='$displayName')...")
                val startTime = System.currentTimeMillis()

                val genConfig = LlamaConfig(
                    contextSize = 2048,
                    batchSize = 2048,
                    threads = 4,
                    temperature = 0.2f,
                    topP = 0.9f,
                    repeatPenalty = 1.15f,
                    maxTokens = 8
                )
                val rawResult = model.generate(prompt, genConfig)
                val latencyMs = System.currentTimeMillis() - startTime

                var topic = rawResult.trim()
                    .replace("<|im_end|>", "")
                    .replace("<|endoftext|>", "")
                    .replace("<|im_start|>", "")
                    .removePrefix("Assistant:")
                    .removePrefix("Reply:")
                    .removePrefix("Topic:")
                    .lines().firstOrNull()?.trim() ?: ""

                // Clean punctuation, quotes
                topic = topic.trim('"', '\'', '.', ',', ':', ';', ' ')

                // Sanitization filters
                topic = topic.replace(Regex("^(the\\s+)?(1st|2nd|3rd|first|second|third)\\s+(message|question|test|point|inquiry)[:,]?\\s*", RegexOption.IGNORE_CASE), "").trim()
                topic = topic.replace(Regex("^regarding\\s+", RegexOption.IGNORE_CASE), "").trim()
                topic = topic.replace(Regex("^about\\s+", RegexOption.IGNORE_CASE), "").trim()

                // If empty or hallucinatory, default to "your message"
                if (topic.isBlank() || topic.length > 50 || topic.contains("assistant", ignoreCase = true) || topic.contains("llama", ignoreCase = true) || topic.contains("review", ignoreCase = true) && topic.contains("found no", ignoreCase = true)) {
                    topic = "your message"
                }

                val finalReply = "Auto-Reply: Regarding $topic, $displayName is in focus until $returnTime and will reply right after. Please call twice if urgent."

                Log.i(TAG, "⚡ Qwen2-0.5B extracted topic '$topic' in ${latencyMs}ms => '$finalReply'")
                _lastGeneratedReply.value = finalReply
                return@withContext finalReply
            } catch (e: Throwable) {
                Log.e(TAG, "Llama generation error: ${e.message}", e)
            } finally {
                PersonRadarDetector.isPausedForLlm = false
            }
        }

        // Guaranteed fallback if model is still downloading or unloading
        val fallbackReply = "Auto-Reply: Regarding your message, $displayName is in a deep work focus session till $returnTime and will get back to you right after. Please call twice if urgent."
        _lastGeneratedReply.value = fallbackReply
        fallbackReply
    }

    suspend fun generateRaw(prompt: String, maxTokens: Int = 60, temperature: Float = 0.3f): String = withContext(Dispatchers.IO) {
        val model = llamaModel
        if (model != null && model.isLoaded) {
            val config = LlamaConfig(
                contextSize = 2048,
                batchSize = 2048,
                threads = 4,
                temperature = temperature,
                topP = 0.9f,
                repeatPenalty = 1.15f,
                maxTokens = maxTokens
            )
            return@withContext model.generate(prompt, config)
        }
        "Model not loaded"
    }

    /**
     * Phase 7 Prompt B: Visitor Summary System
     * "Summarize 10 sec talk into one task line Format Person - Task - Deadline."
     */
    suspend fun generateTaskSummary(
        personName: String,
        transcript: String
    ): String = withContext(Dispatchers.IO) {
        val model = llamaModel
        if (model != null && model.isLoaded && transcript.length > 5) {
            try {
                val prompt = buildString {
                    append("<|im_start|>system\n")
                    append("You are AuraDesk task extractor.\n")
                    append("Extract actionable desk interruption tasks into a single clean line.\n")
                    append("Format: Person — Action Item — Deadline\n\n")
                    append("RULES:\n")
                    append("1. Output ONLY the one-line task. No extra words, no explanation, no quotes.\n")
                    append("2. Infer implicit deadlines (e.g. 'before sprint demo' -> 'Today 4:00 PM (Sprint Demo)').\n")
                    append("3. Keep it crisp and professional.\n\n")
                    append("EXAMPLES:\n")
                    append("User: Person: Rahul. Transcript: Hey, make sure you merge the payment schema fix before the client call at 5\n")
                    append("Assistant: Rahul — Merge payment schema fix — Today 5:00 PM (Client Call)\n\n")
                    append("User: Person: Sneha. Transcript: Can you sign off on the design assets today?\n")
                    append("Assistant: Sneha — Review and sign off on design assets — Today EOD<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Person: $personName. Transcript: $transcript<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }

                val summaryConfig = LlamaConfig(
                    contextSize = 2048,
                    batchSize = 2048,
                    threads = 4,
                    temperature = 0.2f,
                    topP = 0.9f,
                    maxTokens = 40
                )
                val rawResult = model.generate(prompt, summaryConfig)
                var clean = rawResult.trim()
                    .replace("<|im_end|>", "")
                    .replace("<|endoftext|>", "")
                    .replace("<|im_start|>", "")
                    .removePrefix("Assistant:")
                    .removePrefix("Output:")
                    .trim()

                if (clean.contains("\n")) {
                    clean = clean.substringBefore("\n").trim()
                }

                if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length > 2) {
                    clean = clean.substring(1, clean.length - 1).trim()
                }

                if (clean.isNotBlank()) return@withContext clean
            } catch (e: Throwable) {
                Log.w(TAG, "Summary generation fallback: ${e.message}")
            }
        }

        // Rule-based fallback summary
        "$personName — Review request from desk interruption — Today"
    }

    fun calculateReturnTime(focusDurationMinutes: Int = 45): String {
        val returnTimestamp = System.currentTimeMillis() + (focusDurationMinutes * 60 * 1000L)
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(returnTimestamp))
    }

    fun close() {
        try {
            llamaModel?.close()
            llamaModel = null
            _llamaState.value = LlamaState.NotDownloaded
        } catch (e: Exception) {
            Log.w(TAG, "Error closing llama model", e)
        }
    }
}
