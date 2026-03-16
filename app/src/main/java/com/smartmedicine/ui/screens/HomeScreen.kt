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
import com.smartmedicine.ui.utils.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 主屏幕界面 - 自适应布局版本
 * 支持手机和平板设备（API 24 - API 35）
 * 
 * 布局适配策略：
 * - 手机竖屏 (< 600dp): 单列垂直布局
 * - 平板/横屏 (>= 600dp): 双列网格布局
 * 
 * @param uiState UI状态
 * @param onRefresh 刷新回调
 * @param onReset 重置设备回调
 * @param onSetInterval 设置上报间隔回调
 * @param onSetEnvRated 设置环境额定值回调
 * @param onSetBuzzerEnabled 设置蜂鸣器开关回调
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
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 获取窗口尺寸类别，用于自适应布局
    val windowSizeClass = rememberWindowSizeClass()
    val isExpanded = windowSizeClass != WindowSizeClass.COMPACT
    
    // 根据屏幕尺寸调整内边距
    val horizontalPadding = if (isExpanded) 32.dp else 16.dp
    val verticalSpacing = if (isExpanded) 24.dp else 16.dp
    
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
                            // 平板使用更大字体
                            style = if (isExpanded) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
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
                            contentDescription = "刷新",
                            // 平板使用更大图标
                            modifier = if (isExpanded) Modifier.size(28.dp) else Modifier
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "历史数据",
                            modifier = if (isExpanded) Modifier.size(28.dp) else Modifier
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = if (isExpanded) Modifier.size(28.dp) else Modifier
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
        // 根据屏幕尺寸选择布局
        if (isExpanded) {
            // 大屏幕：使用双列布局
            HomeScreenExpandedLayout(
                uiState = uiState,
                onRefresh = onRefresh,
                onReset = onReset,
                onSetInterval = onSetInterval,
                onSetEnvRated = onSetEnvRated,
                onSetBuzzerEnabled = onSetBuzzerEnabled,
                innerPadding = innerPadding,
                horizontalPadding = horizontalPadding,
                verticalSpacing = verticalSpacing
            )
        } else {
            // 小屏幕：使用单列布局
            HomeScreenCompactLayout(
                uiState = uiState,
                onRefresh = onRefresh,
                onReset = onReset,
                onSetInterval = onSetInterval,
                onSetEnvRated = onSetEnvRated,
                onSetBuzzerEnabled = onSetBuzzerEnabled,
                innerPadding = innerPadding,
                horizontalPadding = horizontalPadding,
                verticalSpacing = verticalSpacing
            )
        }
    }
}

/**
 * 小屏幕单列布局
 * 适用于手机竖屏 (< 600dp)
 */
@Composable
private fun HomeScreenCompactLayout(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    innerPadding: PaddingValues,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalSpacing: androidx.compose.ui.unit.Dp
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = horizontalPadding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
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
            modifier = Modifier.fillMaxWidth()
        )
        
        // 药箱状态卡片
        BoxStatusCard(
            isOnline = uiState.isOnline,
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
            onSetEnvRated = onSetEnvRated,
            onSetBuzzerEnabled = onSetBuzzerEnabled,
            buzzerEnabled = uiState.buzzerEnabled,
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

/**
 * 大屏幕双列布局
 * 适用于平板和横屏 (>= 600dp)
 */
@Composable
private fun HomeScreenExpandedLayout(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetEnvRated: (Double, Double) -> Unit,
    onSetBuzzerEnabled: (Boolean) -> Unit,
    innerPadding: PaddingValues,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalSpacing: androidx.compose.ui.unit.Dp
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = horizontalPadding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
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
        
        // 第一行：环境数据 + 药箱状态（双列）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            // 环境数据卡片
            EnvironmentDataCard(
                temperature = uiState.sensorData?.getTemperature(),
                humidity = uiState.sensorData?.getHumidity(),
                modifier = Modifier.weight(1f)
            )
            
            // 药箱状态卡片
            BoxStatusCard(
                isOnline = uiState.isOnline,
                state = uiState.sensorData?.state ?: "unknown",
                vibration = uiState.sensorData?.motion?.vibration,
                pitch = uiState.sensorData?.motion?.pitch,
                roll = uiState.sensorData?.motion?.roll,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 第二行：设备信息 + 控制按钮（双列）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            // 设备信息卡片
            if (uiState.isOnline && uiState.deviceStatus != null) {
                DeviceInfoCard(
                    deviceStatus = uiState.deviceStatus,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 占位，保持布局对齐
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // 控制按钮区域
            ControlButtonsSection(
                onRefresh = onRefresh,
                onReset = onReset,
                onSetInterval = onSetInterval,
                onSetEnvRated = onSetEnvRated,
                onSetBuzzerEnabled = onSetBuzzerEnabled,
                buzzerEnabled = uiState.buzzerEnabled,
                isConnected = uiState.isOnline,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 最后更新时间
        uiState.lastUpdateTime?.let { time ->
            Text(
                text = "最后更新: $time",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 设备信息卡片
 * 
 * @param deviceStatus 设备状态
 * @param modifier 修饰符
 */
@Composable
private fun DeviceInfoCard(
    deviceStatus: DeviceStatus,
    modifier: Modifier = Modifier
) {
    // 根据屏幕尺寸调整内边距
    val isExpanded = isExpandedScreen()
    val cardPadding = if (isExpanded) 20.dp else 16.dp
    
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📱 设备信息",
                style = if (isExpanded) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                }
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            InfoRow(label = "设备ID", value = deviceStatus.deviceId)
            InfoRow(label = "固件版本", value = deviceStatus.firmwareVersion)
            InfoRow(
                label = "WiFi信号", 
                value = "${deviceStatus.wifiRssi} dBm (${deviceStatus.getWifiSignalDescription()})"
            )
            InfoRow(label = "上报间隔", value = "${deviceStatus.publishInterval} 秒")
        }
    }
}

/**
 * 信息行
 * 
 * @param label 标签
 * @param value 值
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
 * 
 * @param isOnline 是否在线
 * @param isConnecting 是否连接中
 * @param deviceId 设备ID
 * @param sensorData 传感器数据
 * @param deviceStatus 设备状态
 * @param lastUpdateTime 最后更新时间
 * @param alerts 警告列表
 * @param offlineDuration 离线时长（毫秒）
 */
data class HomeUiState(
    val isOnline: Boolean = false,
    val isConnecting: Boolean = false,
    val deviceId: String = "",
    val sensorData: SensorData? = null,
    val deviceStatus: DeviceStatus? = null,
    val buzzerEnabled: Boolean = true,
    val lastUpdateTime: String? = null,
    val alerts: List<AlertItem> = emptyList(),
    val offlineDuration: Long = 0L
)

/**
 * 警告项
 * 
 * @param message 警告消息
 * @param level 警告级别
 * @param onDismiss 关闭回调
 */
data class AlertItem(
    val message: String,
    val level: AlertLevel = AlertLevel.WARNING,
    val onDismiss: (() -> Unit)? = null
)
