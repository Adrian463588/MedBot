package com.medbot.app.data.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.medbot.app.domain.repository.ModelFileGateway
import com.medbot.app.domain.repository.SafDocumentGateway
import com.medbot.app.domain.repository.SafDocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Android boundary for user-selected SAF files.
 *
 * Metadata, permission, and staging I/O stay outside Compose and ViewModel code.
 * Staging is bounded and atomic so a partial document/model source is never exposed
 * to a parser.
 */
class AndroidSafGateway(context: Context) : SafDocumentGateway, ModelFileGateway {
    private val appContext = context.applicationContext
    private val stagingDirectory = File(appContext.cacheDir, "saf-staging")
    private val maxDocumentBytes = 50L * 1024L * 1024L

    override suspend fun materialize(uriString: String): Result<SafDocumentSource> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = parseUri(uriString)
            val fileName = queryDisplayName(uri)
                ?: throw IOException("The selected document has no display name")
            val mimeType = appContext.contentResolver.getType(uri).orEmpty()
            stagingDirectory.mkdirs()
            val target = File(stagingDirectory, "${UUID.randomUUID()}.source")
            val temporary = File(stagingDirectory, "${target.name}.part")
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxDocumentBytes) {
                                throw IOException("The selected document exceeds the local ingestion limit")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                } ?: throw IOException("The selected document cannot be opened")
                if (!temporary.renameTo(target)) {
                    throw IOException("The staged document could not be promoted atomically")
                }
                SafDocumentSource(
                    fileName = fileName,
                    fileUri = uriString,
                    mimeType = mimeType,
                    localPath = target.absolutePath
                )
            } catch (error: Throwable) {
                temporary.delete()
                target.delete()
                throw error
            }
        }
    }

    override suspend fun deleteStagedFile(path: String) = withContext(Dispatchers.IO) {
        val candidate = File(path).canonicalFile
        val directory = stagingDirectory.canonicalFile
        if (candidate.parentFile == directory) candidate.delete()
    }

    override suspend fun displayName(uriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { queryDisplayName(parseUri(uriString)) ?: throw IOException("File name unavailable") }
    }

    override suspend fun takePersistableReadPermission(uriString: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                parseUri(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun parseUri(value: String): Uri {
        val uri = Uri.parse(value)
        if (uri.scheme != ContentResolverScheme.CONTENT) {
            throw IOException("Only content:// SAF URIs are supported")
        }
        return uri
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }

    private object ContentResolverScheme {
        const val CONTENT = "content"
    }
}
