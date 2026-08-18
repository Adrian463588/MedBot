package com.medbot.app.data.rag

import com.medbot.app.domain.model.DocChunk
import java.util.UUID

class DocumentChunker(
    private val chunkSizeWords: Int = 120, // ~512 tokens
    private val overlapWords: Int = 20
) {

    fun chunkDocument(docId: String, parsedDoc: ParsedDocument, embedder: LocalEmbedder): List<DocChunk> {
        val chunks = mutableListOf<DocChunk>()
        var chunkIndex = 0

        for (page in parsedDoc.pages) {
            val words = page.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue

            var startIndex = 0
            while (startIndex < words.size) {
                val endIndex = (startIndex + chunkSizeWords).coerceAtMost(words.size)
                val chunkWords = words.subList(startIndex, endIndex)
                val chunkText = chunkWords.joinToString(" ")

                val vector = embedder.embed(chunkText)

                chunks.add(
                    DocChunk(
                        id = UUID.randomUUID().toString(),
                        docId = docId,
                        chunkIndex = chunkIndex++,
                        textContent = chunkText,
                        pageNumber = page.pageNumber,
                        sectionTitle = page.sectionTitle.ifBlank { "Halaman ${page.pageNumber}" },
                        embedding = vector
                    )
                )

                if (endIndex == words.size) break
                startIndex += (chunkSizeWords - overlapWords).coerceAtLeast(1)
            }
        }
        return chunks
    }
}
