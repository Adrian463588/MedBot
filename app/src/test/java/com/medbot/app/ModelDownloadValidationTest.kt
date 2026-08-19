package com.medbot.app

import com.medbot.app.domain.model.DownloadProtocolFailure
import com.medbot.app.domain.model.ModelDownloadProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDownloadValidationTest {
    @Test
    fun parsesValidContentRange() {
        assertEquals(
            Triple(1024L, 2047L, 4096L),
            ModelDownloadProtocol.parseContentRange("bytes 1024-2047/4096")?.let {
                Triple(it.start, it.endInclusive, it.total)
            }
        )
    }

    @Test
    fun freshResponseRequiresExactLength() {
        assertNull(
            ModelDownloadProtocol.validateResponse(
                responseCode = 200,
                offset = 0L,
                expectedTotal = 4096L,
                responseLength = 4096L,
                contentRange = null,
                expectedEtag = null,
                responseEtag = "\"release-a\""
            )
        )
        assertEquals(
            DownloadProtocolFailure.RESPONSE_LENGTH_MISMATCH,
            ModelDownloadProtocol.validateResponse(
                responseCode = 200,
                offset = 0L,
                expectedTotal = 4096L,
                responseLength = 4095L,
                contentRange = null,
                expectedEtag = null,
                responseEtag = "\"release-a\""
            )
        )
    }

    @Test
    fun resumeRequiresMatchingRangeAndEtag() {
        assertNull(
            ModelDownloadProtocol.validateResponse(
                responseCode = 206,
                offset = 1024L,
                expectedTotal = 4096L,
                responseLength = 3072L,
                contentRange = "bytes 1024-4095/4096",
                expectedEtag = "\"release-a\"",
                responseEtag = "\"release-a\""
            )
        )
        assertEquals(
            DownloadProtocolFailure.RANGE_START_MISMATCH,
            ModelDownloadProtocol.validateResponse(
                responseCode = 206,
                offset = 1024L,
                expectedTotal = 4096L,
                responseLength = 3072L,
                contentRange = "bytes 0-3071/4096",
                expectedEtag = "\"release-a\"",
                responseEtag = "\"release-a\""
            )
        )
        assertEquals(
            DownloadProtocolFailure.SOURCE_CHANGED,
            ModelDownloadProtocol.validateResponse(
                responseCode = 206,
                offset = 1024L,
                expectedTotal = 4096L,
                responseLength = 3072L,
                contentRange = "bytes 1024-4095/4096",
                expectedEtag = "\"release-a\"",
                responseEtag = "\"release-b\""
            )
        )
    }

    @Test
    fun resumeRejectsNonPartialResponseAndMissingValidator() {
        assertEquals(
            DownloadProtocolFailure.RANGE_REQUIRED,
            ModelDownloadProtocol.validateResponse(
                responseCode = 200,
                offset = 1024L,
                expectedTotal = 4096L,
                responseLength = 4096L,
                contentRange = null,
                expectedEtag = "\"release-a\"",
                responseEtag = "\"release-a\""
            )
        )
        assertEquals(
            DownloadProtocolFailure.RESUME_VALIDATOR_MISSING,
            ModelDownloadProtocol.validateResponse(
                responseCode = 206,
                offset = 1024L,
                expectedTotal = 4096L,
                responseLength = 3072L,
                contentRange = "bytes 1024-4095/4096",
                expectedEtag = null,
                responseEtag = "\"release-a\""
            )
        )
    }

    @Test
    fun finalSizeMustBeExact() {
        assertNull(ModelDownloadProtocol.validateFinalSize(4096L, 4096L))
        assertEquals(
            DownloadProtocolFailure.INCOMPLETE_DOWNLOAD,
            ModelDownloadProtocol.validateFinalSize(4095L, 4096L)
        )
        assertEquals(
            DownloadProtocolFailure.RESPONSE_OVERSIZED,
            ModelDownloadProtocol.validateFinalSize(4097L, 4096L)
        )
    }
}
