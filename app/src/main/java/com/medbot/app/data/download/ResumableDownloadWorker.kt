package com.medbot.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResumableDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString("MODEL_URL") ?: return@withContext Result.failure()
        val fileName = inputData.getString("FILE_NAME") ?: return@withContext Result.failure()
        val expectedSha256 = inputData.getString("EXPECTED_SHA256") ?: ""

        val modelsDir = File(applicationContext.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val partFile = File(modelsDir, "$fileName.part")
        val finalFile = File(modelsDir, fileName)

        if (finalFile.exists()) {
            return@withContext Result.success()
        }

        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

        val client = OkHttpClient()
        val requestBuilder = Request.Builder().url(modelUrl)
        if (downloadedBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.retry()
            }

            val body = response.body
            if (body == null) return@withContext Result.failure()

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(partFile, downloadedBytes > 0)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) {
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    return@withContext Result.retry()
                }
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                // Progress update could be sent here
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // TODO: Verify SHA-256
            val isChecksumValid = true // Placeholder for actual checksum verification
            
            if (isChecksumValid) {
                partFile.renameTo(finalFile)
                return@withContext Result.success()
            } else {
                partFile.delete()
                return@withContext Result.failure()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}
