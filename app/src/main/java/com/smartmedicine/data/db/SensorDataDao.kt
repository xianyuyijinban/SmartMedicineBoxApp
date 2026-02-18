package com.smartmedicine.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 传感器数据访问对象
 * 
 * 提供对sensor_data_history表的CRUD操作
 */
@Dao
interface SensorDataDao {
    
    /**
     * 插入单条传感器数据
     */
    @Insert
    suspend fun insert(data: SensorDataEntity): Long
    
    /**
     * 插入多条传感器数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dataList: List<SensorDataEntity>)
    
    /**
     * 根据ID删除数据
     */
    @Delete
    suspend fun delete(data: SensorDataEntity)
    
    /**
     * 删除指定时间之前的数据
     */
    @Query("DELETE FROM sensor_data_history WHERE recordedAt < :timestamp")
    suspend fun deleteBefore(timestamp: Long)
    
    /**
     * 删除指定设备的所有数据
     */
    @Query("DELETE FROM sensor_data_history WHERE deviceId = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)
    
    /**
     * 清空所有数据
     */
    @Query("DELETE FROM sensor_data_history")
    suspend fun deleteAll()
    
    /**
     * 获取所有数据（按时间倒序）
     */
    @Query("SELECT * FROM sensor_data_history ORDER BY recordedAt DESC")
    fun getAll(): Flow<List<SensorDataEntity>>
    
    /**
     * 获取指定设备的所有数据（按时间倒序）
     */
    @Query("SELECT * FROM sensor_data_history WHERE deviceId = :deviceId ORDER BY recordedAt DESC")
    fun getByDeviceId(deviceId: String): Flow<List<SensorDataEntity>>
    
    /**
     * 获取指定设备的最近N条数据
     */
    @Query("SELECT * FROM sensor_data_history WHERE deviceId = :deviceId ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getRecentByDeviceId(deviceId: String, limit: Int): List<SensorDataEntity>
    
    /**
     * 获取指定时间范围内的数据
     */
    @Query("""
        SELECT * FROM sensor_data_history 
        WHERE deviceId = :deviceId 
        AND recordedAt BETWEEN :startTime AND :endTime 
        ORDER BY recordedAt ASC
    """)
    suspend fun getByTimeRange(
        deviceId: String, 
        startTime: Long, 
        endTime: Long
    ): List<SensorDataEntity>
    
    /**
     * 获取指定设备的最新一条数据
     */
    @Query("SELECT * FROM sensor_data_history WHERE deviceId = :deviceId ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatestByDeviceId(deviceId: String): SensorDataEntity?
    
    /**
     * 获取数据总数
     */
    @Query("SELECT COUNT(*) FROM sensor_data_history")
    suspend fun getCount(): Int
    
    /**
     * 获取指定设备的数据数量
     */
    @Query("SELECT COUNT(*) FROM sensor_data_history WHERE deviceId = :deviceId")
    suspend fun getCountByDeviceId(deviceId: String): Int
    
    /**
     * 获取数据数量（Flow）
     */
    @Query("SELECT COUNT(*) FROM sensor_data_history")
    fun getCountFlow(): Flow<Int>
    
    /**
     * 获取最早的记录时间
     */
    @Query("SELECT MIN(recordedAt) FROM sensor_data_history")
    suspend fun getOldestRecordTime(): Long?
    
    /**
     * 获取最新的记录时间
     */
    @Query("SELECT MAX(recordedAt) FROM sensor_data_history")
    suspend fun getLatestRecordTime(): Long?
    
    /**
     * 获取温度统计数据（指定时间范围）
     */
    @Query("""
        SELECT 
            AVG(temperature) as avgTemp,
            MIN(temperature) as minTemp,
            MAX(temperature) as maxTemp
        FROM sensor_data_history 
        WHERE deviceId = :deviceId 
        AND recordedAt BETWEEN :startTime AND :endTime
        AND temperature IS NOT NULL
    """)
    suspend fun getTemperatureStats(
        deviceId: String,
        startTime: Long,
        endTime: Long
    ): TemperatureStats?
    
    /**
     * 获取湿度统计数据（指定时间范围）
     */
    @Query("""
        SELECT 
            AVG(humidity) as avgHumidity,
            MIN(humidity) as minHumidity,
            MAX(humidity) as maxHumidity
        FROM sensor_data_history 
        WHERE deviceId = :deviceId 
        AND recordedAt BETWEEN :startTime AND :endTime
        AND humidity IS NOT NULL
    """)
    suspend fun getHumidityStats(
        deviceId: String,
        startTime: Long,
        endTime: Long
    ): HumidityStats?
}

/**
 * 温度统计数据
 */
data class TemperatureStats(
    val avgTemp: Double?,
    val minTemp: Double?,
    val maxTemp: Double?
)

/**
 * 湿度统计数据
 */
data class HumidityStats(
    val avgHumidity: Double?,
    val minHumidity: Double?,
    val maxHumidity: Double?
)
