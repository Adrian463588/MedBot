package com.medbot.app.data.rag

import java.io.IOException

/** Explicit local RAG availability states. */
enum class RagFailureCode { EMBEDDER_UNAVAILABLE, PARSER_UNAVAILABLE, INVALID_DOCUMENT }

/** Typed failure used instead of invented pages, vectors, or document metadata. */
class RagProcessingException(
    val code: RagFailureCode,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
