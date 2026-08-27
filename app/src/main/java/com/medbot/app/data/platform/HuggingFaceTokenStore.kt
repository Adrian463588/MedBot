package com.medbot.app.data.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores an optional user-provided Hugging Face access token encrypted with an
 * Android Keystore AES-GCM key. The plaintext token never enters WorkManager,
 * Room, logs, or a production UI state flow.
 */
class HuggingFaceTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun hasToken(): Boolean = readToken() != null

    /** Returns true only for the exact official source revision the user accepted. */
    fun hasTermsAccepted(sourceRevision: String): Boolean =
        sourceRevision.trim().isNotEmpty() &&
            preferences.getString(KEY_TERMS_REVISION, null) == sourceRevision.trim()

    /** Persists the gated-source acceptance without storing the token in plaintext. */
    fun saveTermsAccepted(sourceRevision: String): Result<Unit> = runCatching {
        val normalized = sourceRevision.trim()
        require(normalized.isNotEmpty()) { "Model source revision is required" }
        preferences.edit().putString(KEY_TERMS_REVISION, normalized).apply()
    }

    fun acceptedTermsRevision(): String? =
        preferences.getString(KEY_TERMS_REVISION, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveToken(token: String): Result<Unit> = runCatching {
        val normalized = token.trim()
        validateToken(normalized)
        saveEncryptedToken(normalized)
    }

    fun readToken(): String? = runCatching {
        val encoded = preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val key = getExistingKey() ?: return null
        val packed = Base64.decode(encoded, Base64.DEFAULT)
        val buffer = ByteBuffer.wrap(packed)
        val ivLength = buffer.int
        require(ivLength in MIN_IV_LENGTH..MAX_IV_LENGTH)
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()?.takeIf { it.startsWith("hf_") }

    /**
     * Creates an encrypted credential envelope suitable for the user-selected
     * SAF model folder. The envelope contains the token and the accepted gated
     * source revision, but never plaintext credentials.
     */
    fun exportSafEnvelope(): Result<ByteArray> = runCatching {
        val token = readToken() ?: throw IllegalStateException("No Hugging Face token is available")
        val termsRevision = acceptedTermsRevision().orEmpty()
        HuggingFaceAccessEnvelope.encrypt(
            token = token,
            termsRevision = termsRevision,
            key = getExistingKey() ?: throw IllegalStateException("Token key is unavailable")
        )
    }

    /** Restores the encrypted SAF envelope into the local Keystore-backed cache. */
    fun importSafEnvelope(envelope: ByteArray): Result<Unit> = runCatching {
        val key = getExistingKey() ?: throw IllegalStateException("Token key is unavailable after reinstall")
        val payload = HuggingFaceAccessEnvelope.decrypt(envelope, key)
        val token = payload.token
        validateToken(token)
        val termsRevision = payload.termsRevision.trim()
        saveEncryptedToken(token)
        preferences.edit()
            .putString(KEY_TERMS_REVISION, termsRevision.takeIf { it.isNotEmpty() })
            .apply()
    }

    fun clearToken() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TERMS_REVISION)
            .apply()
    }

    private fun validateToken(token: String) {
        require(token.startsWith("hf_")) { "Hugging Face token must start with hf_" }
        require(token.length in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH) { "Hugging Face token length is invalid" }
    }

    private fun saveEncryptedToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(INT_BYTES + cipher.iv.size + ciphertext.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(KEY_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "medbot_huggingface_access_token"
        private const val PREFERENCES_NAME = "medbot_secure_credentials"
        private const val KEY_TOKEN = "huggingface_token_gcm"
        private const val KEY_TERMS_REVISION = "huggingface_terms_source_revision"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val INT_BYTES = 4
        private const val MIN_IV_LENGTH = 12
        private const val MAX_IV_LENGTH = 16
        private const val MIN_TOKEN_LENGTH = 10
        private const val MAX_TOKEN_LENGTH = 512
    }
}
