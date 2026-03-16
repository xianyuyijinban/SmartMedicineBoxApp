package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 命令响应数据类
 * 包含命令执行结果、错误信息等
 */
data class CommandResponse(
    @SerializedName("cmd")
    val cmd: String = "",
    
    @SerializedName("result")
    val result: String = "",
    
    @SerializedName("timestamp")
    val timestamp: Long = 0L,
    
    @SerializedName("error_code")
    val errorCode: Int? = null,
    
    @SerializedName("error_msg")
    val errorMsg: String? = null
) {
    companion object {
        private val gson = Gson()
        
        const val RESULT_OK = "ok"
        const val RESULT_ERROR = "error"
        
        // 常见错误码
        const val ERROR_INVALID_CMD = 400
        const val ERROR_INVALID_VALUE = 401
        const val ERROR_DEVICE_OFFLINE = 404
        const val ERROR_TIMEOUT = 408
        const val ERROR_INTERNAL = 500
        const val ERROR_SERVICE_UNAVAILABLE = 503
        
        /**
         * 从JSON字符串解析CommandResponse
         */
        fun fromJson(json: String): CommandResponse? {
            return try {
                gson.fromJson(json, CommandResponse::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * 检查命令是否执行成功
     */
    fun isSuccess(): Boolean {
        return result == RESULT_OK
    }
    
    /**
     * 检查命令是否执行失败
     */
    fun isError(): Boolean {
        return result == RESULT_ERROR
    }
    
    /**
     * 获取完整的错误信息
     */
    fun getFullErrorMessage(): String {
        return if (isError()) {
            "[${errorCode ?: "未知"}] ${errorMsg ?: "未知错误"}"
        } else {
            ""
        }
    }
    
    /**
     * 获取错误码描述
     */
    fun getErrorCodeDescription(): String {
        return when (errorCode) {
            ERROR_INVALID_CMD -> "无效命令"
            ERROR_INVALID_VALUE -> "无效值"
            ERROR_DEVICE_OFFLINE -> "设备离线"
            ERROR_TIMEOUT -> "超时"
            ERROR_INTERNAL -> "内部错误"
            ERROR_SERVICE_UNAVAILABLE -> "服务不可用"
            else -> "未知错误"
        }
    }
    
    /**
     * 获取格式化的时间戳
     */
    fun getFormattedTimestamp(): String {
        return DeviceTimestampFormatter.formatUptime(timestamp)
    }
}
