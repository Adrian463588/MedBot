package com.medbot.app.data.rag

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class ParsedPage(val pageNumber: Int, val text: String, val sectionTitle: String = "")

data class ParsedDocument(
    val fileName: String,
    val pages: List<ParsedPage>,
    val totalPageCount: Int,
    val byteSize: Long,
    val sha256: String
)

/** Parses real plain-text and Markdown input. PDF/DOCX need a wired parser dependency. */
class DocumentParser {
    fun parse(inputStream: InputStream, fileName: String, mimeType: String): ParsedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val isText = extension == "txt" || extension == "md" || mimeType.lowercase(Locale.US).startsWith("text/")
        if (!isText || extension == "pdf" || extension == "docx") {
            throw RagProcessingException(
                RagFailureCode.PARSER_UNAVAILABLE,
                "No real parser is wired for .$extension"
            )
        }

        val bytes = readBounded(inputStream)
        if (bytes.isEmpty()) throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Document is empty")
        val text = String(bytes, StandardCharsets.UTF_8)
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (text.isBlank()) throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Document has no text")

        val section = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("#") }
            ?.trimStart('#', ' ', '\t')
            ?.take(120)
            .orEmpty()
        val page = ParsedPage(1, text, section.ifBlank { fileName })
        return ParsedDocument(
            fileName = fileName,
            pages = listOf(page),
            totalPageCount = 1,
            byteSize = bytes.size.toLong(),
            sha256 = sha256(bytes)
        )
    }

    private fun readBounded(inputStream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        inputStream.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > MAX_DOCUMENT_BYTES) {
                    throw RagProcessingException(RagFailureCode.PARSER_UNAVAILABLE, "Document exceeds parser size limit")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_DOCUMENT_BYTES = 32L * 1024L * 1024L
    }
}
