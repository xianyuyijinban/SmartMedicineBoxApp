package com.example.smartmedicinebox.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmedicinebox.R
import com.example.smartmedicinebox.databinding.ActivitySettingsBinding

/**
 * 设置界面Activity
 * 用于配置MQTT连接参数和告警阈值
 */
class SettingsActivity : AppCompatActivity() {

    // ViewBinding
    private lateinit var binding: ActivitySettingsBinding
    
    // SharedPreferences
    private lateinit var prefs: SharedPreferences
    
    companion object {
        // SharedPreferences文件名
        const val PREFS_NAME = "smart_medicine_box_prefs"
        
        // 配置项键名
        const val KEY_MQTT_BROKER = "mqtt_broker"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_REPORT_INTERVAL = "report_interval"
        const val KEY_TEMP_HIGH = "temp_high"
        const val KEY_TEMP_LOW = "temp_low"
        const val KEY_HUMIDITY_HIGH = "humidity_high"
        const val KEY_HUMIDITY_LOW = "humidity_low"
        
        // 默认值
        const val DEFAULT_MQTT_BROKER = "192.168.1.100"
        const val DEFAULT_DEVICE_ID = "medicine_box_001"
        const val DEFAULT_REPORT_INTERVAL = 5
        const val DEFAULT_TEMP_HIGH = 30.0f
        const val DEFAULT_TEMP_LOW = 10.0f
        const val DEFAULT_HUMIDITY_HIGH = 70.0f
        const val DEFAULT_HUMIDITY_LOW = 30.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 设置工具栏
        setupToolbar()
        
        // 加载当前设置
        loadSettings()
        
        // 设置按钮点击事件
        setupClickListeners()
    }
    
    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // 返回按钮点击事件
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    /**
     * 加载当前设置到界面
     */
    private fun loadSettings() {
        // MQTT设置
        binding.etMqttBroker.setText(prefs.getString(KEY_MQTT_BROKER, DEFAULT_MQTT_BROKER))
        binding.etDeviceId.setText(prefs.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID))
        binding.etReportInterval.setText(
            prefs.getInt(KEY_REPORT_INTERVAL, DEFAULT_REPORT_INTERVAL).toString()
        )
        
        // 温度阈值
        binding.etTempHigh.setText(
            prefs.getFloat(KEY_TEMP_HIGH, DEFAULT_TEMP_HIGH).toString()
        )
        binding.etTempLow.setText(
            prefs.getFloat(KEY_TEMP_LOW, DEFAULT_TEMP_LOW).toString()
        )
        
        // 湿度阈值
        binding.etHumidityHigh.setText(
            prefs.getFloat(KEY_HUMIDITY_HIGH, DEFAULT_HUMIDITY_HIGH).toString()
        )
        binding.etHumidityLow.setText(
            prefs.getFloat(KEY_HUMIDITY_LOW, DEFAULT_HUMIDITY_LOW).toString()
        )
    }
    
    /**
     * 设置按钮点击事件
     */
    private fun setupClickListeners() {
        // 保存按钮
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveSettings()
                showToast("设置已保存")
                finish()
            }
        }
        
        // 取消按钮
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 验证输入是否合法
     */
    private fun validateInputs(): Boolean {
        // 验证MQTT Broker地址
        val broker = binding.etMqttBroker.text.toString().trim()
        if (broker.isEmpty()) {
            binding.tilMqttBroker.error = "请输入MQTT Broker地址"
            return false
        }
        binding.tilMqttBroker.error = null
        
        // 验证设备ID
        val deviceId = binding.etDeviceId.text.toString().trim()
        if (deviceId.isEmpty()) {
            binding.tilDeviceId.error = "请输入设备ID"
            return false
        }
        binding.tilDeviceId.error = null
        
        // 验证上报间隔
        val intervalStr = binding.etReportInterval.text.toString().trim()
        val interval = intervalStr.toIntOrNull()
        if (interval == null || interval < 1 || interval > 3600) {
            binding.tilReportInterval.error = "请输入1-3600之间的整数"
            return false
        }
        binding.tilReportInterval.error = null
        
        // 验证温度阈值
        val tempHigh = binding.etTempHigh.text.toString().toFloatOrNull()
        val tempLow = binding.etTempLow.text.toString().toFloatOrNull()
        if (tempHigh == null || tempLow == null || tempHigh <= tempLow) {
            binding.tilTempHigh.error = "高温阈值必须大于低温阈值"
            binding.tilTempLow.error = "低温阈值必须小于高温阈值"
            return false
        }
        binding.tilTempHigh.error = null
        binding.tilTempLow.error = null
        
        // 验证湿度阈值
        val humidityHigh = binding.etHumidityHigh.text.toString().toFloatOrNull()
        val humidityLow = binding.etHumidityLow.text.toString().toFloatOrNull()
        if (humidityHigh == null || humidityLow == null || 
            humidityHigh <= humidityLow || 
            humidityHigh > 100 || humidityLow < 0) {
            binding.tilHumidityHigh.error = "请输入0-100之间的有效数值，且高湿大于低湿"
            binding.tilHumidityLow.error = "请输入0-100之间的有效数值，且低湿小于高湿"
            return false
        }
        binding.tilHumidityHigh.error = null
        binding.tilHumidityLow.error = null
        
        return true
    }
    
    /**
     * 保存设置到SharedPreferences
     */
    private fun saveSettings() {
        prefs.edit().apply {
            // MQTT设置
            putString(KEY_MQTT_BROKER, binding.etMqttBroker.text.toString().trim())
            putString(KEY_DEVICE_ID, binding.etDeviceId.text.toString().trim())
            putInt(KEY_REPORT_INTERVAL, binding.etReportInterval.text.toString().toInt())
            
            // 温度阈值
            putFloat(KEY_TEMP_HIGH, binding.etTempHigh.text.toString().toFloat())
            putFloat(KEY_TEMP_LOW, binding.etTempLow.text.toString().toFloat())
            
            // 湿度阈值
            putFloat(KEY_HUMIDITY_HIGH, binding.etHumidityHigh.text.toString().toFloat())
            putFloat(KEY_HUMIDITY_LOW, binding.etHumidityLow.text.toString().toFloat())
            
            apply()
        }
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 获取配置值的静态方法（供其他组件使用）
     */
    object Config {
        
        fun getMqttBroker(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_MQTT_BROKER, DEFAULT_MQTT_BROKER) ?: DEFAULT_MQTT_BROKER
        }
        
        fun getDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID) ?: DEFAULT_DEVICE_ID
        }
        
        fun getReportInterval(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_REPORT_INTERVAL, DEFAULT_REPORT_INTERVAL)
        }
        
        fun getTempHigh(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_TEMP_HIGH, DEFAULT_TEMP_HIGH)
        }
        
        fun getTempLow(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_TEMP_LOW, DEFAULT_TEMP_LOW)
        }
        
        fun getHumidityHigh(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_HUMIDITY_HIGH, DEFAULT_HUMIDITY_HIGH)
        }
        
        fun getHumidityLow(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_HUMIDITY_LOW, DEFAULT_HUMIDITY_LOW)
        }
    }
}
