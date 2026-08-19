package com.medbot.app.domain.repository

import java.io.InputStream
import java.io.OutputStream

data class ModelStorageDestination(
    val treeUri: String,
    val displayName: String
)

data class StoredModelArtifact(
    val documentUri: String,
    val displayName: String,
    val sizeBytes: Long
)

enum class ModelStorageFailureCode {
    INVALID_URI,
    NOT_TREE_URI,
    NOT_DIRECTORY,
    NOT_WRITABLE,
    PERMISSION_REQUIRED,
    FILE_NOT_FOUND,
    CREATE_FAILED,
    OPEN_FAILED,
    RENAME_FAILED,
    DELETE_FAILED,
    INTEGRITY_MISMATCH,
    IO_ERROR
}

class ModelStorageException(
    val code: ModelStorageFailureCode,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/** SAF tree boundary used by the durable model downloader. */
interface ModelStorageGateway {
    /** Android action name for a caller-owned ACTION_OPEN_DOCUMENT_TREE launcher. */
    val treePickerAction: String

    fun validateDestination(treeUri: String): Result<ModelStorageDestination>
    fun takePersistableTreePermission(treeUri: String): Result<Unit>
    fun findArtifact(treeUri: String, displayName: String): Result<StoredModelArtifact?>
    fun createArtifact(treeUri: String, displayName: String): Result<StoredModelArtifact>
    fun openInputStream(documentUri: String): Result<InputStream>
    fun openOutputStream(documentUri: String, append: Boolean): Result<OutputStream>
    fun renameArtifact(documentUri: String, displayName: String): Result<StoredModelArtifact>
    fun deleteArtifact(documentUri: String): Result<Unit>
    fun verifyArtifact(
        documentUri: String,
        expectedSizeBytes: Long,
        expectedSha256: String
    ): Result<StoredModelArtifact>

    /** ETag persisted beside a partial artifact to protect Range resume. */
    fun getResumeValidator(treeUri: String, displayName: String): Result<String?>
    fun setResumeValidator(treeUri: String, displayName: String, etag: String?): Result<Unit>
}
