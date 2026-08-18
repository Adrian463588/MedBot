package com.medbot.app.data.rag

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** A source segment with a real page number, or 0 when the format is not paginated. */
data class ParsedPage(val pageNumber: Int, val text: String, val sectionTitle: String = "")

data class ParsedDocument(
    val fileName: String,
    val pages: List<ParsedPage>,
    val totalPageCount: Int,
    val byteSize: Long,
    val sha256: String
)

/**
 * Parses user-selected TXT/MD, PDF, and DOCX bytes without synthesizing source
 * pages or metadata. PDF uses PdfBox-Android; DOCX uses the OpenXML package
 * and the platform XML DOM parser. Unsupported formats fail explicitly.
 */
class DocumentParser {
    fun parse(inputStream: InputStream, fileName: String, mimeType: String): ParsedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val bytes = readBounded(inputStream)
        if (bytes.isEmpty()) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Document is empty")
        }

        return when {
            extension == "txt" || extension == "md" || mimeType.lowercase(Locale.US).startsWith("text/") -> {
                parsePlainText(bytes, fileName)
            }
            extension == "pdf" || mimeType.equals("application/pdf", ignoreCase = true) -> {
                parsePdf(bytes, fileName)
            }
            extension == "docx" || mimeType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ignoreCase = true
            ) -> {
                parseDocx(bytes, fileName)
            }
            else -> throw RagProcessingException(
                RagFailureCode.PARSER_UNAVAILABLE,
                "No real parser is wired for .$extension"
            )
        }
    }

    private fun parsePlainText(bytes: ByteArray, fileName: String): ParsedDocument {
        val text = String(bytes, StandardCharsets.UTF_8)
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (text.isBlank()) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Document has no text")
        }
        val section = firstHeading(text).ifBlank { fileName }
        return ParsedDocument(
            fileName = fileName,
            pages = listOf(ParsedPage(pageNumber = 1, text = text, sectionTitle = section)),
            totalPageCount = 1,
            byteSize = bytes.size.toLong(),
            sha256 = sha256(bytes)
        )
    }

    private fun parsePdf(bytes: ByteArray, fileName: String): ParsedDocument {
        try {
            PDDocument.load(bytes).use { document ->
                if (document.numberOfPages <= 0) {
                    throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "PDF has no pages")
                }
                val stripper = PDFTextStripper()
                val pages = (1..document.numberOfPages).map { pageNumber ->
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    val text = stripper.getText(document)
                        .replace("\u0000", "")
                        .trim()
                    ParsedPage(
                        pageNumber = pageNumber,
                        text = text,
                        sectionTitle = firstHeading(text)
                    )
                }
                if (pages.none { it.text.isNotBlank() }) {
                    throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "PDF contains no extractable text")
                }
                return ParsedDocument(
                    fileName = fileName,
                    pages = pages,
                    totalPageCount = document.numberOfPages,
                    byteSize = bytes.size.toLong(),
                    sha256 = sha256(bytes)
                )
            }
        } catch (error: RagProcessingException) {
            throw error
        } catch (error: Exception) {
            throw RagProcessingException(
                RagFailureCode.PARSER_UNAVAILABLE,
                "PDF parser could not read the selected document",
                error
            )
        }
    }

    private fun parseDocx(bytes: ByteArray, fileName: String): ParsedDocument {
        val documentXml = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var result: ByteArray? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == "word/document.xml") {
                    result = readBounded(zip)
                    break
                }
            }
            result
        } ?: throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "DOCX has no word/document.xml")

        val paragraphs = parseDocxParagraphs(documentXml)
        if (paragraphs.isEmpty()) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "DOCX contains no extractable text")
        }

        val pages = paragraphs.map { paragraph ->
            ParsedPage(
                pageNumber = 0,
                text = paragraph.text,
                sectionTitle = paragraph.sectionTitle.ifBlank { fileName }
            )
        }
        return ParsedDocument(
            fileName = fileName,
            pages = pages,
            totalPageCount = 0,
            byteSize = bytes.size.toLong(),
            sha256 = sha256(bytes)
        )
    }

    private fun parseDocxParagraphs(xmlBytes: ByteArray): List<DocxParagraph> {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            val paragraphNodes = document.getElementsByTagNameNS("*", "p")
            val paragraphs = mutableListOf<DocxParagraph>()
            var currentSectionTitle = ""
            for (index in 0 until paragraphNodes.length) {
                val paragraph = paragraphNodes.item(index)
                val text = collectDocxText(paragraph).trim()
                if (text.isBlank()) continue

                val styleNodes = (paragraph as? org.w3c.dom.Element)
                    ?.getElementsByTagNameNS("*", "pStyle")
                val style = styleNodes?.item(0)?.attributes?.let { attributes ->
                    (0 until attributes.length)
                        .asSequence()
                        .mapNotNull { attributes.item(it)?.nodeValue }
                        .firstOrNull()
                }.orEmpty()
                if (style.startsWith("Heading", ignoreCase = true)) {
                    currentSectionTitle = text.take(120)
                }
                paragraphs += DocxParagraph(text, currentSectionTitle)
            }
            return paragraphs
        } catch (error: Exception) {
            throw RagProcessingException(
                RagFailureCode.PARSER_UNAVAILABLE,
                "DOCX parser could not read the selected document",
                error
            )
        }
    }

    private fun collectDocxText(node: Node): String {
        val localName = (node.localName ?: node.nodeName).substringAfterLast(':')
        return when (localName) {
            "t" -> node.textContent.orEmpty()
            "tab", "br" -> " "
            else -> buildString {
                val children = node.childNodes
                for (index in 0 until children.length) {
                    append(collectDocxText(children.item(index)))
                }
            }
        }
    }

    private data class DocxParagraph(val text: String, val sectionTitle: String)

    private fun firstHeading(text: String): String = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("#") }
        ?.trimStart('#', ' ', '\t')
        ?.take(120)
        .orEmpty()

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
