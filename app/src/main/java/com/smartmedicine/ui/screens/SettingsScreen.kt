package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartmedicine.ui.components.dialog.MqttTutorialDialog

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
    onSaveSettings: (String, String, String, String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mqttBroker by remember { mutableStateOf(settingsState.mqttBroker) }
    var deviceId by remember { mutableStateOf(settingsState.deviceId) }
    var mqttUsername by remember { mutableStateOf(settingsState.mqttUsername) }
    var mqttPassword by remember { mutableStateOf(settingsState.mqttPassword) }
    
    // 输入验证
    var mqttError by remember { mutableStateOf<String?>(null) }
    var deviceIdError by remember { mutableStateOf<String?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }
    
    // 教程对话框显示状态
    var showTutorialDialog by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    fun validateAndUpdateErrors(): Boolean {
        val validation = validateSettingsInputs(
            mqttBroker = mqttBroker,
            deviceId = deviceId,
            mqttUsername = mqttUsername,
            mqttPassword = mqttPassword
        )
        mqttError = validation.mqttError
        deviceIdError = validation.deviceIdError
        authError = validation.authError
        return validation.isValid
    }
    
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
                            if (validateAndUpdateErrors()) {
                                onSaveSettings(mqttBroker, deviceId, mqttUsername, mqttPassword)
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
                    placeholder = { Text("例如: box001") },
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

            // MQTT 鉴权设置
            SettingsSection(title = "MQTT 鉴权") {
                OutlinedTextField(
                    value = mqttUsername,
                    onValueChange = {
                        mqttUsername = it
                        authError = null
                    },
                    label = { Text("用户名") },
                    placeholder = { Text("例如: yunmenglin") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    supportingText = {
                        Text("EMQX账号用户名（如Broker开启鉴权需填写）")
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mqttPassword,
                    onValueChange = {
                        mqttPassword = it
                        authError = null
                    },
                    label = { Text("密码") },
                    placeholder = { Text("MQTT密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    supportingText = {
                        Text("EMQX账号密码")
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                authError?.let {
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
                                if (validateAndUpdateErrors()) {
                                    onSaveSettings(mqttBroker, deviceId, mqttUsername, mqttPassword)
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

            settingsState.errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "连接失败：$message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // 连接教程卡片
            SettingsSection(title = "帮助") {
                TutorialCard(onClick = { showTutorialDialog = true })
            }
            
            // 操作按钮
            SettingsSection(title = "操作") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (validateAndUpdateErrors()) {
                                onSaveSettings(mqttBroker, deviceId, mqttUsername, mqttPassword)
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
                               "• EMQX开启鉴权时用户名/密码不能为空\n" +
                               "• 修改设置后需要重新连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // 显示教程对话框
    if (showTutorialDialog) {
        MqttTutorialDialog(
            onDismiss = { showTutorialDialog = false }
        )
    }
}

/**
 * 教程入口卡片
 * 
 * @param onClick 点击回调
 */
@Composable
private fun TutorialCard(
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Column {
                    Text(
                        text = "连接教程",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "查看MQTT服务器配置说明",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "查看",
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
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
internal fun validateSettingsInputs(
    mqttBroker: String,
    deviceId: String,
    mqttUsername: String = "",
    mqttPassword: String = ""
): SettingsValidationResult {
    val brokerValid = BrokerInputValidator.isValidBroker(mqttBroker)
    val deviceValid = BrokerInputValidator.isValidDeviceId(deviceId)
    val authValid = !(mqttUsername.isBlank() xor mqttPassword.isBlank())

    return SettingsValidationResult(
        isValid = brokerValid && deviceValid && authValid,
        mqttError = if (brokerValid) {
            null
        } else {
            "MQTT Broker 地址格式错误，应为 tcp://host:port 或 ssl://host:port"
        },
        deviceIdError = if (deviceValid) {
            null
        } else {
            "设备ID仅支持字母、数字、下划线和中划线（1-64位）"
        },
        authError = if (authValid) {
            null
        } else {
            "用户名和密码需同时填写，或同时留空"
        }
    )
}

internal data class SettingsValidationResult(
    val isValid: Boolean,
    val mqttError: String?,
    val deviceIdError: String?,
    val authError: String?
)

/**
 * 设置UI状态
 */
data class SettingsUiState(
    val mqttBroker: String = "ssl://jaf12a6c.ala.cn-hangzhou.emqxsl.cn:8883",
    val deviceId: String = "box001",
    val mqttUsername: String = "yunmenglin",
    val mqttPassword: String = "12345678y",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null
)
