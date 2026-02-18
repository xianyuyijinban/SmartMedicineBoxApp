package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * JSON解析工具类
 * 封装所有数据模型的序列化和反序列化逻辑
 */
object JsonParser {
    
    /**
     * Gson实例，配置美化输出
     */
    val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create()
    }
    
    /**
     * 紧凑格式的Gson实例
     */
    val compactGson: Gson by lazy {
        Gson()
    }
    
    // ==================== SensorData 解析 ====================
    
    /**
     * 解析传感器数据JSON
     */
    fun parseSensorData(json: String): SensorData? {
        return SensorData.fromJson(json)
    }
    
    /**
     * 传感器数据转为JSON
     */
    fun toJson(sensorData: SensorData, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(sensorData) else compactGson.toJson(sensorData)
    }
    
    // ==================== DeviceStatus 解析 ====================
    
    /**
     * 解析设备状态JSON
     */
    fun parseDeviceStatus(json: String): DeviceStatus? {
        return DeviceStatus.fromJson(json)
    }
    
    /**
     * 设备状态转为JSON
     */
    fun toJson(deviceStatus: DeviceStatus, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(deviceStatus) else compactGson.toJson(deviceStatus)
    }
    
    // ==================== CommandResponse 解析 ====================
    
    /**
     * 解析命令响应JSON
     */
    fun parseCommandResponse(json: String): CommandResponse? {
        return CommandResponse.fromJson(json)
    }
    
    /**
     * 命令响应转为JSON
     */
    fun toJson(commandResponse: CommandResponse, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(commandResponse) else compactGson.toJson(commandResponse)
    }
    
    // ==================== ControlCommand 解析 ====================
    
    /**
     * 解析控制命令JSON
     */
    fun parseControlCommand(json: String): ControlCommand? {
        return ControlCommand.fromJson(json)
    }
    
    /**
     * 控制命令转为JSON
     */
    fun toJson(controlCommand: ControlCommand, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(controlCommand) else compactGson.toJson(controlCommand)
    }
    
    // ==================== EnvironmentData 解析 ====================
    
    /**
     * 解析环境数据JSON
     */
    fun parseEnvironmentData(json: String): EnvironmentData? {
        return EnvironmentData.fromJson(json)
    }
    
    /**
     * 环境数据转为JSON
     */
    fun toJson(environmentData: EnvironmentData, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(environmentData) else compactGson.toJson(environmentData)
    }
    
    // ==================== MotionData 解析 ====================
    
    /**
     * 解析运动数据JSON
     */
    fun parseMotionData(json: String): MotionData? {
        return MotionData.fromJson(json)
    }
    
    /**
     * 运动数据转为JSON
     */
    fun toJson(motionData: MotionData, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(motionData) else compactGson.toJson(motionData)
    }
    
    // ==================== 通用方法 ====================
    
    /**
     * 解析JSON为指定类型
     */
    inline fun <reified T> fromJson(json: String): T? {
        return try {
            gson.fromJson(json, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 任意对象转为JSON
     */
    fun toJson(obj: Any, pretty: Boolean = false): String {
        return if (pretty) gson.toJson(obj) else compactGson.toJson(obj)
    }
    
    /**
     * 解析原始JSON元素
     */
    fun parseJsonElement(json: String): JsonElement? {
        return try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 验证JSON格式是否有效
     */
    fun isValidJson(json: String): Boolean {
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取JSON中的字段值（字符串）
     */
    fun getStringField(json: String, fieldName: String): String? {
        return try {
            val element = JsonParser.parseString(json)
            element.asJsonObject.get(fieldName)?.asString
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取JSON中的字段值（整数）
     */
    fun getIntField(json: String, fieldName: String): Int? {
        return try {
            val element = JsonParser.parseString(json)
            element.asJsonObject.get(fieldName)?.asInt
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取JSON中的字段值（布尔）
     */
    fun getBooleanField(json: String, fieldName: String): Boolean? {
        return try {
            val element = JsonParser.parseString(json)
            element.asJsonObject.get(fieldName)?.asBoolean
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 批量解析传感器数据列表
     */
    fun parseSensorDataList(json: String): List<SensorData> {
        return try {
            val type = object : TypeToken<List<SensorData>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
