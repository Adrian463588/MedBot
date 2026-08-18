package com.medbot.app.data.vision

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.medbot.app.domain.repository.SkinCaptureTarget
import com.medbot.app.domain.repository.SkinMediaGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

/** Private, bounded image storage for user-selected skin photos. */
class AndroidSkinMediaGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : SkinMediaGateway {
    private val directory: File
        get() = File(context.filesDir, "skin_lineage")

    override suspend fun createCaptureTarget(): SkinCaptureTarget = withContext(Dispatchers.IO) {
        require(directory.exists() || directory.mkdirs()) { "Skin photo storage is unavailable" }
        val file = File(directory, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        SkinCaptureTarget(uri = uri.toString(), path = file.absolutePath)
    }

    override suspend fun importImage(uri: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(directory.exists() || directory.mkdirs()) { "Skin photo storage is unavailable" }
            val target = File(directory, "import_${UUID.randomUUID()}.jpg")
            val part = File.createTempFile("skin-", ".part", directory)
            try {
                val source = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                    ?: error("Selected image cannot be opened")
                source.use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            require(total <= MAX_IMAGE_BYTES) { "Selected image exceeds the size limit" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(part.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a readable raster image" }
                Files.move(
                    part.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                target.absolutePath
            } finally {
                if (part.exists()) part.delete()
            }
        }
    }

    override suspend fun discard(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile?.canonicalFile == directory.canonicalFile && file.exists()) file.delete()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
    }
}
