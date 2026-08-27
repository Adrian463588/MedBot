package com.medbot.app

import com.medbot.app.data.rag.DocumentChunker
import com.medbot.app.data.rag.DocumentParser
import com.medbot.app.data.rag.LocalEmbedder
import com.medbot.app.data.rag.ParsedDocument
import com.medbot.app.data.rag.ParsedPage
import com.medbot.app.data.rag.RagFailureCode
import com.medbot.app.data.rag.RagProcessingException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParserChunkerContractTest {

    @Test
    fun `docx parser extracts real OpenXML paragraphs and section provenance`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Triase</w:t></w:r></w:p>
                <w:p><w:r><w:t>Tanda bahaya perlu dirujuk.</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        val parsed = DocumentParser().parse(
            ByteArrayInputStream(bytes),
            "triase.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )

        assertEquals(0, parsed.totalPageCount)
        assertEquals(2, parsed.pages.size)
        assertEquals("Triase", parsed.pages[0].sectionTitle)
        assertEquals("Triase", parsed.pages[1].sectionTitle)
        assertEquals(bytes.size.toLong(), parsed.byteSize)
        assertEquals(64, parsed.sha256.length)
    }

    @Test
    fun `invalid pdf is rejected instead of being treated as text`() {
        try {
            DocumentParser().parse(
                ByteArrayInputStream("%PDF-invalid".toByteArray()),
                "broken.pdf",
                "application/pdf"
            )
            fail("Invalid PDF must not produce synthetic text pages")
        } catch (error: RagProcessingException) {
            assertEquals(RagFailureCode.PARSER_UNAVAILABLE, error.code)
        }
    }

    @Test
    fun `text parser removes null bytes and keeps first markdown section`() {
        val parsed = DocumentParser().parse(
            inputStream = ByteArrayInputStream("\u0000# Triage\nTanda bahaya perlu dirujuk.".toByteArray()),
            fileName = "triage.md",
            mimeType = "text/markdown"
        )

        assertEquals(0, parsed.totalPageCount)
        assertEquals(0, parsed.pages.single().pageNumber)
        assertEquals("Triage", parsed.pages.single().sectionTitle)
        assertFalse(parsed.pages.single().text.contains('\u0000'))
        assertTrue(parsed.pages.single().text.contains("Tanda bahaya"))
    }

    @Test
    fun `jsonl parser keeps chunk id nested sections and source provenance`() {
        val json = """
            {"chunk_id":"ppk_diare_001","title":"Diare","source_type":"national_clinical_guideline","source_url":"https://example.invalid/source","revision":"2026-01","sections":[{"heading":"Penatalaksanaan","content":"Rehidrasi sesuai kondisi."}]}
        """.trimIndent()

        val parsed = DocumentParser().parse(
            ByteArrayInputStream(json.toByteArray()),
            "clinical.jsonl",
            "application/x-ndjson"
        )

        val page = parsed.pages.single()
        assertEquals("ppk_diare_001", page.recordId)
        assertEquals("national_clinical_guideline", page.sourceRole)
        assertEquals("https://example.invalid/source", page.sourceUrl)
        assertEquals("2026-01", page.revision)
        assertTrue(page.text.contains("Penatalaksanaan"))
        assertTrue(page.text.contains("Rehidrasi"))
    }

    @Test
    fun `chunker fails closed when the real embedder is unavailable`() {
        val parsed = ParsedDocument(
            fileName = "fixture.txt",
            pages = listOf(ParsedPage(3, "one two three four five six", "Section A")),
            totalPageCount = 1,
            byteSize = 35,
            sha256 = "fixture-sha256"
        )

        try {
            DocumentChunker(chunkSizeWords = 4, overlapWords = 1)
                .chunkDocument("fixture-doc", parsed, LocalEmbedder(dimensions = 8))
            fail("Chunking must not invent an embedding when the model is unavailable")
        } catch (error: RagProcessingException) {
            assertEquals(RagFailureCode.EMBEDDER_UNAVAILABLE, error.code)
        }
    }

    @Test
    fun `chunker does not create chunks from an empty source page`() {
        val parsed = ParsedDocument(
            fileName = "empty.txt",
            pages = listOf(ParsedPage(1, "   ", "Empty")),
            totalPageCount = 1,
            byteSize = 0,
            sha256 = ""
        )

        val chunks = DocumentChunker(chunkSizeWords = 4, overlapWords = 1)
            .chunkDocument("empty-doc", parsed, LocalEmbedder(dimensions = 8))

        assertTrue(chunks.isEmpty())
    }
}
