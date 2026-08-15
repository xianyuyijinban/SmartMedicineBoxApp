package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartmedicine.data.model.SmartAdvice
import com.smartmedicine.data.model.SmartAnalysisResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCenterScreen(
    analysis: SmartAnalysisResult,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("智能中心") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            ScoreCard(analysis)
            SmartStatsCard(analysis)
            AdviceCard(analysis.advice)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScoreCard(analysis: SmartAnalysisResult) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Text("服药智能评分", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "${analysis.score}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = scoreColor(analysis.score)
            )
            LinearProgressIndicator(
                progress = { analysis.score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = scoreColor(analysis.score)
            )
            Text(
                "近 7 天完成率 ${analysis.completedRate}% ，系统发现 ${analysis.riskCount} 条需要关注的风险。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmartStatsCard(analysis: SmartAnalysisResult) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, contentDescription = null)
                Text("近 7 天概览", style = MaterialTheme.typography.titleMedium)
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("总任务", analysis.totalDoses.toString())
                StatBlock("已服用", analysis.takenCount.toString())
                StatBlock("延迟", analysis.delayedCount.toString())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("漏服", analysis.missedCount.toString())
                StatBlock("跳过", analysis.skippedCount.toString())
                StatBlock("低库存", analysis.lowStockCount.toString())
            }
        }
    }
}

@Composable
private fun AdviceCard(advice: List<SmartAdvice>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null)
                Text("智能建议", style = MaterialTheme.typography.titleMedium)
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            advice.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = adviceContainerColor(item.level)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            AssistChip(onClick = {}, label = { Text(levelText(item.level)) })
                        }
                        Text(item.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun scoreColor(score: Int) = when {
    score >= 85 -> MaterialTheme.colorScheme.primary
    score >= 65 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun adviceContainerColor(level: String) = when (level) {
    "danger" -> MaterialTheme.colorScheme.errorContainer
    "warning" -> MaterialTheme.colorScheme.tertiaryContainer
    "good" -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun levelText(level: String): String = when (level) {
    "danger" -> "高风险"
    "warning" -> "需关注"
    "good" -> "良好"
    else -> "提示"
}
