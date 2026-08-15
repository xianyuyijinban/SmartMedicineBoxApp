package com.smartmedicine.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDoseDao {
    @Query("SELECT * FROM medicine_doses ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<MedicineDoseEntity>>

    @Query("SELECT * FROM medicine_doses WHERE status = :status AND scheduledAt >= :from ORDER BY scheduledAt ASC LIMIT :limit")
    suspend fun getUpcoming(status: String = MedicineDoseEntity.STATUS_PENDING, from: Long, limit: Int): List<MedicineDoseEntity>

    @Query("SELECT * FROM medicine_doses WHERE status = :status ORDER BY scheduledAt ASC")
    suspend fun getByStatus(status: String): List<MedicineDoseEntity>

    @Query("SELECT * FROM medicine_doses WHERE status IN (:statuses) ORDER BY scheduledAt ASC")
    suspend fun getByStatuses(statuses: List<String>): List<MedicineDoseEntity>

    @Query("SELECT * FROM medicine_doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt ASC")
    suspend fun getBetween(from: Long, to: Long): List<MedicineDoseEntity>

    @Query("SELECT * FROM medicine_doses WHERE doseId = :doseId LIMIT 1")
    suspend fun getById(doseId: Long): MedicineDoseEntity?

    @Query("SELECT * FROM medicine_doses WHERE planId = :planId AND scheduledAt = :scheduledAt LIMIT 1")
    suspend fun findExisting(planId: Long, scheduledAt: Long): MedicineDoseEntity?

    @Query("SELECT * FROM medicine_doses WHERE planId = :planId AND scheduledAt >= :from AND scheduledAt < :to AND status IN (:statuses) LIMIT 1")
    suspend fun findBlockingForPlanDay(planId: Long, from: Long, to: Long, statuses: List<String>): MedicineDoseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dose: MedicineDoseEntity): Long

    @Update
    suspend fun update(dose: MedicineDoseEntity)

    @Query("UPDATE medicine_doses SET timerId = NULL WHERE status = :status")
    suspend fun clearPendingTimerIds(status: String = MedicineDoseEntity.STATUS_PENDING)

    @Query("UPDATE medicine_doses SET status = :newStatus, timerId = NULL, smartReason = :reason WHERE planId = :planId AND status IN (:statuses)")
    suspend fun markPlanDoses(planId: Long, newStatus: String, statuses: List<String>, reason: String)

    @Query("UPDATE medicine_doses SET status = :newStatus, riskLevel = 2, smartReason = '超过计划时间 30 分钟未确认' WHERE status = :oldStatus AND scheduledAt < :before")
    suspend fun markOverdue(oldStatus: String, newStatus: String, before: Long)

    @Query("UPDATE medicine_doses SET status = :newStatus, timerId = NULL, riskLevel = 1, smartReason = :reason WHERE boxId = :boxId AND status IN (:statuses)")
    suspend fun markBoxDoses(boxId: Int, newStatus: String, statuses: List<String>, reason: String)

    @Query("UPDATE medicine_doses SET boxId = :toBoxId, medicineName = :medicineName, timerId = NULL WHERE boxId = :fromBoxId AND status IN (:statuses)")
    suspend fun transferBoxDoses(fromBoxId: Int, toBoxId: Int, medicineName: String, statuses: List<String>)
}
