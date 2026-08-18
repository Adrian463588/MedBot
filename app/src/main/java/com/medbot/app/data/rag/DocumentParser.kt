package com.medbot.app.data.rag

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class ParsedPage(
    val pageNumber: Int,
    val text: String,
    val sectionTitle: String = ""
)

data class ParsedDocument(
    val fileName: String,
    val pages: List<ParsedPage>,
    val totalPageCount: Int
)

class DocumentParser {

    fun parse(inputStream: InputStream, fileName: String, mimeType: String): ParsedDocument {
        val pages = mutableListOf<ParsedPage>()

        if (mimeType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true)) {
            // PDF text extractor: splits stream by page markers or paragraph blocks
            val content = inputStream.bufferedReader().use { it.readText() }
            val cleanContent = sanitizeText(content)
            val virtualPages = splitIntoVirtualPages(cleanContent, charsPerPage = 1800)
            
            virtualPages.forEachIndexed { index, pageText ->
                val section = extractSectionHeader(pageText)
                pages.add(ParsedPage(pageNumber = index + 1, text = pageText, sectionTitle = section))
            }
        } else {
            // Plain text or Markdown parser
            val lines = mutableListOf<String>()
            BufferedReader(InputStreamReader(inputStream)).useLines { lineSeq ->
                lines.addAll(lineSeq)
            }
            val text = lines.joinToString("\n")
            val cleanContent = sanitizeText(text)
            val virtualPages = splitIntoVirtualPages(cleanContent, charsPerPage = 1500)

            virtualPages.forEachIndexed { index, pageText ->
                val section = extractSectionHeader(pageText)
                pages.add(ParsedPage(pageNumber = index + 1, text = pageText, sectionTitle = section))
            }
        }

        if (pages.isEmpty()) {
            pages.add(ParsedPage(pageNumber = 1, text = "Dokumen kosong", sectionTitle = "Dokumen"))
        }

        return ParsedDocument(
            fileName = fileName,
            pages = pages,
            totalPageCount = pages.size
        )
    }

    private fun sanitizeText(input: String): String {
        return input.replace("\u0000", "")
            .replace("\\r\\n", "\n")
            .replace("\\r", "\n")
            .trim()
    }

    private fun splitIntoVirtualPages(text: String, charsPerPage: Int): List<String> {
        if (text.length <= charsPerPage) return listOf(text)
        val pages = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + charsPerPage).coerceAtMost(text.length)
            // Break at newline or space if possible
            val breakPoint = if (end < text.length) {
                val lastBreak = text.lastIndexOf('\n', end)
                if (lastBreak > start + (charsPerPage / 2)) lastBreak else end
            } else end
            val slice = text.substring(start, breakPoint).trim()
            if (slice.isNotEmpty()) {
                pages.add(slice)
            }
            start = breakPoint
        }
        return pages
    }

    private fun extractSectionHeader(pageText: String): String {
        val firstLine = pageText.lines().firstOrNull { it.isNotBlank() } ?: "Bagian Umum"
        return firstLine.take(50).replace("#", "").trim()
    }
}
