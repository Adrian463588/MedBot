package com.medbot.app.data.platform

import com.medbot.app.domain.repository.ModelStorageArtifactNames
import com.medbot.app.domain.repository.ModelStorageGateway
import java.io.ByteArrayOutputStream

/**
 * Keeps an encrypted Hugging Face credential envelope beside the model files.
 *
 * The SAF folder is user-owned storage, so only the authenticated envelope is
 * written there. The Android Keystore remains the decryption boundary; if a
 * full uninstall removes that key, the backup stays on disk but is not opened
 * silently as plaintext.
 */
class SafHuggingFaceTokenBackup(
    private val tokenStore: HuggingFaceTokenStore,
    private val storageGateway: ModelStorageGateway
) {
    /** Writes or refreshes the encrypted credential envelope in the SAF tree. */
    fun persist(treeUri: String): Result<Unit> = runCatching {
        if (!tokenStore.hasToken()) return@runCatching
        val envelope = tokenStore.exportSafEnvelope().getOrThrow()
        val staleTemp = storageGateway
            .findArtifact(treeUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS_TEMP)
            .getOrThrow()
        staleTemp?.let { storageGateway.deleteArtifact(it.documentUri).getOrThrow() }

        val temporary = storageGateway
            .createArtifact(treeUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS_TEMP)
            .getOrThrow()
        try {
            writeBytes(temporary.documentUri, envelope)
            val written = readBytes(temporary.documentUri).getOrThrow()
            require(written.contentEquals(envelope)) { "SAF credential backup verification failed" }

            val current = storageGateway
                .findArtifact(treeUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS)
                .getOrThrow()
            if (current != null) {
                // Providers differ in rename-over-existing support. Preserve
                // the verified temp until the replacement write succeeds.
                writeBytes(current.documentUri, envelope)
                storageGateway.deleteArtifact(temporary.documentUri).getOrThrow()
            } else {
                storageGateway
                    .renameArtifact(temporary.documentUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS)
                    .getOrThrow()
            }
        } catch (error: Throwable) {
            runCatching { storageGateway.deleteArtifact(temporary.documentUri) }
            throw error
        }
    }

    /** Restores the Keystore cache only when local gated access is incomplete. */
    fun restoreIfNeeded(treeUri: String): Result<Unit> = runCatching {
        val localReady = tokenStore.hasToken() && tokenStore.acceptedTermsRevision() != null
        if (localReady) return@runCatching

        val artifact = storageGateway
            .findArtifact(treeUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS)
            .getOrThrow()
            ?: storageGateway
                .findArtifact(treeUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS_TEMP)
                .getOrThrow()
            ?: return@runCatching

        val envelope = readBytes(artifact.documentUri).getOrThrow()
        tokenStore.importSafEnvelope(envelope).getOrThrow()
    }

    /** Returns true only when the encrypted backup artifact is present. */
    fun hasBackup(treeUri: String): Result<Boolean> = runCatching {
        storageGateway
            .findArtifact(treeUri, ModelStorageArtifactNames.HUGGING_FACE_ACCESS)
            .getOrThrow()
            ?.sizeBytes
            ?.let { it in MIN_BACKUP_BYTES..MAX_BACKUP_BYTES }
            ?: false
    }

    /** Removes both the active and interrupted backup artifacts. */
    fun clear(treeUri: String): Result<Unit> = runCatching {
        listOf(
            ModelStorageArtifactNames.HUGGING_FACE_ACCESS,
            ModelStorageArtifactNames.HUGGING_FACE_ACCESS_TEMP
        ).forEach { name ->
            storageGateway.findArtifact(treeUri, name).getOrThrow()?.let {
                storageGateway.deleteArtifact(it.documentUri).getOrThrow()
            }
        }
    }

    private fun writeBytes(documentUri: String, bytes: ByteArray) {
        require(bytes.size in MIN_BACKUP_BYTES..MAX_BACKUP_BYTES)
        storageGateway.openOutputStream(documentUri, append = false).getOrThrow().use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    private fun readBytes(documentUri: String): Result<ByteArray> = runCatching {
        val output = ByteArrayOutputStream()
        storageGateway.openInputStream(documentUri).getOrThrow().use { input ->
            val buffer = ByteArray(1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_BACKUP_BYTES) { "SAF credential backup is oversized" }
                output.write(buffer, 0, count)
            }
        }
        output.toByteArray().also { require(it.size >= MIN_BACKUP_BYTES) }
    }

    companion object {
        private const val MIN_BACKUP_BYTES = 64
        private const val MAX_BACKUP_BYTES = 8 * 1024
    }
}
