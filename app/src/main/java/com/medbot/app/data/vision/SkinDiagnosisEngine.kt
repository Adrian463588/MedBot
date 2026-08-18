package com.medbot.app.data.vision

import android.graphics.BitmapFactory
import com.medbot.app.domain.model.SkinRecord
import com.medbot.app.domain.repository.SkinAnalysisException
import com.medbot.app.domain.repository.SkinAnalysisGateway
import com.medbot.app.domain.repository.SkinAnalysisStatus
import java.io.File

/** Typed fail-closed result until a real local vision model is initialized. */
data class SkinAnalysisUnavailable(val status: SkinAnalysisStatus, val reason: String)

/**
 * Skin analysis boundary. Pixel heuristics are deliberately absent: a real
 * local vision model must be wired before any clinical result is returned.
 */
class SkinDiagnosisEngine : SkinAnalysisGateway {
    /** Returns explicit unavailable/insufficient state; never a benign result. */
    fun analyzeSkinImageResult(
        imagePath: String,
        @Suppress("UNUSED_PARAMETER") bodyPart: String,
        @Suppress("UNUSED_PARAMETER") userNotes: String = ""
    ): SkinAnalysisUnavailable {
        if (imagePath.isBlank()) {
            return SkinAnalysisUnavailable(SkinAnalysisStatus.INSUFFICIENT_DATA, "Image path is empty")
        }
        val file = File(imagePath)
        if (!file.isFile || !file.canRead() || file.length() <= 0L) {
            return SkinAnalysisUnavailable(SkinAnalysisStatus.INSUFFICIENT_DATA, "Readable image is required")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return SkinAnalysisUnavailable(SkinAnalysisStatus.INSUFFICIENT_DATA, "Readable raster image is required")
        }
        return SkinAnalysisUnavailable(
            SkinAnalysisStatus.UNAVAILABLE,
            "No local vision model is initialized"
        )
    }

    /** Existing API now throws a typed unavailable state instead of saving invented data. */
    override fun analyzeSkinImage(imagePath: String, bodyPart: String, userNotes: String): SkinRecord {
        val result = analyzeSkinImageResult(imagePath, bodyPart, userNotes)
        throw SkinAnalysisException(result.status, result.reason)
    }
}
