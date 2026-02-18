package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmedicine.data.db.SensorDataEntity
import com.smartmedicine.repository.HistoryRepository
import com.smartmedicine.ui.components.*
import com.smartmedicine.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史数据屏幕
 * 
 * 展示温度/湿度的历史趋势图和统计数据
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    deviceId: String,
    historyRepository: HistoryRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // 历史数据状态
    var historyData by remember { mutableStateOf<List<SensorDataEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 统计数据
    var tempStats by remember { mutableStateOf<TempHumStats?>(null) }
    var humidityStats by remember { mutableStateOf<TempHumStats?>(null) }
    
    // 时间范围选择
    var selectedHours by remember { mutableStateOf(24) }
    
    // 加载数据
    LaunchedEffect(deviceId, selectedHours) {
        isLoading = true
        scope.launch {
            // 获取历史数据
            historyData = historyRepository.getHistoryByTimeRange(deviceId, selectedHours)
            
            // 计算统计数据
            val temps = historyData.mapNotNull { it.temperature }
            if (temps.isNotEmpty()) {
                tempStats = TempHumStats(
                    current = temps.last(),
                    min = temps.minOrNull() ?: 0.0,
                    max = temps.maxOrNull() ?: 0.0,
                    avg = temps.average()
                )
            }
            
            val humidities = historyData.mapNotNull { it.humidity }
            if (humidities.isNotEmpty()) {
                humidityStats = TempHumStats(
                    current = humidities.last(),
                    min = humidities.minOrNull() ?: 0.0,
                    max = humidities.maxOrNull() ?: 0.0,
                    avg = humidities.average()
                )
            }
            
            isLoading = false
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("历史数据")
                        Text(
                            text = deviceId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
            
            // 时间范围选择器
            TimeRangeSelector(
                selectedHours = selectedHours,
                onTimeRangeSelected = { selectedHours = it }
            )
            
            if (isLoading) {
                // 加载中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (historyData.isEmpty()) {
                // 无数据
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📊 暂无数据",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "连接设备后数据将自动记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 数据点数量
                Text(
                    text = "数据点数: ${historyData.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 温度趋势图
                if (tempStats != null) {
                    TemperatureMiniChart(
                        data = historyData.mapNotNull { it.temperature },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // 温度统计
                    DataStatisticsCard(
                        title = "温度统计",
                        icon = "🌡️",
                        value = tempStats!!.current,
                        min = tempStats!!.min,
                        max = tempStats!!.max,
                        avg = tempStats!!.avg,
                        unit = "°C",
                        color = TemperatureColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 湿度趋势图（简化版）
                if (humidityStats != null) {
                    HumidityMiniChart(
                        data = historyData.mapNotNull { it.humidity },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // 湿度统计
                    DataStatisticsCard(
                        title = "湿度统计",
                        icon = "💧",
                        value = humidityStats!!.current,
                        min = humidityStats!!.min,
                        max = humidityStats!!.max,
                        avg = humidityStats!!.avg,
                        unit = "%",
                        color = HumidityColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 数据列表预览
                HistoryDataPreview(
                    data = historyData.take(5),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 时间范围选择器
 */
@Composable
private fun TimeRangeSelector(
    selectedHours: Int,
    onTimeRangeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeRanges = listOf(
        1 to "1小时",
        6 to "6小时",
        12 to "12小时",
        24 to "24小时",
        72 to "3天"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "时间范围",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeRanges.forEach { (hours, label) ->
                    FilterChip(
                        selected = selectedHours == hours,
                        onClick = { onTimeRangeSelected(hours) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

/**
 * 湿度趋势迷你图
 */
@Composable
private fun HumidityMiniChart(
    data: List<Double>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val minHum = 0.0
    val maxHum = 100.0
    val range = maxHum - minHum
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = HumidityColor.copy(alpha = 0.1f)
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "💧 湿度趋势",
                style = MaterialTheme.typography.labelMedium,
                color = HumidityColor,
                modifier = Modifier.align(Alignment.TopStart)
            )
            
            Text(
                text = "${String.format("%.1f", data.last())}%",
                style = MaterialTheme.typography.headlineSmall,
                color = HumidityColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (data.size - 1).coerceAtLeast(1)
                
                // 绘制折线
                for (i in 0 until data.size - 1) {
                    val x1 = i * stepX
                    val y1 = height - ((data[i] - minHum) / range * height).toFloat()
                    val x2 = (i + 1) * stepX
                    val y2 = height - ((data[i + 1] - minHum) / range * height).toFloat()
                    
                    drawLine(
                        color = HumidityColor,
                        start = androidx.compose.ui.geometry.Offset(x1, y1),
                        end = androidx.compose.ui.geometry.Offset(x2, y2),
                        strokeWidth = 3f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * 历史数据预览列表
 */
@Composable
private fun HistoryDataPreview(
    data: List<SensorDataEntity>,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "最近记录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            data.forEachIndexed { index, entity ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dateFormat.format(Date(entity.recordedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        entity.temperature?.let {
                            Text(
                                text = "${String.format("%.1f", it)}°C",
                                style = MaterialTheme.typography.bodySmall,
                                color = TemperatureColor
                            )
                        }
                        entity.humidity?.let {
                            Text(
                                text = "${String.format("%.1f", it)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = HumidityColor
                            )
                        }
                    }
                }
                
                if (index < data.size - 1) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

/**
 * 温湿度统计数据
 */
private data class TempHumStats(
    val current: Double,
    val min: Double,
    val max: Double,
    val avg: Double
)
