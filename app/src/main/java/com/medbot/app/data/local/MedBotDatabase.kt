package com.medbot.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medbot.app.data.local.dao.ChatDao
import com.medbot.app.data.local.dao.DrugDao
import com.medbot.app.data.local.dao.RagDao
import com.medbot.app.data.local.dao.SkinDao
import com.medbot.app.data.local.entity.ChatMessage
import com.medbot.app.data.local.entity.ChatSession
import com.medbot.app.data.local.entity.DocChunk
import com.medbot.app.data.local.entity.DrugInfo
import com.medbot.app.data.local.entity.RagDocument
import com.medbot.app.data.local.entity.SkinRecord

@Database(
    entities = [
        ChatSession::class,
        ChatMessage::class,
        RagDocument::class,
        DocChunk::class,
        SkinRecord::class,
        DrugInfo::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MedBotDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun ragDao(): RagDao
    abstract fun skinDao(): SkinDao
    abstract fun drugDao(): DrugDao
}
