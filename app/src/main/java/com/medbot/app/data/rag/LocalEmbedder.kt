package com.medbot.app.data.rag

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlin.math.sqrt

/**
 * Release-owned embedding asset contract.
 *
 * The model and vocabulary are bundled from the verified
 * sentence-transformers/all-MiniLM-L6-v2 export used by the local reference
 * implementation. The checksum gate prevents an accidentally replaced asset
 * from silently changing the vector space of an existing Room index.
 */
data class LocalEmbeddingManifest(
    val modelAssetPath: String = "embedding/all-MiniLM-L6-v2.tflite",
    val vocabularyAssetPath: String = "embedding/vocab.txt",
    val version: String = "all-MiniLM-L6-v2-384d-wp-v1",
    val dimensions: Int = 384,
    val maxSequenceLength: Int = 128,
    val modelSha256: String = "0aac5b0b76be23ab94f065a7fab6e0daead5e57f6ff7d55e19a2641d6a81f276",
    val vocabularySha256: String = "07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3",
    val source: String = "sentence-transformers/all-MiniLM-L6-v2",
    val license: String = "Apache-2.0"
)

data class EmbeddingRuntimeInfo(
    val dimensions: Int,
    val maxSequenceLength: Int,
    val source: String,
    val license: String
)

/**
 * Real on-device MiniLM embedding boundary.
 *
 * There is deliberately no hash, random, zero-vector, or lexical fallback.
 * Missing or invalid assets raise [RagFailureCode.EMBEDDER_UNAVAILABLE] so
 * ingestion and retrieval fail closed instead of creating an unusable index.
 */
