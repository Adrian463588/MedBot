package com.medbot.app

import com.medbot.app.data.rag.*
import com.medbot.app.domain.model.DocChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class DocumentChunkerAndSearchTest {

    @Test
    fun `DocumentParser parses real text and records source metadata`() {
        val parser = DocumentParser()
        val text = "Judul Dokumen: Hipertensi\n\nHipertensi adalah tekanan darah tinggi di atas 140/90 mmHg."
        val stream = ByteArrayInputStream(text.toByteArray())
        val parsed = parser.parse(stream, "hipertensi.txt", "text/plain")

        assertTrue(parsed.pages.isNotEmpty())
        assertEquals(0, parsed.totalPageCount)
        assertTrue(parsed.pages[0].text.contains("Hipertensi"))
        assertEquals(text.toByteArray().size.toLong(), parsed.byteSize)
        assertEquals(64, parsed.sha256.length)
    }

    @Test
    fun `LocalEmbedder reports unavailable without a real local model`() {
        val embedder = LocalEmbedder(dimensions = 384)

        try {
            embedder.embed("Pasien demam berdarah dengue dengan trombositopenia")
            fail("No embedding success may be claimed without a model")
        } catch (error: RagProcessingException) {
            assertEquals(RagFailureCode.EMBEDDER_UNAVAILABLE, error.code)
        }
    }

    @Test
    fun `VectorSearchEngine ranks measured fixture vectors without embedding claims`() {
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
            embedding = floatArrayOf(1f, 0f)
        )

        val chunk2 = DocChunk(
            id = "c2",
            docId = "d2",
            chunkIndex = 0,
            textContent = docText2,
            pageNumber = 1,
            sectionTitle = "Gigi",
            embedding = floatArrayOf(0f, 1f)
        )

        val results = vectorEngine.searchTopK(
            queryVector = floatArrayOf(1f, 0f),
            chunksWithTitle = listOf(chunk1 to "Panduan DBD", chunk2 to "Panduan Gigi"),
            topK = 2
        )

        assertTrue(results.isNotEmpty())
        assertEquals("Panduan DBD", results.first().documentTitle)
        assertEquals(1f, results.first().similarityScore, 0f)
    }
}
