package com.medbot.app

import com.medbot.app.data.download.ModelDownloadRecovery
import com.medbot.app.domain.repository.ModelStorageException
import com.medbot.app.domain.repository.ModelStorageFailureCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadRecoveryTest {
    @Test
    fun integrityFailureDiscardsPartialCandidate() {
        assertTrue(
            ModelDownloadRecovery.shouldDiscardPartial(
                ModelStorageException(
                    ModelStorageFailureCode.INTEGRITY_MISMATCH,
                    "checksum mismatch"
                )
            )
        )
    }

    @Test
    fun providerFailureKeepsPartialCandidateForResume() {
        assertFalse(
            ModelDownloadRecovery.shouldDiscardPartial(
                ModelStorageException(
                    ModelStorageFailureCode.OPEN_FAILED,
                    "provider unavailable"
                )
            )
        )
    }
}
