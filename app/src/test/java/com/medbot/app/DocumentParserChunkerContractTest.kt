package com.medbot.app

import com.medbot.app.data.rag.DocumentChunker
import com.medbot.app.data.rag.DocumentParser
import com.medbot.app.data.rag.LocalEmbedder
import com.medbot.app.data.rag.ParsedDocument
import com.medbot.app.data.rag.ParsedPage
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParserChunkerContractTest {

    @Test
    fun `text parser removes null bytes and keeps first markdown section`() {
        val parsed = DocumentParser().parse(
            inputStream = ByteArrayInputStream("\u0000# Triage\nTanda bahaya perlu dirujuk.".toByteArray()),
            fileName = "triage.md",
            mimeType = "text/markdown"
        )

        assertEquals(1, parsed.totalPageCount)
        assertEquals("Triage", parsed.pages.single().sectionTitle)
        assertFalse(parsed.pages.single().text.contains('\u0000'))
        assertTrue(parsed.pages.single().text.contains("Tanda bahaya"))
    }

    @Test
    fun `chunker preserves overlap and source page metadata`() {
        val parsed = ParsedDocument(
            fileName = "fixture.txt",
            pages = listOf(ParsedPage(3, "one two three four five six", "Section A")),
            totalPageCount = 1
        )

        val chunks = DocumentChunker(chunkSizeWords = 4, overlapWords = 1)
            .chunkDocument("fixture-doc", parsed, LocalEmbedder(dimensions = 8))

        assertEquals(2, chunks.size)
        assertEquals(listOf(0, 1), chunks.map { it.chunkIndex })
        assertEquals(3, chunks[0].pageNumber)
        assertEquals("Section A", chunks[0].sectionTitle)
        assertEquals("four", chunks[1].textContent.split(" ").first())
        assertTrue(chunks.all { it.textContent.isNotBlank() && it.embedding.size == 8 })
    }

    @Test
    fun `chunker does not create chunks from an empty source page`() {
        val parsed = ParsedDocument(
            fileName = "empty.txt",
            pages = listOf(ParsedPage(1, "   ", "Empty")),
            totalPageCount = 1
        )

        val chunks = DocumentChunker(chunkSizeWords = 4, overlapWords = 1)
            .chunkDocument("empty-doc", parsed, LocalEmbedder(dimensions = 8))

        assertTrue(chunks.isEmpty())
    }
}
