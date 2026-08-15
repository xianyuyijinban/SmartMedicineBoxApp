package com.smartmedicine.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_doses")
data class MedicineDoseEntity(
    @PrimaryKey(autoGenerate = true)
    val doseId: Long = 0L,
    val planId: Long,
    val boxId: Int,
    val medicineName: String,
    val doseAmount: Int = 1,
    val scheduledAt: Long,
    val status: String = STATUS_PENDING,
    val timerId: Int? = null,
    val completedAt: Long? = null,
    val actualTakenAt: Long? = null,
    val riskLevel: Int = 0,
    val reminderCount: Int = 0,
    val smartReason: String = "",
    val note: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_TAKEN = "taken"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_MISSED = "missed"
        const val STATUS_SNOOZED = "snoozed"
    }
}
