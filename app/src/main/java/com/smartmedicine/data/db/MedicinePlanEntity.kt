package com.smartmedicine.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_plans")
data class MedicinePlanEntity(
    @PrimaryKey(autoGenerate = true)
    val planId: Long = 0L,
    val boxId: Int,
    val medicineName: String,
    val doseAmount: Int,
    val hour: Int,
    val minute: Int,
    val repeatType: String,
    val daysOfWeek: String = "",
    val startDate: String,
    val endDate: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
