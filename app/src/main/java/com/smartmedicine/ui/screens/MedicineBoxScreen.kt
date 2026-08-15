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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineBoxScreen(
    compartments: List<MedicineCompartmentUiItem>,
    onSaveInfo: (Int, String, Int, Int, Int) -> Unit,
    onAddInfo: (Int, String, Int, Int, Int) -> Unit,
    onDeactivate: (Int) -> Unit,
    onTransfer: (Int, Int) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItems = compartments.filter { it.active }.sortedBy { it.boxId }
    val emptyItems = compartments.filterNot { it.active }.sortedBy { it.boxId }
    var showAddDialog by remember { mutableStateOf(false) }
    var transferSource by remember { mutableStateOf<MedicineCompartmentUiItem?>(null) }
    val names = remember { mutableStateMapOf<Int, String>() }
    val stocks = remember { mutableStateMapOf<Int, String>() }
    val doseAmounts = remember { mutableStateMapOf<Int, String>() }
    val lowStockThresholds = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(compartments) {
        compartments.forEach { item ->
            names[item.boxId] = item.name
            stocks[item.boxId] = item.stock.toString()
            doseAmounts[item.boxId] = item.dosePerUse.toString()
            lowStockThresholds[item.boxId] = item.lowStockThreshold.toString()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("药盒管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }, enabled = emptyItems.isNotEmpty()) {
                        Icon(Icons.Default.Add, contentDescription = "添加药盒")
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
            if (activeItems.isEmpty()) {
                Text("暂无启用药盒，点击右上角 + 添加药品。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            activeItems.forEach { item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.boxId} 号药盒", style = MaterialTheme.typography.titleMedium)
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                IconButton(onClick = { transferSource = item }, enabled = emptyItems.isNotEmpty()) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "转移药盒")
                                }
                                IconButton(onClick = { onDeactivate(item.boxId) }) {
                                    Icon(Icons.Default.Close, contentDescription = "停用药盒")
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutlinedTextField(
                            value = names[item.boxId] ?: item.name,
                            onValueChange = { names[item.boxId] = it },
                            label = { Text("药品名称") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("库存", stocks[item.boxId] ?: "0", { stocks[item.boxId] = it }, Modifier.weight(1f))
                            NumberField("每次用量", doseAmounts[item.boxId] ?: "1", { doseAmounts[item.boxId] = it }, Modifier.weight(1f))
                            NumberField("低库存", lowStockThresholds[item.boxId] ?: "3", { lowStockThresholds[item.boxId] = it }, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    onSaveInfo(
                                        item.boxId,
                                        names[item.boxId]?.trim().orEmpty().ifBlank { "${item.boxId} 号药盒" },
                                        stocks[item.boxId]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                        doseAmounts[item.boxId]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                        lowStockThresholds[item.boxId]?.toIntOrNull()?.coerceAtLeast(0) ?: 3
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "保存")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMedicineBoxDialog(
            emptyItems = emptyItems,
            onConfirm = { boxId, name, stock, dose, low ->
                onAddInfo(boxId, name, stock, dose, low)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    transferSource?.let { source ->
        TransferMedicineBoxDialog(
            source = source,
            emptyItems = emptyItems,
            onConfirm = { target ->
                onTransfer(source.boxId, target)
                transferSource = null
            },
            onDismiss = { transferSource = null }
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMedicineBoxDialog(
    emptyItems: List<MedicineCompartmentUiItem>,
    onConfirm: (Int, String, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBoxId by remember(emptyItems) { mutableStateOf(emptyItems.firstOrNull()?.boxId ?: 1) }
    var boxMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }
    var dose by remember { mutableStateOf("1") }
    var lowStock by remember { mutableStateOf("3") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加药盒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = boxMenu, onExpandedChange = { boxMenu = !boxMenu }) {
                    OutlinedTextField(
                        value = "${selectedBoxId} 号药盒",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("空置药盒") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boxMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                        emptyItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.boxId} 号药盒") },
                                onClick = {
                                    selectedBoxId = item.boxId
                                    boxMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("药品名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("库存", stock, { stock = it }, Modifier.weight(1f))
                    NumberField("每次用量", dose, { dose = it }, Modifier.weight(1f))
                    NumberField("低库存", lowStock, { lowStock = it }, Modifier.weight(1f))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val stockValue = stock.toIntOrNull()
                val doseValue = dose.toIntOrNull()
                val lowValue = lowStock.toIntOrNull()
                when {
                    emptyItems.none { it.boxId == selectedBoxId } -> error = "请选择空置药盒"
                    name.isBlank() -> error = "请输入药品名称"
                    stockValue == null || stockValue < 0 -> error = "库存不能小于 0"
                    doseValue == null || doseValue < 1 -> error = "每次用量必须大于等于 1"
                    lowValue == null || lowValue < 0 -> error = "低库存阈值不能小于 0"
                    else -> onConfirm(selectedBoxId, name.trim(), stockValue, doseValue, lowValue)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferMedicineBoxDialog(
    source: MedicineCompartmentUiItem,
    emptyItems: List<MedicineCompartmentUiItem>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBoxId by remember(emptyItems) { mutableStateOf(emptyItems.firstOrNull()?.boxId ?: 1) }
    var boxMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转移药盒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("将 ${source.boxId} 号药盒的 ${source.name} 转移到空置药盒。")
                ExposedDropdownMenuBox(expanded = boxMenu, onExpandedChange = { boxMenu = !boxMenu }) {
                    OutlinedTextField(
                        value = "${selectedBoxId} 号药盒",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标空药盒") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boxMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                        emptyItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.boxId} 号药盒") },
                                onClick = {
                                    selectedBoxId = item.boxId
                                    boxMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selectedBoxId) }) { Text("转移") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
