package com.smartmedicine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartmedicine.ui.theme.*

/**
 * 温度趋势迷你图表
 * 
 * @param data 温度数据列表 (最近N个数据点)
 * @param modifier 修饰符
 */
@Composable
fun TemperatureMiniChart(
    data: List<Double>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val minTemp = data.minOrNull() ?: 0.0
    val maxTemp = data.maxOrNull() ?: 40.0
    val range = (maxTemp - minTemp).coerceAtLeast(1.0)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TemperatureColor.copy(alpha = 0.1f)
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = "🌡️ 温度趋势",
                style = MaterialTheme.typography.labelMedium,
                color = TemperatureColor,
                modifier = Modifier.align(Alignment.TopStart)
            )
            
            // 当前值
            Text(
                text = "${String.format("%.1f", data.last())}°C",
                style = MaterialTheme.typography.headlineSmall,
                color = TemperatureColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            
            // 折线图
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (data.size - 1).coerceAtLeast(1)
                
                // 绘制折线
                for (i in 0 until data.size - 1) {
                    val x1 = i * stepX
                    val y1 = height - ((data[i] - minTemp) / range * height).toFloat()
                    val x2 = (i + 1) * stepX
                    val y2 = height - ((data[i + 1] - minTemp) / range * height).toFloat()
                    
                    drawLine(
                        color = TemperatureColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
                
                // 绘制数据点
                data.forEachIndexed { index, temp ->
                    val x = index * stepX
                    val y = height - ((temp - minTemp) / range * height).toFloat()
                    
                    drawCircle(
                        color = TemperatureColor,
                        radius = 4f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

/**
 * 湿度仪表盘
 * 
 * @param value 当前湿度值 (0-100)
 * @param modifier 修饰符
 */
@Composable
fun HumidityGauge(
    value: Double,
    modifier: Modifier = Modifier
) {
    val percentage = value.coerceIn(0.0, 100.0) / 100f
    
    Card(
        modifier = modifier
            .size(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HumidityColor.copy(alpha = 0.1f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 标题
            Text(
                text = "💧 湿度",
                style = MaterialTheme.typography.labelMedium,
                color = HumidityColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
            
            // 仪表盘
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
            ) {
                val strokeWidth = 12f
                val diameter = size.minDimension - strokeWidth
                val radius = diameter / 2
                val centerX = size.width / 2
                val centerY = size.height / 2 + 10f
                
                // 背景弧
                drawArc(
                    color = HumidityColor.copy(alpha = 0.2f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                // 进度弧
                drawArc(
                    color = when {
                        value < 30 -> StatusWarning
                        value > 70 -> StatusWarning
                        else -> HumidityColor
                    },
                    startAngle = 135f,
                    sweepAngle = 270f * percentage.toFloat(),
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            
            // 数值
            Text(
                text = "${String.format("%.0f", value)}%",
                style = MaterialTheme.typography.headlineSmall,
                color = HumidityColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 20.dp)
            )
        }
    }
}

/**
 * 数据趋势指示器
 * 
 * @param current 当前值
 * @param previous 前一个值
 * @param unit 单位
 */
@Composable
fun TrendIndicator(
    current: Double,
    previous: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    val diff = current - previous
    val trend = when {
        diff > 0.1 -> "↑"
        diff < -0.1 -> "↓"
        else -> "→"
    }
    val color = when {
        diff > 0.1 -> StatusWarning
        diff < -0.1 -> StatusInfo
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = trend,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${String.format("%.1f", kotlin.math.abs(diff))}$unit",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

/**
 * 数据统计卡片
 * 
 * @param title 标题
 * @param value 当前值
 * @param min 最小值
 * @param max 最大值
 * @param avg 平均值
 * @param unit 单位
 * @param color 主题色
 */
@Composable
fun DataStatisticsCard(
    title: String,
    icon: String,
    value: Double,
    min: Double,
    max: Double,
    avg: Double,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // 当前值
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "当前值",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${String.format("%.1f", value)}$unit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 统计值
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "最低", value = min, unit = unit, color = color)
                StatItem(label = "平均", value = avg, unit = unit, color = color)
                StatItem(label = "最高", value = max, unit = unit, color = color)
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(
    label: String,
    value: Double,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${String.format("%.1f", value)}$unit",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 实时数据流指示器
 */
@Composable
fun LiveDataIndicator(
    isReceiving: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 脉冲点
        PulsingIndicator(
            isActive = isReceiving,
            color = StatusOnline
        )
        
        Text(
            text = if (isReceiving) "数据接收中" else "等待数据",
            style = MaterialTheme.typography.bodySmall,
            color = if (isReceiving) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
