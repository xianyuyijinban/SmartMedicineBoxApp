package com.smartmedicine.repository

import android.content.Context
import com.smartmedicine.data.db.*
import com.smartmedicine.data.model.SensorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 历史数据仓库
 * 
 * 管理传感器历史数据的存储和查询
 */
class HistoryRepository(context: Context) {
    
    private val dao = AppDatabase.getInstance(context).sensorDataDao()
    
    companion object {
        @Volatile
        private var instance: HistoryRepository? = null
        
        fun getInstance(context: Context): HistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        /**
         * 清理实例
         */
        fun clearInstance() {
            instance = null
        }
    }
    
    /**
     * 保存传感器数据
     */
    suspend fun saveSensorData(deviceId: String, data: SensorData) {
        withContext(Dispatchers.IO) {
            try {
                val entity = SensorDataEntity(
                    deviceId = deviceId,
                    timestamp = data.timestamp,
                    state = data.state,
                    temperature = data.environment?.temperature,
                    humidity = data.environment?.humidity,
                    pressure = data.environment?.pressure,
                    altitude = data.environment?.altitude,
                    accelX = data.motion?.accelX,
                    accelY = data.motion?.accelY,
                    accelZ = data.motion?.accelZ,
                    gyroX = data.motion?.gyroX,
                    gyroY = data.motion?.gyroY,
                    gyroZ = data.motion?.gyroZ,
                    pitch = data.motion?.pitch,
                    roll = data.motion?.roll,
                    vibration = data.motion?.vibration,
                    valid = data.valid
                )
                dao.insert(entity)
                Timber.d("历史数据已保存: $deviceId")
                
                // 清理过期数据
                cleanupOldData()
            } catch (e: Exception) {
                Timber.e(e, "保存历史数据失败")
            }
        }
    }
    
    /**
     * 获取指定设备的所有历史数据
     */
    fun getHistoryByDevice(deviceId: String): Flow<List<SensorDataEntity>> {
        return dao.getByDeviceId(deviceId)
    }
    
    /**
     * 获取指定设备的最近N条数据
     */
    suspend fun getRecentHistory(deviceId: String, limit: Int = 100): List<SensorDataEntity> {
        return withContext(Dispatchers.IO) {
            dao.getRecentByDeviceId(deviceId, limit)
        }
    }
    
    /**
     * 获取指定时间范围的数据
     */
    suspend fun getHistoryByTimeRange(
        deviceId: String,
        hours: Int = 24
    ): List<SensorDataEntity> {
        return withContext(Dispatchers.IO) {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.HOURS.toMillis(hours.toLong())
            dao.getByTimeRange(deviceId, startTime, endTime)
        }
    }
    
    /**
     * 获取最近24小时的数据（用于图表显示）
     */
    suspend fun getDataForCharts(deviceId: String): List<SensorDataEntity> {
        return getHistoryByTimeRange(deviceId, 24)
    }
    
    /**
     * 获取温度统计数据
     */
    suspend fun getTemperatureStats(
        deviceId: String,
        hours: Int = 24
    ): TemperatureStats? {
        return withContext(Dispatchers.IO) {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.HOURS.toMillis(hours.toLong())
            dao.getTemperatureStats(deviceId, startTime, endTime)
        }
    }
    
    /**
     * 获取湿度统计数据
     */
    suspend fun getHumidityStats(
        deviceId: String,
        hours: Int = 24
    ): HumidityStats? {
        return withContext(Dispatchers.IO) {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.HOURS.toMillis(hours.toLong())
            dao.getHumidityStats(deviceId, startTime, endTime)
        }
    }
    
    /**
     * 获取数据总数
     */
    suspend fun getTotalCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getCount()
        }
    }
    
    /**
     * 删除指定设备的所有数据
     */
    suspend fun deleteDeviceHistory(deviceId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteByDeviceId(deviceId)
            Timber.d("已删除设备 $deviceId 的历史数据")
        }
    }
    
    /**
     * 清空所有历史数据
     */
    suspend fun clearAllHistory() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
            Timber.d("所有历史数据已清空")
        }
    }
    
    /**
     * 清理过期数据（超过30天）
     */
    private suspend fun cleanupOldData() {
        val cutoffTime = System.currentTimeMillis() - 
            TimeUnit.DAYS.toMillis(SensorDataEntity.MAX_STORAGE_DAYS.toLong())
        dao.deleteBefore(cutoffTime)
        
        // 如果数据量超过限制，删除最旧的数据
        val count = dao.getCount()
        if (count > SensorDataEntity.MAX_RECORDS) {
            val oldestTime = dao.getOldestRecordTime()
            oldestTime?.let {
                // 删除最旧的10%数据
                val deleteBefore = it + ((System.currentTimeMillis() - it) / 10)
                dao.deleteBefore(deleteBefore)
                Timber.d("数据量超限，已清理旧数据")
            }
        }
    }
}
