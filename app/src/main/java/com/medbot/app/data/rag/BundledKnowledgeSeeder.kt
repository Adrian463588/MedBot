package com.medbot.app.data.rag

import android.content.Context
import com.medbot.app.domain.model.RagDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Release-owned BankBook corpus metadata. The bytes are copied from the
 * cleaned source file into the APK asset and are verified again before Room
 * indexing; these values are not a generated document or a model response.
 */
object BankBookCorpusManifest {
    const val ASSET_FILE_NAME = "bankbook_rag_chunks_medgemma.jsonl"
    const val ASSET_URI = "asset://bankbook/DataCleaned/rag_chunks_medgemma.jsonl"
    const val VERSION = "2.2.0"
    const val RECORD_COUNT = 1132
    const val BYTE_SIZE = 2_642_327L
    const val SHA256 = "08dc04293e6e4b36e811b64cd3a0ac165962ea484d16799d97e530a4410b629a"
    const val SOURCE = "BankBook/DataCleaned/rag_chunks_medgemma.jsonl"
    const val EMBEDDINGS_ASSET_FILE_NAME = "embedding/bankbook_embeddings.f32"
    const val EMBEDDING_VERSION = "all-MiniLM-L6-v2-384d-wp-v1"
    const val EMBEDDING_VECTOR_COUNT = 3113
    const val EMBEDDING_DIMENSIONS = 384
    const val EMBEDDINGS_BYTE_SIZE = 4_781_568L
    const val EMBEDDINGS_SHA256 = "5c5b897c436126bda7814f24676e021b50302e46c7f5c99e85f4e1c0341bf95e"
}

sealed interface BundledKnowledgeState {
    data object NotStarted : BundledKnowledgeState
    data object Indexing : BundledKnowledgeState
    data class Ready(val document: RagDocument) : BundledKnowledgeState
    data class Failed(val reason: String) : BundledKnowledgeState
}

/**
 * Idempotently embeds the verified BankBook JSONL into the existing Room RAG
 * tables. Indexing is owned by the Application scope and never blocks Compose.
 */
class BundledKnowledgeSeeder(
    private val context: Context,
    private val ragOrchestrator: RagOrchestrator
) {
    private val _state = MutableStateFlow<BundledKnowledgeState>(BundledKnowledgeState.NotStarted)
    val state: StateFlow<BundledKnowledgeState> = _state.asStateFlow()
    private val runningJob = AtomicReference<Job?>(null)

    /** Starts one background seed operation; repeated calls are no-ops. */
    fun start(scope: CoroutineScope) {
        if (runningJob.get() != null || _state.value is BundledKnowledgeState.Ready) return
        val job = scope.launch {
            runSeed()
        }
        if (!runningJob.compareAndSet(null, job)) {
            job.cancel()
            return
        }
        job.invokeOnCompletion {
            runningJob.compareAndSet(job, null)
        }
    }

    private suspend fun runSeed() {
        _state.value = BundledKnowledgeState.Indexing
        try {
            val existing = ragOrchestrator.getDocumentByFileUri(BankBookCorpusManifest.ASSET_URI)
            if (existing != null && isCurrent(existing)) {
                _state.value = BundledKnowledgeState.Ready(existing)
                return
            }

            // Validate the candidate vector asset before touching a previous
            // index. A corrupt update must not remove a usable prior index.
            val embeddings = loadPrecomputedEmbeddings()

            val result = context.assets.open(BankBookCorpusManifest.ASSET_FILE_NAME).use { input ->
                ragOrchestrator.ingestDocumentWithPrecomputedEmbeddings(
                    fileName = BankBookCorpusManifest.ASSET_FILE_NAME,
                    fileUri = BankBookCorpusManifest.ASSET_URI,
                    mimeType = "application/x-ndjson",
                    inputStream = input,
                    embeddings = embeddings,
                    documentId = BANKBOOK_DOCUMENT_ID,
                    previousDocumentId = existing?.id
                )
            }
            val document = result.getOrElse { throw it }
            if (!isCurrent(document)) {
                ragOrchestrator.deleteDocument(document.id)
                error("Bundled corpus checksum or embedding provenance did not validate")
            }
            _state.value = BundledKnowledgeState.Ready(document)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            _state.value = BundledKnowledgeState.Failed(
                error.message?.takeIf { it.isNotBlank() } ?: "Bundled corpus indexing failed"
            )
        }
    }

    private fun isCurrent(document: RagDocument): Boolean =
        document.id == BANKBOOK_DOCUMENT_ID &&
            document.fileName == BankBookCorpusManifest.ASSET_FILE_NAME &&
            document.fileUri == BankBookCorpusManifest.ASSET_URI &&
            document.fileSize == BankBookCorpusManifest.BYTE_SIZE &&
            document.sha256.equals(BankBookCorpusManifest.SHA256, ignoreCase = true) &&
            document.chunkCount == BankBookCorpusManifest.EMBEDDING_VECTOR_COUNT &&
            ragOrchestrator.hasCurrentEmbedding(document)

    private fun loadPrecomputedEmbeddings(): List<FloatArray> {
        val expectedByteSize = BankBookCorpusManifest.EMBEDDINGS_BYTE_SIZE.toInt()
        val bytes = context.assets.open(BankBookCorpusManifest.EMBEDDINGS_ASSET_FILE_NAME).use { input ->
            val output = ByteArrayOutputStream(expectedByteSize)
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > expectedByteSize) {
                    error("Precomputed embedding asset is larger than its manifest")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.size.toLong() != BankBookCorpusManifest.EMBEDDINGS_BYTE_SIZE) {
            error("Precomputed embedding asset size does not match its manifest")
        }
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        if (!actualSha256.equals(BankBookCorpusManifest.EMBEDDINGS_SHA256, ignoreCase = true)) {
            error("Precomputed embedding asset checksum does not match its manifest")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(BankBookCorpusManifest.EMBEDDING_VECTOR_COUNT) {
            FloatArray(BankBookCorpusManifest.EMBEDDING_DIMENSIONS) {
                buffer.float
            }.also { vector ->
                if (vector.any { !it.isFinite() }) {
                    error("Precomputed embedding asset contains a non-finite vector")
                }
            }
        }
    }

    companion object {
        /** Stable ID prevents duplicate corpus documents after reinstall/restart. */
        const val BANKBOOK_DOCUMENT_ID = "bankbook-cleaned-v2"
    }
}
