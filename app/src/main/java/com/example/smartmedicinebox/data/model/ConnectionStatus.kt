package com.example.smartmedicinebox.data.model

/**
 * 设备连接状态枚举
 */
enum class ConnectionStatus {
    /** 已连接 */
    CONNECTED,
    
    /** 未连接 */
    DISCONNECTED,
    
    /** 连接中 */
    CONNECTING,
    
    /** 连接错误 */
    ERROR
}
