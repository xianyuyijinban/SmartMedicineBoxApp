package com.smartmedicine.ui.utils

import android.os.Build

/**
 * API版本检测工具类
 * 用于处理不同Android版本的行为差异
 * 支持API 24 (Android 7.0) 到 API 35 (Android 15)
 */
object ApiVersionUtils {
    
    // ==================== 常用API级别常量 ====================
    
    /** Android 7.0 - minSdk */
    const val API_24 = Build.VERSION_CODES.N
    
    /** Android 8.0 */
    const val API_26 = Build.VERSION_CODES.O
    
    /** Android 9.0 */
    const val API_28 = Build.VERSION_CODES.P
    
    /** Android 10.0 */
    const val API_29 = Build.VERSION_CODES.Q
    
    /** Android 11.0 */
    const val API_30 = Build.VERSION_CODES.R
    
    /** Android 12.0 */
    const val API_31 = Build.VERSION_CODES.S
    
    /** Android 12L (API 32) - 大屏幕优化 */
    const val API_32 = Build.VERSION_CODES.S_V2
    
    /** Android 13.0 */
    const val API_33 = Build.VERSION_CODES.TIRAMISU
    
    /** Android 14.0 */
    const val API_34 = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    
    /** Android 15.0 (API 35) - 最新版本 */
    const val API_35 = 35
    
    // ==================== 当前设备API级别检测 ====================
    
    /**
     * 获取当前设备的API级别
     * 
     * @return 当前设备的API级别
     */
    fun currentApi(): Int = Build.VERSION.SDK_INT
    
    /**
     * 检测当前设备是否运行在指定API级别或更高版本
     * 
     * @param apiLevel 目标API级别
     * @return true如果当前API >= 目标API
     */
    fun isAtLeast(apiLevel: Int): Boolean = Build.VERSION.SDK_INT >= apiLevel
    
    /**
     * 检测当前设备是否运行在指定API级别或更低版本
     * 
     * @param apiLevel 目标API级别
     * @return true如果当前API <= 目标API
     */
    fun isAtMost(apiLevel: Int): Boolean = Build.VERSION.SDK_INT <= apiLevel
    
    // ==================== 特定API级别快捷检测 ====================
    
    /** 是否为Android 8.0+ (API 26+) */
    fun isOreoOrAbove(): Boolean = isAtLeast(API_26)
    
    /** 是否为Android 9.0+ (API 28+) */
    fun isPieOrAbove(): Boolean = isAtLeast(API_28)
    
    /** 是否为Android 10.0+ (API 29+) */
    fun isAndroid10OrAbove(): Boolean = isAtLeast(API_29)
    
    /** 是否为Android 11.0+ (API 30+) */
    fun isAndroid11OrAbove(): Boolean = isAtLeast(API_30)
    
    /** 是否为Android 12.0+ (API 31+) */
    fun isAndroid12OrAbove(): Boolean = isAtLeast(API_31)
    
    /** 是否为Android 12L+ (API 32+) - 大屏幕优化 */
    fun isAndroid12LOrAbove(): Boolean = isAtLeast(API_32)
    
    /** 是否为Android 13.0+ (API 33+) */
    fun isAndroid13OrAbove(): Boolean = isAtLeast(API_33)
    
    /** 是否为Android 14.0+ (API 34+) */
    fun isAndroid14OrAbove(): Boolean = isAtLeast(API_34)
    
    /** 是否为Android 15.0+ (API 35+) */
    fun isAndroid15OrAbove(): Boolean = isAtLeast(API_35)
    
    // ==================== 功能支持检测 ====================
    
    /**
     * 是否支持通知渠道 (API 26+)
     * 
     * @return true支持通知渠道
     */
    fun supportsNotificationChannels(): Boolean = isOreoOrAbove()
    
    /**
     * 是否支持前台服务类型声明 (API 34+)
     * Android 14+ 必须声明前台服务类型
     * 
     * @return true需要声明前台服务类型
     */
    fun requiresForegroundServiceType(): Boolean = isAndroid14OrAbove()
    
    /**
     * 是否支持预测性返回手势 (API 35+)
     * Android 15 引入了预测性返回手势
     * 
     * @return true支持预测性返回手势
     */
    fun supportsPredictiveBackGesture(): Boolean = isAndroid15OrAbove()
    
    /**
     * 是否支持精确闹钟权限 (API 31+)
     * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限
     * 
     * @return true需要精确闹钟权限
     */
    fun requiresExactAlarmPermission(): Boolean = isAndroid12OrAbove()
    
    /**
     * 是否支持USE_EXACT_ALARM权限 (API 33+)
     * Android 13+ 可以使用更精确的 USE_EXACT_ALARM
     * 
     * @return true支持 USE_EXACT_ALARM
     */
    fun supportsUseExactAlarm(): Boolean = isAndroid13OrAbove()
    
    /**
     * 是否支持通知权限请求 (API 33+)
     * Android 13+ 需要动态申请 POST_NOTIFICATIONS 权限
     * 
     * @return true需要动态申请通知权限
     */
    fun requiresNotificationPermission(): Boolean = isAndroid13OrAbove()
    
    /**
     * 是否支持16KB页面大小 (API 35+)
     * Android 15 支持16KB内存页面，提升性能
     * 
     * @return true支持16KB页面
     */
    fun supports16KbPages(): Boolean = isAndroid15OrAbove()
    
    /**
     * 是否支持部分媒体权限 (API 33+)
     * Android 13+ 引入细化的媒体权限
     * 
     * @return true支持细化媒体权限
     */
    fun supportsGranularMediaPermissions(): Boolean = isAndroid13OrAbove()
    
    // ==================== 版本名称获取 ====================
    
    /**
     * 获取当前Android版本的友好名称
     * 
     * @return 版本名称，如 "Android 15"
     */
    fun getAndroidVersionName(): String {
        return when (Build.VERSION.SDK_INT) {
            API_24, 25 -> "Android 7.x"
            API_26, 27 -> "Android 8.x"
            API_28 -> "Android 9"
            API_29 -> "Android 10"
            API_30 -> "Android 11"
            API_31 -> "Android 12"
            API_32 -> "Android 12L"
            API_33 -> "Android 13"
            API_34 -> "Android 14"
            API_35 -> "Android 15"
            else -> if (Build.VERSION.SDK_INT > API_35) "Android 15+" else "Android 7.0以下"
        }
    }
    
    /**
     * 获取完整的版本信息字符串
     * 
     * @return 如 "Android 15 (API 35)"
     */
    fun getFullVersionInfo(): String {
        return "${getAndroidVersionName()} (API ${Build.VERSION.SDK_INT})"
    }
}

/**
 * 执行API级别相关的代码块
 * 简化版本检查语法
 * 
 * @param apiLevel 最低API级别
 * @param block 要执行的代码块
 */
inline fun runOnApi(apiLevel: Int, block: () -> Unit) {
    if (ApiVersionUtils.isAtLeast(apiLevel)) {
        block()
    }
}

/**
 * 根据API级别执行不同的代码块
 * 
 * @param atLeast 高版本执行的代码块
 * @param otherwise 低版本执行的代码块
 */
inline fun <T> runByApi(
    apiLevel: Int,
    atLeast: () -> T,
    otherwise: () -> T
): T {
    return if (ApiVersionUtils.isAtLeast(apiLevel)) {
        atLeast()
    } else {
        otherwise()
    }
}
