package com.medbot.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rag_documents")
data class RagDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileUri: String,
    val pageCount: Int,
    val chunkCount: Int,
    val sha256: String,
    val indexedAt: Long
)
