package com.smartmedicine.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartmedicine.data.db.AppDatabase
import com.smartmedicine.data.db.MedicineCompartmentEntity
import com.smartmedicine.data.db.MedicineDoseEntity
import com.smartmedicine.data.db.MedicinePlanEntity
import com.smartmedicine.data.model.AlertEvent
import com.smartmedicine.data.model.CommandResponse
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.SensorData
import com.smartmedicine.data.model.SmartAnalysisInput
import com.smartmedicine.data.model.SmartCompartmentRecord
import com.smartmedicine.data.model.SmartDoseRecord
import com.smartmedicine.data.model.SmartMedicineAnalysis
import com.smartmedicine.mqtt.MqttManager
import com.smartmedicine.notification.NotificationManager
import com.smartmedicine.ui.components.AlertLevel
import com.smartmedicine.ui.screens.AlertItem
import com.smartmedicine.ui.screens.HomeScreen
import com.smartmedicine.ui.screens.HomeUiState
import com.smartmedicine.ui.screens.MedicineBoxScreen
import com.smartmedicine.ui.screens.MedicineCompartmentUiItem
import com.smartmedicine.ui.screens.MedicineDoseUiItem
import com.smartmedicine.ui.screens.MedicinePlanScreen
import com.smartmedicine.ui.screens.MedicinePlanUiItem
import com.smartmedicine.ui.screens.MedicineTimerUiItem
import com.smartmedicine.ui.screens.SettingsScreen
import com.smartmedicine.ui.screens.SettingsUiState
import com.smartmedicine.ui.screens.SmartCenterScreen
import com.smartmedicine.ui.theme.SmartMedicineBoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val DEFAULT_MQTT_BROKER = "ssl://jaf12a6c.ala.cn-hangzhou.emqxsl.cn:8883"
        const val DEFAULT_DEVICE_ID = "box001"
        const val DEFAULT_MQTT_USERNAME = "yunmenglin"
        const val DEFAULT_MQTT_PASSWORD = "12345678y"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Timber.d("Notification permission granted=$granted") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        notificationManager = NotificationManager.getInstance(this)
        viewModel.setNotificationManager(notificationManager)
        requestNotificationPermission()
        setContent {
            SmartMedicineBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartMedicineBoxApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun SmartMedicineBoxApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                uiState = uiState,
                onRefresh = { viewModel.publishNow() },
                onReset = { viewModel.resetDevice() },
                onSetInterval = { viewModel.setInterval(it) },
                onSetEnvRated = { temperature, humidity -> viewModel.setEnvironmentRated(temperature, humidity) },
                onSetBuzzerEnabled = { viewModel.setBuzzerEnabled(it) },
                onSetMedicineTimer = { boxId, mode, hour, minute, second ->
                    viewModel.setMedicineTimer(boxId, mode, hour, minute, second)
                },
                onCancelMedicineTimer = { viewModel.cancelMedicineTimer(it) },
                onDoseTaken = { viewModel.markDoseTaken(it) },
                onDoseSkipped = { viewModel.markDoseSkipped(it) },
                onDoseSnoozed = { viewModel.snoozeDose(it) },
                onNavigateToMedicineBoxes = { navController.navigate("medicine_boxes") },
                onNavigateToPlans = { navController.navigate("medicine_plans") },
                onNavigateToSmartCenter = { navController.navigate("smart_center") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsState = settingsState,
                onSaveSettings = { broker, deviceId, username, password ->
                    viewModel.updateSettings(broker, deviceId, username, password)
                },
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("medicine_boxes") {
            MedicineBoxScreen(
                compartments = uiState.medicineCompartments,
                onSaveInfo = { boxId, name, stock, dose, low -> viewModel.saveCompartmentInfo(boxId, name, stock, dose, low) },
                onAddInfo = { boxId, name, stock, dose, low -> viewModel.addCompartmentInfo(boxId, name, stock, dose, low) },
                onDeactivate = { viewModel.deactivateCompartment(it) },
                onTransfer = { from, to -> viewModel.transferCompartment(from, to) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("medicine_plans") {
            MedicinePlanScreen(
                plans = uiState.medicinePlans,
                compartments = uiState.medicineCompartments.filter { it.active },
                onAddPlan = { boxId, name, dose, hour, minute, repeat, days ->
                    viewModel.addMedicinePlan(boxId, name, dose, hour, minute, repeat, days)
                },
                onUpdatePlan = { planId, boxId, name, dose, hour, minute, repeat, days ->
                    viewModel.updateMedicinePlan(planId, boxId, name, dose, hour, minute, repeat, days)
                },
                onSetPlanEnabled = { planId, enabled -> viewModel.setPlanEnabled(planId, enabled) },
                onDeletePlan = { viewModel.deleteMedicinePlan(it) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("smart_center") {
            SmartCenterScreen(
                analysis = uiState.smartAnalysis,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val mqttManager = MqttManager()
    private val medicineCompartmentDao = AppDatabase.getInstance(application).medicineCompartmentDao()
    private val medicinePlanDao = AppDatabase.getInstance(application).medicinePlanDao()
    private val medicineDoseDao = AppDatabase.getInstance(application).medicineDoseDao()
    private var notificationManager: NotificationManager? = null
    private var pendingMedicineTimerDraft: MedicineTimerDraft? = null
    private val pendingTimerDrafts = mutableMapOf<Int, MedicineTimerDraft>()
    private var lastSyncedPlanTimerIds: Set<Int> = emptySet()
    private val notifiedDoseIds = mutableSetOf<Long>()
    private var latestEventAlert: AlertItem? = null
    private val alertHistory = mutableListOf<AlertRecord>()
    private var latestDoses: List<MedicineDoseEntity> = emptyList()
    private var latestCompartments: List<MedicineCompartmentEntity> = emptyList()

    private val _uiState = MutableStateFlow(HomeUiState(deviceId = MainActivity.DEFAULT_DEVICE_ID))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _settingsState = MutableStateFlow(
        SettingsUiState(
            mqttBroker = MainActivity.DEFAULT_MQTT_BROKER,
            deviceId = MainActivity.DEFAULT_DEVICE_ID,
            mqttUsername = MainActivity.DEFAULT_MQTT_USERNAME,
            mqttPassword = MainActivity.DEFAULT_MQTT_PASSWORD
        )
    )
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    companion object {
        private const val MAX_MEDICINE_TIMERS = 5
        private const val MEDICINE_COMPARTMENT_COUNT = 15
        private const val SNOOZE_DELAY_MS = 5L * 60L * 1000L
        private const val OVERDUE_DELAY_MS = 30L * 60L * 1000L
    }

    init {
        observeMedicineCompartments()
        observeMedicinePlans()
        observeMedicineDoses()
        startMedicineTimerTicker()
        startDoseMaintenanceTicker()
    }

    fun setNotificationManager(manager: NotificationManager) {
        notificationManager = manager
    }

    private fun observeMedicineCompartments() {
        viewModelScope.launch {
            ensureDefaultMedicineCompartments()
            medicineCompartmentDao.observeAll().collectLatest { compartments ->
                latestCompartments = compartments
                _uiState.update { current ->
                    current.copy(medicineCompartments = compartments.map { it.toUiItem() })
                }
                refreshSmartAnalysis()
            }
        }
    }

    private suspend fun ensureDefaultMedicineCompartments() {
        if (medicineCompartmentDao.getAll().isNotEmpty()) return
        val now = System.currentTimeMillis()
        medicineCompartmentDao.insertAll(
            (1..MEDICINE_COMPARTMENT_COUNT).map { boxId ->
                MedicineCompartmentEntity(boxId = boxId, name = "$boxId 号药盒", sortOrder = boxId, updatedAt = now)
            }
        )
    }

    private fun observeMedicinePlans() {
        viewModelScope.launch {
            medicinePlanDao.observeAll().collectLatest { plans ->
                _uiState.update { current ->
                    current.copy(
                        medicinePlans = plans.map { it.toUiItem() },
                        activePlanCount = plans.count { it.enabled }
                    )
                }
                refreshSmartAnalysis()
            }
        }
    }

    private fun observeMedicineDoses() {
        viewModelScope.launch {
            medicineDoseDao.observeAll().collectLatest { doses ->
                latestDoses = doses
                val todayStart = startOfTodayMillis()
                val tomorrowStart = todayStart + 24L * 60L * 60L * 1000L
                val hiddenReasons = setOf("重复稍后提醒已合并", "服药计划已关闭", "服药计划已修改", "服药计划已删除", "药盒已停用")
                val today = doses.filter {
                    it.scheduledAt in todayStart until tomorrowStart &&
                        it.smartReason !in hiddenReasons
                }
                    .sortedBy { it.scheduledAt }
                _uiState.update { current ->
                    current.copy(
                        todayDoses = today.map { it.toUiItem() },
                        todayTotalCount = today.size,
                        todayTakenCount = today.count { it.status == MedicineDoseEntity.STATUS_TAKEN },
                        missedCount = doses.count { it.status == MedicineDoseEntity.STATUS_MISSED }
                    )
                }
                refreshSmartAnalysis()
            }
        }
    }

    private fun refreshSmartAnalysis() {
        val current = _uiState.value
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val analysis = SmartMedicineAnalysis.analyze(
            SmartAnalysisInput(
                now = System.currentTimeMillis(),
                doses = latestDoses.filter { it.scheduledAt >= sevenDaysAgo }.map {
                    SmartDoseRecord(
                        scheduledAt = it.scheduledAt,
                        status = it.status,
                        actualTakenAt = it.actualTakenAt,
                        reminderCount = it.reminderCount
                    )
                },
                compartments = latestCompartments.filter { it.active }.map {
                    SmartCompartmentRecord(
                        boxId = it.boxId,
                        name = it.name,
                        stock = it.stock,
                        dosePerUse = it.dosePerUse,
                        lowStockThreshold = it.lowStockThreshold,
                        active = it.active
                    )
                },
                activePlanCount = current.activePlanCount,
                isOnline = current.isOnline,
                boxState = current.sensorData?.state,
                environmentAbnormal = current.alerts.any { it.message.contains("环境") }
            )
        )
        _uiState.update { it.copy(smartAnalysis = analysis) }
    }

    fun renameCompartment(boxId: Int, name: String) {
        val trimmed = name.trim()
        if (boxId !in 1..MEDICINE_COMPARTMENT_COUNT || trimmed.isEmpty()) return
        viewModelScope.launch { medicineCompartmentDao.updateName(boxId, trimmed) }
    }

    fun saveCompartmentInfo(boxId: Int, name: String, stock: Int, dosePerUse: Int, lowStockThreshold: Int) {
        if (boxId !in 1..MEDICINE_COMPARTMENT_COUNT) return
        viewModelScope.launch {
            medicineCompartmentDao.updateMedicineInfo(
                boxId = boxId,
                name = name.trim().ifBlank { "$boxId 号药盒" },
                stock = stock.coerceAtLeast(0),
                dosePerUse = dosePerUse.coerceAtLeast(1),
                lowStockThreshold = lowStockThreshold.coerceAtLeast(0)
            )
        }
    }

    fun addCompartmentInfo(boxId: Int, name: String, stock: Int, dosePerUse: Int, lowStockThreshold: Int) {
        if (boxId !in 1..MEDICINE_COMPARTMENT_COUNT) return
        viewModelScope.launch {
            medicineCompartmentDao.activate(
                boxId = boxId,
                name = name.trim().ifBlank { "$boxId 号药盒" },
                stock = stock.coerceAtLeast(0),
                dosePerUse = dosePerUse.coerceAtLeast(1),
                lowStockThreshold = lowStockThreshold.coerceAtLeast(0)
            )
        }
    }

    fun deactivateCompartment(boxId: Int) {
        if (boxId !in 1..MEDICINE_COMPARTMENT_COUNT) return
        viewModelScope.launch {
            medicineCompartmentDao.deactivate(boxId, "$boxId 号药盒")
            medicinePlanDao.disableByBoxId(boxId)
            medicineDoseDao.markBoxDoses(
                boxId = boxId,
                newStatus = MedicineDoseEntity.STATUS_SKIPPED,
                statuses = listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_SNOOZED),
                reason = "药盒已停用"
            )
            cancelMedicineTimer()
            generateUpcomingDoses()
            syncUpcomingDosesToDevice()
        }
    }

    fun transferCompartment(fromBoxId: Int, toBoxId: Int) {
        if (fromBoxId !in 1..MEDICINE_COMPARTMENT_COUNT || toBoxId !in 1..MEDICINE_COMPARTMENT_COUNT || fromBoxId == toBoxId) return
        viewModelScope.launch {
            val items = medicineCompartmentDao.getAll()
            val from = items.firstOrNull { it.boxId == fromBoxId && it.active } ?: return@launch
            val to = items.firstOrNull { it.boxId == toBoxId && !it.active } ?: return@launch
            medicineCompartmentDao.activate(
                boxId = to.boxId,
                name = from.name,
                stock = from.stock,
                dosePerUse = from.dosePerUse,
                lowStockThreshold = from.lowStockThreshold
            )
            medicineCompartmentDao.deactivate(from.boxId, "${from.boxId} 号药盒")
            medicinePlanDao.transferBox(from.boxId, to.boxId, from.name)
            medicineDoseDao.transferBoxDoses(
                fromBoxId = from.boxId,
                toBoxId = to.boxId,
                medicineName = from.name,
                statuses = listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_SNOOZED, MedicineDoseEntity.STATUS_MISSED)
            )
            cancelMedicineTimer()
            generateUpcomingDoses()
            syncUpcomingDosesToDevice()
        }
    }

    fun moveCompartment(boxId: Int, direction: Int) {
        viewModelScope.launch {
            val items = medicineCompartmentDao.getAll().toMutableList()
            val index = items.indexOfFirst { it.boxId == boxId }
            val targetIndex = index + direction
            if (index < 0 || targetIndex !in items.indices) return@launch
            val current = items[index]
            val target = items[targetIndex]
            val now = System.currentTimeMillis()
            medicineCompartmentDao.insertAll(
                listOf(
                    current.copy(sortOrder = target.sortOrder, updatedAt = now),
                    target.copy(sortOrder = current.sortOrder, updatedAt = now)
                )
            )
        }
    }

    private fun startMedicineTimerTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { current ->
                    current.copy(
                        medicineTimers = current.medicineTimers.map { timer ->
                            if (timer.isRinging) timer else timer.copy(remainingSeconds = (timer.remainingSeconds - 1L).coerceAtLeast(0L))
                        }
                    )
                }
            }
        }
    }

    private fun startDoseMaintenanceTicker() {
        viewModelScope.launch {
            while (true) {
                mergeDuplicateSnoozedDoses()
                generateUpcomingDoses()
                markOverdueDoses()
                notifyDueDoses()
                syncUpcomingDosesToDevice()
                delay(60_000L)
            }
        }
    }

    fun connect() {
        val settings = _settingsState.value
        _settingsState.update { it.copy(isConnecting = true, errorMessage = null) }
        _uiState.update { it.copy(isConnecting = true) }
        val clientId = "AndroidApp_${System.currentTimeMillis()}"
        mqttManager.connect(
            brokerUrl = settings.mqttBroker,
            clientId = clientId,
            deviceId = settings.deviceId,
            username = settings.mqttUsername,
            password = settings.mqttPassword,
            onConnected = {
                _settingsState.update { it.copy(isConnected = true, isConnecting = false) }
                _uiState.update { it.copy(isOnline = true, isConnecting = false, deviceId = settings.deviceId) }
            },
            onDisconnected = {
                _settingsState.update { it.copy(isConnected = false, isConnecting = false) }
                _uiState.update { it.copy(isOnline = false, isConnecting = false) }
            },
            onSensorDataReceived = { handleSensorData(it) },
            onStatusReceived = { handleDeviceStatus(it) },
            onCommandResponseReceived = { handleCommandResponse(it) },
            onAlertEventReceived = { handleAlertEvent(it) },
            onError = { error ->
                _settingsState.update { it.copy(isConnecting = false, errorMessage = error) }
                _uiState.update { it.copy(isConnecting = false) }
            }
        )
    }

    fun disconnect() {
        mqttManager.disconnect()
        _settingsState.update { it.copy(isConnected = false, isConnecting = false) }
        _uiState.update { it.copy(isOnline = false, isConnecting = false) }
    }

    fun updateSettings(mqttBroker: String, deviceId: String, username: String, password: String) {
        _settingsState.update {
            it.copy(mqttBroker = mqttBroker, deviceId = deviceId, mqttUsername = username, mqttPassword = password)
        }
        _uiState.update { it.copy(deviceId = deviceId) }
    }

    private fun handleSensorData(data: SensorData) {
        val alerts = mutableListOf<AlertItem>()
        latestEventAlert?.let { alerts.add(it) }
        if (data.state == "tilted" || data.state == "moving") {
            alerts.add(AlertItem(message = "药箱正在移动或倾斜", level = AlertLevel.ERROR))
        }
        _uiState.update {
            it.copy(
                sensorData = data,
                isOnline = true,
                isConnecting = false,
                lastUpdateTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                alerts = alerts.distinctBy { alert -> alert.message },
                buzzerEnabled = data.buzzerEnabled?.let { value -> value != 0 } ?: it.buzzerEnabled
            )
        }
        refreshSmartAnalysis()
    }

    private fun handleDeviceStatus(status: DeviceStatus) {
        _uiState.update {
            it.copy(
                deviceStatus = status,
                isOnline = status.isOnline(),
                buzzerEnabled = status.buzzerEnabled?.let { value -> value != 0 } ?: it.buzzerEnabled
            )
        }
        _settingsState.update { it.copy(isConnected = status.isOnline()) }
        refreshSmartAnalysis()
    }

    private fun handleCommandResponse(response: CommandResponse) {
        if (response.isSuccess()) {
            when (response.cmd) {
                "set_medicine_timer" -> {
                    addOrUpdateMedicineTimerFromResponse(response)
                    notificationManager?.notifyInfo("定时已设置", "服药提醒已更新")
                }
                "cancel_medicine_timer" -> {
                    if (response.timerId != null) removeMedicineTimer(response.timerId) else clearMedicineTimers()
                    notificationManager?.notifyInfo("定时已取消", "服药提醒已取消")
                }
                else -> notificationManager?.notifyInfo("命令成功", response.cmd)
            }
        } else {
            if (response.cmd == "set_medicine_timer") pendingMedicineTimerDraft = null
            notificationManager?.notifyInfo("命令失败", "${response.cmd}: ${response.getErrorCodeDescription()}")
        }
    }

    private fun handleAlertEvent(event: AlertEvent) {
        when (event.event) {
            AlertEvent.EVENT_MEDICINE_TIMER_ALARM -> {
                val timerId = event.timerId ?: 0
                var message = "服药时间到了"
                _uiState.update { current ->
                    val timer = current.medicineTimers.firstOrNull { it.timerId == timerId }
                    if (timer != null) {
                        message = "请服用 ${timer.medicineName}（${timer.boxId}号药盒）"
                    }
                    current.copy(
                        medicineTimers = current.medicineTimers.map { item ->
                            if (item.timerId == timerId) item.copy(remainingSeconds = 0L, isRinging = true) else item
                        }
                    )
                }
                latestEventAlert = AlertItem(message = message, level = AlertLevel.ERROR)
                notificationManager?.notifyInfo("服药提醒", message)
                recordAlert("服药提醒", message)
            }
            AlertEvent.EVENT_MEDICINE_TIMER_CANCELLED -> {
                event.timerId?.let { removeMedicineTimer(it) }
                val message = "服药提醒已停止"
                latestEventAlert = AlertItem(message = message, level = AlertLevel.INFO)
                notificationManager?.notifyInfo("定时已停止", message)
                recordAlert("定时已停止", "source=${event.source ?: "unknown"}")
            }
            AlertEvent.EVENT_DROP_ALARM_CANCELLED -> {
                latestEventAlert = AlertItem(message = "报警已取消", level = AlertLevel.INFO)
                notificationManager?.clearTiltedAlert()
            }
            AlertEvent.EVENT_ENV_RECOVERED -> {
                latestEventAlert = AlertItem(message = "环境已恢复正常", level = AlertLevel.INFO)
            }
            AlertEvent.EVENT_ENV_ABNORMAL -> {
                latestEventAlert = AlertItem(message = "环境异常", level = AlertLevel.ERROR)
            }
            AlertEvent.EVENT_DROP_DETECTED -> {
                latestEventAlert = AlertItem(message = "检测到跌落", level = AlertLevel.ERROR)
            }
        }
        _uiState.update { current ->
            val merged = buildList {
                latestEventAlert?.let { add(it) }
                addAll(current.alerts)
            }.distinctBy { it.message }
            current.copy(alerts = merged)
        }
        refreshSmartAnalysis()
    }

    private fun addOrUpdateMedicineTimerFromResponse(response: CommandResponse) {
        val timerId = response.timerId ?: return
        if (timerId !in 1..MAX_MEDICINE_TIMERS) return
        val draft = pendingTimerDrafts.remove(timerId) ?: pendingMedicineTimerDraft
        if (draft == null) return
        pendingMedicineTimerDraft = null
        val item = MedicineTimerUiItem(
            timerId = timerId,
            boxId = draft.boxId,
            medicineName = draft.medicineName,
            mode = response.mode ?: "countdown",
            targetHour = response.targetHour ?: 0,
            targetMinute = response.targetMinute ?: 0,
            targetSecond = response.targetSecond ?: 0,
            remainingSeconds = response.remainingSeconds ?: 0L,
            doseId = draft.doseId
        )
        _uiState.update { current ->
            val withoutOld = current.medicineTimers.filterNot { it.timerId == timerId }
            current.copy(medicineTimers = (withoutOld + item).sortedBy { it.timerId }.take(MAX_MEDICINE_TIMERS))
        }
    }

    private fun removeMedicineTimer(timerId: Int) {
        _uiState.update { current -> current.copy(medicineTimers = current.medicineTimers.filterNot { it.timerId == timerId }) }
    }

    private fun clearMedicineTimers() {
        _uiState.update { it.copy(medicineTimers = emptyList()) }
    }

    fun publishNow() {
        mqttManager.publishCommand(_settingsState.value.deviceId, "publish_now")
    }

    fun resetDevice() {
        mqttManager.publishCommand(_settingsState.value.deviceId, "reset")
    }

    fun setInterval(interval: Int) {
        mqttManager.publishCommand(_settingsState.value.deviceId, "set_interval", interval)
    }

    fun setEnvironmentRated(temperature: Double, humidity: Double) {
        mqttManager.publishCommand(
            deviceId = _settingsState.value.deviceId,
            cmd = "set_env_rated",
            extraParams = mapOf("temperature" to temperature, "humidity" to humidity)
        )
    }

    fun setBuzzerEnabled(enabled: Boolean) {
        val ok = mqttManager.publishCommand(
            deviceId = _settingsState.value.deviceId,
            cmd = "set_buzzer_enable",
            value = if (enabled) 1 else 0
        )
        if (ok) _uiState.update { it.copy(buzzerEnabled = enabled) }
    }

    fun addMedicinePlan(
        boxId: Int,
        medicineName: String,
        doseAmount: Int,
        hour: Int,
        minute: Int,
        repeatType: String,
        daysOfWeek: String
    ) {
        val compartment = _uiState.value.medicineCompartments.firstOrNull { it.boxId == boxId && it.active }
        if (boxId !in 1..MEDICINE_COMPARTMENT_COUNT || medicineName.isBlank() || compartment == null) return
        viewModelScope.launch {
            val plan = MedicinePlanEntity(
                boxId = boxId,
                medicineName = medicineName.trim(),
                doseAmount = doseAmount.coerceAtLeast(1),
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                repeatType = repeatType,
                daysOfWeek = daysOfWeek,
                startDate = dateKey(System.currentTimeMillis())
            )
            medicinePlanDao.insert(plan)
            generateUpcomingDoses()
            syncUpcomingDosesToDevice()
        }
    }

    fun setPlanEnabled(planId: Long, enabled: Boolean) {
        viewModelScope.launch {
            medicinePlanDao.setEnabled(planId, enabled)
            if (!enabled) {
                medicineDoseDao.markPlanDoses(
                    planId = planId,
                    newStatus = MedicineDoseEntity.STATUS_SKIPPED,
                    statuses = listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_SNOOZED),
                    reason = "服药计划已关闭"
                )
            }
            generateUpcomingDoses()
            syncUpcomingDosesToDevice()
        }
    }

    fun updateMedicinePlan(
        planId: Long,
        boxId: Int,
        medicineName: String,
        doseAmount: Int,
        hour: Int,
        minute: Int,
        repeatType: String,
        daysOfWeek: String
    ) {
        val compartment = _uiState.value.medicineCompartments.firstOrNull { it.boxId == boxId && it.active }
        if (medicineName.isBlank() || compartment == null) return
        viewModelScope.launch {
            val existing = medicinePlanDao.getById(planId) ?: return@launch
            medicinePlanDao.update(
                existing.copy(
                    boxId = boxId,
                    medicineName = medicineName.trim(),
                    doseAmount = doseAmount.coerceAtLeast(1),
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                    repeatType = repeatType,
                    daysOfWeek = daysOfWeek
                )
            )
            medicineDoseDao.markPlanDoses(
                planId = planId,
                newStatus = MedicineDoseEntity.STATUS_SKIPPED,
                statuses = listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_SNOOZED),
                reason = "服药计划已修改"
            )
            generateUpcomingDoses()
            syncUpcomingDosesToDevice()
        }
    }

    fun deleteMedicinePlan(planId: Long) {
        viewModelScope.launch {
            val plan = medicinePlanDao.getById(planId) ?: return@launch
            medicinePlanDao.delete(plan)
            medicineDoseDao.markPlanDoses(
                planId = planId,
                newStatus = MedicineDoseEntity.STATUS_SKIPPED,
                statuses = listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_SNOOZED),
                reason = "服药计划已删除"
            )
            generateUpcomingDoses()
            syncUpcomingDosesToDevice()
        }
    }

    fun markDoseTaken(doseId: Long) {
        viewModelScope.launch {
            val dose = medicineDoseDao.getById(doseId) ?: return@launch
            val now = System.currentTimeMillis()
            val delayed = now - dose.scheduledAt > 10L * 60L * 1000L
            medicineDoseDao.update(
                dose.copy(
                    status = MedicineDoseEntity.STATUS_TAKEN,
                    completedAt = now,
                    actualTakenAt = now,
                    riskLevel = if (delayed) 1 else 0,
                    smartReason = if (delayed) "超过计划时间 10 分钟后确认" else "按时服用",
                    timerId = null
                )
            )
            notifiedDoseIds.remove(dose.doseId)
            medicineCompartmentDao.decrementStock(dose.boxId, dose.doseAmount)
            notificationManager?.notifyInfo("服药已记录", "已记录服用 ${dose.medicineName}")
            syncUpcomingDosesToDevice()
        }
    }

    fun markDoseSkipped(doseId: Long) {
        viewModelScope.launch {
            val dose = medicineDoseDao.getById(doseId) ?: return@launch
            medicineDoseDao.update(
                dose.copy(
                    status = MedicineDoseEntity.STATUS_SKIPPED,
                    completedAt = System.currentTimeMillis(),
                    riskLevel = 2,
                    smartReason = "用户主动跳过",
                    timerId = null,
                    note = "用户跳过"
                )
            )
            notifiedDoseIds.remove(dose.doseId)
            syncUpcomingDosesToDevice()
        }
    }

    fun snoozeDose(doseId: Long) {
        viewModelScope.launch {
            val dose = medicineDoseDao.getById(doseId) ?: return@launch
            val snoozedAt = System.currentTimeMillis() + SNOOZE_DELAY_MS
            medicineDoseDao.update(
                dose.copy(
                    scheduledAt = snoozedAt,
                    status = MedicineDoseEntity.STATUS_PENDING,
                    completedAt = null,
                    timerId = null,
                    reminderCount = dose.reminderCount + 1,
                    riskLevel = 1,
                    smartReason = "用户选择稍后提醒"
                )
            )
            notifiedDoseIds.remove(dose.doseId)
            mergeDuplicateSnoozedDoses()
            syncUpcomingDosesToDevice()
        }
    }

    private suspend fun mergeDuplicateSnoozedDoses() {
        val candidates = medicineDoseDao.getByStatuses(
            listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_SNOOZED)
        ).filter {
            it.reminderCount > 0 || it.smartReason.contains("稍后") || it.note.contains("稍后")
        }
        candidates.groupBy { dose -> "${dose.planId}:${dateKey(dose.scheduledAt)}" }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val keep = group.maxByOrNull { it.scheduledAt } ?: return@forEach
                group.filterNot { it.doseId == keep.doseId }.forEach { duplicate ->
                    medicineDoseDao.update(
                        duplicate.copy(
                            status = MedicineDoseEntity.STATUS_SKIPPED,
                            timerId = null,
                            completedAt = System.currentTimeMillis(),
                            riskLevel = 0,
                            smartReason = "重复稍后提醒已合并",
                            note = "重复稍后提醒已合并"
                        )
                    )
                }
            }
    }

    private suspend fun generateUpcomingDoses() {
        val plans = medicinePlanDao.getEnabled()
        val activeBoxIds = medicineCompartmentDao.getActive().map { it.boxId }.toSet()
        val todayStart = startOfTodayMillis()
        val blockingStatuses = listOf(
            MedicineDoseEntity.STATUS_PENDING,
            MedicineDoseEntity.STATUS_SNOOZED,
            MedicineDoseEntity.STATUS_TAKEN,
            MedicineDoseEntity.STATUS_MISSED
        )
        plans.filter { it.boxId in activeBoxIds }.forEach { plan ->
            for (dayOffset in 0..7) {
                val scheduledAt = scheduledMillis(todayStart, dayOffset, plan.hour, plan.minute)
                val dayStart = todayStart + dayOffset * 24L * 60L * 60L * 1000L
                val dayEnd = dayStart + 24L * 60L * 60L * 1000L
                if (scheduledAt < System.currentTimeMillis() - 60_000L) continue
                if (!planMatchesDay(plan, scheduledAt)) continue
                if (medicineDoseDao.findBlockingForPlanDay(plan.planId, dayStart, dayEnd, blockingStatuses) == null) {
                    medicineDoseDao.insert(
                        MedicineDoseEntity(
                            planId = plan.planId,
                            boxId = plan.boxId,
                            medicineName = plan.medicineName,
                            doseAmount = plan.doseAmount,
                            scheduledAt = scheduledAt
                        )
                    )
                }
                if (plan.repeatType == "once") break
            }
        }
    }

    private suspend fun notifyDueDoses() {
        val now = System.currentTimeMillis()
        val dueDoses = medicineDoseDao.getByStatuses(
            listOf(MedicineDoseEntity.STATUS_PENDING, MedicineDoseEntity.STATUS_MISSED)
        ).filter { it.status == MedicineDoseEntity.STATUS_PENDING && it.scheduledAt <= now && now - it.scheduledAt < OVERDUE_DELAY_MS }
        dueDoses.forEach { dose ->
            if (notifiedDoseIds.add(dose.doseId)) {
                notificationManager?.notifyMedicineDoseReminder(
                    doseId = dose.doseId,
                    medicineName = dose.medicineName,
                    boxId = dose.boxId,
                    doseAmount = dose.doseAmount
                )
            }
        }
        val activeDueIds = dueDoses.map { it.doseId }.toSet()
        notifiedDoseIds.retainAll(activeDueIds)
    }

    private suspend fun markOverdueDoses() {
        val overdueBefore = System.currentTimeMillis() - OVERDUE_DELAY_MS
        medicineDoseDao.markOverdue(
            oldStatus = MedicineDoseEntity.STATUS_PENDING,
            newStatus = MedicineDoseEntity.STATUS_MISSED,
            before = overdueBefore
        )
    }

    private suspend fun syncUpcomingDosesToDevice() {
        val activeBoxIds = medicineCompartmentDao.getActive().map { it.boxId }.toSet()
        val enabledPlanIds = medicinePlanDao.getEnabled().filter { it.boxId in activeBoxIds }.map { it.planId }.toSet()
        val manualTimerIds = _uiState.value.medicineTimers
            .filter { it.doseId == null }
            .map { it.timerId }
            .toSet()
        val freeTimerIds = (1..MAX_MEDICINE_TIMERS).filterNot { it in manualTimerIds }
        val upcoming = medicineDoseDao.getUpcoming(from = System.currentTimeMillis(), limit = 30)
            .filter { it.boxId in activeBoxIds && it.planId in enabledPlanIds }
            .take(freeTimerIds.size)
        medicineDoseDao.clearPendingTimerIds()
        pendingTimerDrafts.entries.removeAll { it.value.doseId != null }
        val planTimers = upcoming.mapIndexed { index, dose ->
            val target = Calendar.getInstance().apply { timeInMillis = dose.scheduledAt }
            MedicineTimerUiItem(
                timerId = freeTimerIds[index],
                boxId = dose.boxId,
                medicineName = dose.medicineName,
                mode = "clock",
                targetHour = target.get(Calendar.HOUR_OF_DAY),
                targetMinute = target.get(Calendar.MINUTE),
                targetSecond = target.get(Calendar.SECOND),
                remainingSeconds = ((dose.scheduledAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
                doseId = dose.doseId
            )
        }
        val newPlanTimerIds = planTimers.map { it.timerId }.toSet()
        _uiState.update {
            it.copy(
                medicineTimers = (it.medicineTimers.filter { timer -> timer.doseId == null } + planTimers)
                    .sortedBy { timer -> timer.timerId }
            )
        }
        if (!mqttManager.isConnected()) return
        (lastSyncedPlanTimerIds - newPlanTimerIds).forEach { timerId ->
            mqttManager.publishCommand(
                deviceId = _settingsState.value.deviceId,
                cmd = "cancel_medicine_timer",
                extraParams = mapOf("timer_id" to timerId)
            )
        }
        upcoming.zip(freeTimerIds).forEach { (dose, timerId) ->
            val target = Calendar.getInstance().apply { timeInMillis = dose.scheduledAt }
            pendingTimerDrafts[timerId] = MedicineTimerDraft(dose.boxId, dose.medicineName, dose.doseId)
            mqttManager.publishCommand(
                deviceId = _settingsState.value.deviceId,
                cmd = "set_medicine_timer",
                extraParams = mapOf(
                    "timer_id" to timerId,
                    "mode" to "clock",
                    "hour" to target.get(Calendar.HOUR_OF_DAY),
                    "minute" to target.get(Calendar.MINUTE),
                    "second" to target.get(Calendar.SECOND),
                    "now_hour" to Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    "now_minute" to Calendar.getInstance().get(Calendar.MINUTE),
                    "now_second" to Calendar.getInstance().get(Calendar.SECOND)
                )
            )
            medicineDoseDao.update(dose.copy(timerId = timerId))
        }
        lastSyncedPlanTimerIds = newPlanTimerIds
    }

    fun setMedicineTimer(boxId: Int, mode: String, hour: Int, minute: Int, second: Int) {
        val timerId = (1..MAX_MEDICINE_TIMERS).firstOrNull { id -> _uiState.value.medicineTimers.none { it.timerId == id } }
        if (timerId == null) {
            notificationManager?.notifyInfo("定时已满", "最多只能同时同步 5 个设备定时")
            return
        }
        if (pendingMedicineTimerDraft != null) {
            notificationManager?.notifyInfo("请稍等", "上一条定时正在等待设备确认")
            return
        }
        val compartment = _uiState.value.medicineCompartments.firstOrNull { it.boxId == boxId && it.active }
        if (compartment == null) {
            notificationManager?.notifyInfo("药盒无效", "请选择药盒")
            return
        }
        val params = mutableMapOf<String, Any?>(
            "timer_id" to timerId,
            "mode" to mode,
            "hour" to hour,
            "minute" to minute,
            "second" to second
        )
        if (mode == "clock") {
            val calendar = java.util.Calendar.getInstance()
            params["now_hour"] = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            params["now_minute"] = calendar.get(java.util.Calendar.MINUTE)
            params["now_second"] = calendar.get(java.util.Calendar.SECOND)
        }
        val ok = mqttManager.publishCommand(_settingsState.value.deviceId, "set_medicine_timer", extraParams = params)
        if (ok) {
            pendingMedicineTimerDraft = null
            pendingTimerDrafts[timerId] = MedicineTimerDraft(compartment.boxId, compartment.name)
            val target = "%02d:%02d:%02d".format(hour, minute, second)
            notificationManager?.notifyInfo("定时命令已发送", "${compartment.name} $target")
        }
    }

    fun cancelMedicineTimer(timerId: Int? = null) {
        val ok = mqttManager.publishCommand(
            deviceId = _settingsState.value.deviceId,
            cmd = "cancel_medicine_timer",
            extraParams = mapOf("timer_id" to timerId)
        )
        if (ok) {
            if (timerId != null) removeMedicineTimer(timerId) else clearMedicineTimers()
            if (timerId == null) {
                lastSyncedPlanTimerIds = emptySet()
            } else {
                lastSyncedPlanTimerIds = lastSyncedPlanTimerIds - timerId
            }
        }
    }

    private fun recordAlert(type: String, message: String) {
        alertHistory.add(AlertRecord(type, message, System.currentTimeMillis()))
    }

    fun getAlertHistory(): List<AlertRecord> = alertHistory.toList()

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

data class AlertRecord(
    val type: String,
    val message: String,
    val timestamp: Long
) {
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

private data class MedicineTimerDraft(
    val boxId: Int,
    val medicineName: String,
    val doseId: Long? = null
)

private fun MedicineCompartmentEntity.toUiItem(): MedicineCompartmentUiItem = MedicineCompartmentUiItem(
    boxId = boxId,
    name = name,
    sortOrder = sortOrder,
    stock = stock,
    dosePerUse = dosePerUse,
    lowStockThreshold = lowStockThreshold,
    active = active
)

private fun MedicinePlanEntity.toUiItem(): MedicinePlanUiItem = MedicinePlanUiItem(
    planId = planId,
    boxId = boxId,
    medicineName = medicineName,
    doseAmount = doseAmount,
    hour = hour,
    minute = minute,
    repeatType = repeatType,
    daysOfWeek = daysOfWeek,
    enabled = enabled
)

private fun MedicineDoseEntity.toUiItem(): MedicineDoseUiItem = MedicineDoseUiItem(
    doseId = doseId,
    planId = planId,
    boxId = boxId,
    medicineName = medicineName,
    doseAmount = doseAmount,
    scheduledAt = scheduledAt,
    status = status,
    timerId = timerId
)

private fun startOfTodayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun scheduledMillis(todayStart: Long, dayOffset: Int, hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = todayStart + dayOffset * 24L * 60L * 60L * 1000L
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun planMatchesDay(plan: MedicinePlanEntity, scheduledAt: Long): Boolean {
    return when (plan.repeatType) {
        "once" -> dateKey(scheduledAt) == plan.startDate
        "weekly" -> {
            val calendar = Calendar.getInstance().apply { timeInMillis = scheduledAt }
            val mondayBased = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> 7
            }
            plan.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(mondayBased)
        }
        else -> true
    }
}

private fun dateKey(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}
