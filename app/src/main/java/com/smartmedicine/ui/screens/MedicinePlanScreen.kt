package com.smartmedicine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinePlanScreen(
    plans: List<MedicinePlanUiItem>,
    compartments: List<MedicineCompartmentUiItem>,
    onAddPlan: (Int, String, Int, Int, Int, String, String) -> Unit,
    onUpdatePlan: (Long, Int, String, Int, Int, Int, String, String) -> Unit,
    onSetPlanEnabled: (Long, Boolean) -> Unit,
    onDeletePlan: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<MedicinePlanUiItem?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("服药计划") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增计划")
                    }
                },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (plans.isEmpty()) {
                Text("暂无服药计划", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                plans.filter { plan -> compartments.any { it.boxId == plan.boxId } }.forEach { plan ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.medicineName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${plan.boxId} 号药盒  ${plan.timeText}  ${plan.repeatText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingPlan = plan }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑计划")
                                }
                                IconButton(onClick = { onDeletePlan(plan.planId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除计划")
                                }
                                Switch(
                                    checked = plan.enabled,
                                    onCheckedChange = { onSetPlanEnabled(plan.planId, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddPlanDialog(
            compartments = compartments,
            onConfirm = { boxId, name, dose, hour, minute, repeat, days ->
                onAddPlan(boxId, name, dose, hour, minute, repeat, days)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
    editingPlan?.let { plan ->
        AddPlanDialog(
            compartments = compartments,
            initialPlan = plan,
            onConfirm = { boxId, name, dose, hour, minute, repeat, days ->
                onUpdatePlan(plan.planId, boxId, name, dose, hour, minute, repeat, days)
                editingPlan = null
            },
            onDismiss = { editingPlan = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlanDialog(
    compartments: List<MedicineCompartmentUiItem>,
    initialPlan: MedicinePlanUiItem? = null,
    onConfirm: (Int, String, Int, Int, Int, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBoxId by remember(compartments, initialPlan) { mutableStateOf(initialPlan?.boxId ?: compartments.firstOrNull()?.boxId ?: 1) }
    var repeatMenu by remember { mutableStateOf(false) }
    var medicineName by remember(initialPlan) { mutableStateOf(initialPlan?.medicineName ?: compartments.firstOrNull()?.name ?: "") }
    var doseText by remember(initialPlan) { mutableStateOf(initialPlan?.doseAmount?.toString() ?: "1") }
    var hourText by remember(initialPlan) { mutableStateOf(initialPlan?.hour?.toString() ?: "8") }
    var minuteText by remember(initialPlan) { mutableStateOf(initialPlan?.minute?.toString() ?: "0") }
    var repeatType by remember(initialPlan) { mutableStateOf(initialPlan?.repeatType ?: "daily") }
    var daysOfWeek by remember(initialPlan) { mutableStateOf(initialPlan?.daysOfWeek?.ifBlank { "1,2,3,4,5,6,7" } ?: "1,2,3,4,5,6,7") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPlan == null) "新增服药计划" else "编辑服药计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择药盒", style = MaterialTheme.typography.labelLarge)
                if (compartments.isEmpty()) {
                    Text("请先在药盒管理中添加药盒", color = MaterialTheme.colorScheme.error)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        compartments.forEach { box ->
                            val selected = box.boxId == selectedBoxId
                            if (selected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${box.boxId} 号药盒 - ${box.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedBoxId = box.boxId
                                        medicineName = box.name
                                        doseText = box.dosePerUse.toString()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${box.boxId} 号药盒 - ${box.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(medicineName, { medicineName = it }, label = { Text("药品名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("每次用量", doseText, { doseText = it }, Modifier.weight(1f))
                    NumberField("小时", hourText, { hourText = it }, Modifier.weight(1f))
                    NumberField("分钟", minuteText, { minuteText = it }, Modifier.weight(1f))
                }
                ExposedDropdownMenuBox(expanded = repeatMenu, onExpandedChange = { repeatMenu = !repeatMenu }) {
                    OutlinedTextField(
                        value = repeatTypeText(repeatType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("重复方式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                        listOf("daily", "weekly", "once").forEach { item ->
                            DropdownMenuItem(text = { Text(repeatTypeText(item)) }, onClick = { repeatType = item; repeatMenu = false })
                        }
                    }
                }
                if (repeatType == "weekly") {
                    OutlinedTextField(daysOfWeek, { daysOfWeek = it }, label = { Text("星期，1=周一..7=周日") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dose = doseText.toIntOrNull()
                    val hour = hourText.toIntOrNull()
                    val minute = minuteText.toIntOrNull()
                    when {
                        compartments.none { it.boxId == selectedBoxId } -> error = "请选择药盒"
                        medicineName.isBlank() -> error = "请输入药品名称"
                        dose == null || dose < 1 -> error = "每次用量必须大于等于 1"
                        hour == null || hour !in 0..23 -> error = "小时范围为 0-23"
                        minute == null || minute !in 0..59 -> error = "分钟范围为 0-59"
                        else -> onConfirm(selectedBoxId, medicineName.trim(), dose, hour, minute, repeatType, if (repeatType == "weekly") daysOfWeek else "")
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun repeatTypeText(type: String): String = when (type) {
    "daily" -> "每天"
    "weekly" -> "每周"
    "once" -> "一次"
    else -> type
}
