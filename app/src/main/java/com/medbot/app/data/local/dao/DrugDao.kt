package com.medbot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medbot.app.data.local.entity.DrugInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrug(drug: DrugInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrugs(drugs: List<DrugInfo>)

    @Update
    suspend fun updateDrug(drug: DrugInfo)

    @Delete
    suspend fun deleteDrug(drug: DrugInfo)

    @Query("SELECT * FROM drug_info ORDER BY name ASC")
    fun getAllDrugs(): Flow<List<DrugInfo>>

    @Query("SELECT * FROM drug_info WHERE name = :name")
    suspend fun getDrugByName(name: String): DrugInfo?

    @Query("SELECT * FROM drug_info WHERE name LIKE '%' || :query || '%' OR genericName LIKE '%' || :query || '%'")
    suspend fun searchDrugs(query: String): List<DrugInfo>
}
