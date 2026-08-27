package com.medbot.app.domain.model

/** Trust tier assigned by the allowlisted online source adapter. */
enum class OnlineEvidenceSourceRole {
    /** Official guidance or health-topic material that may support general care facts. */
    AUTHORITATIVE_GUIDANCE,

    /** Indexed biomedical research; useful context, but not an individual prescription. */
    PRIMARY_RESEARCH,

    /** Educational material that must not satisfy a medication evidence gate by itself. */
    SECONDARY_EDUCATION
}

/** Explicit failure states for optional online evidence retrieval. */
enum class OnlineEvidenceFailure {
    UNSAFE_QUERY,
    OFFLINE,
    ROBOTS_DISALLOWED,
    NO_RESULTS,
    NETWORK_ERROR,
    SOURCE_UNAVAILABLE,
    RESPONSE_TOO_LARGE,
    INVALID_RESPONSE
}

/** A bounded, provenance-preserving web excerpt. The local model is the only answer generator. */
data class OnlineEvidence(
    val sourceName: String,
    val title: String,
    val url: String,
    val excerpt: String,
    val sourceRole: OnlineEvidenceSourceRole,
    val retrievedAt: Long,
    val responseSha256: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val freshnessUntil: Long? = null
)

data class OnlineEvidenceBundle(
    val sanitizedQuery: String,
    val sources: List<OnlineEvidence>
)

sealed interface OnlineEvidenceResult {
    data class Success(val bundle: OnlineEvidenceBundle) : OnlineEvidenceResult

    data class Unavailable(
        val failure: OnlineEvidenceFailure,
        val detail: String
    ) : OnlineEvidenceResult
}
