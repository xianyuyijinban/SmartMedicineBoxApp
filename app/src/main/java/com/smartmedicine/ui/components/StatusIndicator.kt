package com.smartmedicine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartmedicine.ui.theme.*

/**
 * 设备连接状态指示器
 * 
 * @param isOnline 是否在线
 * @param isConnecting 是否正在连接
 * @param modifier 修饰符
 */
@Composable
fun ConnectionStatusIndicator(
    isOnline: Boolean,
    isConnecting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isConnecting -> StatusConnecting
            isOnline -> StatusOnline
            else -> StatusOffline
        },
        animationSpec = tween(300),
        label = "status_color"
    )
    
    val statusText = when {
        isConnecting -> "连接中..."
        isOnline -> "设备在线"
        else -> "设备离线"
    }
    
    val statusIcon = when {
        isConnecting -> "⏳"
        isOnline -> "●"
        else -> "!"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 状态指示点
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(containerColor)
            )
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = containerColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 药箱状态卡片
 * 
 * @param state 药箱状态字符串 (closed/opened/moving/tilted)
 * @param vibration 振动值
 * @param pitch 俯仰角
 * @param roll 横滚角
 * @param modifier 修饰符
 */
@Composable
fun BoxStatusCard(
    isOnline: Boolean,
    state: String,
    vibration: Double?,
    pitch: Double?,
    roll: Double?,
    modifier: Modifier = Modifier
) {
    val (stateText, stateColor, stateIcon) = if (!isOnline) {
        Triple("尚未连接", MaterialTheme.colorScheme.onSurfaceVariant, "🔌")
    } else {
        when (state.lowercase()) {
            "closed" -> Triple("已关闭", BoxStateClosed, "📦")
            "opened" -> Triple("已打开", BoxStateOpened, "📂")
            "moving" -> Triple("移动中", BoxStateMoving, "🚚")
            "tilted" -> Triple("倾斜状态", BoxStateTilted, "⚠️")
            else -> Triple("未知状态", StatusOffline, "❓")
        }
    }
    
    // 判断是否倾斜（俯仰角或横滚角大于30度）
    val isTilted = (pitch != null && kotlin.math.abs(pitch) > 30) || 
                   (roll != null && kotlin.math.abs(roll) > 30)
    val tiltStatus = when {
        !isOnline -> "尚未连接"
        isTilted -> "倾斜"
        else -> "正常"
    }
    val tiltColor = when {
        !isOnline -> MaterialTheme.colorScheme.onSurfaceVariant
        isTilted -> StatusWarning
        else -> StatusOnline
    }
    
    // 振动状态
    val vibrationStatus = when {
        !isOnline -> "尚未连接"
        vibration == null -> "--"
        vibration > 0.5 -> "异常"
        vibration > 0.1 -> "轻微"
        else -> "正常"
    }
    val vibrationColor = when {
        !isOnline -> MaterialTheme.colorScheme.onSurfaceVariant
        vibration == null -> MaterialTheme.colorScheme.onSurfaceVariant
        vibration > 0.5 -> StatusOffline
        vibration > 0.1 -> StatusWarning
        else -> StatusOnline
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(
                text = "📦 药箱状态",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // 状态显示
            StatusRow(
                icon = stateIcon,
                label = "当前状态",
                value = stateText,
                valueColor = stateColor
            )
            
            // 振动状态
            StatusRow(
                icon = "📳",
                label = "振动",
                value = vibrationStatus,
                valueColor = vibrationColor
            )
            
            // 倾斜状态
            StatusRow(
                icon = "📐",
                label = "倾斜",
                value = tiltStatus,
                valueColor = tiltColor
            )
            
            // 显示角度详情（如果倾斜）
            if (isOnline && isTilted && (pitch != null || roll != null)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = StatusWarning.copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        pitch?.let {
                            Text(
                                text = "俯仰角: ${String.format("%.1f", it)}°",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusWarning
                            )
                        }
                        roll?.let {
                            Text(
                                text = "横滚角: ${String.format("%.1f", it)}°",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusWarning
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 状态行组件
 */
@Composable
private fun StatusRow(
    icon: String,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = valueColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 警告提示卡片
 * 
 * @param message 警告消息
 * @param level 警告级别 (error/warning/info)
 * @param onDismiss 关闭回调
 */
@Composable
fun AlertCard(
    message: String,
    level: AlertLevel = AlertLevel.WARNING,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon) = when (level) {
        AlertLevel.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⚠️"
        )
        AlertLevel.WARNING -> Triple(
            StatusWarning.copy(alpha = 0.15f),
            StatusWarning,
            "⚡"
        )
        AlertLevel.INFO -> Triple(
            StatusInfo.copy(alpha = 0.15f),
            StatusInfo,
            "ℹ️"
        )
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            onDismiss?.let {
                TextButton(onClick = it) {
                    Text("关闭", color = contentColor)
                }
            }
        }
    }
}

enum class AlertLevel {
    ERROR, WARNING, INFO
}
