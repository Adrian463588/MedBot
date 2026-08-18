package com.medbot.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.ModelManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Resumable, integrity-checked model download worker. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext failure("MODEL_UNAVAILABLE")
        val manifest = ModelRegistry.getManifestById(modelId)
            ?: return@withContext failure("MODEL_UNAVAILABLE")
        if (!isValidManifest(manifest)) return@withContext failure("MODEL_MANIFEST_INVALID")

        val url = manifest.downloadUrl.toHttpUrlOrNull()
        if (url == null || url.scheme != "https") return@withContext failure("MODEL_URL_INVALID")

        val modelsDir = File(applicationContext.filesDir, "models")
        if (!modelsDir.exists() && !modelsDir.mkdirs()) return@withContext failure("STORAGE_UNAVAILABLE")
        if (!modelsDir.isDirectory) return@withContext failure("STORAGE_UNAVAILABLE")

        val extension = when (manifest.format.name) {
            "LITERTLM" -> "litertlm"
            "GGUF" -> "gguf"
            "ONNX" -> "onnx"
            else -> return@withContext failure("MODEL_FORMAT_UNSUPPORTED")
        }
        val target = File(modelsDir, "${manifest.id}.$extension")
        val part = File(modelsDir, "${manifest.id}.$extension.part")

        if (target.isFile && target.length() == manifest.sizeBytes && sha256(target) == manifest.sha256.lowercase()) {
            return@withContext success(target)
        }
        if (part.length() > manifest.sizeBytes) return@withContext failure("PARTIAL_SIZE_INVALID")
        val remaining = (manifest.sizeBytes - part.length()).coerceAtLeast(0L)
        if (modelsDir.usableSpace < remaining + MIN_FREE_BYTES) return@withContext failure("INSUFFICIENT_STORAGE")

        val offset = part.length()
        val request = Request.Builder().url(url).apply {
            if (offset > 0L) header("Range", "bytes=$offset-")
        }.build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 416) return@withContext failure("RANGE_NOT_SATISFIABLE")
                if (response.code != 200 && response.code != 206) {
                    return@withContext failure("HTTP_${response.code}")
                }

                val range = response.header("Content-Range")?.let(::parseContentRange)
                if (response.code == 206 && (range == null || range.first != offset || range.third != manifest.sizeBytes)) {
                    return@withContext failure("RANGE_RESPONSE_INVALID")
                }

                val append = offset > 0L && response.code == 206
                var downloaded = if (append) offset else 0L
                val body = response.body ?: return@withContext failure("RESPONSE_BODY_EMPTY")
                FileOutputStream(part, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var lastUpdate = System.nanoTime()
                        val started = System.nanoTime()
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            if (isStopped) return@withContext Result.retry()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            downloaded += count
                            if (downloaded > manifest.sizeBytes) return@withContext failure("RESPONSE_TOO_LARGE")
                            output.write(buffer, 0, count)

                            val now = System.nanoTime()
                            if (now - lastUpdate >= PROGRESS_INTERVAL_NS) {
                                val elapsed = ((now - started).coerceAtLeast(1L)) / 1_000_000_000.0
                                val speed = ((downloaded - if (append) offset else 0L) / elapsed).toLong()
                                setProgress(workDataOf(
                                    KEY_BYTES to downloaded,
                                    KEY_TOTAL to manifest.sizeBytes,
                                    KEY_SPEED to speed,
                                    KEY_STATUS to "DOWNLOADING"
                                ))
                                lastUpdate = now
                            }
                        }
                        output.fd.sync()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return@withContext if (isStopped) Result.retry() else failure(t.message ?: "DOWNLOAD_FAILED")
        }

        if (part.length() != manifest.sizeBytes) return@withContext failure("INCOMPLETE_DOWNLOAD")
        setProgress(workDataOf(
            KEY_BYTES to manifest.sizeBytes,
            KEY_TOTAL to manifest.sizeBytes,
            KEY_SPEED to 0L,
            KEY_STATUS to "VERIFYING"
        ))
        if (sha256(part) != manifest.sha256.lowercase()) return@withContext failure("SHA256_MISMATCH")

        try {
            Files.move(
                part.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (t: Throwable) {
            return@withContext failure("ATOMIC_PROMOTION_UNAVAILABLE")
        }
        success(target)
    }

    private fun isValidManifest(manifest: ModelManifest): Boolean =
        manifest.sizeBytes > 0L && SHA256_PATTERN.matches(manifest.sha256.trim().lowercase())

    private suspend fun success(file: File): Result {
        setProgress(workDataOf(
            KEY_BYTES to file.length(),
            KEY_TOTAL to file.length(),
            KEY_SPEED to 0L,
            KEY_STATUS to "READY_TO_LOAD"
        ))
        return Result.success(workDataOf(KEY_FILE_PATH to file.absolutePath))
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseContentRange(value: String): Triple<Long, Long, Long>? {
        val match = CONTENT_RANGE.matchEntire(value.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return if (start <= end && end < total) Triple(start, end, total) else null
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_STATUS = "status"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ERROR = "error"
        private const val BUFFER_SIZE = 1024 * 1024
        private const val MIN_FREE_BYTES = 1024L * 1024L
        private const val PROGRESS_INTERVAL_NS = 250_000_000L
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}
