package com.medbot.app

import com.medbot.app.data.platform.HuggingFaceAccessEnvelope
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceAccessEnvelopeTest {
    @Test
    fun roundTripKeepsCredentialsEncrypted() {
        val token = "hf_test_read_only_token_123456"
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

        val envelope = HuggingFaceAccessEnvelope.encrypt(token, "medgemma-terms-v1", key)
        val encoded = envelope.toString(Charsets.ISO_8859_1)
        val restored = HuggingFaceAccessEnvelope.decrypt(envelope, key)

        assertFalse(encoded.contains(token))
        assertEquals(token, restored.token)
        assertEquals("medgemma-terms-v1", restored.termsRevision)
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        val envelope = HuggingFaceAccessEnvelope.encrypt("hf_test_read_only_token_123456", "terms", key)
        val tampered = envelope.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }

        val rejected = runCatching {
            HuggingFaceAccessEnvelope.decrypt(tampered, key)
        }.isFailure
        assertTrue(rejected)
    }
}
