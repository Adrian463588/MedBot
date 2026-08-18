package com.medbot.app.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Schedules and observes resumable model downloads. */
class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun startDownload(modelId: String) {
        if (ModelRegistry.getManifestById(modelId) == null) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to modelId))
            .addTag("download_$modelId")
            .build()
        workManager.enqueueUniqueWork("download_$modelId", ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancels active work while preserving `.part` for a later resume. */
    fun pauseDownload(modelId: String) = workManager.cancelUniqueWork("download_$modelId")

    /** Cancels active work and removes only this model's partial artifact. */
    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = ModelRegistry.getManifestById(modelId) ?: return
        if (manifest.format != com.medbot.app.domain.model.ModelFormat.LITERTLM) return
        File(appContext.filesDir, "models/${manifest.id}.litertlm.part").delete()
    }

    fun deleteModel(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = ModelRegistry.getManifestById(modelId) ?: return
        if (manifest.format != com.medbot.app.domain.model.ModelFormat.LITERTLM) return
        File(appContext.filesDir, "models/${manifest.id}.litertlm").delete()
        File(appContext.filesDir, "models/${manifest.id}.litertlm.part").delete()
    }

    fun getInstalledModelPath(modelId: String): String? {
        val manifest = ModelRegistry.getManifestById(modelId) ?: return null
        if (manifest.format != com.medbot.app.domain.model.ModelFormat.LITERTLM) return null
        val target = File(appContext.filesDir, "models/${manifest.id}.litertlm")
        return target.takeIf { it.isFile }?.absolutePath
    }

    fun getDownloadProgressFlow(modelId: String): Flow<DownloadProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow("download_$modelId").map { infos ->
            val info = infos.firstOrNull() ?: return@map checkFileStatus(modelId)
            val manifest = ModelRegistry.getManifestById(modelId)
            val progress = info.progress
            val status = when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadStatus.DOWNLOADING
                WorkInfo.State.SUCCEEDED -> ModelDownloadStatus.READY_TO_LOAD
                WorkInfo.State.CANCELLED -> ModelDownloadStatus.PAUSED
                WorkInfo.State.FAILED -> ModelDownloadStatus.ERROR
            }
            DownloadProgress(
                modelId = modelId,
                bytesDownloaded = progress.getLong(ModelDownloadWorker.KEY_BYTES, 0L),
                totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL, manifest?.sizeBytes ?: 0L),
                speedBytesPerSec = progress.getLong(ModelDownloadWorker.KEY_SPEED, 0L),
                status = status,
                errorMessage = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
            )
        }

    fun checkFileStatus(modelId: String): DownloadProgress {
        val manifest = ModelRegistry.getManifestById(modelId)
            ?: return DownloadProgress(modelId, 0L, 0L, 0L, ModelDownloadStatus.ERROR, "MODEL_UNAVAILABLE")
        if (manifest.format != com.medbot.app.domain.model.ModelFormat.LITERTLM) {
            return DownloadProgress(modelId, 0L, manifest.sizeBytes, 0L, ModelDownloadStatus.ERROR, "MODEL_FORMAT_UNSUPPORTED")
        }
        val directory = File(appContext.filesDir, "models")
        val target = File(directory, "${manifest.id}.litertlm")
        val part = File(directory, "${manifest.id}.litertlm.part")
        return when {
            target.isFile && target.length() == manifest.sizeBytes -> DownloadProgress(
                modelId, target.length(), manifest.sizeBytes, 0L, ModelDownloadStatus.READY_TO_LOAD
            )
            part.isFile -> DownloadProgress(
                modelId, part.length(), manifest.sizeBytes, 0L, ModelDownloadStatus.PAUSED
            )
            else -> DownloadProgress(modelId, 0L, manifest.sizeBytes, 0L, ModelDownloadStatus.NOT_DOWNLOADED)
        }
    }
}
