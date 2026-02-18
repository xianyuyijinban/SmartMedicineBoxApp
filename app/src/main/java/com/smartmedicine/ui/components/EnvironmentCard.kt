package com.smartmedicine.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartmedicine.ui.theme.*

/**
 * 环境数据卡片组件
 * 显示温度、湿度、气压
 * 
 * @param temperature 温度值(°C)
 * @param humidity 湿度值(%)
 * @param pressure 气压值(Pa)
 * @param modifier 修饰符
 */
@Composable
fun EnvironmentDataCard(
    temperature: Double?,
    humidity: Double?,
    pressure: Double?,
    modifier: Modifier = Modifier
) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = "🌡️ 环境数据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // 三个数据项
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 温度
                EnvironmentItem(
                    icon = "🌡️",
                    label = "温度",
                    value = temperature,
                    unit = "°C",
                    color = TemperatureColor,
                    modifier = Modifier.weight(1f)
                )
                
                VerticalDivider(
                    modifier = Modifier.height(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // 湿度
                EnvironmentItem(
                    icon = "💧",
                    label = "湿度",
                    value = humidity,
                    unit = "%",
                    color = HumidityColor,
                    modifier = Modifier.weight(1f)
                )
                
                VerticalDivider(
                    modifier = Modifier.height(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // 气压 (转换为hPa)
                EnvironmentItem(
                    icon = "🌀",
                    label = "气压",
                    value = pressure?.let { it / 100.0 }, // Pa to hPa
                    unit = "hPa",
                    color = PressureColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 环境数据项
 */
@Composable
private fun EnvironmentItem(
    icon: String,
    label: String,
    value: Double?,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 图标
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall
        )
        
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 数值
        val displayValue = value?.let { 
            when (unit) {
                "hPa" -> String.format("%.2f", it)
                else -> String.format("%.1f", it)
            }
        } ?: "--"
        
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 详细环境数据卡片（展开式）
 * 包含额外的数据如海拔等
 */
@Composable
fun DetailedEnvironmentCard(
    temperature: Double?,
    humidity: Double?,
    pressure: Double?,
    altitude: Double?,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = "🌍 环境详情",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // 数据网格
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DataRow(
                    icon = "🌡️",
                    label = "温度",
                    value = temperature,
                    unit = "°C",
                    color = TemperatureColor,
                    normalRange = 10.0..30.0
                )
                
                DataRow(
                    icon = "💧",
                    label = "湿度",
                    value = humidity,
                    unit = "%",
                    color = HumidityColor,
                    normalRange = 30.0..70.0
                )
                
                DataRow(
                    icon = "🌀",
                    label = "气压",
                    value = pressure?.let { it / 100.0 },
                    unit = "hPa",
                    color = PressureColor
                )
                
                altitude?.let {
                    DataRow(
                        icon = "⛰️",
                        label = "海拔",
                        value = it,
                        unit = "m",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * 数据行组件（带正常范围指示）
 */
@Composable
private fun DataRow(
    icon: String,
    label: String,
    value: Double?,
    unit: String,
    color: Color,
    normalRange: ClosedRange<Double>? = null
) {
    val isOutOfRange = value != null && normalRange != null && value !in normalRange
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOutOfRange) {
                StatusBadge(
                    text = if (value!! < normalRange!!.start) "过低" else "过高",
                    color = StatusWarning
                )
            }
            
            val displayValue = value?.let { 
                when (unit) {
                    "hPa" -> String.format("%.2f", it)
                    else -> String.format("%.1f", it)
                }
            } ?: "--"
            
            Text(
                text = "$displayValue $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isOutOfRange) StatusWarning else color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 状态徽章
 */
@Composable
private fun StatusBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 脉冲动画指示器（用于数据刷新）
 */
@Composable
fun PulsingIndicator(
    isActive: Boolean,
    color: Color = StatusOnline,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(8.dp)
            .scale(scale)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = alpha)
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
