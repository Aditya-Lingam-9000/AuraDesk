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
                contextSize = 1024
                threads = 4
                temperature = 0.6f
                maxTokens = 80
                stopSequences = listOf("<|im_end|>", "<|endoftext|>", "\n\nUser:", "User:")
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
     * "You are focus guard for userName deep work till returnTime write short polite reply
     *  style from past messages include return time offer urgent tap Max 30 words"
     */
    suspend fun generateAutoReply(
        senderName: String,
        messageText: String,
        returnTime: String,
        userName: String = "Arjun"
    ): String = withContext(Dispatchers.IO) {
        val model = llamaModel
        if (model != null && model.isLoaded) {
            try {
                val prompt = buildString {
                    append("<|im_start|>system\n")
                    append("You are AuraDesk, focus guard for $userName. ")
                    append("$userName is in deep work till $returnTime. ")
                    append("Write a short, polite reply in under 30 words. ")
                    append("Include return time $returnTime. Offer urgent call override if blocking. ")
                    append("Keep the same language as incoming message.<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Message from $senderName: $messageText<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }

                Log.i(TAG, "🦙 Generating on-device auto-reply for $senderName with Qwen2-0.5B...")
                val startTime = System.currentTimeMillis()

                val generated = StringBuilder()
                model.generateStream(prompt).collect { token ->
                    val cleanToken = token.replace("<|im_end|>", "").replace("<|endoftext|>", "")
                    generated.append(cleanToken)
                }

                val latencyMs = System.currentTimeMillis() - startTime
                val cleanResult = generated.toString().trim()
                Log.i(TAG, "⚡ Qwen2-0.5B generated reply in ${latencyMs}ms: '$cleanResult'")

                if (cleanResult.isNotBlank()) {
                    _lastGeneratedReply.value = cleanResult
                    return@withContext cleanResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Llama generation fallback: ${e.message}")
            }
        }

        // Guaranteed fallback if model is still downloading or unloading
        val fallbackReply = "Hey $senderName! $userName is in a deep work focus session till $returnTime. I'll get back to you right after. Please call twice if urgent."
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
                    append("You are AuraDesk task extractor. Summarize the visitor conversation into a single line format: Person - Task - Deadline.<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Person: $personName\nTranscript: $transcript<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }

                val result = StringBuilder()
                model.generateStream(prompt).collect { token ->
                    result.append(token.replace("<|im_end|>", "").replace("<|endoftext|>", ""))
                }

                val clean = result.toString().trim()
                if (clean.isNotBlank()) return@withContext clean
            } catch (e: Exception) {
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
