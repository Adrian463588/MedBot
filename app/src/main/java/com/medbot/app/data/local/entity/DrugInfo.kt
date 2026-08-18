package com.medbot.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drug_info")
data class DrugInfo(
    @PrimaryKey val name: String,
    val genericName: String,
    val indication: String,
    val adultDose: String,
    val childDose: String?,
    val contraindications: String?,
    val sideEffects: String?,
    val isOtc: Boolean
)
