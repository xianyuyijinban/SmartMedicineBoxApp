package com.example.smartmedicinebox.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smartmedicinebox.R
import com.example.smartmedicinebox.data.model.BoxState
import com.example.smartmedicinebox.data.model.ConnectionStatus
import com.example.smartmedicinebox.databinding.ActivityMainBinding
import com.example.smartmedicinebox.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面Activity
 * 显示智能药箱的实时监控数据，包括环境数据和运动状态
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding
    private lateinit var binding: ActivityMainBinding
    
    // ViewModel
    private val viewModel: MainViewModel by viewModels()
    
    // 时间格式化
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置工具栏
        setupToolbar()
        
        // 初始化UI
        initUI()
        
        // 绑定ViewModel
        bindViewModel()
        
        // 设置按钮点击事件
        setupClickListeners()
    }
    
    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }
    
    /**
     * 初始化UI状态
     */
    private fun initUI() {
        // 初始显示未连接状态
        updateConnectionStatus(ConnectionStatus.DISCONNECTED)
        resetSensorData()
    }
    
    /**
     * 绑定ViewModel数据
     */
    private fun bindViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 收集连接状态
                launch {
                    viewModel.connectionStatus.collect { status ->
                        updateConnectionStatus(status)
                    }
                }
                
                // 收集传感器数据
                launch {
                    viewModel.sensorData.collect { data ->
                        updateSensorData(data)
                    }
                }
                
                // 收集药箱状态
                launch {
                    viewModel.boxState.collect { state ->
                        updateBoxState(state)
                    }
                }
                
                // 收集振动状态
                launch {
                    viewModel.vibrationStatus.collect { status ->
                        updateVibrationStatus(status)
                    }
                }
                
                // 收集倾斜状态
                launch {
                    viewModel.tiltStatus.collect { status ->
                        updateTiltStatus(status)
                    }
                }
                
                // 收集加载状态
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                // 收集错误消息
                launch {
                    viewModel.errorMessage.collect { message ->
                        message?.let {
                            showToast(it)
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 设置按钮点击事件
     */
    private fun setupClickListeners() {
        // 刷新按钮
        binding.btnRefresh.setOnClickListener {
            viewModel.refreshData()
        }
        
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            navigateToSettings()
        }
        
        // 状态卡片点击 - 连接/断开
        binding.cardConnectionStatus.setOnClickListener {
            when (viewModel.connectionStatus.value) {
                ConnectionStatus.CONNECTED -> showDisconnectConfirmDialog()
                ConnectionStatus.DISCONNECTED -> viewModel.connect()
                else -> { /* 连接中，不处理 */ }
            }
        }
    }
    
    /**
     * 更新连接状态UI
     */
    private fun updateConnectionStatus(status: ConnectionStatus) {
        val context = this
        when (status) {
            ConnectionStatus.CONNECTED -> {
                binding.tvConnectionStatus.text = getString(R.string.status_online)
                binding.viewStatusIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(context, R.color.status_online)
                binding.cardConnectionStatus.strokeColor = 
                    ContextCompat.getColor(context, R.color.status_online)
                binding.tvLastUpdate.text = timeFormat.format(Date())
            }
            ConnectionStatus.DISCONNECTED -> {
                binding.tvConnectionStatus.text = getString(R.string.status_offline)
                binding.viewStatusIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(context, R.color.status_offline)
                binding.cardConnectionStatus.strokeColor = 
                    ContextCompat.getColor(context, R.color.status_offline)
            }
            ConnectionStatus.CONNECTING -> {
                binding.tvConnectionStatus.text = getString(R.string.status_connecting)
                binding.viewStatusIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(context, R.color.status_connecting)
                binding.cardConnectionStatus.strokeColor = 
                    ContextCompat.getColor(context, R.color.status_connecting)
            }
            ConnectionStatus.ERROR -> {
                binding.tvConnectionStatus.text = getString(R.string.status_error)
                binding.viewStatusIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(context, R.color.status_offline)
                binding.cardConnectionStatus.strokeColor = 
                    ContextCompat.getColor(context, R.color.status_offline)
            }
        }
    }
    
    /**
     * 更新传感器数据显示
     */
    private fun updateSensorData(data: MainViewModel.SensorDataDisplay) {
        binding.tvTemperature.text = String.format(Locale.getDefault(), "%.1f", data.temperature)
        binding.tvHumidity.text = String.format(Locale.getDefault(), "%.1f", data.humidity)
        binding.tvPressure.text = String.format(Locale.getDefault(), "%.2f", data.pressure)
        binding.tvAltitude.text = String.format(Locale.getDefault(), "%.0f", data.altitude)
        
        // 更新最后更新时间
        binding.tvLastUpdate.text = timeFormat.format(Date())
        
        // 根据阈值更新温度卡片颜色
        updateTemperatureCardColor(data.temperature, data.tempHigh, data.tempLow)
        
        // 根据阈值更新湿度卡片颜色
        updateHumidityCardColor(data.humidity, data.humidityHigh, data.humidityLow)
    }
    
    /**
     * 根据温度值更新温度卡片颜色
     */
    private fun updateTemperatureCardColor(temp: Float, high: Float, low: Float) {
        val colorRes = when {
            temp > high -> R.color.status_warning
            temp < low -> R.color.md_theme_secondary
            else -> R.color.text_primary
        }
        binding.tvTemperature.setTextColor(ContextCompat.getColor(this, colorRes))
    }
    
    /**
     * 根据湿度值更新湿度卡片颜色
     */
    private fun updateHumidityCardColor(humidity: Float, high: Float, low: Float) {
        val colorRes = when {
            humidity > high -> R.color.status_warning
            humidity < low -> R.color.md_theme_secondary
            else -> R.color.text_primary
        }
        binding.tvHumidity.setTextColor(ContextCompat.getColor(this, colorRes))
    }
    
    /**
     * 更新药箱状态显示
     */
    private fun updateBoxState(state: BoxState) {
        val (textRes, colorRes) = when (state) {
            BoxState.CLOSED -> R.string.box_state_closed to R.color.box_state_closed
            BoxState.OPENED -> R.string.box_state_opened to R.color.box_state_opened
            BoxState.MOVING -> R.string.box_state_moving to R.color.box_state_moving
            BoxState.TILTED -> R.string.box_state_tilted to R.color.box_state_tilted
            BoxState.UNKNOWN -> R.string.box_state_unknown to R.color.text_secondary
        }
        
        binding.tvBoxStatus.text = getString(textRes)
        binding.tvBoxStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }
    
    /**
     * 更新振动状态显示
     */
    private fun updateVibrationStatus(isNormal: Boolean) {
        if (isNormal) {
            binding.tvVibrationStatus.text = getString(R.string.status_normal)
            binding.tvVibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_online))
        } else {
            binding.tvVibrationStatus.text = getString(R.string.status_abnormal)
            binding.tvVibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
        }
    }
    
    /**
     * 更新倾斜状态显示
     */
    private fun updateTiltStatus(isNormal: Boolean) {
        if (isNormal) {
            binding.tvTiltStatus.text = getString(R.string.status_normal)
            binding.tvTiltStatus.setTextColor(ContextCompat.getColor(this, R.color.status_online))
        } else {
            binding.tvTiltStatus.text = getString(R.string.status_abnormal)
            binding.tvTiltStatus.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
        }
    }
    
    /**
     * 重置传感器数据显示
     */
    private fun resetSensorData() {
        binding.tvTemperature.text = "--.-"
        binding.tvHumidity.text = "--.-"
        binding.tvPressure.text = "----.--"
        binding.tvAltitude.text = "---"
    }
    
    /**
     * 导航到设置界面
     */
    private fun navigateToSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 显示断开连接确认对话框
     */
    private fun showDisconnectConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("断开连接")
            .setMessage("确定要断开与设备的连接吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.disconnect()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                navigateToSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 页面恢复时自动刷新数据
        viewModel.refreshData()
    }
}
