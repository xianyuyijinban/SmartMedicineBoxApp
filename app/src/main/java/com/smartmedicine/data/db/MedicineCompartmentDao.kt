package com.smartmedicine.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineCompartmentDao {
    @Query("SELECT * FROM medicine_compartments ORDER BY sortOrder ASC, boxId ASC")
    fun observeAll(): Flow<List<MedicineCompartmentEntity>>

    @Query("SELECT * FROM medicine_compartments ORDER BY sortOrder ASC, boxId ASC")
    suspend fun getAll(): List<MedicineCompartmentEntity>

    @Query("SELECT * FROM medicine_compartments WHERE active = 1 ORDER BY boxId ASC")
    suspend fun getActive(): List<MedicineCompartmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MedicineCompartmentEntity>)

    @Update
    suspend fun update(item: MedicineCompartmentEntity)

    @Query("UPDATE medicine_compartments SET name = :name, updatedAt = :updatedAt WHERE boxId = :boxId")
    suspend fun updateName(boxId: Int, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE medicine_compartments
        SET name = :name,
            stock = :stock,
            dosePerUse = :dosePerUse,
            lowStockThreshold = :lowStockThreshold,
            active = 1,
            updatedAt = :updatedAt
        WHERE boxId = :boxId
    """)
    suspend fun updateMedicineInfo(
        boxId: Int,
        name: String,
        stock: Int,
        dosePerUse: Int,
        lowStockThreshold: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE medicine_compartments SET stock = MAX(stock - :amount, 0), updatedAt = :updatedAt WHERE boxId = :boxId")
    suspend fun decrementStock(boxId: Int, amount: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE medicine_compartments
        SET name = :name,
            stock = :stock,
            dosePerUse = :dosePerUse,
            lowStockThreshold = :lowStockThreshold,
            active = 1,
            updatedAt = :updatedAt
        WHERE boxId = :boxId
    """)
    suspend fun activate(
        boxId: Int,
        name: String,
        stock: Int,
        dosePerUse: Int,
        lowStockThreshold: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE medicine_compartments
        SET name = :name,
            stock = 0,
            dosePerUse = 1,
            lowStockThreshold = 3,
            active = 0,
            updatedAt = :updatedAt
        WHERE boxId = :boxId
    """)
    suspend fun deactivate(boxId: Int, name: String, updatedAt: Long = System.currentTimeMillis())
}
