package com.kosmanyar.notaair.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmanyar.notaair.UiState
import com.kosmanyar.notaair.data.ReceiptCore
import com.kosmanyar.notaair.data.ReceiptData
import com.kosmanyar.notaair.printer.PrinterDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotaAirScreen(
    state: UiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectPrinter: (String) -> Unit,
    onCheckPrinter: () -> Unit,
    onSelectUnit: (String) -> Unit,
    onSaveSettings: (String, Int, Long) -> Unit,
    onSaveUnit: (String?, String, Int, Long) -> Unit,
    onDeleteUnit: (String) -> Unit,
    onResetUnits: () -> Unit,
    onSetPrintDelay: (Long) -> Unit,
    onPrintOne: (String) -> Unit,
    onPrintAll: () -> Unit,
    onRefreshPreview: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nota Air Kos Manyar", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.settings.period,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = "Cari printer Bluetooth")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrinterCard(
                state = state,
                onScan = onScan,
                onStopScan = onStopScan,
                onSelectPrinter = onSelectPrinter,
                onCheckPrinter = onCheckPrinter,
                onPrintAll = onPrintAll,
                onSetPrintDelay = onSetPrintDelay
            )

            TabRow(selectedTabIndex = selectedTab) {
                listOf("Daftar Unit", "Pratinjau").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> UnitTab(
                    state = state,
                    onSelectUnit = onSelectUnit,
                    onSaveSettings = onSaveSettings,
                    onSaveUnit = onSaveUnit,
                    onDeleteUnit = onDeleteUnit,
                    onResetUnits = onResetUnits,
                    onPrintOne = onPrintOne,
                    onPrintAll = onPrintAll
                )

                else -> PreviewTab(
                    state = state,
                    onSelectUnit = onSelectUnit,
                    onRefreshPreview = onRefreshPreview,
                    onPrintOne = onPrintOne
                )
            }
        }
    }

    if (state.busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Mencetak") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(state.message ?: "Mengirim data ke printer...")
                }
            }
        )
    }
}

@Composable
private fun PrinterCard(
    state: UiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectPrinter: (String) -> Unit,
    onCheckPrinter: () -> Unit,
    onPrintAll: () -> Unit,
    onSetPrintDelay: (Long) -> Unit
) {
    val selected = state.devices.firstOrNull { it.address == state.selectedPrinterAddress }
    var delayText by remember(state.printDelayMs) { mutableStateOf(state.printDelayMs.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Printer Bluetooth (SPP)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (state.scanning) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = selected?.let { "${it.displayName}  •  ${it.address}" }
                    ?: "Belum ada printer dipilih",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = if (state.scanning) onStopScan else onScan) {
                    Icon(
                        if (state.scanning) Icons.Filled.Refresh else Icons.Filled.Search,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.scanning) "Berhenti" else "Cari Printer")
                }
                OutlinedButton(onClick = onCheckPrinter, enabled = !state.scanning) {
                    Text("Cek Printer")
                }
            }

            if (state.devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (state.devices.size > 3) 168.dp else (state.devices.size * 52).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.devices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            selected = device.address == state.selectedPrinterAddress,
                            onClick = { onSelectPrinter(device.address) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = delayText,
                    onValueChange = {
                        delayText = it.filter { ch -> ch.isDigit() }.take(5)
                        delayText.toLongOrNull()?.let(onSetPrintDelay)
                    },
                    label = { Text("Jeda antar cetak (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onPrintAll,
                    enabled = !state.busy && state.devices.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cetak Semua")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: PrinterDevice, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            DeviceRowContent(device, true)
        }
    } else {
        androidx.compose.material3.OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            DeviceRowContent(device, false)
        }
    }
}

