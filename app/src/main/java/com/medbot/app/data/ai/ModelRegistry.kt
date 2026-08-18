package com.medbot.app.data.ai

import com.medbot.app.domain.model.ModelManifest

/**
 * Trusted model metadata supplied by a release process.
 *
 * No release manifest is currently checked into this checkout, so the list is
 * intentionally empty. A guessed URL, size, or digest would make downloads
 * look available while violating model provenance and integrity requirements.
 */
object ModelRegistry {
    val OFFICIAL_MODELS: List<ModelManifest> = emptyList()

    fun getManifestById(id: String): ModelManifest? = OFFICIAL_MODELS.firstOrNull { it.id == id }
}
