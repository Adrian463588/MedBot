package com.medbot.app

import com.medbot.app.data.rag.DocumentParser
import com.medbot.app.data.rag.BankBookCorpusManifest
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the Android JSON implementation, which is not available to local JVM unit tests. */
class DocumentParserJsonInstrumentedTest {

    @Test
    fun bundledBankBookAssetMatchesPinnedSourceAndParserContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = context.assets.open(BankBookCorpusManifest.ASSET_FILE_NAME).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val parsed = DocumentParser().parse(
            bytes.inputStream(),
            BankBookCorpusManifest.ASSET_FILE_NAME,
            "application/x-ndjson"
        )

        assertEquals(BankBookCorpusManifest.BYTE_SIZE, bytes.size.toLong())
        assertEquals(BankBookCorpusManifest.SHA256, digest)
        assertEquals(BankBookCorpusManifest.RECORD_COUNT, parsed.pages.size)
        assertTrue(parsed.pages.first().sectionTitle.contains("Morbili"))
    }

    @Test
    fun jsonlParserKeepsSourceTextAndProvenance() {
        val jsonl = """
            {"id":"malaria","title":"Malaria","source_book":"clinical-guideline-2022","text":"Diagnosis dan penatalaksanaan tersedia di sumber."}
            {"id":"catalog-only","title":"Product identity","text":"Tablet 500 mg"}
        """.trimIndent()

        val parsed = DocumentParser().parse(
            ByteArrayInputStream(jsonl.toByteArray()),
            "clinical-corpus.jsonl",
            "application/x-ndjson"
        )

        assertEquals(0, parsed.totalPageCount)
        assertEquals(2, parsed.pages.size)
        assertEquals("Malaria • clinical-guideline-2022", parsed.pages.first().sectionTitle)
        assertTrue(parsed.pages.first().text.contains("Diagnosis"))
        assertEquals(64, parsed.sha256.length)
    }
}
