package com.medbot.app

import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.rag.LocalEmbedder
import com.medbot.app.data.rag.VectorSearchEngine
import com.medbot.app.domain.agents.tools.ToolRegistry
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationContractsTest {

    @Test
    fun `download progress keeps not downloaded state distinct from zero percent`() {
        val progress = DownloadProgress(
            modelId = "fixture-model",
            bytesDownloaded = 0,
            totalBytes = 0,
            speedBytesPerSec = 0,
            status = ModelDownloadStatus.NOT_DOWNLOADED
        )

        assertEquals(0f, progress.progressPercent, 0f)
        assertEquals(ModelDownloadStatus.NOT_DOWNLOADED, progress.status)
    }

    @Test
    fun `download progress reports measured fraction without claiming model readiness`() {
        val progress = DownloadProgress(
            modelId = "fixture-model",
            bytesDownloaded = 25,
            totalBytes = 100,
            speedBytesPerSec = 10,
            status = ModelDownloadStatus.DOWNLOADING
        )

        assertEquals(25f, progress.progressPercent, 0f)
        assertEquals(ModelDownloadStatus.DOWNLOADING, progress.status)
    }

    @Test
    fun `model registry entries have inspectable metadata without network access`() {
        val ids = ModelRegistry.OFFICIAL_MODELS.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.isNotEmpty())
        ModelRegistry.OFFICIAL_MODELS.forEach { manifest ->
            assertTrue(manifest.id.isNotBlank())
            assertTrue(manifest.downloadUrl.startsWith("https://"))
            assertTrue(manifest.sizeBytes > 0)
            assertTrue(manifest.sha256.matches(Regex("[0-9a-fA-F]{64}")))
            assertTrue(manifest.description.isNotBlank())
        }
        assertEquals(null, ModelRegistry.getManifestById("not-in-registry"))
    }

    @Test
    fun `unknown tool returns explicit failure and no fabricated data`() = runBlocking {
        val result = ToolRegistry.executeTool("not-in-registry", emptyMap())

        assertFalse(result.isSuccess)
        assertTrue(result.data.isEmpty())
        assertEquals("Not found", result.errorMessage)
    }

    @Test
    fun `empty embedding remains an unknown zero vector`() {
        val vector = LocalEmbedder(dimensions = 8).embed("")

        assertEquals(8, vector.size)
        assertTrue(vector.all { it == 0f })
    }

    @Test
    fun `vector search rejects incompatible embedding dimensions`() {
        val engine = VectorSearchEngine()

        assertEquals(0f, engine.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0f)
    }
}
