package com.smartmedicine.data.model

/**
 * 药箱状态枚举
 * - closed: 药箱关闭
 * - opened: 药箱打开
 * - moving: 移动中
 * - tilted: 倾斜状态
 */
enum class BoxState(val value: String) {
    CLOSED("closed"),
    OPENED("opened"),
    MOVING("moving"),
    TILTED("tilted"),
    UNKNOWN("unknown");

    companion object {
        /**
         * 从字符串值获取对应的枚举
         */
        fun fromValue(value: String?): BoxState {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}
