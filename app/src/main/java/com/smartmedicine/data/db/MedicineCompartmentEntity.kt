package com.smartmedicine.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_compartments")
data class MedicineCompartmentEntity(
    @PrimaryKey
    val boxId: Int,
    val name: String,
    val sortOrder: Int,
    val stock: Int = 0,
    val dosePerUse: Int = 1,
    val lowStockThreshold: Int = 3,
    val active: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
