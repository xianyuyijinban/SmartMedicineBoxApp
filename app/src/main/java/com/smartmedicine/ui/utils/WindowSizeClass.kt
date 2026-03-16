package com.smartmedicine.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口尺寸类别枚举
 * 用于自适应布局，支持手机和平板设备
 * 
 * Material3窗口尺寸类别规范：
 * - Compact: 手机竖屏 (< 600dp)
 * - Medium: 手机横屏/小平板 (600dp - 840dp)
 * - Expanded: 大屏平板/桌面 (> 840dp)
 */
enum class WindowSizeClass {
    COMPACT,    // 紧凑 - 手机竖屏
    MEDIUM,     // 中等 - 手机横屏/小平板
    EXPANDED;   // 展开 - 大屏平板/桌面

    companion object {
        /**
         * 根据屏幕宽度获取窗口尺寸类别
         * 
         * @param widthDp 屏幕宽度(dp)
         * @return 对应的窗口尺寸类别
         */
        fun fromWidth(widthDp: Dp): WindowSizeClass {
            return when {
                widthDp < 600.dp -> COMPACT
                widthDp < 840.dp -> MEDIUM
                else -> EXPANDED
            }
        }
    }
}

/**
 * 设备类型枚举
 * 用于区分手机和平板
 */
enum class DeviceType {
    PHONE,      // 手机
    TABLET,     // 平板
    FOLDABLE;   // 折叠屏

    companion object {
        /**
         * 根据屏幕尺寸判断设备类型
         * 
         * @param widthDp 屏幕宽度
         * @param heightDp 屏幕高度
         * @return 设备类型
         */
        fun fromDimensions(widthDp: Dp, heightDp: Dp): DeviceType {
            val smallestWidth = minOf(widthDp.value, heightDp.value)
            val largestWidth = maxOf(widthDp.value, heightDp.value)
            
            return when {
                // 折叠屏：展开后短边大于600dp但小于600dp折叠时
                smallestWidth >= 600 && largestWidth >= 840 -> FOLDABLE
                // 平板：短边大于600dp
                smallestWidth >= 600 -> TABLET
                // 否则为手机
                else -> PHONE
            }
        }
    }
}

/**
 * 获取当前窗口尺寸类别
 * 在Compose中使用
 * 
 * @return 当前窗口尺寸类别
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    return WindowSizeClass.fromWidth(screenWidth)
}

/**
 * 获取当前设备类型
 * 在Compose中使用
 * 
 * @return 当前设备类型
 */
@Composable
fun rememberDeviceType(): DeviceType {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    return DeviceType.fromDimensions(screenWidth, screenHeight)
}

/**
 * 判断是否为平板设备
 * 
 * @return true如果是平板或折叠屏
 */
@Composable
fun isTablet(): Boolean {
    val deviceType = rememberDeviceType()
    return deviceType == DeviceType.TABLET || deviceType == DeviceType.FOLDABLE
}

/**
 * 判断是否为展开状态的大屏幕
 * 用于折叠屏设备检测展开状态
 * 
 * @return true如果屏幕宽度大于等于600dp
 */
@Composable
fun isExpandedScreen(): Boolean {
    val windowSizeClass = rememberWindowSizeClass()
    return windowSizeClass != WindowSizeClass.COMPACT
}

/**
 * 根据窗口尺寸选择不同的值
 * 用于响应式布局
 * 
 * @param compact 紧凑屏幕的值
 * @param medium 中等屏幕的值
 * @param expanded 展开屏幕的值
 * @return 根据当前屏幕尺寸返回对应的值
 */
@Composable
fun <T> windowSizeValue(
    compact: T,
    medium: T,
    expanded: T
): T {
    return when (rememberWindowSizeClass()) {
        WindowSizeClass.COMPACT -> compact
        WindowSizeClass.MEDIUM -> medium
        WindowSizeClass.EXPANDED -> expanded
    }
}
