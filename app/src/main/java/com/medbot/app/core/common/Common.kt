package com.medbot.app.core.common

sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

object AppConstants {
    const val DATABASE_NAME = "medbot_local.db"
    const val PREFERENCES_NAME = "medbot_prefs"
    const val MAX_CHAT_HISTORY_CONTEXT = 10
    const val RAG_CHUNK_SIZE = 512
    const val RAG_CHUNK_OVERLAP = 50
    const val DEFAULT_TOP_K_RAG = 4
    const val MODEL_DIR_NAME = "models"
    const val SKIN_IMAGES_DIR = "skin_lineage"
    const val DOCUMENTS_DIR = "rag_docs"
}

data class ParsedAiMessage(
    val thinkingContent: String?,
    val isThinking: Boolean,
    val displayContent: String
)

object AiOutputFormatter {
    private val THINK_BLOCK_REGEX = Regex("(?s)<think>(.*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)

    fun parse(rawText: String, isGenerating: Boolean = false): ParsedAiMessage {
        val trimmed = rawText.trim()
        if (!trimmed.contains("<think>")) {
            return ParsedAiMessage(
                thinkingContent = null,
                isThinking = false,
                displayContent = rawText
            )
        }

        val thinkStart = trimmed.indexOf("<think>")
        val thinkEnd = trimmed.indexOf("</think>")

        return if (thinkEnd == -1) {
            val thinking = trimmed.substring(thinkStart + 7).trim()
            ParsedAiMessage(
                thinkingContent = thinking.ifBlank { null },
                isThinking = true,
                displayContent = ""
            )
        } else {
            val thinking = trimmed.substring(thinkStart + 7, thinkEnd).trim()
            val content = trimmed.substring(thinkEnd + 8).trim()
            ParsedAiMessage(
                thinkingContent = thinking.ifBlank { null },
                isThinking = false,
                displayContent = content
            )
        }
    }

    fun cleanThinkingTags(text: String): String {
        return parse(text).displayContent
    }

    /**
     * Sanitizes and cleans chat session titles to prevent AI thinking leaks,
     * CJK artifacts, markdown symbols, and multiline clutter.
     */
    fun cleanSessionTitle(rawText: String): String {
        val withoutThinking = cleanThinkingTags(rawText).trim()
        val withoutTags = withoutThinking.replace(Regex("<[^>]+>"), " ")
        val cleanedChars = sanitizeMedicalText(withoutTags)
            .replace(Regex("[#*`_~>\\[\\]()•\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val candidate = cleanedChars.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        if (candidate.isBlank() || candidate.startsWith("Okay, the user", ignoreCase = true) || candidate.startsWith("The user is", ignoreCase = true)) {
            return "Konsultasi Medis"
        }
        val capitalized = candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        return if (capitalized.length > 36) {
            capitalized.take(34).trimEnd() + "..."
        } else {
            capitalized
        }
    }

    /** Filters malformed CJK output without changing the model's medical claims. */
    fun sanitizeMedicalText(text: String): String {
        return text
            .replace(Regex("[\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uF900-\\uFAFF]"), "")
    }
}
