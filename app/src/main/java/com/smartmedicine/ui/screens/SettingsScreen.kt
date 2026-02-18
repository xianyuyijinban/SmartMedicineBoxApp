package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 设置屏幕界面
 * 
 * @param settingsState 设置状态
 * @param onSaveSettings 保存设置回调
 * @param onConnect 连接回调
 * @param onDisconnect 断开连接回调
 * @param onNavigateBack 返回回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsState: SettingsUiState,
    onSaveSettings: (String, String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mqttBroker by remember { mutableStateOf(settingsState.mqttBroker) }
    var deviceId by remember { mutableStateOf(settingsState.deviceId) }
    
    // 输入验证
    var mqttError by remember { mutableStateOf<String?>(null) }
    var deviceIdError by remember { mutableStateOf<String?>(null) }
    
    val scrollState = rememberScrollState()
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (validateInputs(mqttBroker, deviceId)) {
                                onSaveSettings(mqttBroker, deviceId)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "保存"
                        )
                    }
                }
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
            
            // MQTT Broker 设置
            SettingsSection(title = "MQTT 连接设置") {
                OutlinedTextField(
                    value = mqttBroker,
                    onValueChange = { 
                        mqttBroker = it
                        mqttError = null
                    },
                    label = { Text("MQTT Broker 地址") },
                    placeholder = { Text("例如: tcp://192.168.1.100:1883") },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    supportingText = {
                        Text("格式: tcp://host:port 或 ssl://host:port")
                    },
                    isError = mqttError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                mqttError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // 设备ID设置
            SettingsSection(title = "设备设置") {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { 
                        deviceId = it
                        deviceIdError = null
                    },
                    label = { Text("设备ID") },
                    placeholder = { Text("例如: medicine_box_001") },
                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    supportingText = {
                        Text("设备唯一标识符，用于MQTT主题")
                    },
                    isError = deviceIdError != null,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                deviceIdError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // 连接状态
            SettingsSection(title = "连接状态") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            settingsState.isConnecting -> MaterialTheme.colorScheme.tertiaryContainer
                            settingsState.isConnected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = when {
                                    settingsState.isConnecting -> MaterialTheme.colorScheme.onTertiaryContainer
                                    settingsState.isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Column {
                                Text(
                                    text = when {
                                        settingsState.isConnecting -> "连接中..."
                                        settingsState.isConnected -> "已连接"
                                        else -> "未连接"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        settingsState.isConnecting -> MaterialTheme.colorScheme.onTertiaryContainer
                                        settingsState.isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                if (settingsState.isConnected) {
                                    Text(
                                        text = "设备在线",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        
                        // 连接/断开按钮
                        if (settingsState.isConnected) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("断开")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (validateInputs(mqttBroker, deviceId)) {
                                        onSaveSettings(mqttBroker, deviceId)
                                        onConnect()
                                    }
                                },
                                enabled = !settingsState.isConnecting
                            ) {
                                if (settingsState.isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("连接")
                            }
                        }
                    }
                }
            }
            
            // 操作按钮
            SettingsSection(title = "操作") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (validateInputs(mqttBroker, deviceId)) {
                                onSaveSettings(mqttBroker, deviceId)
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存并返回")
                    }
                    
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }
                }
            }
            
            // 提示信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 提示",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• MQTT Broker 地址需要包含协议和端口\n" +
                               "• 设备ID需要与药箱设备配置一致\n" +
                               "• 修改设置后需要重新连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 设置区块
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

/**
 * 验证输入
 */
private fun validateInputs(mqttBroker: String, deviceId: String): Boolean {
    if (mqttBroker.isBlank()) return false
    if (deviceId.isBlank()) return false
    if (!mqttBroker.startsWith("tcp://") && !mqttBroker.startsWith("ssl://")) return false
    return true
}

/**
 * 设置UI状态
 */
data class SettingsUiState(
    val mqttBroker: String = "tcp://192.168.1.100:1883",
    val deviceId: String = "medicine_box_001",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null
)
