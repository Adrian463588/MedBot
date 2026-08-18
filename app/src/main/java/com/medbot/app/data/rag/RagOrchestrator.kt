package com.medbot.app.data.rag

import com.medbot.app.data.local.dao.RagDao
import com.medbot.app.data.local.entities.DocChunkEntity
import com.medbot.app.data.local.entities.RagDocumentEntity
import com.medbot.app.domain.model.Citation
import com.medbot.app.domain.model.DocChunk
import com.medbot.app.domain.model.RagDocument
import com.medbot.app.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

/** Coordinates real local parsing, embedding, persistence, and retrieval. */
class RagOrchestrator(
    private val ragDao: RagDao,
    private val parser: DocumentParser = DocumentParser(),
    private val chunker: DocumentChunker = DocumentChunker(),
    private val embedder: LocalEmbedder = LocalEmbedder(),
    private val vectorEngine: VectorSearchEngine = VectorSearchEngine()
) {
    fun getDocumentsFlow(): Flow<List<RagDocument>> = ragDao.getDocuments().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun ingestDocument(
        fileName: String,
        fileUri: String,
        mimeType: String,
        inputStream: InputStream
    ): Result<RagDocument> = withContext(Dispatchers.IO) {
        try {
            val parsed = parser.parse(inputStream, fileName, mimeType)
            val docId = UUID.randomUUID().toString()
            val chunks = chunker.chunkDocument(docId, parsed, embedder)
            if (chunks.isEmpty()) {
                throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Document produced no chunks")
            }
            val document = RagDocumentEntity(
                id = docId,
                fileName = parsed.fileName,
                fileUri = fileUri,
                mimeType = mimeType,
                fileSize = parsed.byteSize,
                pageCount = parsed.totalPageCount,
                chunkCount = chunks.size,
                sha256 = parsed.sha256,
                indexedAt = System.currentTimeMillis()
            )
            val entities = chunks.map { chunk ->
                DocChunkEntity(
                    id = chunk.id,
                    docId = chunk.docId,
                    chunkIndex = chunk.chunkIndex,
                    textContent = chunk.textContent,
                    pageNumber = chunk.pageNumber,
                    sectionTitle = chunk.sectionTitle,
                    embeddingCsv = embedder.vectorToCsv(chunk.embedding)
                )
            }
            ragDao.insertDocument(document)
            ragDao.insertChunks(entities)
            Result.success(document.toDomain())
        } catch (error: CancellationException) {
            throw error
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun searchSimilar(query: String, topK: Int = 4): List<SearchResult> = withContext(Dispatchers.IO) {
        val queryVector = embedder.embed(query)
        val documents = ragDao.getDocuments().first().associateBy { it.id }
        val chunks = ragDao.getAllChunks().mapNotNull { entity ->
            val document = documents[entity.docId] ?: return@mapNotNull null
            val chunk = DocChunk(
                id = entity.id,
                docId = entity.docId,
                chunkIndex = entity.chunkIndex,
                textContent = entity.textContent,
                pageNumber = entity.pageNumber,
                sectionTitle = entity.sectionTitle,
                embedding = embedder.csvToVector(entity.embeddingCsv)
            )
            chunk to document.fileName
        }
        vectorEngine.searchTopK(queryVector, chunks, topK = topK)
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) { ragDao.deleteDocument(docId) }

    suspend fun getChunkCount(): Int = withContext(Dispatchers.IO) { ragDao.getChunkCount() }

    fun buildContextAndCitations(searchResults: List<SearchResult>): Pair<String, List<Citation>> {
        if (searchResults.isEmpty()) return "" to emptyList()
        val context = searchResults.mapIndexed { index, result ->
            val location = if (result.chunk.pageNumber > 0) {
                "page ${result.chunk.pageNumber}"
            } else {
                "non-paginated section"
            }
            "[Source ${index + 1} - ${result.documentTitle}, $location]:\n${result.chunk.textContent}"
        }.joinToString("\n\n")
        val citations = searchResults.map { result ->
            Citation(
                documentTitle = result.documentTitle,
                pageNumber = result.chunk.pageNumber,
                snippet = result.chunk.textContent.take(150),
                sectionTitle = result.chunk.sectionTitle
            )
        }
        return context to citations
    }

    private fun RagDocumentEntity.toDomain() = RagDocument(
        id = id,
        fileName = fileName,
        fileUri = fileUri,
        mimeType = mimeType,
        fileSize = fileSize,
        pageCount = pageCount,
        chunkCount = chunkCount,
        sha256 = sha256,
        indexedAt = indexedAt
    )
}
