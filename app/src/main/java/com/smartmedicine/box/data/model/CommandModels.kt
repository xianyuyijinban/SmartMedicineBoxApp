package com.smartmedicine.box.data.model

/**
 * 控制命令枚举
 */
enum class ControlCommand(val command: String) {
    RESET("reset"),
    PUBLISH_NOW("publish_now"),
    SET_INTERVAL("set_interval");

    companion object {
        fun fromString(value: String): ControlCommand? {
            return values().find { it.command == value }
        }
    }
}

/**
 * 命令请求数据类
 */
data class CommandRequest(
    val cmd: String,
    val value: Any? = null
) {
    fun toJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("cmd", cmd)
            value?.let { put("value", it) }
        }
    }
}

/**
 * 命令响应数据类
 */
data class CommandResponse(
    val cmd: String,
    val result: ResultType,
    val timestamp: Long = 0,
    val errorCode: Int? = null,
    val errorMsg: String? = null
) {
    companion object {
        fun fromJson(json: org.json.JSONObject): CommandResponse {
            val resultStr = json.optString("result", "error")
            return CommandResponse(
                cmd = json.optString("cmd", ""),
                result = if (resultStr == "ok") ResultType.SUCCESS else ResultType.ERROR,
                timestamp = json.optLong("timestamp", 0),
                errorCode = json.optInt("error_code", -1).takeIf { it != -1 },
                errorMsg = json.optString("error_msg", null)
            )
        }
    }
}

/**
 * 命令执行结果类型
 */
enum class ResultType {
    SUCCESS,
    ERROR
}

/**
 * 错误码枚举
 * 对应接口文档中的错误码定义
 */
enum class ErrorCode(
    val code: Int,
    val description: String,
    val chineseMessage: String
) {
    INVALID_PARAMETER(400, "参数错误", "命令参数无效或超出范围"),
    UNAUTHORIZED(401, "权限错误", "未授权的操作"),
    DEVICE_ERROR(500, "设备错误", "设备执行命令失败"),
    SERVICE_UNAVAILABLE(503, "服务不可用", "设备当前状态不允许执行该命令");

    companion object {
        fun fromCode(code: Int): ErrorCode? {
            return values().find { it.code == code }
        }

        /**
         * 获取错误码对应的中文错误信息
         */
        fun getChineseMessage(code: Int): String {
            return fromCode(code)?.chineseMessage ?: "未知错误 (代码: $code)"
        }

        /**
         * 获取错误码对应的描述
         */
        fun getDescription(code: Int): String {
            return fromCode(code)?.description ?: "未知错误"
        }
    }
}

/**
 * 命令执行结果封装类
 */
sealed class CommandResult<out T> {
    data class Success<T>(val data: T) : CommandResult<T>()
    data class Error(val code: Int, val message: String) : CommandResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): Error? = this as? Error
}
