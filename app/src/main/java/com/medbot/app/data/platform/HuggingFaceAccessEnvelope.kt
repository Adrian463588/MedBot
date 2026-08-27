package com.medbot.app.data.platform

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class HuggingFaceAccessEnvelopePayload(
    val token: String,
    val termsRevision: String
)

/** Pure AES-GCM envelope codec used by the Keystore and SAF credential paths. */
internal object HuggingFaceAccessEnvelope {
    private const val INT_BYTES = 4
    private const val GCM_TAG_BITS = 128
    private const val MIN_IV_LENGTH = 12
    private const val MAX_IV_LENGTH = 16
    private const val MIN_CIPHERTEXT_BYTES = 16
    private const val MAX_TOKEN_BYTES = 2_048
    private const val MAX_SOURCE_REVISION_BYTES = 256
    private const val MAX_ENVELOPE_BYTES = 8 * 1024
    private val MAGIC = "MEDBOT_HF_ACCESS_V1".toByteArray(Charsets.US_ASCII)

    fun encrypt(token: String, termsRevision: String, key: SecretKey): ByteArray {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        val termsBytes = termsRevision.toByteArray(Charsets.UTF_8)
        require(tokenBytes.size in 1..MAX_TOKEN_BYTES)
        require(termsBytes.size <= MAX_SOURCE_REVISION_BYTES)
        val plaintext = ByteBuffer.allocate(INT_BYTES + tokenBytes.size + INT_BYTES + termsBytes.size)
            .putInt(tokenBytes.size)
            .put(tokenBytes)
            .putInt(termsBytes.size)
            .put(termsBytes)
            .array()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(MAGIC.size + INT_BYTES + cipher.iv.size + INT_BYTES + ciphertext.size)
            .put(MAGIC)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .putInt(ciphertext.size)
            .put(ciphertext)
            .array()
            .also { require(it.size <= MAX_ENVELOPE_BYTES) }
    }

    fun decrypt(envelope: ByteArray, key: SecretKey): HuggingFaceAccessEnvelopePayload {
        require(envelope.size in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES)
        val buffer = ByteBuffer.wrap(envelope)
        val magic = ByteArray(MAGIC.size)
        buffer.get(magic)
        require(magic.contentEquals(MAGIC))
        val ivLength = buffer.int
        require(ivLength in MIN_IV_LENGTH..MAX_IV_LENGTH)
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertextLength = buffer.int
        require(ciphertextLength in MIN_CIPHERTEXT_BYTES..buffer.remaining())
        val ciphertext = ByteArray(ciphertextLength)
        buffer.get(ciphertext)
        require(!buffer.hasRemaining())

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val payload = ByteBuffer.wrap(cipher.doFinal(ciphertext))
        val tokenLength = payload.int
        require(tokenLength in 1..MAX_TOKEN_BYTES)
        val tokenBytes = ByteArray(tokenLength)
        payload.get(tokenBytes)
        val termsLength = payload.int
        require(termsLength in 0..MAX_SOURCE_REVISION_BYTES)
        val termsBytes = ByteArray(termsLength)
        payload.get(termsBytes)
        require(!payload.hasRemaining())
        return HuggingFaceAccessEnvelopePayload(
            token = String(tokenBytes, Charsets.UTF_8),
            termsRevision = String(termsBytes, Charsets.UTF_8)
        )
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val MIN_ENVELOPE_BYTES = MAGIC.size + INT_BYTES + MIN_IV_LENGTH + INT_BYTES + MIN_CIPHERTEXT_BYTES
}
