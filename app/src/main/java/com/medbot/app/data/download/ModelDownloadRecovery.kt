package com.medbot.app.data.download

import com.medbot.app.domain.repository.ModelStorageException
import com.medbot.app.domain.repository.ModelStorageFailureCode

/**
 * Defines which verification failures invalidate a resumable candidate.
 *
 * A checksum/size mismatch means the bytes cannot be safely resumed. Other
 * storage failures retain the candidate so the user does not lose a potentially
 * recoverable transfer merely because the provider is temporarily unavailable.
 */
internal object ModelDownloadRecovery {
    fun shouldDiscardPartial(error: Throwable): Boolean =
        error is ModelStorageException &&
            error.code == ModelStorageFailureCode.INTEGRITY_MISMATCH
}
