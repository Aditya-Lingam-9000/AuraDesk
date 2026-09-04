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

sealed class LlamaState {
    object NotDownloaded : LlamaState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : LlamaState()
    object Loading : LlamaState()
    object Ready : LlamaState()
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
        val extDir = context.getExternalFilesDir("models") ?: context.filesDir
        val intDir = File(context.filesDir, "models")

        val candidates = listOf(
            File(extDir, MODEL_FILENAME),
            File(extDir, "$MODEL_FILENAME.tmp"),
            File(intDir, MODEL_FILENAME),
            File(intDir, "$MODEL_FILENAME.tmp")
        )
        val readyCandidate = candidates.firstOrNull { it.exists() && it.length() > 100_000_000L }

        if (readyCandidate != null) {
            Log.i(TAG, "🎯 Found valid model file on device: ${readyCandidate.absolutePath} (${readyCandidate.length() / (1024 * 1024)}MB)")
            if (llamaModel != null && llamaModel?.isLoaded == true) {
                _llamaState.value = LlamaState.Ready
            } else {
                scope.launch { loadModel() }
            }
        } else {
            _llamaState.value = LlamaState.NotDownloaded
        }
    }

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
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
                threads = 4
                temperature = 0.25f
                maxTokens = 80
            }
            _llamaState.value = LlamaState.Ready
            Log.i(TAG, "✅ Qwen2-0.5B-Instruct successfully loaded in memory via llama.cpp NDK!")
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
        val existing = getModelFile()
        if (existing.exists() && existing.length() > 100_000_000L) {
            Log.i(TAG, "Model file already exists on disk (${existing.length() / (1024 * 1024)}MB). Loading directly...")
            scope.launch {
                val success = loadModel()
                onComplete(success)
            }
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
                    Log.i(TAG, "✅ Model download ready at: ${fileToLoad.absolutePath}")
                    loadModel()
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
     * High-quality few-shot prompt with strict relevance and tone matching.
     */
    suspend fun generateAutoReply(
        senderName: String,
        messageText: String,
        returnTime: String,
        userName: String = ""
    ): String = withContext(Dispatchers.IO) {
        val displayName = if (userName.isNotBlank()) userName.trim() else "the user"

        val model = llamaModel
        if (model != null && model.isLoaded) {
            try {
                val prompt = buildString {
                    append("<|im_start|>system\n")
                    append("You are AuraDesk, an executive AI focus assistant for $displayName.\n")
                    append("$displayName is currently locked in a deep work focus session and will return at $returnTime.\n\n")
                    append("TASK:\n")
                    append("Generate a single, natural, highly relevant auto-reply to the incoming message.\n\n")
                    append("STRICT RULES:\n")
                    append("1. PREFIX: The output MUST start with 'Auto-Reply: ' followed by the message.\n")
                    append("2. RELEVANCE: Directly reference the specific topic, action, or question in the message (e.g. PR review, bug, meeting, report, greeting, lunch). Never send a generic template.\n")
                    append("3. RETURN TIME: Explicitly state $displayName will reply right after $returnTime.\n")
                    append("4. URGENCY: Offer that if it is an urgent blocker, they can call twice.\n")
                    append("5. IDENTITY: Refer to the focused person as '$displayName'.\n")
                    append("6. TONE & LANGUAGE: Mirror the sender's exact language, style, and vocabulary (e.g. professional English, casual English, or Hinglish like 'bhai/yaar/hote hi').\n")
                    append("7. LENGTH: 1 to 2 short sentences (maximum 28 words).\n")
                    append("8. OUTPUT FORMAT: Output ONLY 'Auto-Reply: <text>'. Do NOT include quotes, conversational filler, or preamble.\n\n")
                    append("FEW-SHOT EXAMPLES:\n\n")
                    append("User: Message from Contact: Can you review the payment auth PR before deployment?\n")
                    append("Assistant: Auto-Reply: $displayName is in deep work till $returnTime and will review your payment auth PR right after. Please call twice if urgent.\n\n")
                    append("User: Message from Contact: Bhai sham ko chai pine chalte hain kya?\n")
                    append("Assistant: Auto-Reply: Abhi focus session chal raha hai $returnTime tak. Free hote hi ping karta hoon chai ke liye!\n\n")
                    append("User: Message from Contact: Need the Q3 infrastructure budget sheet ASAP.\n")
                    append("Assistant: Auto-Reply: $displayName is in a focus block until $returnTime. The Q3 infrastructure budget sheet will be sent immediately after. Please call if critical.\n\n")
                    append("User: Message from Contact: Good morning! Let me know when you get a chance to test the new build.\n")
                    append("Assistant: Auto-Reply: Good morning! $displayName is in focus till $returnTime and will test the new build right after.<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Message from $senderName: $messageText<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                    append("Auto-Reply: ")
                }

                Log.i(TAG, "🦙 Generating on-device auto-reply for $senderName with Qwen2-0.5B (displayName='$displayName')...")
                val startTime = System.currentTimeMillis()

                // Robust blocking generation in native C++ without JNI callback bridge crashes
                val rawResult = model.generate(prompt)

                val latencyMs = System.currentTimeMillis() - startTime
                var cleanResult = rawResult.trim()
                    .replace("<|im_end|>", "")
                    .replace("<|endoftext|>", "")
                    .replace("<|im_start|>", "")
                    .removePrefix("Assistant:")
                    .removePrefix("Reply:")
                    .removePrefix("AuraDesk:")
                    .removePrefix("Output:")
                    .trim()

                // Cut off accidental turn repeats
                if (cleanResult.contains("<|im_start|>")) {
                    cleanResult = cleanResult.substringBefore("<|im_start|>").trim()
                }
                if (cleanResult.contains("User:")) {
                    cleanResult = cleanResult.substringBefore("User:").trim()
                }
                if (cleanResult.contains("\n\n")) {
                    cleanResult = cleanResult.substringBefore("\n\n").trim()
                }

                // Strip surrounding quotes if model added them
                if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"") && cleanResult.length > 2) {
                    cleanResult = cleanResult.substring(1, cleanResult.length - 1).trim()
                }
                if (cleanResult.startsWith("'") && cleanResult.endsWith("'") && cleanResult.length > 2) {
                    cleanResult = cleanResult.substring(1, cleanResult.length - 1).trim()
                }

                // Ensure the output cleanly starts with "Auto-Reply: "
                val finalReply = if (cleanResult.startsWith("Auto-Reply:", ignoreCase = true)) {
                    "Auto-Reply: " + cleanResult.substring("Auto-Reply:".length).trim()
                } else {
                    "Auto-Reply: $cleanResult"
                }

                Log.i(TAG, "⚡ Qwen2-0.5B generated high-quality reply in ${latencyMs}ms: '$finalReply'")

                if (finalReply.isNotBlank()) {
                    _lastGeneratedReply.value = finalReply
                    return@withContext finalReply
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Llama generation error: ${e.message}", e)
            }
        }

        // Guaranteed fallback if model is still downloading or unloading
        val fallbackReply = "Auto-Reply: $displayName is in a deep work focus session till $returnTime and will get back to you right after. Please call twice if urgent."
        _lastGeneratedReply.value = fallbackReply
        fallbackReply
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

                val rawResult = model.generate(prompt)
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
