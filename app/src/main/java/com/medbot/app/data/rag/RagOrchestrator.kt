package com.medbot.app.data.rag

import com.medbot.app.data.local.dao.RagDao
import com.medbot.app.data.local.entities.DocChunkEntity
import com.medbot.app.data.local.entities.RagDocumentEntity
import com.medbot.app.domain.model.Citation
import com.medbot.app.domain.model.DocChunk
import com.medbot.app.domain.model.RagDocument
import com.medbot.app.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class RagOrchestrator(
    private val ragDao: RagDao,
    private val parser: DocumentParser = DocumentParser(),
    private val chunker: DocumentChunker = DocumentChunker(),
    private val embedder: LocalEmbedder = LocalEmbedder(),
    private val vectorEngine: VectorSearchEngine = VectorSearchEngine()
) {

    fun getDocumentsFlow(): Flow<List<RagDocument>> {
        return ragDao.getDocuments().map { entityList ->
            entityList.map { entity ->
                RagDocument(
                    id = entity.id,
                    fileName = entity.fileName,
                    fileUri = entity.fileUri,
                    mimeType = entity.mimeType,
                    fileSize = entity.fileSize,
                    pageCount = entity.pageCount,
                    chunkCount = entity.chunkCount,
                    sha256 = entity.sha256,
                    indexedAt = entity.indexedAt
                )
            }
        }
    }

    suspend fun ingestDocument(
        fileName: String,
        fileUri: String,
        mimeType: String,
        inputStream: InputStream
    ): Result<RagDocument> = withContext(Dispatchers.IO) {
        try {
            val docId = UUID.randomUUID().toString()
            val parsed = parser.parse(inputStream, fileName, mimeType)
            val chunks = chunker.chunkDocument(docId, parsed, embedder)

            val docEntity = RagDocumentEntity(
                id = docId,
                fileName = fileName,
                fileUri = fileUri,
                mimeType = mimeType,
                fileSize = parsed.pages.sumOf { it.text.length.toLong() },
                pageCount = parsed.totalPageCount,
                chunkCount = chunks.size,
                sha256 = fileName.hashCode().toString(),
                indexedAt = System.currentTimeMillis()
            )

            val chunkEntities = chunks.map { chunk ->
                DocChunkEntity(
                    id = chunk.id,
                    docId = docId,
                    chunkIndex = chunk.chunkIndex,
                    textContent = chunk.textContent,
                    pageNumber = chunk.pageNumber,
                    sectionTitle = chunk.sectionTitle,
                    embeddingCsv = embedder.vectorToCsv(chunk.embedding)
                )
            }

            ragDao.insertDocument(docEntity)
            ragDao.insertChunks(chunkEntities)

            val domainDoc = RagDocument(
                id = docEntity.id,
                fileName = docEntity.fileName,
                fileUri = docEntity.fileUri,
                mimeType = docEntity.mimeType,
                fileSize = docEntity.fileSize,
                pageCount = docEntity.pageCount,
                chunkCount = docEntity.chunkCount,
                sha256 = docEntity.sha256,
                indexedAt = docEntity.indexedAt
            )
            Result.success(domainDoc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchSimilar(query: String, topK: Int = 4): List<SearchResult> = withContext(Dispatchers.IO) {
        val queryVector = embedder.embed(query)
        val chunkEntities = ragDao.getAllChunks()
        val docs = ragDao.getAllChunks().groupBy { it.docId }

        val domainChunksWithTitle = chunkEntities.map { entity ->
            val domainChunk = DocChunk(
                id = entity.id,
                docId = entity.docId,
                chunkIndex = entity.chunkIndex,
                textContent = entity.textContent,
                pageNumber = entity.pageNumber,
                sectionTitle = entity.sectionTitle,
                embedding = embedder.csvToVector(entity.embeddingCsv)
            )
            val docTitle = entity.sectionTitle.ifBlank { "Dokumen Klinis" }
            Pair(domainChunk, docTitle)
        }

        vectorEngine.searchTopK(queryVector, domainChunksWithTitle, topK = topK)
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        ragDao.deleteDocument(docId)
    }

    suspend fun getChunkCount(): Int = withContext(Dispatchers.IO) {
        ragDao.getChunkCount()
    }

    fun buildContextAndCitations(searchResults: List<SearchResult>): Pair<String, List<Citation>> {
        if (searchResults.isEmpty()) return Pair("", emptyList())

        val contextBuilder = StringBuilder()
        val citations = mutableListOf<Citation>()

        for ((idx, res) in searchResults.withIndex()) {
            val chunk = res.chunk
            contextBuilder.append("[Sumber ${idx + 1} - ${res.documentTitle} Hal. ${chunk.pageNumber}]:\n")
            contextBuilder.append(chunk.textContent)
            contextBuilder.append("\n\n")

            citations.add(
                Citation(
                    documentTitle = res.documentTitle,
                    pageNumber = chunk.pageNumber,
                    snippet = chunk.textContent.take(150),
                    sectionTitle = chunk.sectionTitle
                )
            )
        }

        return Pair(contextBuilder.toString().trim(), citations)
    }
}
