package com.example.smartmedicinebox.data.model

/**
 * 药箱状态枚举
 */
enum class BoxState {
    /** 已关闭 */
    CLOSED,
    
    /** 已打开 */
    OPENED,
    
    /** 移动中 */
    MOVING,
    
    /** 倾斜状态 */
    TILTED,
    
    /** 未知状态 */
    UNKNOWN
}
