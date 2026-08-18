package com.medbot.app.data.download

import android.content.Context
import androidx.work.*
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class ModelDownloadManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun startDownload(modelId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to modelId))
            .addTag("download_$modelId")
            .build()

        workManager.enqueueUniqueWork(
            "download_$modelId",
            ExistingWorkPolicy.REPLACE,
            downloadWork
        )
    }

    fun pauseDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
    }

    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = ModelRegistry.getManifestById(modelId) ?: return
        val ext = if (manifest.format.name == "GGUF") "gguf" else "litertlm"
        val partFile = File(context.filesDir, "models/${manifest.id}.$ext.part")
        if (partFile.exists()) partFile.delete()
    }

    fun getDownloadProgressFlow(modelId: String): Flow<DownloadProgress?> {
        return workManager.getWorkInfosForUniqueWorkFlow("download_$modelId").map { workInfoList ->
            val info = workInfoList.firstOrNull() ?: return@map checkFileStatus(modelId)
            val progress = info.progress
            val bytes = progress.getLong(ModelDownloadWorker.KEY_BYTES, 0L)
            val total = progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0L)

            val status = when (info.state) {
                WorkInfo.State.RUNNING -> ModelDownloadStatus.DOWNLOADING
                WorkInfo.State.SUCCEEDED -> ModelDownloadStatus.READY_TO_LOAD
                WorkInfo.State.FAILED -> ModelDownloadStatus.ERROR
                WorkInfo.State.CANCELLED -> ModelDownloadStatus.PAUSED
                WorkInfo.State.ENQUEUED -> ModelDownloadStatus.DOWNLOADING
                WorkInfo.State.BLOCKED -> ModelDownloadStatus.DOWNLOADING
            }

            DownloadProgress(
                modelId = modelId,
                bytesDownloaded = bytes,
                totalBytes = total,
                speedBytesPerSec = 1024 * 512,
                status = status
            )
        }
    }

    fun checkFileStatus(modelId: String): DownloadProgress {
        val manifest = ModelRegistry.getManifestById(modelId)
        if (manifest == null) {
            return DownloadProgress(modelId, 0, 0, 0, ModelDownloadStatus.NOT_DOWNLOADED)
        }
        val ext = if (manifest.format.name == "GGUF") "gguf" else "litertlm"
        val targetFile = File(context.filesDir, "models/${manifest.id}.$ext")
        val partFile = File(context.filesDir, "models/${manifest.id}.$ext.part")

        return when {
            targetFile.exists() -> DownloadProgress(
                modelId = modelId,
                bytesDownloaded = targetFile.length(),
                totalBytes = manifest.sizeBytes,
                speedBytesPerSec = 0,
                status = ModelDownloadStatus.READY_TO_LOAD
            )
            partFile.exists() -> DownloadProgress(
                modelId = modelId,
                bytesDownloaded = partFile.length(),
                totalBytes = manifest.sizeBytes,
                speedBytesPerSec = 0,
                status = ModelDownloadStatus.PAUSED
            )
            else -> DownloadProgress(
                modelId = modelId,
                bytesDownloaded = 0,
                totalBytes = manifest.sizeBytes,
                speedBytesPerSec = 0,
                status = ModelDownloadStatus.NOT_DOWNLOADED
            )
        }
    }
}
