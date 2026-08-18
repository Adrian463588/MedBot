package com.medbot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medbot.app.data.local.entity.DocChunk
import com.medbot.app.data.local.entity.RagDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: RagDocument): Long

    @Update
    suspend fun updateDocument(document: RagDocument)

    @Delete
    suspend fun deleteDocument(document: RagDocument)

    @Query("SELECT * FROM rag_documents ORDER BY indexedAt DESC")
    fun getAllDocuments(): Flow<List<RagDocument>>

    @Query("SELECT * FROM rag_documents WHERE id = :docId")
    suspend fun getDocumentById(docId: Long): RagDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocChunk>)

    @Query("SELECT * FROM doc_chunks WHERE docId = :docId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocument(docId: Long): List<DocChunk>

    @Query("SELECT * FROM doc_chunks")
    suspend fun getAllChunks(): List<DocChunk>
}
