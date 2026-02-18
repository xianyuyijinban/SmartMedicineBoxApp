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
    val value: Int? = null
) {
    companion object {
        private val gson = Gson()
        
        // 预定义命令
        const val CMD_RESET = "reset"
        const val CMD_PUBLISH_NOW = "publish_now"
        const val CMD_SET_INTERVAL = "set_interval"
        const val CMD_GET_STATUS = "get_status"
        
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
            CMD_GET_STATUS -> "获取设备状态"
            else -> "未知命令"
        }
    }
}
