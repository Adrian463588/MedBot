package com.medbot.app

import com.medbot.app.data.rag.BankBookCorpusManifest
import org.junit.Assert.assertEquals
import org.junit.Test

class BankBookCorpusContractTest {
    @Test
    fun bundledCorpusMetadataIsPinnedToCleanedBankBookSource() {
        assertEquals("bankbook_rag_chunks_medgemma.jsonl", BankBookCorpusManifest.ASSET_FILE_NAME)
        assertEquals(1132, BankBookCorpusManifest.RECORD_COUNT)
        assertEquals(2_642_327L, BankBookCorpusManifest.BYTE_SIZE)
        assertEquals(
            "08dc04293e6e4b36e811b64cd3a0ac165962ea484d16799d97e530a4410b629a",
            BankBookCorpusManifest.SHA256
        )
        assertEquals("2.2.0", BankBookCorpusManifest.VERSION)
    }
}
