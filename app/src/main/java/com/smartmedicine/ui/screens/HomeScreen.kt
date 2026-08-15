package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.SensorData
import com.smartmedicine.data.model.SmartAnalysisInput
import com.smartmedicine.data.model.SmartAnalysisResult
import com.smartmedicine.data.model.SmartMedicineAnalysis
import com.smartmedicine.ui.components.AlertLevel
import com.smartmedicine.ui.components.BoxStatusCard
import com.smartmedicine.ui.components.ConnectionStatusIndicator
import com.smartmedicine.ui.components.ControlButtonsSection
import com.smartmedicine.ui.components.EnvironmentDataCard
import com.smartmedicine.ui.utils.WindowSizeClass
import com.smartmedicine.ui.utils.rememberWindowSizeClass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DOSE_MISSED_GRACE_MS = 30L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    onSetMedicineTimer: (Int, String, Int, Int, Int) -> Unit,
    onCancelMedicineTimer: (Int?) -> Unit,
    onDoseTaken: (Long) -> Unit,
    onDoseSkipped: (Long) -> Unit,
    onDoseSnoozed: (Long) -> Unit,
    onNavigateToMedicineBoxes: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToSmartCenter: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = rememberWindowSizeClass() != WindowSizeClass.COMPACT
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "智能药箱",
                            style = if (isExpanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.deviceId.ifBlank { "未连接设备" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMedicineBoxes) {
                        Icon(Icons.Default.Inventory, contentDescription = "药盒管理")
                    }
                    IconButton(onClick = onNavigateToPlans) {
                        Icon(Icons.Default.Schedule, contentDescription = "服药计划")
                    }
                    IconButton(onClick = onNavigateToSmartCenter) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "智能中心")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        HomeContent(
            uiState = uiState,
            onRefresh = onRefresh,
            onReset = onReset,
            onSetInterval = onSetInterval,
            onSetEnvRated = onSetEnvRated,
            onSetBuzzerEnabled = onSetBuzzerEnabled,
            onSetMedicineTimer = onSetMedicineTimer,
            onCancelMedicineTimer = onCancelMedicineTimer,
            onDoseTaken = onDoseTaken,
            onDoseSkipped = onDoseSkipped,
            onDoseSnoozed = onDoseSnoozed,
            innerPadding = innerPadding,
            isExpanded = isExpanded
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    onSetMedicineTimer: (Int, String, Int, Int, Int) -> Unit,
    onCancelMedicineTimer: (Int?) -> Unit,
    onDoseTaken: (Long) -> Unit,
    onDoseSkipped: (Long) -> Unit,
    onDoseSnoozed: (Long) -> Unit,
    innerPadding: PaddingValues,
    isExpanded: Boolean
) {
    val spacing = if (isExpanded) 24.dp else 16.dp
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val dueDose = uiState.todayDoses
        .filter { it.status == "pending" && it.scheduledAt <= nowMillis && nowMillis - it.scheduledAt < DOSE_MISSED_GRACE_MS }
        .minByOrNull { it.scheduledAt }
    dueDose?.let { dose ->
        MedicineDoseReminderDialog(
            dose = dose,
            onTaken = onDoseTaken,
            onSnoozed = onDoseSnoozed,
            onSkipped = onDoseSkipped
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = if (isExpanded) 32.dp else 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ConnectionStatusIndicator(uiState.isOnline, uiState.isConnecting, Modifier.fillMaxWidth())
        uiState.alerts.forEach { alert ->
            com.smartmedicine.ui.components.AlertCard(alert.message, alert.level, alert.onDismiss)
        }
        TodayDosesCard(uiState.todayDoses, onDoseTaken, onDoseSkipped, onDoseSnoozed, Modifier.fillMaxWidth())
        LowStockCard(uiState.lowStockCompartments, Modifier.fillMaxWidth())
        EnvironmentDataCard(uiState.sensorData?.getTemperature(), uiState.sensorData?.getHumidity(), Modifier.fillMaxWidth())
        BoxStatusCard(
            isOnline = uiState.isOnline,
            state = uiState.sensorData?.state ?: "unknown",
            vibration = uiState.sensorData?.motion?.vibration,
            pitch = uiState.sensorData?.motion?.pitch,
            roll = uiState.sensorData?.motion?.roll,
            modifier = Modifier.fillMaxWidth()
        )
        uiState.deviceStatus?.takeIf { uiState.isOnline }?.let { DeviceInfoCard(it, Modifier.fillMaxWidth()) }
        ControlButtonsSection(
            onRefresh = onRefresh,
            onReset = onReset,
            onSetInterval = onSetInterval,
            onSetEnvRated = onSetEnvRated,
            onSetBuzzerEnabled = onSetBuzzerEnabled,
            onSetMedicineTimer = onSetMedicineTimer,
            onCancelMedicineTimer = onCancelMedicineTimer,
            medicineCompartments = uiState.medicineCompartments.filter { it.active },
            buzzerEnabled = uiState.buzzerEnabled,
            isConnected = uiState.isOnline,
            modifier = Modifier.fillMaxWidth()
        )
        MedicineTimerListCard(uiState.medicineTimers, uiState.isOnline, { onCancelMedicineTimer(it) }, { onCancelMedicineTimer(null) }, Modifier.fillMaxWidth())
        StatsCard(uiState, Modifier.fillMaxWidth())
        uiState.lastUpdateTime?.let {
            Text("最后更新 $it", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MedicineDoseReminderDialog(
    dose: MedicineDoseUiItem,
    onTaken: (Long) -> Unit,
    onSnoozed: (Long) -> Unit,
    onSkipped: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
        title = { Text("该服药了") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${dose.timeText}  ${dose.medicineName}")
                Text("${dose.boxId} 号药盒  每次 ${dose.doseAmount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onTaken(dose.doseId) }) {
                Text("已服用")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSnoozed(dose.doseId) }) {
                    Text("稍后提醒")
                }
                TextButton(onClick = { onSkipped(dose.doseId) }) {
                    Text("跳过")
                }
            }
        }
    )
}

@Composable
private fun TodayDosesCard(
    doses: List<MedicineDoseUiItem>,
    onTaken: (Long) -> Unit,
    onSkipped: (Long) -> Unit,
    onSnoozed: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text("今日服药", style = MaterialTheme.typography.titleMedium)
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            if (doses.isEmpty()) {
                Text("今天暂无服药任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                doses.forEach { dose ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${dose.timeText}  ${dose.medicineName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${dose.boxId} 号药盒  每次 ${dose.doseAmount}  状态：${dose.statusText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AssistChip(onClick = {}, label = { Text(dose.statusText) })
                            }
                            if (dose.status == "pending" || dose.status == "missed") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onTaken(dose.doseId) }) { Text("已服用") }
                                    OutlinedButton(onClick = { onSnoozed(dose.doseId) }) { Text("稍后提醒") }
                                    TextButton(onClick = { onSkipped(dose.doseId) }) { Text("跳过") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LowStockCard(items: List<MedicineCompartmentUiItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("库存不足", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            items.forEach { item ->
                Text("${item.boxId} 号药盒 ${item.name}：剩余 ${item.stock}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatsCard(uiState: HomeUiState, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoBlock("今日", "${uiState.todayTakenCount}/${uiState.todayTotalCount}")
            InfoBlock("漏服", uiState.missedCount.toString())
            InfoBlock("服药计划", uiState.activePlanCount.toString())
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceInfoCard(deviceStatus: DeviceStatus, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("设备信息", style = MaterialTheme.typography.titleMedium)
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow("设备ID", deviceStatus.deviceId)
            InfoRow("固件版本", deviceStatus.firmwareVersion)
            InfoRow("WiFi", "${deviceStatus.wifiRssi} dBm")
            InfoRow("上报间隔", "${deviceStatus.publishInterval}s")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MedicineTimerListCard(
    timers: List<MedicineTimerUiItem>,
    isConnected: Boolean,
    onCancelTimer: (Int) -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null)
                    Text("设备定时", style = MaterialTheme.typography.titleMedium)
                }
                if (timers.isNotEmpty()) TextButton(onClick = onCancelAll, enabled = isConnected) { Text("全部取消") }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            if (timers.isEmpty()) {
                Text("暂无已同步定时", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                timers.sortedBy { it.timerId }.forEach { timer ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("#${timer.timerId}  ${timer.boxId}号药盒  ${timer.medicineName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${if (timer.mode == "clock") "当天时刻" else "倒计时"} ${timer.targetText()}  ${timer.remainingText()}", style = MaterialTheme.typography.bodySmall, color = if (timer.isRinging) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onCancelTimer(timer.timerId) }, enabled = isConnected) {
                                Icon(Icons.Default.Close, contentDescription = "取消定时")
                            }
                        }
                    }
                }
            }
        }
    }
}

data class HomeUiState(
    val isOnline: Boolean = false,
    val isConnecting: Boolean = false,
    val deviceId: String = "",
    val sensorData: SensorData? = null,
    val deviceStatus: DeviceStatus? = null,
    val buzzerEnabled: Boolean = true,
    val medicineCompartments: List<MedicineCompartmentUiItem> = emptyList(),
    val medicinePlans: List<MedicinePlanUiItem> = emptyList(),
    val todayDoses: List<MedicineDoseUiItem> = emptyList(),
    val medicineTimers: List<MedicineTimerUiItem> = emptyList(),
    val lastUpdateTime: String? = null,
    val alerts: List<AlertItem> = emptyList(),
    val offlineDuration: Long = 0L,
    val todayTakenCount: Int = 0,
    val todayTotalCount: Int = 0,
    val missedCount: Int = 0,
    val activePlanCount: Int = 0,
    val smartAnalysis: SmartAnalysisResult = SmartMedicineAnalysis.analyze(
        SmartAnalysisInput(
            now = 0L,
            doses = emptyList(),
            compartments = emptyList(),
            activePlanCount = 0,
            isOnline = false,
            boxState = null,
            environmentAbnormal = false
        )
    )
) {
    val lowStockCompartments: List<MedicineCompartmentUiItem>
        get() = medicineCompartments.filter { it.active && it.stock <= it.lowStockThreshold && it.stock > 0 }
}

data class MedicineTimerUiItem(
    val timerId: Int,
    val boxId: Int,
    val medicineName: String,
    val mode: String,
    val targetHour: Int,
    val targetMinute: Int,
    val targetSecond: Int,
    val remainingSeconds: Long,
    val doseId: Long? = null,
    val isRinging: Boolean = false
) {
    fun targetText(): String = "%02d:%02d:%02d".format(targetHour, targetMinute, targetSecond)
    fun remainingText(): String {
        if (isRinging) return "正在提醒"
        val safeSeconds = remainingSeconds.coerceAtLeast(0L)
        return "%02d:%02d:%02d".format(safeSeconds / 3600L, (safeSeconds % 3600L) / 60L, safeSeconds % 60L)
    }
}

data class MedicineCompartmentUiItem(
    val boxId: Int,
    val name: String,
    val sortOrder: Int,
    val stock: Int = 0,
    val dosePerUse: Int = 1,
    val lowStockThreshold: Int = 3,
    val active: Boolean = false
)

data class MedicinePlanUiItem(
    val planId: Long,
    val boxId: Int,
    val medicineName: String,
    val doseAmount: Int,
    val hour: Int,
    val minute: Int,
    val repeatType: String,
    val daysOfWeek: String,
    val enabled: Boolean
) {
    val timeText: String get() = "%02d:%02d".format(hour, minute)
    val repeatText: String
        get() = when (repeatType) {
            "daily" -> "每天"
            "weekly" -> "每周 ${daysOfWeek.ifBlank { "" }}"
            "once" -> "一次"
            else -> repeatType
        }
}

data class MedicineDoseUiItem(
    val doseId: Long,
    val planId: Long,
    val boxId: Int,
    val medicineName: String,
    val doseAmount: Int,
    val scheduledAt: Long,
    val status: String,
    val timerId: Int?
) {
    val timeText: String get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scheduledAt))
    val statusText: String
        get() = when (status) {
            "pending" -> "待服用"
            "taken" -> "已服用"
            "skipped" -> "已跳过"
            "missed" -> "可能漏服"
            "snoozed" -> "已稍后提醒"
            else -> status
        }
}

data class AlertItem(
    val message: String,
    val level: AlertLevel = AlertLevel.WARNING,
    val onDismiss: (() -> Unit)? = null
)
