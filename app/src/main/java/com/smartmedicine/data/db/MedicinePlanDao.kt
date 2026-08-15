package com.smartmedicine.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicinePlanDao {
    @Query("SELECT * FROM medicine_plans ORDER BY enabled DESC, hour ASC, minute ASC")
    fun observeAll(): Flow<List<MedicinePlanEntity>>

    @Query("SELECT * FROM medicine_plans WHERE enabled = 1")
    suspend fun getEnabled(): List<MedicinePlanEntity>

    @Query("SELECT * FROM medicine_plans WHERE planId = :planId LIMIT 1")
    suspend fun getById(planId: Long): MedicinePlanEntity?

    @Insert
    suspend fun insert(plan: MedicinePlanEntity): Long

    @Update
    suspend fun update(plan: MedicinePlanEntity)

    @Delete
    suspend fun delete(plan: MedicinePlanEntity)

    @Query("UPDATE medicine_plans SET enabled = :enabled WHERE planId = :planId")
    suspend fun setEnabled(planId: Long, enabled: Boolean)

    @Query("UPDATE medicine_plans SET enabled = 0 WHERE boxId = :boxId")
    suspend fun disableByBoxId(boxId: Int)

    @Query("UPDATE medicine_plans SET boxId = :toBoxId, medicineName = :medicineName WHERE boxId = :fromBoxId")
    suspend fun transferBox(fromBoxId: Int, toBoxId: Int, medicineName: String)
}
