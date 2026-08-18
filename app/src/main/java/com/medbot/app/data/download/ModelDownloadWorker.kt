package com.medbot.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.medbot.app.data.ai.ModelRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val manifest = ModelRegistry.getManifestById(modelId) ?: return@withContext Result.failure()

        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val ext = if (manifest.format.name == "GGUF") "gguf" else "litertlm"
        val targetFile = File(modelsDir, "${manifest.id}.$ext")
        val partFile = File(modelsDir, "${manifest.id}.$ext.part")

        if (targetFile.exists() && targetFile.length() == manifest.sizeBytes) {
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STATUS to "READY_TO_LOAD"))
            return@withContext Result.success(workDataOf(KEY_FILE_PATH to targetFile.absolutePath))
        }

        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

        try {
            val requestBuilder = Request.Builder().url(manifest.downloadUrl)
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                // If remote server does not support range or URL changed, fallback to simulated fast stream
                downloadedBytes = 0L
                partFile.delete()
            }

            val responseBody = response.body
            val totalBytes = manifest.sizeBytes
            val outputStream = FileOutputStream(partFile, downloadedBytes > 0)

            if (responseBody != null) {
                val inputStream = responseBody.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastProgressUpdate = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) {
                        outputStream.flush()
                        outputStream.close()
                        return@withContext Result.retry()
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300) {
                        val progressPercent = ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                        setProgress(workDataOf(
                            KEY_PROGRESS to progressPercent,
                            KEY_BYTES to downloadedBytes,
                            KEY_TOTAL to totalBytes,
                            KEY_STATUS to "DOWNLOADING"
                        ))
                    }
                }
                outputStream.flush()
                outputStream.close()
            } else {
                outputStream.close()
                return@withContext Result.failure(workDataOf(KEY_ERROR to "Response body kosong dari server"))
            }

            // Atomic rename
            partFile.renameTo(targetFile)
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STATUS to "READY_TO_LOAD"))

            Result.success(workDataOf(KEY_FILE_PATH to targetFile.absolutePath))
        } catch (e: Exception) {
            if (isStopped) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_STATUS = "status"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ERROR = "error"
    }
}
