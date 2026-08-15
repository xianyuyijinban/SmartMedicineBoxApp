package com.smartmedicine.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.EnvironmentData
import com.smartmedicine.data.model.MotionData
import com.smartmedicine.data.model.SensorData
import com.smartmedicine.ui.components.*
import com.smartmedicine.ui.screens.*
import com.smartmedicine.ui.theme.SmartMedicineBoxTheme

/**
 * UI组件预览
 * 用于Android Studio设计时预览
 */

// ========== 状态指示器预览 ==========

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorOnlinePreview() {
    SmartMedicineBoxTheme {
        ConnectionStatusIndicator(
            isOnline = true,
            isConnecting = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorOfflinePreview() {
    SmartMedicineBoxTheme {
        ConnectionStatusIndicator(
            isOnline = false,
            isConnecting = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorConnectingPreview() {
    SmartMedicineBoxTheme {
        ConnectionStatusIndicator(
            isOnline = false,
            isConnecting = true
        )
    }
}

// ========== 环境数据卡片预览 ==========

@Preview(showBackground = true)
@Composable
fun EnvironmentDataCardPreview() {
    SmartMedicineBoxTheme {
        EnvironmentDataCard(
            temperature = 25.3,
            humidity = 55.5
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EnvironmentDataCardNullPreview() {
    SmartMedicineBoxTheme {
        EnvironmentDataCard(
            temperature = null,
            humidity = null
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DetailedEnvironmentCardPreview() {
    SmartMedicineBoxTheme {
        DetailedEnvironmentCard(
            temperature = 25.3,
            humidity = 55.5,
            altitude = 50.0
        )
    }
}

// ========== 药箱状态卡片预览 ==========

@Preview(showBackground = true)
@Composable
fun BoxStatusCardClosedPreview() {
    SmartMedicineBoxTheme {
        BoxStatusCard(
            isOnline = true,
            state = "closed",
            vibration = 0.005,
            pitch = 1.0,
            roll = -0.5
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BoxStatusCardOpenedPreview() {
    SmartMedicineBoxTheme {
        BoxStatusCard(
            isOnline = true,
            state = "opened",
            vibration = 0.01,
            pitch = 45.0,
            roll = 0.0
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BoxStatusCardMovingPreview() {
    SmartMedicineBoxTheme {
        BoxStatusCard(
            isOnline = true,
            state = "moving",
            vibration = 0.8,
            pitch = 5.0,
            roll = 3.0
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BoxStatusCardTiltedPreview() {
    SmartMedicineBoxTheme {
        BoxStatusCard(
            isOnline = true,
            state = "tilted",
            vibration = 0.1,
            pitch = 35.0,
            roll = 40.0
        )
    }
}

// ========== 警告卡片预览 ==========

@Preview(showBackground = true)
@Composable
fun AlertCardErrorPreview() {
    SmartMedicineBoxTheme {
        AlertCard(
            message = "设备连接超时，请检查网络",
            level = AlertLevel.ERROR
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertCardWarningPreview() {
    SmartMedicineBoxTheme {
        AlertCard(
            message = "温度异常: 32.5°C",
            level = AlertLevel.WARNING
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AlertCardInfoPreview() {
    SmartMedicineBoxTheme {
        AlertCard(
            message = "药箱已打开",
            level = AlertLevel.INFO
        )
    }
}

// ========== 控制按钮预览 ==========

@Preview(showBackground = true)
@Composable
fun ControlButtonsSectionConnectedPreview() {
    SmartMedicineBoxTheme {
        ControlButtonsSection(
            onRefresh = {},
            onReset = {},
            onSetInterval = {},
            onSetEnvRated = { _, _ -> },
            onSetBuzzerEnabled = {},
            onSetMedicineTimer = { _, _, _, _, _ -> },
            onCancelMedicineTimer = { _ -> },
            medicineCompartments = previewCompartments(),
            buzzerEnabled = true,
            isConnected = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ControlButtonsSectionDisconnectedPreview() {
    SmartMedicineBoxTheme {
        ControlButtonsSection(
            onRefresh = {},
            onReset = {},
            onSetInterval = {},
            onSetEnvRated = { _, _ -> },
            onSetBuzzerEnabled = {},
            onSetMedicineTimer = { _, _, _, _, _ -> },
            onCancelMedicineTimer = { _ -> },
            medicineCompartments = previewCompartments(),
            buzzerEnabled = false,
            isConnected = false
        )
    }
}

// ========== 主屏幕预览 ==========

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun HomeScreenOnlinePreview() {
    SmartMedicineBoxTheme {
        HomeScreen(
            uiState = HomeUiState(
                isOnline = true,
                isConnecting = false,
                deviceId = "medicine_box_001",
                sensorData = SensorData(
                    timestamp = 123456789,
                    deviceId = "medicine_box_001",
                    state = "closed",
                    environment = EnvironmentData(
                        temperature = 25.3,
                        humidity = 55.5,
                        pressure = 101325.0,
                        altitude = 50.0
                    ),
                    motion = MotionData(
                        accelX = 0.015,
                        accelY = -0.008,
                        accelZ = 0.995,
                        pitch = 2.15,
                        roll = -1.02,
                        vibration = 0.005
                    ),
                    valid = 1
                ),
                deviceStatus = DeviceStatus(
                    status = "online",
                    timestamp = 123456789,
                    deviceId = "medicine_box_001",
                    firmwareVersion = "1.0.0",
                    wifiRssi = -65,
                    publishInterval = 5
                ),
                lastUpdateTime = "14:30:25",
                alerts = emptyList()
            ),
            onRefresh = {},
            onReset = {},
            onSetInterval = {},
            onSetEnvRated = { _, _ -> },
            onSetBuzzerEnabled = {},
            onSetMedicineTimer = { _, _, _, _, _ -> },
            onCancelMedicineTimer = { _ -> },
            onDoseTaken = {},
            onDoseSkipped = {},
            onDoseSnoozed = {},
            onNavigateToMedicineBoxes = {},
            onNavigateToPlans = {},
            onNavigateToSmartCenter = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun HomeScreenOfflinePreview() {
    SmartMedicineBoxTheme {
        HomeScreen(
            uiState = HomeUiState(
                isOnline = false,
                isConnecting = false,
                deviceId = "medicine_box_001",
                sensorData = null,
                deviceStatus = null,
                lastUpdateTime = null,
                alerts = listOf(
                    AlertItem(
                        message = "设备离线超过15秒",
                        level = AlertLevel.ERROR
                    )
                )
            ),
            onRefresh = {},
            onReset = {},
            onSetInterval = {},
            onSetEnvRated = { _, _ -> },
            onSetBuzzerEnabled = {},
            onSetMedicineTimer = { _, _, _, _, _ -> },
            onCancelMedicineTimer = { _ -> },
            onDoseTaken = {},
            onDoseSkipped = {},
            onDoseSnoozed = {},
            onNavigateToMedicineBoxes = {},
            onNavigateToPlans = {},
            onNavigateToSmartCenter = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun HomeScreenWithAlertsPreview() {
    SmartMedicineBoxTheme {
        HomeScreen(
            uiState = HomeUiState(
                isOnline = true,
                isConnecting = false,
                deviceId = "medicine_box_001",
                sensorData = SensorData(
                    timestamp = 123456789,
                    deviceId = "medicine_box_001",
                    state = "tilted",
                    environment = EnvironmentData(
                        temperature = 32.5,
                        humidity = 75.0,
                        pressure = 101325.0,
                        altitude = 50.0
                    ),
                    motion = MotionData(
                        pitch = 35.0,
                        roll = 40.0,
                        vibration = 0.1
                    ),
                    valid = 1
                ),
                deviceStatus = DeviceStatus(
                    status = "online",
                    deviceId = "medicine_box_001",
                    firmwareVersion = "1.0.0",
                    wifiRssi = -65,
                    publishInterval = 5
                ),
                lastUpdateTime = "14:30:25",
                alerts = listOf(
                    AlertItem(
                        message = "温度异常: 32.5°C",
                        level = AlertLevel.WARNING
                    ),
                    AlertItem(
                        message = "药箱处于倾斜状态",
                        level = AlertLevel.ERROR
                    )
                )
            ),
            onRefresh = {},
            onReset = {},
            onSetInterval = {},
            onSetEnvRated = { _, _ -> },
            onSetBuzzerEnabled = {},
            onSetMedicineTimer = { _, _, _, _, _ -> },
            onCancelMedicineTimer = { _ -> },
            onDoseTaken = {},
            onDoseSkipped = {},
            onDoseSnoozed = {},
            onNavigateToMedicineBoxes = {},
            onNavigateToPlans = {},
            onNavigateToSmartCenter = {},
            onNavigateToSettings = {}
        )
    }
}

// ========== 设置屏幕预览 ==========

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun SettingsScreenDisconnectedPreview() {
    SmartMedicineBoxTheme {
        SettingsScreen(
            settingsState = SettingsUiState(
                mqttBroker = "tcp://192.168.1.100:1883",
                deviceId = "medicine_box_001",
                isConnected = false,
                isConnecting = false,
                errorMessage = null
            ),
            onSaveSettings = { _, _, _, _ -> },
            onConnect = {},
            onDisconnect = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun SettingsScreenConnectedPreview() {
    SmartMedicineBoxTheme {
        SettingsScreen(
            settingsState = SettingsUiState(
                mqttBroker = "tcp://192.168.1.100:1883",
                deviceId = "medicine_box_001",
                isConnected = true,
                isConnecting = false,
                errorMessage = null
            ),
            onSaveSettings = { _, _, _, _ -> },
            onConnect = {},
            onDisconnect = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun SettingsScreenConnectingPreview() {
    SmartMedicineBoxTheme {
        SettingsScreen(
            settingsState = SettingsUiState(
                mqttBroker = "tcp://192.168.1.100:1883",
                deviceId = "medicine_box_001",
                isConnected = false,
                isConnecting = true,
                errorMessage = null
            ),
            onSaveSettings = { _, _, _, _ -> },
            onConnect = {},
            onDisconnect = {},
            onNavigateBack = {}
        )
    }
}


private fun previewCompartments(): List<MedicineCompartmentUiItem> =
    (1..15).map { boxId ->
        MedicineCompartmentUiItem(
            boxId = boxId,
            name = "Medicine $boxId",
            sortOrder = boxId
        )
    }
