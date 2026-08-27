package com.medbot.app

import com.medbot.app.domain.clinical.WebQueryDecision
import com.medbot.app.domain.clinical.WebQuerySanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebQuerySanitizerTest {
    @Test
    fun `safe topic is allowed without patient context`() {
        val result = WebQuerySanitizer.sanitize("penanganan diare pada dewasa")
        assertEquals("penanganan diare pada dewasa", (result as WebQueryDecision.Allowed).query)
        assertTrue(!result.redacted)
    }

    @Test
    fun `identifying language is blocked instead of sent`() {
        val result = WebQuerySanitizer.sanitize("nama saya Budi, apa obat batuk?")
        assertTrue(result is WebQueryDecision.Blocked)
    }

    @Test
    fun `email phone and date are removed before retrieval`() {
        val result = WebQuerySanitizer.sanitize("diare 081234567890 email budi@example.com pada 12/01/2026")
        val allowed = result as WebQueryDecision.Allowed
        assertTrue(allowed.redacted)
        assertTrue(!allowed.query.contains("081234567890"))
        assertTrue(!allowed.query.contains("budi@example.com"))
        assertTrue(!allowed.query.contains("12/01/2026"))
    }

    @Test
    fun `first person wording is reduced to a topic-only query`() {
        val result = WebQuerySanitizer.sanitize("Saya demam dan batuk") as WebQueryDecision.Allowed

        assertEquals("demam dan batuk", result.query)
        assertTrue(result.redacted)
    }

    @Test
    fun `likely person name is blocked when paired with a clinical topic`() {
        val result = WebQuerySanitizer.sanitize("Budi demam")

        assertTrue(result is WebQueryDecision.Blocked)
    }
}
