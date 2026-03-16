package com.smartmedicine.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 控制按钮区域组件
 * 
 * @param onRefresh 刷新回调
 * @param onReset 重置设备回调
 * @param onSetInterval 设置上报间隔回调
 * @param onSetEnvRated 设置温湿度额定值回调
 * @param onSetBuzzerEnabled 设置蜂鸣器开关回调
 * @param buzzerEnabled 蜂鸣器是否开启
 * @param isConnected 是否已连接
 * @param modifier 修饰符
 */
@Composable
fun ControlButtonsSection(
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    buzzerEnabled: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showEnvRatedDialog by remember { mutableStateOf(false) }

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
                text = "🎮 设备控制",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 立即上报按钮
                ControlButton(
                    icon = Icons.Default.Refresh,
                    label = "上报",
                    onClick = onRefresh,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                
                // 设置间隔按钮
                ControlButton(
                    icon = Icons.Default.Schedule,
                    label = "间隔",
                    onClick = { showIntervalDialog = true },
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary
                )

                // 设置额定值按钮
                ControlButton(
                    icon = Icons.Default.Settings,
                    label = "额定",
                    onClick = { showEnvRatedDialog = true },
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // 蜂鸣器开关模块
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (buzzerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (buzzerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (buzzerEnabled) "蜂鸣器：已开启" else "蜂鸣器：已关闭",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = buzzerEnabled,
                        onCheckedChange = { enabled ->
                            onSetBuzzerEnabled(enabled)
                        },
                        enabled = isConnected
                    )
                }
            }

            // 重置按钮（单独一行，避免和3列控制区拥挤）
            OutlinedButton(
                onClick = { showResetDialog = true },
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("重置设备")
            }
        }
    }
    
    // 重置确认对话框
    if (showResetDialog) {
        ResetConfirmDialog(
            onConfirm = {
                onReset()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }
    
    // 设置间隔对话框
    if (showIntervalDialog) {
        SetIntervalDialog(
            onConfirm = { interval ->
                onSetInterval(interval)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    // 设置额定值对话框
    if (showEnvRatedDialog) {
        SetEnvironmentRatedDialog(
            onConfirm = { temperature, humidity ->
                onSetEnvRated(temperature, humidity)
                showEnvRatedDialog = false
            },
            onDismiss = { showEnvRatedDialog = false }
        )
    }
}

/**
 * 控制按钮
 */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color
) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = if (enabled) 2.dp else 0.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 重置确认对话框
 */
@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
        title = { Text("确认重置设备") },
        text = {
            Text(
                "重置设备将清除所有配置并重启设备。\n" +
                "此操作可能需要几秒钟完成。\n\n" +
                "确定要继续吗？"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("确认重置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 设置上报间隔对话框
 */
@Composable
private fun SetIntervalDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var intervalText by remember { mutableStateOf("5") }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
        title = { Text("设置上报间隔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设置传感器数据上报的时间间隔（秒）")
                Text(
                    "推荐值：5-60秒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { 
                        intervalText = it
                        error = null
                    },
                    label = { Text("上报间隔（秒）") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val interval = intervalText.toIntOrNull()
                    when {
                        interval == null -> error = "请输入有效的数字"
                        interval < 1 -> error = "间隔不能小于1秒"
                        interval > 300 -> error = "间隔不能大于300秒"
                        else -> onConfirm(interval)
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 设置温湿度额定值对话框
 */
@Composable
private fun SetEnvironmentRatedDialog(
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var temperatureText by remember { mutableStateOf("15.0") }
    var humidityText by remember { mutableStateOf("50.0") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text("设置额定环境") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设备将按额定值 ±30% 判断温湿度异常")
                Text(
                    "默认: 15°C / 50%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = {
                        temperatureText = it
                        error = null
                    },
                    label = { Text("额定温度(°C)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    isError = error != null
                )

                OutlinedTextField(
                    value = humidityText,
                    onValueChange = {
                        humidityText = it
                        error = null
                    },
                    label = { Text("额定湿度(%)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val temperature = temperatureText.toDoubleOrNull()
                    val humidity = humidityText.toDoubleOrNull()
                    when {
                        temperature == null || humidity == null -> error = "请输入有效的数字"
                        temperature !in -40.0..85.0 -> error = "温度范围应为 -40°C 到 85°C"
                        humidity !in 0.0..100.0 -> error = "湿度范围应为 0% 到 100%"
                        else -> onConfirm(temperature, humidity)
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 快速设置按钮组
 * 提供常用的上报间隔快捷选项
 */
@Composable
fun QuickIntervalButtons(
    onIntervalSelected: (Int) -> Unit,
    currentInterval: Int,
    modifier: Modifier = Modifier
) {
    val intervals = listOf(5, 10, 30, 60)
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        intervals.forEach { interval ->
            val isSelected = currentInterval == interval
            FilterChip(
                selected = isSelected,
                onClick = { onIntervalSelected(interval) },
                label = { Text("${interval}秒") },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}