@Composable
private fun DeviceRowContent(device: PrinterDevice, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.displayName,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                device.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        if (device.bonded) {
            AssistChip(onClick = {}, label = { Text("Paired") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitTab(
    state: UiState,
    onSelectUnit: (String) -> Unit,
    onSaveSettings: (String, Int, Long) -> Unit,
    onSaveUnit: (String?, String, Int, Long) -> Unit,
    onDeleteUnit: (String) -> Unit,
    onResetUnits: () -> Unit,
    onPrintOne: (String) -> Unit,
    onPrintAll: () -> Unit
) {
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var usage by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }

    LaunchedEffect(state.selectedId) {
        val bill = state.receipts.firstOrNull { it.id == state.selectedId }
        if (bill != null) {
            editId = bill.id
            name = bill.name
            usage = bill.usageM3.toString()
            total = bill.total.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        PeriodSection(state = state, onSaveSettings = onSaveSettings)

        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("Tambah / Ubah Unit", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Penghuni") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = usage,
                        onValueChange = { usage = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Pemakaian (m3)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = total,
                        onValueChange = { total = it.filter { ch -> ch.isDigit() }.take(10) },
                        label = { Text("Total (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                val totalValue = total.toLongOrNull() ?: 0L
                val fee = state.settings.garbageFee
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tagihan Air: ${ReceiptCore.formatRupiah(totalValue - fee)}   •   Sampah: ${
                        ReceiptCore.formatRupiah(fee)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onSaveUnit(
                            editId,
                            name,
                            usage.toIntOrNull() ?: 0,
                            totalValue
                        )
                    }) { Text(if (editId == null) "Simpan Unit" else "Perbarui Unit") }

                    OutlinedButton(onClick = {
                        editId = null
                        name = ""
                        usage = ""
                        total = ""
                    }) { Text("Form Baru") }

                    if (editId != null) {
                        OutlinedButton(onClick = {
                            editId?.let(onDeleteUnit)
                            editId = null
                            name = ""
                            usage = ""
                            total = ""
                        }) { Text("Hapus") }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Daftar Unit (${state.totals.units})",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                if (state.receipts.isEmpty()) {
                    Text(
                        "Belum ada unit. Tambahkan unit pada form di atas.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                state.receipts.forEachIndexed { index, bill ->
                    UnitRow(
                        index = index + 1,
                        bill = bill,
                        selected = bill.id == state.selectedId,
                        onClick = { onSelectUnit(bill.id) },
                        onPrint = { onPrintOne(bill.id) }
                    )
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                if (state.receipts.isNotEmpty()) {
                    Column(Modifier.padding(14.dp)) {
                        TotalLine("Total pemakaian", "${state.totals.usageM3} m3")
                        TotalLine("Total tagihan air", ReceiptCore.formatRupiah(state.totals.water))
                        TotalLine("Total uang sampah", ReceiptCore.formatRupiah(state.totals.garbage))
                        TotalLine("Total keseluruhan", ReceiptCore.formatRupiah(state.totals.grand), bold = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onResetUnits) { Text("Reset Bawaan") }
            Button(onClick = onPrintAll, enabled = state.receipts.isNotEmpty()) { Text("Cetak Semua") }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun UnitRow(
    index: Int,
    bill: ReceiptData,
    selected: Boolean,
    onClick: () -> Unit,
    onPrint: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index.", modifier = Modifier.width(26.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(bill.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${bill.usageM3} m3  •  Air ${bill.waterBillFormatted}  •  Sampah ${bill.garbageFeeFormatted}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(bill.totalFormatted, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onPrint) {
            Icon(Icons.Filled.Print, contentDescription = "Cetak ${bill.name}")
        }
        TextButton(onClick = onClick) { Text("Ubah") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSection(state: UiState, onSaveSettings: (String, Int, Long) -> Unit) {
    var month by remember(state.settings) { mutableStateOf(state.settings.month) }
    var year by remember(state.settings) { mutableStateOf(state.settings.year.toString()) }
    var fee by remember(state.settings) { mutableStateOf(state.settings.garbageFee.toString()) }
    var expanded by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("Periode Tagihan", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = month,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bulan") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ReceiptCore.MONTHS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                month = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Tahun") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it.filter { ch -> ch.isDigit() }.take(10) },
                    label = { Text("Biaya Sampah (Rp)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onSaveSettings(
                        month,
                        year.toIntOrNull() ?: state.settings.year,
                        fee.toLongOrNull() ?: state.settings.garbageFee
                    )
                }) { Text("Simpan Periode") }

                OutlinedButton(onClick = {
                    val index = ReceiptCore.MONTHS.indexOf(month).let { if (it < 0) 0 else it }
                    val currentYear = year.toIntOrNull() ?: state.settings.year
                    val next = if (index == 0) 11 else index - 1
                    val nextYear = if (index == 0) currentYear - 1 else currentYear
                    month = ReceiptCore.MONTHS[next]
                    year = nextYear.toString()
                }) { Text("‹ Bulan lalu") }

                OutlinedButton(onClick = {
                    val index = ReceiptCore.MONTHS.indexOf(month).let { if (it < 0) 0 else it }
                    val currentYear = year.toIntOrNull() ?: state.settings.year
                    val next = if (index == 11) 0 else index + 1
                    val nextYear = if (index == 11) currentYear + 1 else currentYear
                    month = ReceiptCore.MONTHS[next]
                    year = nextYear.toString()
                }) { Text("Bulan depan ›") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewTab(
    state: UiState,
    onSelectUnit: (String) -> Unit,
    onRefreshPreview: () -> Unit,
    onPrintOne: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.receipts.firstOrNull { it.id == state.selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("Pratinjau Nota 58mm", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selected?.name ?: "Pilih unit",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.receipts.forEach { bill ->
                            DropdownMenuItem(
                                text = { Text(bill.name) },
                                onClick = {
                                    onSelectUnit(bill.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshPreview) { Text("Muat Ulang") }
                    Button(
                        onClick = { selected?.let { onPrintOne(it.id) } },
                        enabled = selected != null && !state.busy
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cetak Nota")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.preview.isBlank()) {
                    Text(
                        "Pilih unit untuk melihat pratinjau nota.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = state.preview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .padding(10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Data: ${state.dataFile}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(20.dp))
    }
}
