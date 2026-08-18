package com.medbot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medbot.app.data.local.entity.SkinRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SkinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkinRecord(record: SkinRecord): Long

    @Update
    suspend fun updateSkinRecord(record: SkinRecord)

    @Delete
    suspend fun deleteSkinRecord(record: SkinRecord)

    @Query("SELECT * FROM skin_records ORDER BY createdAt DESC")
    fun getAllSkinRecords(): Flow<List<SkinRecord>>

    @Query("SELECT * FROM skin_records WHERE id = :recordId")
    suspend fun getSkinRecordById(recordId: Long): SkinRecord?
}
