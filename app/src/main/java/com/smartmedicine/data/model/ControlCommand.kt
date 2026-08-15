package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 控制命令数据类
 * 用于向设备发送控制指令
 */
data class ControlCommand(
    @SerializedName("cmd")
    val cmd: String = "",
    
    @SerializedName("value")
    val value: Int? = null,

    @SerializedName("temperature")
    val temperature: Double? = null,

    @SerializedName("humidity")
    val humidity: Double? = null,

    @SerializedName("timer_id")
    val timerId: Int? = null,

    @SerializedName("mode")
    val mode: String? = null,

    @SerializedName("hour")
    val hour: Int? = null,

    @SerializedName("minute")
    val minute: Int? = null,

    @SerializedName("second")
    val second: Int? = null,

    @SerializedName("now_hour")
    val nowHour: Int? = null,

    @SerializedName("now_minute")
    val nowMinute: Int? = null,

    @SerializedName("now_second")
    val nowSecond: Int? = null
) {
    companion object {
        private val gson = Gson()
        
        // 预定义命令
        const val CMD_RESET = "reset"
        const val CMD_PUBLISH_NOW = "publish_now"
        const val CMD_SET_INTERVAL = "set_interval"
        const val CMD_SET_ENV_RATED = "set_env_rated"
        const val CMD_SET_BUZZER_ENABLE = "set_buzzer_enable"
        const val CMD_SET_MEDICINE_TIMER = "set_medicine_timer"
        const val CMD_CANCEL_MEDICINE_TIMER = "cancel_medicine_timer"
        const val CMD_GET_STATUS = "get_status"
        const val TIMER_MODE_COUNTDOWN = "countdown"
        const val TIMER_MODE_CLOCK = "clock"
        
        /**
         * 创建重置命令
         */
        fun createResetCommand(): ControlCommand {
            return ControlCommand(cmd = CMD_RESET)
        }
        
        /**
         * 创建立即上报命令
         */
        fun createPublishNowCommand(): ControlCommand {
            return ControlCommand(cmd = CMD_PUBLISH_NOW)
        }
        
        /**
         * 创建设置上报间隔命令
         * @param interval 间隔秒数，范围1-3600
         */
        fun createSetIntervalCommand(interval: Int): ControlCommand {
            require(interval in 1..3600) { "Interval must be between 1 and 3600 seconds" }
            return ControlCommand(cmd = CMD_SET_INTERVAL, value = interval)
        }

        /**
         * 创建设置温湿度额定值命令
         */
        fun createSetEnvRatedCommand(temperature: Double, humidity: Double): ControlCommand {
            require(temperature in -40.0..85.0) { "Temperature must be between -40 and 85" }
            require(humidity in 0.0..100.0) { "Humidity must be between 0 and 100" }
            return ControlCommand(
                cmd = CMD_SET_ENV_RATED,
                temperature = temperature,
                humidity = humidity
            )
        }

        /**
         * 创建蜂鸣器开关命令
         */
        fun createSetBuzzerEnableCommand(enabled: Boolean): ControlCommand {
            return ControlCommand(
                cmd = CMD_SET_BUZZER_ENABLE,
                value = if (enabled) 1 else 0
            )
        }

        fun createSetMedicineTimerCommand(
            mode: String,
            hour: Int,
            minute: Int,
            second: Int,
            timerId: Int? = null,
            nowHour: Int? = null,
            nowMinute: Int? = null,
            nowSecond: Int? = null
        ): ControlCommand {
            require(mode == TIMER_MODE_COUNTDOWN || mode == TIMER_MODE_CLOCK) {
                "Unsupported timer mode"
            }
            require(hour in 0..23) { "Hour must be between 0 and 23" }
            require(minute in 0..59) { "Minute must be between 0 and 59" }
            require(second in 0..59) { "Second must be between 0 and 59" }
            if (mode == TIMER_MODE_COUNTDOWN) {
                require(hour != 0 || minute != 0 || second != 0) {
                    "Countdown must be greater than zero"
                }
            }
            if (mode == TIMER_MODE_CLOCK) {
                require(nowHour in 0..23 && nowMinute in 0..59 && nowSecond in 0..59) {
                    "Clock timer requires a valid current time"
                }
            }
            if (timerId != null) {
                require(timerId in 1..5) { "Timer id must be between 1 and 5" }
            }
            return ControlCommand(
                cmd = CMD_SET_MEDICINE_TIMER,
                timerId = timerId,
                mode = mode,
                hour = hour,
                minute = minute,
                second = second,
                nowHour = nowHour,
                nowMinute = nowMinute,
                nowSecond = nowSecond
            )
        }

        fun createCancelMedicineTimerCommand(timerId: Int? = null): ControlCommand {
            if (timerId != null) {
                require(timerId in 1..5) { "Timer id must be between 1 and 5" }
            }
            return ControlCommand(cmd = CMD_CANCEL_MEDICINE_TIMER, timerId = timerId)
        }
        
        /**
         * 创建获取状态命令
         */
        fun createGetStatusCommand(): ControlCommand {
            return ControlCommand(cmd = CMD_GET_STATUS)
        }
        
        /**
         * 从JSON字符串解析ControlCommand
         */
        fun fromJson(json: String): ControlCommand? {
            return try {
                gson.fromJson(json, ControlCommand::class.java)
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
     * 检查命令是否需要值参数
     */
    fun requiresValue(): Boolean {
        return cmd == CMD_SET_INTERVAL
    }
    
    /**
     * 检查命令是否有效
     */
    fun isValid(): Boolean {
        return when (cmd) {
            CMD_RESET, CMD_PUBLISH_NOW, CMD_GET_STATUS -> true
            CMD_SET_INTERVAL -> value != null && value in 1..3600
            CMD_SET_ENV_RATED -> {
                temperature != null && humidity != null &&
                    temperature in -40.0..85.0 &&
                    humidity in 0.0..100.0
            }
            CMD_SET_BUZZER_ENABLE -> value == 0 || value == 1
            CMD_SET_MEDICINE_TIMER -> {
                val h = hour
                val m = minute
                val s = second
                val validTimerId = timerId == null || timerId in 1..5
                val validHms = h != null && h in 0..23 &&
                    m != null && m in 0..59 &&
                    s != null && s in 0..59
                when (mode) {
                    TIMER_MODE_COUNTDOWN -> validTimerId && validHms && (h != 0 || m != 0 || s != 0)
                    TIMER_MODE_CLOCK -> validTimerId && validHms &&
                        nowHour != null && nowHour in 0..23 &&
                        nowMinute != null && nowMinute in 0..59 &&
                        nowSecond != null && nowSecond in 0..59
                    else -> false
                }
            }
            CMD_CANCEL_MEDICINE_TIMER -> timerId == null || timerId in 1..5
            else -> false
        }
    }
    
    /**
     * 获取命令描述
     */
    fun getCommandDescription(): String {
        return when (cmd) {
            CMD_RESET -> "重置设备"
            CMD_PUBLISH_NOW -> "立即上报数据"
            CMD_SET_INTERVAL -> "设置上报间隔为 ${value}秒"
            CMD_SET_ENV_RATED -> "设置额定环境为 ${temperature}°C / ${humidity}%"
            CMD_SET_BUZZER_ENABLE -> if (value == 1) "开启蜂鸣器" else "关闭蜂鸣器"
            CMD_GET_STATUS -> "获取设备状态"
            else -> "未知命令"
        }
    }
}
