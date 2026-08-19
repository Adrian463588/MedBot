package com.medbot.app.data.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.medbot.app.domain.model.DoctorAgent
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.repository.LocalLlmGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

/** Explicit model-load failures. */
enum class ModelFailureCode {
    MODEL_UNAVAILABLE,
    MODEL_INVALID,
    ENGINE_INITIALIZATION_FAILED,
    VISION_UNAVAILABLE
}

/** Typed failure used when a model cannot be validated or initialized. */
class ModelUnavailableException(
    val code: ModelFailureCode,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/** Typed result for callers that need more than the legacy Boolean repository API. */
sealed interface ModelLoadResult {
    data class Loaded(val path: String, val backend: String, val supportsVision: Boolean) : ModelLoadResult
    data class Unavailable(val code: ModelFailureCode, val message: String) : ModelLoadResult
}

/** Real LiteRT-LM wrapper. No model means no inference response. */
class LlmInferenceEngine(context: Context) : LocalLlmGateway {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.cacheDir, "litertlm-models")

    @Volatile private var runtime: RuntimeHandle? = null
    @Volatile private var activeModelPath: String? = null
    @Volatile private var lastFailure: ModelLoadResult.Unavailable? = null
    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()
    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    /** True only after native engine initialization and conversation creation succeed. */
    fun isModelLoaded(): Boolean = runtime != null

    /** Prepared path used by the initialized native engine. */
    fun getActiveModelPath(): String? = activeModelPath

    /** Last explicit load failure, if one occurred. */
    fun getLastFailure(): ModelLoadResult.Unavailable? = lastFailure

    /** Legacy repository entry point; returns false on every unavailable state. */
    suspend fun loadModel(
        path: String,
        backend: String = "AUTO",
        expectedSizeBytes: Long? = null,
        expectedSha256: String? = null,
        requiresVision: Boolean = false
    ): Boolean = loadModelResult(
        path = path,
        backend = backend,
        expectedSizeBytes = expectedSizeBytes,
        expectedSha256 = expectedSha256,
        requiresVision = requiresVision
    ) is ModelLoadResult.Loaded

    /** Validates a local/content model and initializes the real LiteRT-LM engine. */
    suspend fun loadModelResult(
        path: String,
        backend: String = "AUTO",
        expectedSizeBytes: Long? = null,
        expectedSha256: String? = null,
        requiresVision: Boolean = false
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        val model = try {
            prepareModel(path, expectedSizeBytes, expectedSha256)
        } catch (e: ModelUnavailableException) {
            return@withContext rememberFailure(e.code, e.message ?: e.code.name)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "model preparation failed", t)
            return@withContext rememberFailure(ModelFailureCode.MODEL_UNAVAILABLE, "Model source could not be read")
        }

        val candidate = try {
            createRuntime(model, backend, requiresVision)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT-LM initialization failed", t)
            null
        }
        if (candidate == null) {
            return@withContext rememberFailure(
                if (requiresVision) ModelFailureCode.VISION_UNAVAILABLE
                else ModelFailureCode.ENGINE_INITIALIZATION_FAILED,
                if (requiresVision) "LiteRT-LM vision runtime initialization failed"
                else "LiteRT-LM engine initialization failed"
            )
        }

        val old = synchronized(this@LlmInferenceEngine) {
            val previous = runtime
            runtime = candidate
            activeModelPath = model.absolutePath
            lastFailure = null
            _modelLoaded.value = true
            _activeModelName.value = model.absolutePath
            previous
        }
        old?.close()
        ModelLoadResult.Loaded(model.absolutePath, candidate.backend, candidate.supportsVision)
    }

    /** Closes native resources deterministically. */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        val old = synchronized(this@LlmInferenceEngine) {
            val previous = runtime
            runtime = null
            activeModelPath = null
            _modelLoaded.value = false
            _activeModelName.value = null
            previous
        }
        old?.close()
    }

    /** Builds the prompt consumed by the real local model. */
    override fun buildFullSystemPrompt(agent: DoctorAgent, persona: PersonaConfig, ragContext: String?): String {
        val language = if (persona.language.code == "en") {
            "LANGUAGE RULE: Respond in English."
        } else {
            "ATURAN BAHASA: Jawab dalam Bahasa Indonesia."
        }
        val safety = """
            MEDICAL SAFETY:
            You are an on-device medical information assistant, not a diagnosing clinician.
            Do not claim a definitive diagnosis. State uncertainty and recommend professional care.
            Direct emergency symptoms to local emergency services.
        """.trimIndent()
        val custom = persona.customInstructions.takeIf { it.isNotBlank() }?.let { "USER INSTRUCTIONS: $it" }
        val profile = persona.patientProfileSummary.takeIf { it.isNotBlank() }?.let { "PATIENT PROFILE: $it" }
        val grounding = ragContext?.takeIf { it.isNotBlank() }?.let {
            "CLINICAL DOCUMENT CONTEXT:\n$it\nUse only supported facts and cite uncertainty."
        }
        return listOf(
            safety,
            "SPECIALIST: ${agent.displayNameEn} (${agent.specialtyEn})",
            if (persona.language.code == "en") agent.systemPromptEn else agent.systemPromptId,
            persona.tone.promptModifier,
            persona.depth.promptModifier,
            language,
            profile,
            custom,
            grounding
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /** Streams text deltas from LiteRT-LM's initialized conversation. */
    override fun streamInference(
        prompt: String,
        systemPrompt: String,
        @Suppress("UNUSED_PARAMETER") agent: DoctorAgent
    ): Flow<String> = callbackFlow {
        val active = runtime
        if (active == null) {
            close(ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Model is not initialized"))
            return@callbackFlow
        }
        val request = "$systemPrompt\n\nUSER QUERY:\n$prompt".trim()
        if (request.isBlank()) {
            close(ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Prompt is empty"))
            return@callbackFlow
        }
        try {
            active.conversation.sendMessageAsync(
                Contents.of(listOf(Content.Text(request))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val delta = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                        if (delta.isNotEmpty()) trySend(delta)
                    }

                    override fun onDone() { close() }

                    override fun onError(t: Throwable) {
                        Log.e(TAG, "LiteRT-LM generation failed", t)
                        close(t)
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            close(t)
        }
        awaitClose { runCatching { active.conversation.cancelProcess() } }
    }.flowOn(Dispatchers.IO)

    private fun rememberFailure(code: ModelFailureCode, message: String): ModelLoadResult.Unavailable {
        return ModelLoadResult.Unavailable(code, message).also { lastFailure = it }
    }

    private fun prepareModel(path: String, expectedSize: Long?, expectedSha256: String?): File {
        if (path.isBlank()) throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Model source is empty")
        if (expectedSize != null && expectedSize <= 0L) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Expected model size is invalid")
        }
        val checksum = expectedSha256?.trim()?.lowercase(Locale.US)
        if (checksum != null && !SHA256_PATTERN.matches(checksum)) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Expected SHA-256 is invalid")
        }

        val uri = Uri.parse(path)
        val file = when (uri.scheme?.lowercase(Locale.US)) {
            "content" -> copyContentUri(uri)
            "file" -> uri.path?.let(::File)
            else -> File(path)
        } ?: throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Model path is unreadable")
        validateFile(file, expectedSize, checksum)
        return file
    }

    private fun copyContentUri(uri: Uri): File {
        val name = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { it.isNotBlank() }
            ?: throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Content URI has no display name")
        validateExtension(name)
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Model cache is unavailable")
        }
        val safeName = name.replace(UNSAFE_FILENAME, "_").take(128)
        val target = File(cacheDirectory, safeName)
        val part = File.createTempFile("model-", ".part", cacheDirectory)
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Content URI cannot be opened")
            input.use { source ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        if (total > MAX_MODEL_BYTES || part.parentFile?.usableSpace ?: 0L < count) {
                            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Model exceeds storage limits")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            atomicMove(part, target)
            return target
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun validateFile(file: File, expectedSize: Long?, expectedSha256: String?) {
        validateExtension(file.name)
        if (!file.isFile || !file.canRead()) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Model file is not readable")
        }
        val length = file.length()
        if (length <= 0L || length > MAX_MODEL_BYTES) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Model file size is invalid")
        }
        if (expectedSize != null && length != expectedSize) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Model size does not match manifest")
        }
        if (expectedSha256 != null && sha256(file) != expectedSha256) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "Model SHA-256 does not match manifest")
        }
    }

    private fun validateExtension(name: String) {
        if (!name.endsWith(".litertlm", ignoreCase = true)) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_INVALID, "LiteRT-LM requires a .litertlm model")
        }
    }

    private fun createRuntime(
        model: File,
        requestedBackend: String,
        requiresVision: Boolean
    ): RuntimeHandle? {
        val selected = if (requestedBackend.equals("GPU", true)) "gpu" else "cpu"
        val attempts = if (requiresVision) {
            buildList {
                add(EngineAttempt(selected, selected, true))
                if (selected == "gpu") add(EngineAttempt("gpu", "cpu", true))
                add(EngineAttempt("cpu", "cpu", true))
            }
        } else {
            buildList {
                add(EngineAttempt(selected, null, false))
                if (selected == "gpu") add(EngineAttempt("cpu", null, false))
            }
        }.distinct()
        for (attempt in attempts) {
            var engine: Engine? = null
            try {
                val config = if (attempt.visionBackend != null) {
                    EngineConfig(
                        modelPath = model.absolutePath,
                        backend = backendOf(attempt.textBackend),
                        visionBackend = backendOf(attempt.visionBackend),
                        cacheDir = cacheDirectory.absolutePath
                    )
                } else {
                    EngineConfig(
                        modelPath = model.absolutePath,
                        backend = backendOf(attempt.textBackend),
                        cacheDir = cacheDirectory.absolutePath
                    )
                }
                engine = Engine(config)
                engine.initialize()
                return RuntimeHandle(engine, engine.createConversation(), attempt.textBackend, attempt.supportsVision)
            } catch (e: CancellationException) {
                engine?.close()
                throw e
            } catch (t: Throwable) {
                engine?.close()
                Log.w(TAG, "LiteRT-LM configuration failed", t)
            }
        }
        return null
    }

    private fun backendOf(name: String): Backend = if (name == "gpu") Backend.GPU() else Backend.CPU()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (t: Throwable) {
            throw ModelUnavailableException(ModelFailureCode.MODEL_UNAVAILABLE, "Atomic model promotion unavailable", t)
        }
    }

    private data class EngineAttempt(val textBackend: String, val visionBackend: String?, val supportsVision: Boolean)

    private class RuntimeHandle(
        private val engine: Engine,
        val conversation: Conversation,
        val backend: String,
        val supportsVision: Boolean
    ) {
        fun close() {
            runCatching { conversation.close() }
            runCatching { engine.close() }
        }
    }

    companion object {
        private const val TAG = "LlmInferenceEngine"
        private const val MAX_MODEL_BYTES = 16L * 1024L * 1024L * 1024L
        private val UNSAFE_FILENAME = Regex("[^A-Za-z0-9._-]")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
