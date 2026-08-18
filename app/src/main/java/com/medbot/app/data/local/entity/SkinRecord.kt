package com.medbot.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skin_records")
data class SkinRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyPart: String,
    val imagePath: String,
    val asymmetryScore: Float?,
    val borderScore: Float?,
    val colorScore: Float?,
    val diameterMm: Float?,
    val differentialDx: String?,
    val urgencyLevel: String?,
    val userNotes: String?,
    val createdAt: Long
)
