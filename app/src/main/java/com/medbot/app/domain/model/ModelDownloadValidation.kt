package com.medbot.app.domain.model

data class ContentRange(val start: Long, val endInclusive: Long, val total: Long)

enum class DownloadProtocolFailure {
    INVALID_MANIFEST_SIZE,
    HTTP_STATUS,
    RANGE_REQUIRED,
    RANGE_NOT_SATISFIABLE,
    RANGE_HEADER_INVALID,
    RANGE_START_MISMATCH,
    RANGE_TOTAL_MISMATCH,
    RESPONSE_LENGTH_MISMATCH,
    RESUME_VALIDATOR_MISSING,
    SOURCE_CHANGED,
    RESPONSE_OVERSIZED,
    INCOMPLETE_DOWNLOAD,
    INVALID_FINAL_SIZE
}

/** Pure HTTP/integrity rules for resumable model transfer. */
object ModelDownloadProtocol {
    private val contentRangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    fun parseContentRange(value: String?): ContentRange? {
        val match = value?.trim()?.let(contentRangePattern::matchEntire) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return if (start <= end && end < total) ContentRange(start, end, total) else null
    }

    fun validateResponse(
        responseCode: Int,
        offset: Long,
        expectedTotal: Long,
        responseLength: Long?,
        contentRange: String?,
        expectedEtag: String?,
        responseEtag: String?
    ): DownloadProtocolFailure? {
        if (expectedTotal <= 0L || offset < 0L || offset >= expectedTotal) {
            return DownloadProtocolFailure.INVALID_MANIFEST_SIZE
        }
        val cleanExpected = expectedEtag?.trim()?.removePrefix("W/")?.removeSurrounding("\"")
        val cleanResponse = responseEtag?.trim()?.removePrefix("W/")?.removeSurrounding("\"")
        if (!cleanExpected.isNullOrEmpty() && !cleanResponse.isNullOrEmpty() && !cleanExpected.equals(cleanResponse, ignoreCase = true)) {
            return DownloadProtocolFailure.SOURCE_CHANGED
        }
        if (offset == 0L) {
            if (responseCode != 200) return DownloadProtocolFailure.HTTP_STATUS
            if (responseLength != null && responseLength >= 0L && responseLength > expectedTotal) {
                return DownloadProtocolFailure.RESPONSE_OVERSIZED
            }
            if (responseLength != null && responseLength >= 0L && responseLength != expectedTotal) {
                return DownloadProtocolFailure.RESPONSE_LENGTH_MISMATCH
            }
            return null
        }

        if (expectedEtag.isNullOrBlank()) return DownloadProtocolFailure.RESUME_VALIDATOR_MISSING
        if (responseCode == 416) return DownloadProtocolFailure.RANGE_NOT_SATISFIABLE
        if (responseCode != 206) return DownloadProtocolFailure.RANGE_REQUIRED
        val parsed = parseContentRange(contentRange) ?: return DownloadProtocolFailure.RANGE_HEADER_INVALID
        if (parsed.start != offset) return DownloadProtocolFailure.RANGE_START_MISMATCH
        if (parsed.total != expectedTotal) return DownloadProtocolFailure.RANGE_TOTAL_MISMATCH
        val expectedRangeLength = parsed.endInclusive - parsed.start + 1L
        if (responseLength != null && responseLength >= 0L && responseLength != expectedRangeLength) {
            return DownloadProtocolFailure.RESPONSE_LENGTH_MISMATCH
        }
        return null
    }

    fun validateFinalSize(actualSize: Long, expectedSize: Long): DownloadProtocolFailure? {
        if (expectedSize <= 0L) return DownloadProtocolFailure.INVALID_MANIFEST_SIZE
        return when {
            actualSize > expectedSize -> DownloadProtocolFailure.RESPONSE_OVERSIZED
            actualSize < expectedSize -> DownloadProtocolFailure.INCOMPLETE_DOWNLOAD
            else -> null
        }
    }
}
