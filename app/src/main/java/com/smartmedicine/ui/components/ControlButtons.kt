package com.smartmedicine.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 控制按钮区域组件
 * 
 * @param onRefresh 刷新回调
 * @param onReset 重置设备回调
 * @param onSetInterval 设置上报间隔回调
 * @param isConnected 是否已连接
 * @param modifier 修饰符
 */
@Composable
fun ControlButtonsSection(
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

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
                    label = "立即上报",
                    onClick = onRefresh,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                
                // 设置间隔按钮
                ControlButton(
                    icon = Icons.Default.Schedule,
                    label = "设置间隔",
                    onClick = { showIntervalDialog = true },
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary
                )
                
                // 重置按钮
                ControlButton(
                    icon = Icons.Default.RestartAlt,
                    label = "重置设备",
                    onClick = { showResetDialog = true },
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error
                )
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
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
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
                style = MaterialTheme.typography.labelMedium
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
