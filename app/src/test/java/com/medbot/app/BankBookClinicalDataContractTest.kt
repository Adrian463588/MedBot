package com.medbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BankBookClinicalDataContractTest {
    @Test
    fun `production corpus contains complete diarrhoea treatment evidence and WHO provenance`() {
        val asset = listOf(
            File("app/src/main/assets/bankbook_rag_chunks_medgemma.jsonl"),
            File("../app/src/main/assets/bankbook_rag_chunks_medgemma.jsonl")
        ).firstOrNull { it.isFile }
        assertTrue("Bundled BankBook asset is missing", asset != null)
        val corpusFile = asset ?: return

        val records = corpusFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertEquals(1132, records.size)

        val diare = records.firstOrNull { it.contains("\"title\": \"Diare\"") }
        assertTrue("Diare record is missing", diare != null)
        val diareText = diare ?: return
        assertTrue(diareText.contains("Penatalaksanaan", ignoreCase = true))
        assertTrue(diareText.contains("Zink", ignoreCase = true))
        assertTrue(diareText.contains("antibiotik", ignoreCase = true))
        assertTrue(diareText.contains("Red Flags", ignoreCase = true))
        assertFalse(diareText.trimEnd().endsWith("asidosis met", ignoreCase = true))

        val who = records.firstOrNull { it.contains("\"chunk_id\": \"who_2024_diarrhoea_children\"") }
        assertTrue("WHO record is missing", who != null)
        val whoText = who ?: return
        assertTrue(whoText.contains("who.int/publications/i/item/9789240103412"))
        assertTrue(whoText.contains("5 mg"))

        val skdi = records.filter { it.contains("\"source_type\": \"competency_index\"") }
        assertEquals(144, skdi.size)
        assertTrue(skdi.all { it.contains("classification_only") })
        assertTrue(skdi.any { it.contains("Kejang Demam") })
        assertTrue(skdi.any { it.contains("Kekerasan Tajam") })

        val regulation = records.firstOrNull {
            it.contains("reg_kemkes_permenkes_4_2026_ppk_status")
        }
        assertTrue("Current regulation status record is missing", regulation != null)
        assertTrue(regulation?.contains("2026permenkes004.pdf") == true)

        val webSources = records.filter { it.contains("\"source_type\": \"secondary_web_education\"") }
        assertEquals(2, webSources.size)
        assertTrue(webSources.all { it.contains("education_only") })
        assertTrue(webSources.all { !it.contains("Rp ") })
        assertTrue(
            webSources.any {
                it.contains("https://www.halodoc.com/artikel/diare-3-hari-belum-sembuh-jangan-panik-ini-cara-mengatasi")
            }
        )
        assertTrue(webSources.any { it.contains("https://www.k24klik.com/blog/obat-diare/") })
    }
}