class LocalEmbedder(
    private val context: Context? = null,
    val dimensions: Int = DEFAULT_DIMENSIONS,
    val manifest: LocalEmbeddingManifest = LocalEmbeddingManifest()
) : Closeable {

    constructor(dimensions: Int) : this(context = null, dimensions = dimensions)

    init {
        require(dimensions > 0) { "Embedding dimensions must be positive" }
    }

    private val lock = Any()
    @Volatile private var runtime: Runtime? = null
    @Volatile private var initializationFailure: RagProcessingException? = null

    /** Loads and validates the real embedding runtime. */
    fun checkAvailability(): Result<EmbeddingRuntimeInfo> = runCatching {
        getRuntime()
        EmbeddingRuntimeInfo(
            dimensions = manifest.dimensions,
            maxSequenceLength = manifest.maxSequenceLength,
            source = manifest.source,
            license = manifest.license
        )
    }

    /** Generates one normalized 384-dimensional vector from the local model. */
    fun embed(text: String): FloatArray {
        if (text.isBlank()) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Cannot embed empty text")
        }

        val loaded = getRuntime()
        val encoded = loaded.tokenizer.encode(text, manifest.maxSequenceLength)
        val output = Array(1) { FloatArray(manifest.dimensions) }

        try {
            // TFLite Interpreter is not thread-safe. Serialise inference and
            // lifecycle operations so concurrent search keystrokes cannot
            // corrupt the native tensor state.
            synchronized(lock) {
                if (runtime !== loaded) {
                    throw RagProcessingException(
                        RagFailureCode.EMBEDDER_UNAVAILABLE,
                        "The local embedding runtime was unloaded during inference"
                    )
                }
                loaded.interpreter.runForMultipleInputsOutputs(
                    arrayOf(encoded.inputIds, encoded.attentionMask),
                    mapOf(0 to output)
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw RagProcessingException(
                RagFailureCode.EMBEDDER_UNAVAILABLE,
                "The local embedding model failed during inference",
                error
            )
        }

        val vector = output[0]
        if (vector.any { !it.isFinite() }) {
            throw RagProcessingException(
                RagFailureCode.EMBEDDER_UNAVAILABLE,
                "The local embedding model returned a non-finite vector"
            )
        }

        var normSquared = 0.0f
        vector.forEach { value -> normSquared += value * value }
        val norm = sqrt(normSquared.toDouble()).toFloat()
        if (!norm.isFinite() || norm <= 1.0e-8f) {
            throw RagProcessingException(
                RagFailureCode.EMBEDDER_UNAVAILABLE,
                "The local embedding model returned a zero vector"
            )
        }
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    fun vectorToCsv(vector: FloatArray): String {
        require(vector.size == dimensions) { "Embedding dimension mismatch" }
        require(vector.all { it.isFinite() }) { "Embedding contains a non-finite value" }
        return vector.joinToString(",")
    }

    fun csvToVector(csv: String): FloatArray {
        if (csv.isBlank()) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Stored embedding is empty")
        }
        val parts = csv.split(',')
        if (parts.size != dimensions) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Stored embedding dimension mismatch")
        }
        return FloatArray(dimensions) { index ->
            parts[index].toFloatOrNull()?.takeIf { it.isFinite() }
                ?: throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Stored embedding is invalid")
        }
    }

    override fun close() {
        synchronized(lock) {
            runtime?.interpreter?.close()
            runtime = null
        }
    }

    private fun getRuntime(): Runtime {
        runtime?.let { return it }
        initializationFailure?.let { throw it }

        synchronized(lock) {
            runtime?.let { return it }
            initializationFailure?.let { throw it }

            val appContext = context ?: fail("No Android context was supplied for the embedding asset")
            try {
                verifyAsset(appContext, manifest.modelAssetPath, manifest.modelSha256)
                verifyAsset(appContext, manifest.vocabularyAssetPath, manifest.vocabularySha256)

                val vocabulary = loadVocabulary(appContext)
                val interpreter = Interpreter(
                    FileUtil.loadMappedFile(appContext, manifest.modelAssetPath),
                    Interpreter.Options().setNumThreads(INFERENCE_THREADS)
                )
                validateTensorContract(interpreter)

                return Runtime(interpreter, WordPieceTokenizer(vocabulary)).also { runtime = it }
            } catch (error: RagProcessingException) {
                initializationFailure = error
                throw error
            } catch (error: Throwable) {
                val failure = RagProcessingException(
                    RagFailureCode.EMBEDDER_UNAVAILABLE,
                    "The verified local embedding asset could not be initialized",
                    error
                )
                initializationFailure = failure
                throw failure
            }
        }
    }

    private fun validateTensorContract(interpreter: Interpreter) {
        if (interpreter.inputTensorCount < 2 || interpreter.outputTensorCount < 1) {
            interpreter.close()
            fail("Embedding model tensor contract is incomplete")
        }

        val expectedInputShape = intArrayOf(1, manifest.maxSequenceLength)
        val initialInputIds = interpreter.getInputTensor(0)
        val initialAttentionMask = interpreter.getInputTensor(1)
        if (!initialInputIds.shape().contentEquals(expectedInputShape) ||
            !initialAttentionMask.shape().contentEquals(expectedInputShape)
        ) {
            try {
                // The verified MiniLM export exposes a dynamic [1, 1] allocation
                // until the caller resizes it. Refuse fixed, incompatible models.
                interpreter.resizeInput(0, expectedInputShape)
                interpreter.resizeInput(1, expectedInputShape)
                interpreter.allocateTensors()
            } catch (error: Throwable) {
                interpreter.close()
                fail("Embedding model input tensors cannot be resized to [1, 128]")
            }
        }

        val inputIds = interpreter.getInputTensor(0)
        val attentionMask = interpreter.getInputTensor(1)
        val output = interpreter.getOutputTensor(0)
        if (!inputIds.shape().contentEquals(expectedInputShape) ||
            !attentionMask.shape().contentEquals(expectedInputShape) ||
            inputIds.dataType() != DataType.INT32 ||
            attentionMask.dataType() != DataType.INT32
        ) {
            interpreter.close()
            fail("Embedding model input tensor contract is not [1, 128] INT32")
        }

        val outputSize = output.shape().fold(1) { total, size -> total * size }
        if (output.dataType() != DataType.FLOAT32 || outputSize != manifest.dimensions) {
            interpreter.close()
            fail("Embedding model output tensor contract is not a 384-dimensional FLOAT32 vector")
        }
    }

    private fun loadVocabulary(context: Context): Map<String, Int> =
        context.assets.open(manifest.vocabularyAssetPath).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence()
                .mapIndexed { index, token -> token.trim() to index }
                .filter { it.first.isNotEmpty() }
                .toMap()
                .also { vocabulary ->
                    if (vocabulary[CLS_TOKEN] == null || vocabulary[SEP_TOKEN] == null || vocabulary[UNK_TOKEN] == null) {
                        fail("Embedding vocabulary is missing required BERT special tokens")
                    }
                }
        }

    private fun verifyAsset(context: Context, path: String, expectedSha256: String) {
        val actual = MessageDigest.getInstance("SHA-256").let { digest ->
            context.assets.open(path).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            fail("Embedding asset checksum does not match the pinned manifest")
        }
    }

    private fun fail(message: String): Nothing = throw RagProcessingException(
        RagFailureCode.EMBEDDER_UNAVAILABLE,
        message
    )

    private data class Runtime(
        val interpreter: Interpreter,
        val tokenizer: WordPieceTokenizer
    )

    private data class EncodedInput(
        val inputIds: Array<IntArray>,
        val attentionMask: Array<IntArray>
    )

    /** BERT WordPiece tokenizer matching the MiniLM export's vocabulary. */
    private class WordPieceTokenizer(private val vocabulary: Map<String, Int>) {
        fun encode(text: String, maxLength: Int): EncodedInput {
            val clsId = vocabulary[CLS_TOKEN] ?: error("[CLS] missing")
            val sepId = vocabulary[SEP_TOKEN] ?: error("[SEP] missing")
            val unkId = vocabulary[UNK_TOKEN] ?: error("[UNK] missing")
            val padId = vocabulary[PAD_TOKEN] ?: 0

            val tokenIds = mutableListOf(clsId)
            // Match the reproducible Python indexer: split on every
            // non-letter/non-number character, including Unicode punctuation
            // and symbols. This keeps query vectors in the same space as the
            // precomputed BankBook vectors.
            text.lowercase(Locale.US)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
                .forEach { word ->
                    wordPiece(word).forEach { token -> tokenIds += vocabulary[token] ?: unkId }
                }
            tokenIds += sepId

            val truncated = tokenIds.take(maxLength)
            val ids = IntArray(maxLength) { index -> truncated.getOrElse(index) { padId } }
            val mask = IntArray(maxLength) { index -> if (index < truncated.size) 1 else 0 }
            return EncodedInput(arrayOf(ids), arrayOf(mask))
        }

        private fun wordPiece(word: String): List<String> {
            if (word.isBlank()) return emptyList()
            val pieces = mutableListOf<String>()
            var start = 0
            while (start < word.length) {
                var end = word.length
                var matched: String? = null
                while (start < end) {
                    val candidate = if (start == 0) {
                        word.substring(start, end)
                    } else {
                        "##${word.substring(start, end)}"
                    }
                    if (vocabulary.containsKey(candidate)) {
                        matched = candidate
                        break
                    }
                    end--
                }
                if (matched == null) return listOf(UNK_TOKEN)
                pieces += matched
                start = end
            }
            return pieces
        }
    }

    companion object {
        const val DEFAULT_DIMENSIONS = 384
        private const val INFERENCE_THREADS = 2
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val UNK_TOKEN = "[UNK]"
        private const val PAD_TOKEN = "[PAD]"
    }
}
