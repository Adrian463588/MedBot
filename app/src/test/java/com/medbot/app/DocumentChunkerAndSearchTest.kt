package com.medbot.app

import com.medbot.app.data.rag.*
import com.medbot.app.domain.model.DocChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DocumentChunkerAndSearchTest {

    @Test
    fun `DocumentParser parses PDF and text streams with virtual pages`() {
        val parser = DocumentParser()
        val text = "Judul Dokumen: Hipertensi\n\nHipertensi adalah tekanan darah tinggi di atas 140/90 mmHg."
        val stream = ByteArrayInputStream(text.toByteArray())
        val parsed = parser.parse(stream, "hipertensi.txt", "text/plain")

        assertTrue(parsed.pages.isNotEmpty())
        assertEquals(1, parsed.totalPageCount)
        assertTrue(parsed.pages[0].text.contains("Hipertensi"))
    }

    @Test
    fun `LocalEmbedder produces 384-dimensional normalized vector`() {
        val embedder = LocalEmbedder(dimensions = 384)
        val vector = embedder.embed("Pasien demam berdarah dengue dengan trombositopenia")

        assertEquals(384, vector.size)
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        assertTrue("Norm should be close to 1.0", Math.abs(sumSquares - 1.0f) < 0.05f)
    }

    @Test
    fun `VectorSearchEngine returns high similarity for matching query and document`() {
        val embedder = LocalEmbedder(dimensions = 384)
        val vectorEngine = VectorSearchEngine()

        val docText1 = "Gejala Demam Berdarah Dengue meliputi demam tinggi mendadak dan bintik merah petekie."
        val docText2 = "Sakit gigi karies dapat diobati dengan analgesik dan tambal gigi ke dokter gigi."

        val chunk1 = DocChunk(
            id = "c1",
            docId = "d1",
            chunkIndex = 0,
            textContent = docText1,
            pageNumber = 1,
            sectionTitle = "DBD",
            embedding = embedder.embed(docText1)
        )

        val chunk2 = DocChunk(
            id = "c2",
            docId = "d2",
            chunkIndex = 0,
            textContent = docText2,
            pageNumber = 1,
            sectionTitle = "Gigi",
            embedding = embedder.embed(docText2)
        )

        val queryVec = embedder.embed("tanda klinis penyakit demam berdarah")
        val results = vectorEngine.searchTopK(
            queryVector = queryVec,
            chunksWithTitle = listOf(chunk1 to "Panduan DBD", chunk2 to "Panduan Gigi"),
            topK = 2
        )

        assertTrue(results.isNotEmpty())
        assertEquals("Panduan DBD", results.first().documentTitle)
        assertTrue(results.first().similarityScore > 0.3f)
    }
}
