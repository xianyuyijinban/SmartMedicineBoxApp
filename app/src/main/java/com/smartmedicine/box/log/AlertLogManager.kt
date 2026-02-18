package com.smartmedicine.box.log

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.smartmedicine.box.alert.AlertData
import com.smartmedicine.box.alert.AlertLevel
import com.smartmedicine.box.alert.AlertType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 告警日志管理器
 * 
 * 功能：
 * 1. 保存告警历史到本地（使用SharedPreferences存储）
 * 2. 查询历史告警（支持按类型、级别、时间范围筛选）
 * 3. 清空日志
 * 4. 支持导出日志（JSON格式）
 * 
 * 存储方式：使用SharedPreferences存储告警记录，以JSON数组格式保存
 * 最多保存200条记录，超过时自动清理旧记录
 * 
 * @param context Android上下文
 */
class AlertLogManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AlertLogManager"
        
        // SharedPreferences文件名
        private const val PREFS_NAME = "alert_logs"
        
        // 存储键名
        private const val KEY_ALERTS = "alert_history"
        private const val KEY_LAST_CLEAR_TIME = "last_clear_time"
        
        // 最大存储记录数
        private const val MAX_LOG_COUNT = 200
        
        // 单例实例
        @Volatile
        private var instance: AlertLogManager? = null

        /**
         * 获取AlertLogManager单例实例
         * 
         * @param context Android上下文
         * @return AlertLogManager实例
         */
        fun getInstance(context: Context): AlertLogManager {
            return instance ?: synchronized(this) {
                instance ?: AlertLogManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 清除单例实例
         */
        fun clearInstance() {
            instance = null
        }
    }

    // SharedPreferences实例
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存告警记录到本地存储
     * 新记录添加到列表头部，超过最大数量时删除旧记录
     * 
     * @param alert 告警数据
     * @return 是否保存成功
     */
    fun saveAlert(alert: AlertData): Boolean {
        return try {
            val alerts = getAllAlerts().toMutableList()
            alerts.add(0, alert) // 新记录放在前面
            
            // 限制数量
            if (alerts.size > MAX_LOG_COUNT) {
                alerts.removeAt(alerts.lastIndex)
            }
            
            // 保存到SharedPreferences
            saveAlertsToPrefs(alerts)
            Log.d(TAG, "告警已保存: ${alert.type.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存告警失败", e)
            false
        }
    }

    /**
     * 批量保存告警记录
     * 
     * @param alerts 告警列表
     * @return 是否保存成功
     */
    fun saveAlerts(alerts: List<AlertData>): Boolean {
        return try {
            val existingAlerts = getAllAlerts().toMutableList()
            existingAlerts.addAll(0, alerts)
            
            // 限制数量
            while (existingAlerts.size > MAX_LOG_COUNT) {
                existingAlerts.removeAt(existingAlerts.lastIndex)
            }
            
            saveAlertsToPrefs(existingAlerts)
            Log.d(TAG, "批量保存 ${alerts.size} 条告警")
            true
        } catch (e: Exception) {
            Log.e(TAG, "批量保存告警失败", e)
            false
        }
    }

    /**
     * 获取所有告警历史记录
     * 按时间倒序排列（最新的在前）
     * 
     * @return 告警列表
     */
    fun getAllAlerts(): List<AlertData> {
        return try {
            val jsonString = prefs.getString(KEY_ALERTS, "[]") ?: "[]"
            parseAlertsFromJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "读取告警历史失败", e)
            emptyList()
        }
    }

    /**
     * 获取指定类型的告警
     * 
     * @param type 告警类型
     * @return 该类型的告警列表
     */
    fun getAlertsByType(type: AlertType): List<AlertData> {
        return getAllAlerts().filter { it.type == type }
    }

    /**
     * 获取指定级别的告警
     * 
     * @param level 告警级别
     * @return 该级别的告警列表
     */
    fun getAlertsByLevel(level: AlertLevel): List<AlertData> {
        return getAllAlerts().filter { it.level == level }
    }

    /**
     * 获取指定时间范围内的告警
     * 
     * @param startTime 开始时间戳（毫秒）
     * @param endTime 结束时间戳（毫秒）
     * @return 该时间范围内的告警列表
     */
    fun getAlertsByTimeRange(startTime: Long, endTime: Long): List<AlertData> {
        return getAllAlerts().filter { 
            it.timestamp in startTime..endTime 
        }
    }

    /**
     * 获取最近的N条告警
     * 
     * @param count 数量
     * @return 最近的告警列表
     */
    fun getRecentAlerts(count: Int): List<AlertData> {
        return getAllAlerts().take(count.coerceAtLeast(0))
    }

    /**
     * 获取告警统计信息
     * 
     * @return 统计信息（各类型、各级别数量）
     */
    fun getAlertStatistics(): AlertStatistics {
        val alerts = getAllAlerts()
        
        // 按类型统计
        val typeCount = mutableMapOf<AlertType, Int>()
        AlertType.values().forEach { typeCount[it] = 0 }
        alerts.forEach { typeCount[it.type] = typeCount.getOrDefault(it.type, 0) + 1 }
        
        // 按级别统计
        val levelCount = mutableMapOf<AlertLevel, Int>()
        AlertLevel.values().forEach { levelCount[it] = 0 }
        alerts.forEach { levelCount[it.level] = levelCount.getOrDefault(it.level, 0) + 1 }
        
        return AlertStatistics(
            totalCount = alerts.size,
            typeCount = typeCount,
            levelCount = levelCount,
            criticalCount = levelCount[AlertLevel.CRITICAL] ?: 0,
            warningCount = levelCount[AlertLevel.WARNING] ?: 0
        )
    }

    /**
     * 清空所有告警日志
     * 同时记录清空时间
     * 
     * @return 是否清空成功
     */
    fun clearLogs(): Boolean {
        return try {
            prefs.edit()
                .putString(KEY_ALERTS, "[]")
                .putLong(KEY_LAST_CLEAR_TIME, System.currentTimeMillis())
                .apply()
            Log.i(TAG, "告警日志已清空")
            true
        } catch (e: Exception) {
            Log.e(TAG, "清空日志失败", e)
            false
        }
    }

    /**
     * 获取上次清空日志的时间
     * 
     * @return 时间戳（毫秒），如果没有清空过返回0
     */
    fun getLastClearTime(): Long {
        return prefs.getLong(KEY_LAST_CLEAR_TIME, 0)
    }

    /**
     * 导出告警日志为JSON字符串
     * 可用于备份或分享
     * 
     * @return JSON格式字符串
     */
    fun exportToJson(): String {
        val alerts = getAllAlerts()
        val jsonArray = JSONArray()
        
        alerts.forEach { alert ->
            jsonArray.put(alertToJson(alert))
        }
        
        val exportObj = JSONObject().apply {
            put("exportTime", System.currentTimeMillis())
            put("exportTimeFormatted", formatTime(System.currentTimeMillis()))
            put("count", alerts.size)
            put("alerts", jsonArray)
        }
        
        return exportObj.toString(2) // 格式化输出
    }

    /**
     * 导入告警日志（从JSON）
     * 
     * @param jsonString JSON字符串
     * @return 导入的告警数量
     */
    fun importFromJson(jsonString: String): Int {
        return try {
            val jsonObj = JSONObject(jsonString)
            val jsonArray = jsonObj.getJSONArray("alerts")
            val alerts = mutableListOf<AlertData>()
            
            for (i in 0 until jsonArray.length()) {
                parseAlertFromJson(jsonArray.getJSONObject(i))?.let {
                    alerts.add(it)
                }
            }
            
            saveAlerts(alerts)
            Log.i(TAG, "导入 ${alerts.size} 条告警记录")
            alerts.size
        } catch (e: Exception) {
            Log.e(TAG, "导入日志失败", e)
            0
        }
    }

    /**
     * 删除单条告警记录
     * 
     * @param alertId 告警ID
     * @return 是否删除成功
     */
    fun deleteAlert(alertId: Long): Boolean {
        return try {
            val alerts = getAllAlerts().filter { it.id != alertId }
            saveAlertsToPrefs(alerts)
            Log.d(TAG, "删除告警记录: $alertId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "删除告警失败", e)
            false
        }
    }

    /**
     * 获取日志存储数量
     * 
     * @return 当前存储的告警数量
     */
    fun getLogCount(): Int {
        return getAllAlerts().size
    }

    /**
     * 检查存储是否已满
     * 
     * @return 是否达到最大存储数量
     */
    fun isStorageFull(): Boolean {
        return getLogCount() >= MAX_LOG_COUNT
    }

    // ==================== 私有方法 ====================

    /**
     * 保存告警列表到SharedPreferences
     */
    private fun saveAlertsToPrefs(alerts: List<AlertData>) {
        val jsonArray = JSONArray()
        alerts.forEach { alert ->
            jsonArray.put(alertToJson(alert))
        }
        
        prefs.edit()
            .putString(KEY_ALERTS, jsonArray.toString())
            .apply()
    }

    /**
     * 将告警对象转换为JSON
     */
    private fun alertToJson(alert: AlertData): JSONObject {
        return JSONObject().apply {
            put("id", alert.id)
            put("type", alert.type.name)
            put("level", alert.level.name)
            put("message", alert.message)
            put("timestamp", alert.timestamp)
            put("deviceId", alert.deviceId)
            
            // 转换data Map
            val dataObj = JSONObject()
            alert.data.forEach { (key, value) ->
                dataObj.put(key, value)
            }
            put("data", dataObj)
        }
    }

    /**
     * 从JSON字符串解析告警列表
     */
    private fun parseAlertsFromJson(jsonString: String): List<AlertData> {
        val alerts = mutableListOf<AlertData>()
        
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                parseAlertFromJson(jsonArray.getJSONObject(i))?.let {
                    alerts.add(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析告警JSON失败", e)
        }
        
        return alerts
    }

    /**
     * 从JSON对象解析单个告警
     */
    private fun parseAlertFromJson(json: JSONObject): AlertData? {
        return try {
            val type = try {
                AlertType.valueOf(json.getString("type"))
            } catch (e: IllegalArgumentException) {
                AlertType.DEVICE_OFFLINE
            }
            
            val level = try {
                AlertLevel.valueOf(json.getString("level"))
            } catch (e: IllegalArgumentException) {
                AlertLevel.INFO
            }
            
            // 解析data对象
            val dataMap = mutableMapOf<String, Any>()
            val dataObj = json.optJSONObject("data")
            dataObj?.keys()?.forEach { key ->
                dataMap[key] = dataObj.get(key)
            }
            
            AlertData(
                id = json.optLong("id", System.currentTimeMillis()),
                type = type,
                level = level,
                message = json.optString("message", ""),
                timestamp = json.optLong("timestamp", 0),
                deviceId = json.optString("deviceId", ""),
                data = dataMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析单条告警失败", e)
            null
        }
    }

    /**
     * 格式化时间戳
     */
    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

/**
 * 告警统计信息数据类
 * 
 * @param totalCount 总告警数
 * @param typeCount 各类型告警数量映射
 * @param levelCount 各级别告警数量映射
 * @param criticalCount 严重告警数
 * @param warningCount 警告告警数
 */
data class AlertStatistics(
    val totalCount: Int,
    val typeCount: Map<AlertType, Int>,
    val levelCount: Map<AlertLevel, Int>,
    val criticalCount: Int,
    val warningCount: Int
) {
    /**
     * 获取最严重级别的告警数量
     */
    fun getCriticalAlertCount(): Int {
        return criticalCount
    }

    /**
     * 是否需要关注（有严重或警告告警）
     */
    fun needsAttention(): Boolean {
        return criticalCount > 0 || warningCount > 0
    }

    override fun toString(): String {
        return buildString {
            append("总告警数: $totalCount\n")
            append("严重告警: $criticalCount\n")
            append("警告告警: $warningCount\n")
            append("类型分布:\n")
            typeCount.forEach { (type, count) ->
                if (count > 0) append("  ${type.displayName}: $count\n")
            }
        }
    }
}
