package com.medbot.app.data.platform

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.medbot.app.domain.repository.ModelFileGateway
import com.medbot.app.domain.repository.ModelStorageDestination
import com.medbot.app.domain.repository.ModelStorageException
import com.medbot.app.domain.repository.ModelStorageFailureCode
import com.medbot.app.domain.repository.ModelStorageGateway
import com.medbot.app.domain.repository.SafDocumentGateway
import com.medbot.app.domain.repository.SafDocumentSource
import com.medbot.app.domain.repository.StoredModelArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Android boundary for user-selected SAF files and model trees.
 *
 * Model artifacts are never written to app-private model storage. The gateway
 * only reports a model artifact as verified after reading the selected SAF
 * document and matching its exact size and SHA-256.
 */
class AndroidSafGateway(context: Context) : SafDocumentGateway, ModelFileGateway, ModelStorageGateway {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val stagingDirectory = File(appContext.cacheDir, "saf-staging")
    private val modelMetadataPreferences =
        appContext.getSharedPreferences("medbot_model_storage", Context.MODE_PRIVATE)
    private val maxDocumentBytes = 50L * 1024L * 1024L

    override val treePickerAction: String = Intent.ACTION_OPEN_DOCUMENT_TREE

    override suspend fun materialize(uriString: String): Result<SafDocumentSource> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = parseUri(uriString)
            val fileName = queryDisplayName(uri)
                ?: throw IOException("The selected document has no display name")
            val mimeType = resolver.getType(uri).orEmpty()
            if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
                throw IOException("The SAF staging directory is unavailable")
            }
            val target = File(stagingDirectory, "${UUID.randomUUID()}.source")
            val temporary = File(stagingDirectory, "${target.name}.part")
            try {
                resolver.openInputStream(uri)?.use { input ->
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

    override suspend fun takePersistableReadPermission(uriString: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.takePersistableUriPermission(
                    parseUri(uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

    override fun validateDestination(treeUriString: String): Result<ModelStorageDestination> = runCatching {
        val treeUri = parseUri(treeUriString)
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw ModelStorageException(ModelStorageFailureCode.NOT_TREE_URI, "Model destination must be a SAF tree URI")
        }
        val metadata = queryDocumentMetadata(treeUri)
        if (metadata.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
            throw ModelStorageException(ModelStorageFailureCode.NOT_DIRECTORY, "Model destination is not a folder")
        }
        // SAF exposes directory creation through FLAG_DIR_SUPPORTS_CREATE.
        // FLAG_SUPPORTS_WRITE describes replacing an individual document and
        // is not required (and is often absent) on a writable directory.
        val canCreate = metadata.flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong() != 0L
        if (!canCreate) {
            throw ModelStorageException(ModelStorageFailureCode.NOT_WRITABLE, "Model destination cannot create files")
        }
        ModelStorageDestination(
            treeUri = treeUriString,
            displayName = metadata.displayName
                ?: throw ModelStorageException(ModelStorageFailureCode.IO_ERROR, "Model folder name is unavailable")
        )
    }

    override fun takePersistableTreePermission(treeUriString: String): Result<Unit> = runCatching {
        val treeUri = parseUri(treeUriString)
        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw ModelStorageException(ModelStorageFailureCode.NOT_TREE_URI, "Model destination must be a SAF tree URI")
        }
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }.recoverCatching { error ->
        if (error is SecurityException) {
            throw ModelStorageException(
                ModelStorageFailureCode.PERMISSION_REQUIRED,
                "Write permission for the model folder is unavailable",
                error
            )
        }
        throw error
    }

    override fun findArtifact(treeUriString: String, displayName: String): Result<StoredModelArtifact?> = runCatching {
        val treeUri = requireWritableTree(treeUriString)
        if (displayName.isBlank()) {
            throw ModelStorageException(ModelStorageFailureCode.FILE_NOT_FOUND, "Model file name is empty")
        }
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        resolver.query(
            childUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (name == displayName) {
                    val documentId = cursor.getString(idIndex)
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                    return@use StoredModelArtifact(documentUri.toString(), name, size)
                }
            }
            null
        }
    }

    override fun createArtifact(treeUriString: String, displayName: String): Result<StoredModelArtifact> = runCatching {
        val treeUri = requireWritableTree(treeUriString)
        if (displayName.isBlank() || displayName.contains('/') || displayName.contains('\\')) {
            throw ModelStorageException(ModelStorageFailureCode.CREATE_FAILED, "Invalid model file name")
        }
        // createDocument requires a document URI. A persisted ACTION_OPEN_DOCUMENT_TREE
        // result is a tree URI, so convert it to the selected folder document first.
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val uri = DocumentsContract.createDocument(
            resolver,
            parentDocumentUri,
            "application/octet-stream",
            displayName
        ) ?: throw ModelStorageException(ModelStorageFailureCode.CREATE_FAILED, "SAF provider could not create model file")
        readArtifactMetadata(uri, displayName)
    }

    override fun openInputStream(documentUri: String): Result<InputStream> = runCatching {
        resolver.openInputStream(parseUri(documentUri))
            ?: throw ModelStorageException(ModelStorageFailureCode.OPEN_FAILED, "SAF model file cannot be read")
    }

    override fun openOutputStream(documentUri: String, append: Boolean): Result<OutputStream> = runCatching {
        resolver.openOutputStream(parseUri(documentUri), if (append) "wa" else "w")
            ?: throw ModelStorageException(ModelStorageFailureCode.OPEN_FAILED, "SAF model file cannot be written")
    }

    override fun renameArtifact(documentUri: String, displayName: String): Result<StoredModelArtifact> = runCatching {
        val renamed = DocumentsContract.renameDocument(resolver, parseUri(documentUri), displayName)
            ?: throw ModelStorageException(ModelStorageFailureCode.RENAME_FAILED, "SAF provider cannot promote model file")
        readArtifactMetadata(renamed, displayName)
    }

    override fun deleteArtifact(documentUri: String): Result<Unit> = runCatching {
        if (!DocumentsContract.deleteDocument(resolver, parseUri(documentUri))) {
            throw ModelStorageException(ModelStorageFailureCode.DELETE_FAILED, "SAF provider could not delete model file")
        }
    }

    override fun verifyArtifact(
        documentUri: String,
        expectedSizeBytes: Long,
        expectedSha256: String
    ): Result<StoredModelArtifact> = runCatching {
        if (expectedSizeBytes <= 0L || !SHA256_PATTERN.matches(expectedSha256.trim())) {
            throw ModelStorageException(ModelStorageFailureCode.INTEGRITY_MISMATCH, "Model integrity contract is invalid")
        }
        val metadata = readArtifactMetadata(parseUri(documentUri), null)
        var size = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        openInputStream(documentUri).getOrThrow().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                size += count
                if (size > expectedSizeBytes) {
                    throw ModelStorageException(ModelStorageFailureCode.INTEGRITY_MISMATCH, "Model is larger than manifest")
                }
                digest.update(buffer, 0, count)
            }
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (size != expectedSizeBytes || !actualSha.equals(expectedSha256.trim(), ignoreCase = true)) {
            throw ModelStorageException(ModelStorageFailureCode.INTEGRITY_MISMATCH, "Model size or SHA-256 does not match manifest")
        }
        metadata.copy(sizeBytes = size)
    }

    override fun getResumeValidator(treeUri: String, displayName: String): Result<String?> = runCatching {
        modelMetadataPreferences.getString(resumeKey(treeUri, displayName), null)
    }

    override fun setResumeValidator(treeUri: String, displayName: String, etag: String?): Result<Unit> = runCatching {
        val key = resumeKey(treeUri, displayName)
        val editor = modelMetadataPreferences.edit()
        if (etag.isNullOrBlank()) editor.remove(key) else editor.putString(key, etag)
        editor.apply()
    }

    private fun requireWritableTree(treeUriString: String): Uri {
        val result = validateDestination(treeUriString)
        return result.getOrElse { throw it }
            .let { parseUri(it.treeUri) }
    }

    private fun readArtifactMetadata(uri: Uri, fallbackName: String?): StoredModelArtifact {
        val metadata = queryDocumentMetadata(uri)
        val name = metadata.displayName ?: fallbackName
            ?: throw ModelStorageException(ModelStorageFailureCode.IO_ERROR, "SAF model file name is unavailable")
        return StoredModelArtifact(uri.toString(), name, metadata.sizeBytes)
    }

    private fun queryDocumentMetadata(uri: Uri): DocumentMetadata {
        val documentUri = if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }
        return resolver.query(
            documentUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                throw ModelStorageException(ModelStorageFailureCode.FILE_NOT_FOUND, "SAF document is unavailable")
            }
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            DocumentMetadata(
                displayName = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                mimeType = mimeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                flags = flagsIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L,
                sizeBytes = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: -1L
            )
        } ?: throw ModelStorageException(ModelStorageFailureCode.OPEN_FAILED, "SAF provider did not return metadata")
    }

    private fun queryDisplayName(uri: Uri): String? {
        val openableName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
        return openableName ?: runCatching { queryDocumentMetadata(uri).displayName }.getOrNull()
    }

    private fun parseUri(value: String): Uri {
        val uri = Uri.parse(value)
        if (uri.scheme != ContentResolverScheme.CONTENT) {
            throw ModelStorageException(ModelStorageFailureCode.INVALID_URI, "Only content:// SAF URIs are supported")
        }
        return uri
    }

    private fun resumeKey(treeUri: String, displayName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$treeUri\u0000$displayName".toByteArray())
        return "etag_${digest.joinToString("") { "%02x".format(it) }}"
    }

    private data class DocumentMetadata(
        val displayName: String?,
        val mimeType: String?,
        val flags: Long,
        val sizeBytes: Long
    )

    private object ContentResolverScheme {
        const val CONTENT = "content"
    }

    companion object {
        private const val BUFFER_SIZE = 1024 * 1024
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}
