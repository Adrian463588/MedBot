package com.medbot.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(
    tableName = "doc_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RagDocument::class,
            parentColumns = ["id"],
            childColumns = ["docId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["docId"])]
)
data class DocChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val chunkIndex: Int,
    val textContent: String,
    val pageNumber: Int?,
    val sectionTitle: String?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocChunk

        if (id != other.id) return false
        if (docId != other.docId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (textContent != other.textContent) return false
        if (pageNumber != other.pageNumber) return false
        if (sectionTitle != other.sectionTitle) return false
        if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + docId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + textContent.hashCode()
        result = 31 * result + (pageNumber ?: 0)
        result = 31 * result + (sectionTitle?.hashCode() ?: 0)
        result = 31 * result + embeddingBlob.contentHashCode()
        return result
    }
}
