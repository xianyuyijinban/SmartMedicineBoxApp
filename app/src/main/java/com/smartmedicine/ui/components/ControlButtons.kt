package com.smartmedicine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmedicine.ui.screens.MedicineCompartmentUiItem

@Composable
fun ControlButtonsSection(
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    onSetMedicineTimer: (Int, String, Int, Int, Int) -> Unit,
    onCancelMedicineTimer: (Int?) -> Unit,
    medicineCompartments: List<MedicineCompartmentUiItem>,
    buzzerEnabled: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showEnvRatedDialog by remember { mutableStateOf(false) }
    var showMedicineTimerDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("设备控制", style = MaterialTheme.typography.titleMedium)
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ControlButton(Icons.Default.Refresh, "立即上报", onRefresh, isConnected, Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                    ControlButton(Icons.Default.Schedule, "上报间隔", { showIntervalDialog = true }, isConnected, Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ControlButton(Icons.Default.Settings, "环境阈值", { showEnvRatedDialog = true }, isConnected, Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                    ControlButton(Icons.Default.Alarm, "定时提醒", { showMedicineTimerDialog = true }, isConnected, Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (buzzerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (buzzerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(if (buzzerEnabled) "蜂鸣器：已开启" else "蜂鸣器：已关闭")
                    }
                    Switch(checked = buzzerEnabled, onCheckedChange = onSetBuzzerEnabled, enabled = isConnected)
                }
            }

            OutlinedButton(
                onClick = { showResetDialog = true },
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重置设备")
            }
        }
    }

    if (showResetDialog) {
        ResetConfirmDialog(onConfirm = { onReset(); showResetDialog = false }, onDismiss = { showResetDialog = false })
    }
    if (showIntervalDialog) {
        SetIntervalDialog(onConfirm = { onSetInterval(it); showIntervalDialog = false }, onDismiss = { showIntervalDialog = false })
    }
    if (showEnvRatedDialog) {
        SetEnvironmentRatedDialog(onConfirm = { t, h -> onSetEnvRated(t, h); showEnvRatedDialog = false }, onDismiss = { showEnvRatedDialog = false })
    }
    if (showMedicineTimerDialog) {
        SetMedicineTimerDialog(
            compartments = medicineCompartments,
            onConfirm = { boxId, mode, hour, minute, second -> onSetMedicineTimer(boxId, mode, hour, minute, second); showMedicineTimerDialog = false },
            onCancelTimer = { onCancelMedicineTimer(null); showMedicineTimerDialog = false },
            onDismiss = { showMedicineTimerDialog = false }
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color
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
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
        title = { Text("确认重置设备") },
        text = { Text("重置设备将清除设备侧配置并重启，可能需要几秒钟完成。确定继续吗？") },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认重置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SetIntervalDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var intervalText by remember { mutableStateOf("5") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
        title = { Text("设置上报间隔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设置传感器数据上报间隔，单位为秒。")
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it; error = null },
                    label = { Text("上报间隔（秒）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val interval = intervalText.toIntOrNull()
                when {
                    interval == null -> error = "请输入有效数字"
                    interval < 1 -> error = "间隔不能小于 1 秒"
                    interval > 300 -> error = "间隔不能大于 300 秒"
                    else -> onConfirm(interval)
                }
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetMedicineTimerDialog(
    compartments: List<MedicineCompartmentUiItem>,
    onConfirm: (Int, String, Int, Int, Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("countdown") }
    var selectedBoxId by remember(compartments) { mutableStateOf(compartments.firstOrNull()?.boxId ?: 1) }
    var expanded by remember { mutableStateOf(false) }
    var hourText by remember { mutableStateOf("0") }
    var minuteText by remember { mutableStateOf("10") }
    var secondText by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
        title = { Text("药盒定时提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    val selected = compartments.firstOrNull { it.boxId == selectedBoxId }
                    OutlinedTextField(
                        value = selected?.let { "${it.boxId} 号药盒 - ${it.name}" } ?: "未选择药盒",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("药盒") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        compartments.forEach { box ->
                            DropdownMenuItem(text = { Text("${box.boxId} 号药盒 - ${box.name}") }, onClick = { selectedBoxId = box.boxId; expanded = false; error = null })
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = mode == "countdown", onClick = { mode = "countdown"; error = null }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("倒计时") }
                    SegmentedButton(selected = mode == "clock", onClick = { mode = "clock"; error = null }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("当天时刻") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("时", hourText, { hourText = it; error = null }, Modifier.weight(1f))
                    TimeField("分", minuteText, { minuteText = it; error = null }, Modifier.weight(1f))
                    TimeField("秒", secondText, { secondText = it; error = null }, Modifier.weight(1f))
                }
                Text(if (mode == "countdown") "例如 00:10:00 表示 10 分钟后提醒。" else "例如 08:30:00 表示今天 08:30:00 提醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val hour = hourText.toIntOrNull()
                val minute = minuteText.toIntOrNull()
                val second = secondText.toIntOrNull()
                when {
                    hour == null || minute == null || second == null -> error = "请输入有效数字"
                    hour !in 0..23 -> error = "小时范围为 0-23"
                    minute !in 0..59 -> error = "分钟范围为 0-59"
                    second !in 0..59 -> error = "秒范围为 0-59"
                    mode == "countdown" && hour == 0 && minute == 0 && second == 0 -> error = "倒计时必须大于 0 秒"
                    compartments.none { it.boxId == selectedBoxId } -> error = "请选择药盒"
                    else -> onConfirm(selectedBoxId, mode, hour, minute, second)
                }
            }) { Text("设置") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancelTimer) { Icon(Icons.Default.AlarmOff, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("取消定时") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun TimeField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), singleLine = true, modifier = modifier)
}

@Composable
private fun SetEnvironmentRatedDialog(onConfirm: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    var temperatureText by remember { mutableStateOf("15.0") }
    var humidityText by remember { mutableStateOf("50.0") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text("设置环境阈值") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设备会按额定温湿度的上下浮动范围判断环境异常。")
                OutlinedTextField(temperatureText, { temperatureText = it; error = null }, label = { Text("额定温度（℃）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next), singleLine = true, isError = error != null)
                OutlinedTextField(humidityText, { humidityText = it; error = null }, label = { Text("额定湿度（%）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } })
            }
        },
        confirmButton = {
            Button(onClick = {
                val temperature = temperatureText.toDoubleOrNull()
                val humidity = humidityText.toDoubleOrNull()
                when {
                    temperature == null || humidity == null -> error = "请输入有效数字"
                    temperature !in -40.0..85.0 -> error = "温度范围应为 -40℃ 到 85℃"
                    humidity !in 0.0..100.0 -> error = "湿度范围应为 0% 到 100%"
                    else -> onConfirm(temperature, humidity)
                }
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun QuickIntervalButtons(onIntervalSelected: (Int) -> Unit, currentInterval: Int, modifier: Modifier = Modifier) {
    val intervals = listOf(5, 10, 30, 60)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        intervals.forEach { interval ->
            FilterChip(selected = currentInterval == interval, onClick = { onIntervalSelected(interval) }, label = { Text("${interval}秒") }, leadingIcon = if (currentInterval == interval) { { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null)
        }
    }
}
