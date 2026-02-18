package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.MotionData
import com.smartmedicine.data.model.SensorData
import com.smartmedicine.ui.components.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 主屏幕界面
 * 
 * @param uiState UI状态
 * @param onRefresh 刷新回调
 * @param onReset 重置设备回调
 * @param onSetInterval 设置上报间隔回调
 * @param onNavigateToSettings 导航到设置界面
 * @param onNavigateToHistory 导航到历史数据界面
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "智能药箱监控",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.deviceId.takeIf { it.isNotEmpty() } ?: "未连接设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "历史数据"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // 连接状态指示器
            ConnectionStatusIndicator(
                isOnline = uiState.isOnline,
                isConnecting = uiState.isConnecting,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 警告提示（如果有）
            uiState.alerts.forEach { alert ->
                AlertCard(
                    message = alert.message,
                    level = alert.level,
                    onDismiss = alert.onDismiss
                )
            }
            
            // 环境数据卡片
            EnvironmentDataCard(
                temperature = uiState.sensorData?.getTemperature(),
                humidity = uiState.sensorData?.getHumidity(),
                pressure = uiState.sensorData?.environment?.pressure,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 药箱状态卡片
            BoxStatusCard(
                state = uiState.sensorData?.state ?: "unknown",
                vibration = uiState.sensorData?.motion?.vibration,
                pitch = uiState.sensorData?.motion?.pitch,
                roll = uiState.sensorData?.motion?.roll,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 设备信息卡片（仅在在线时显示）
            if (uiState.isOnline && uiState.deviceStatus != null) {
                DeviceInfoCard(
                    deviceStatus = uiState.deviceStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 控制按钮区域
            ControlButtonsSection(
                onRefresh = onRefresh,
                onReset = onReset,
                onSetInterval = onSetInterval,
                isConnected = uiState.isOnline,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 最后更新时间
            uiState.lastUpdateTime?.let { time ->
                Text(
                    text = "最后更新: $time",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 设备信息卡片
 */
@Composable
private fun DeviceInfoCard(
    deviceStatus: DeviceStatus,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📱 设备信息",
                style = MaterialTheme.typography.titleMedium
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            InfoRow(label = "设备ID", value = deviceStatus.deviceId)
            InfoRow(label = "固件版本", value = deviceStatus.firmwareVersion)
            InfoRow(label = "WiFi信号", value = "${deviceStatus.wifiRssi} dBm (${deviceStatus.getWifiSignalDescription()})")
            InfoRow(label = "上报间隔", value = "${deviceStatus.publishInterval} 秒")
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 主屏幕UI状态
 */
data class HomeUiState(
    val isOnline: Boolean = false,
    val isConnecting: Boolean = false,
    val deviceId: String = "",
    val sensorData: SensorData? = null,
    val deviceStatus: DeviceStatus? = null,
    val lastUpdateTime: String? = null,
    val alerts: List<AlertItem> = emptyList(),
    val offlineDuration: Long = 0L  // 离线时长（毫秒）
)

/**
 * 警告项
 */
data class AlertItem(
    val message: String,
    val level: AlertLevel = AlertLevel.WARNING,
    val onDismiss: (() -> Unit)? = null
)
