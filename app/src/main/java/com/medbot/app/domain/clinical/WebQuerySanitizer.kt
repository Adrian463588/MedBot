package com.medbot.app.domain.clinical

/** Result of removing or rejecting data before it can leave the device. */
sealed interface WebQueryDecision {
    data class Allowed(
        val query: String,
        val redacted: Boolean
    ) : WebQueryDecision

    data class Blocked(val reason: String) : WebQueryDecision
}

/**
 * Keeps the optional web search limited to a short clinical topic query.
 * Conversation history, images, persona profile, and patient identifiers are
 * never passed to the web gateway.
 */
object WebQuerySanitizer {
    private val whitespace = Regex("\\s+")
    private val email = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val phone = Regex("(?<!\\d)(?:\\+?62|0)?8\\d{7,12}(?!\\d)")
    private val longNumber = Regex("(?<!\\d)\\d{10,16}(?!\\d)")
    private val date = Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b")
    private val personalPronouns = Regex(
        "\\b(?:saya|aku|kami|kita|i|me|my|pasien|patient)\\b",
        RegexOption.IGNORE_CASE
    )
    private val capitalizedWord = Regex("^[A-Z][a-z]{2,}$")
    private val identityTerms = listOf(
        "nama saya", "my name", "alamat saya", "my address", "nomor ktp", "nik ",
        "bpjs", "email saya", "nomor telepon", "phone number", "patient id"
    )

    fun sanitize(rawQuery: String): WebQueryDecision {
        val normalized = rawQuery
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(whitespace, " ")
            .trim()
            .take(MAX_QUERY_CHARS)
        if (normalized.isBlank()) return WebQueryDecision.Blocked("empty query")
        if (identityTerms.any(normalized.lowercase()::contains)) {
            return WebQueryDecision.Blocked("remove personal identifiers before using web evidence")
        }
        if (containsLikelyPersonalName(normalized)) {
            return WebQueryDecision.Blocked("use a topic-only query without a person's name")
        }

        val scrubbed = normalized
            .replace(email, " ")
            .replace(phone, " ")
            .replace(longNumber, " ")
            .replace(date, " ")
            .replace(personalPronouns, " ")
            .replace(whitespace, " ")
            .trim()
        if (scrubbed.length < MIN_QUERY_CHARS || scrubbed.count { it.isLetter() } < MIN_LETTERS) {
            return WebQueryDecision.Blocked("query does not contain a safe clinical topic")
        }
        return WebQueryDecision.Allowed(scrubbed, scrubbed != normalized)
    }

    private fun containsLikelyPersonalName(value: String): Boolean {
        val tokens = value.split(whitespace).filter(String::isNotBlank)
        if (tokens.size < 2) return false
        val hasTopicWord = tokens.any { token ->
            token.trim(',', '.', ':', ';', '!', '?').lowercase() in SAFE_CAPITALIZED_TOPIC_WORDS
        }
        return tokens.withIndex().any { (index, token) ->
            val word = token.trim(',', '.', ':', ';', '!', '?')
            (index > 0 || hasTopicWord) && capitalizedWord.matches(word) &&
                word.lowercase() !in SAFE_CAPITALIZED_TOPIC_WORDS
        }
    }

    private val SAFE_CAPITALIZED_TOPIC_WORDS = setOf(
        "diare", "diarrhea", "diarrhoea", "demam", "fever", "batuk", "cough", "muntah",
        "vomiting", "nyeri", "pain", "ruam", "rash", "asma", "asthma", "pneumonia",
        "diabetes", "hipertensi", "hypertension", "migrain", "migraine", "flu", "covid",
        "covid-19", "hiv", "aids", "sars", "mers", "obat", "medicine", "medication",
        "dosis", "dose", "resep", "prescription", "racikan", "compounding", "dewasa",
        "adult", "anak", "child", "bayi", "infant", "hamil", "pregnancy", "kehamilan",
        "gejala", "symptom", "penanganan", "treatment", "diagnosis", "triase", "triage",
        "urgent", "emergency", "who", "ncbi", "pubmed", "what", "how", "is", "are", "can",
        "should", "please", "jelaskan", "bagaimana", "apa", "saya", "aku", "kami", "kita",
        "pasien", "patient", "my", "me", "i"
    )

    private const val MAX_QUERY_CHARS = 320
    private const val MIN_QUERY_CHARS = 3
    private const val MIN_LETTERS = 3
}
