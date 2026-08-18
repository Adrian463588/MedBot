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
