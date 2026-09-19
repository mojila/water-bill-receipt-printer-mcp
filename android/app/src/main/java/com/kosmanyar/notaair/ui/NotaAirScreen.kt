package com.kosmanyar.notaair.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmanyar.notaair.UiState
import com.kosmanyar.notaair.data.ReceiptCore
import com.kosmanyar.notaair.data.ReceiptData
import com.kosmanyar.notaair.printer.PrinterDevice
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Visual transformation that inserts Indonesian dot ('.') thousand separators
 * without changing the underlying raw digits model.
 */
class IndonesianThousandSeparatorTransformation : VisualTransformation {
    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID")).apply {
        groupingSeparator = '.'
    }
    private val formatter = DecimalFormat("#,###", symbols)

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isBlank() || !raw.all { it.isDigit() }) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val parsed = raw.toLongOrNull() ?: return TransformedText(text, OffsetMapping.Identity)
        val formatted = formatter.format(parsed)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val safeOffset = offset.coerceAtMost(raw.length)
                val rawPrefix = raw.substring(0, safeOffset)
                val rawVal = rawPrefix.toLongOrNull() ?: 0L
                val formattedPrefix = formatter.format(rawVal)
                return formattedPrefix.length.coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val safeOffset = offset.coerceAtMost(formatted.length)
                val formattedPrefix = formatted.substring(0, safeOffset)
                return formattedPrefix.count { it.isDigit() }.coerceAtMost(raw.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

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
    onCancelPrinting: () -> Unit,
    onRefreshPreview: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Bottom sheets state
    var showPrinterSheet by remember { mutableStateOf(false) }
    var showUnitFormSheet by remember { mutableStateOf(false) }
    var editingUnitId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<ReceiptData?>(null) }

    val printerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unitFormSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Trigger haptic feedback when print finishes successfully
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) {
        if (wasBusy && !state.busy) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasBusy = state.busy
    }

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
                        Text(
                            "Nota Air Kos Manyar",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            state.settings.period,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    // Bluetooth printer status icon button with status badge
                    val selectedDevice = state.devices.firstOrNull { it.address == state.selectedPrinterAddress }
                    val isConnected = selectedDevice != null && !state.selectedPrinterAddress.isNullOrBlank()

                    IconButton(
                        onClick = { showPrinterSheet = true }
                    ) {
                        BadgedBox(
                            badge = {
                                when {
                                    state.scanning -> {
                                        Badge(
                                            containerColor = Color(0xFFF59E0B),
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }
                                    isConnected -> {
                                        Badge(
                                            containerColor = Color(0xFF10B981),
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }
                                    else -> {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = when {
                                    state.scanning -> Icons.AutoMirrored.Filled.BluetoothSearching
                                    isConnected -> Icons.Filled.BluetoothConnected
                                    else -> Icons.Filled.Bluetooth
                                },
                                contentDescription = "Pengaturan Printer",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingUnitId = null
                        showUnitFormSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah Unit")
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Unit", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                listOf("Daftar Unit", "Pratinjau Nota").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> UnitTab(
                    state = state,
                    onSelectUnit = onSelectUnit,
                    onEditUnit = { unitId ->
                        editingUnitId = unitId
                        showUnitFormSheet = true
                    },
                    onDeletePrompt = { bill ->
                        showDeleteConfirmDialog = bill
                    },
                    onSaveSettings = onSaveSettings,
                    onResetUnits = onResetUnits,
                    onPrintOne = onPrintOne,
                    onPrintAll = onPrintAll
                )

                else -> ReceiptPreviewTab(
                    state = state,
                    onSelectUnit = onSelectUnit,
                    onRefreshPreview = onRefreshPreview,
                    onPrintOne = onPrintOne
                )
            }
        }
    }

    // Modal Bottom Sheet: Printer & Bluetooth Management
    if (showPrinterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPrinterSheet = false },
            sheetState = printerSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PrinterBottomSheetContent(
                state = state,
                onScan = onScan,
                onStopScan = onStopScan,
                onSelectPrinter = onSelectPrinter,
                onCheckPrinter = onCheckPrinter,
                onSetPrintDelay = onSetPrintDelay,
                onClose = { showPrinterSheet = false }
            )
        }
    }

    // Modal Bottom Sheet: Tambah / Ubah Unit Form
    if (showUnitFormSheet) {
        val targetUnit = state.receipts.firstOrNull { it.id == editingUnitId }
        ModalBottomSheet(
            onDismissRequest = { showUnitFormSheet = false },
            sheetState = unitFormSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            UnitFormBottomSheetContent(
                existingUnit = targetUnit,
                garbageFee = state.settings.garbageFee,
                onSave = { existingId, name, usage, total ->
                    onSaveUnit(existingId, name, usage, total)
                    showUnitFormSheet = false
                },
                onCancel = { showUnitFormSheet = false }
            )
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { bill ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Hapus Unit") },
            text = { Text("Apakah Anda yakin ingin menghapus '${bill.name}' dari daftar tagihan kos?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUnit(bill.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Print Progress Modal Dialog with Cancel
    if (state.busy) {
        AlertDialog(
            onDismissRequest = { /* Modal print cannot be dismissed by background tap */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Mencetak Nota", fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.printProgressTotal > 1) {
                        val fraction = if (state.printProgressTotal > 0) {
                            state.printProgressCurrent.toFloat() / state.printProgressTotal.toFloat()
                        } else 0f

                        Text(
                            text = "Mencetak ${state.printProgressCurrent} dari ${state.printProgressTotal}: ${state.printProgressName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Text(
                            text = "${(fraction * 100).toInt()}% selesai",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = state.message ?: "Mengirim data ke printer...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = onCancelPrinting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * Bottom Sheet content for Bluetooth printer discovery and settings.
 */
@Composable
private fun PrinterBottomSheetContent(
    state: UiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectPrinter: (String) -> Unit,
    onCheckPrinter: () -> Unit,
    onSetPrintDelay: (Long) -> Unit,
    onClose: () -> Unit
) {
    val selected = state.devices.firstOrNull { it.address == state.selectedPrinterAddress }
    // Local text state so typing (including transient values below the clamp or empty)
    // is not overwritten when the ViewModel clamps printDelayMs. Synced back to the
    // canonical value whenever the ValueState's clamped value changes externally.
    var delayText by remember { mutableStateOf(state.printDelayMs.toString()) }
    LaunchedEffect(state.printDelayMs) {
        if (delayText.toLongOrNull() != state.printDelayMs) {
            delayText = state.printDelayMs.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Printer Bluetooth (POS-58)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    selected?.let { "${it.displayName} • ${it.address}" }
                        ?: "Belum ada printer dipilih",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.scanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = if (state.scanning) onStopScan else onScan,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (state.scanning) Icons.Filled.Refresh else Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.scanning) "Berhenti" else "Cari Printer")
            }

            OutlinedButton(
                onClick = onCheckPrinter,
                enabled = !state.scanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Cek Status")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Daftar Perangkat (${state.devices.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (state.devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.scanning) "Sedang mencari printer di sekitar..."
                    else "Tidak ada printer terdeteksi. Tekan 'Cari Printer'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (state.devices.size > 4) 210.dp else (state.devices.size * 56).dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = delayText,
                onValueChange = {
                    delayText = it.filter { ch -> ch.isDigit() }.take(5)
                    delayText.toLongOrNull()?.let(onSetPrintDelay)
                },
                label = { Text("Jeda Cetak Massal (ms)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Tutup")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeviceRow(device: PrinterDevice, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            DeviceRowContent(device, true)
        }
    } else {
        OutlinedCard(
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.displayName,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                device.address,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (device.bonded) {
            AssistChip(
                onClick = {},
                label = { Text("Paired") },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

/**
 * Bottom Sheet for adding or editing a unit with visual thousand separators.
 */
@Composable
private fun UnitFormBottomSheetContent(
    existingUnit: ReceiptData?,
    garbageFee: Long,
    onSave: (existingId: String?, name: String, usage: Int, total: Long) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(existingUnit) { mutableStateOf(existingUnit?.name.orEmpty()) }
    var usageText by remember(existingUnit) { mutableStateOf(existingUnit?.usageM3?.toString() ?: "") }
    var totalText by remember(existingUnit) { mutableStateOf(existingUnit?.total?.toString() ?: "") }

    val rawTotal = totalText.toLongOrNull() ?: 0L
    val calculatedWater = (rawTotal - garbageFee).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (existingUnit == null) "Tambah Unit Baru" else "Ubah Unit: ${existingUnit.name}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nama Penghuni") },
            placeholder = { Text("Contoh: Mbak Oci, Pak Budi") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = usageText,
                onValueChange = { usageText = it.filter { ch -> ch.isDigit() }.take(4) },
                label = { Text("Pemakaian (m3)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it.filter { ch -> ch.isDigit() }.take(10) },
                label = { Text("Total (Rp)") },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = IndonesianThousandSeparatorTransformation(),
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Air: ${ReceiptCore.formatRupiah(calculatedWater)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Sampah: ${ReceiptCore.formatRupiah(garbageFee)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Batal")
            }

            Button(
                onClick = {
                    onSave(
                        existingUnit?.id,
                        name,
                        usageText.toIntOrNull() ?: 0,
                        rawTotal
                    )
                },
                enabled = name.isNotBlank() && usageText.isNotBlank() && rawTotal > 0L,
                modifier = Modifier.weight(1.4f)
            ) {
                Text(if (existingUnit == null) "Simpan Unit" else "Perbarui Unit")
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Tab: Daftar Unit kos, search filter, period settings, and unit cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitTab(
    state: UiState,
    onSelectUnit: (String) -> Unit,
    onEditUnit: (String) -> Unit,
    onDeletePrompt: (ReceiptData) -> Unit,
    onSaveSettings: (String, Int, Long) -> Unit,
    onResetUnits: () -> Unit,
    onPrintOne: (String) -> Unit,
    onPrintAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showPeriodDialog by remember { mutableStateOf(false) }

    val filteredReceipts = remember(state.receipts, searchQuery) {
        if (searchQuery.isBlank()) {
            state.receipts
        } else {
            val query = searchQuery.trim().lowercase()
            state.receipts.filter { it.name.lowercase().contains(query) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // Quick summary card with period settings trigger
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Periode Tagihan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        state.settings.period,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Uang sampah: ${ReceiptCore.formatRupiah(state.settings.garbageFee)} / unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { showPeriodDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ubah")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari nama penghuni...") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(14.dp))

        // Unit list card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Daftar Penghuni (${filteredReceipts.size}${if (searchQuery.isNotBlank()) " dari ${state.receipts.size}" else ""})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = onPrintAll,
                        enabled = state.receipts.isNotEmpty() && !state.busy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cetak Semua", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (filteredReceipts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (searchQuery.isNotBlank()) "Tidak ditemukan penghuni dengan nama '$searchQuery'."
                            else "Belum ada unit penghuni. Tekan tombol + untuk menambah.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    filteredReceipts.forEachIndexed { index, bill ->
                        UnitRow(
                            index = index + 1,
                            bill = bill,
                            selected = bill.id == state.selectedId,
                            onClick = {
                                onSelectUnit(bill.id)
                            },
                            onEdit = { onEditUnit(bill.id) },
                            onDelete = { onDeletePrompt(bill) },
                            onPrint = { onPrintOne(bill.id) }
                        )
                        if (index < filteredReceipts.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        }
                    }
                }

                if (state.receipts.isNotEmpty() && searchQuery.isBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Column(Modifier.padding(14.dp)) {
                        TotalLine("Total pemakaian air", "${state.totals.usageM3} m3")
                        TotalLine("Total tagihan air", ReceiptCore.formatRupiah(state.totals.water))
                        TotalLine("Total uang sampah", ReceiptCore.formatRupiah(state.totals.garbage))
                        TotalLine("Total penerimaan kas", ReceiptCore.formatRupiah(state.totals.grand), bold = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onResetUnits,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Reset Bawaan")
            }

            Text(
                "Data: ${state.dataFile.substringAfterLast(java.io.File.separatorChar)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(80.dp)) // Padding for FAB
    }

    if (showPeriodDialog) {
        PeriodSettingsDialog(
            state = state,
            onDismiss = { showPeriodDialog = false },
            onSaveSettings = { month, year, fee ->
                onSaveSettings(month, year, fee)
                showPeriodDialog = false
            }
        )
    }
}

/**
 * Ergonomic UnitRow with clear touch targets and overflow action menu
 * preventing accidental deletion or edits.
 */
@Composable
private fun UnitRow(
    index: Int,
    bill: ReceiptData,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrint: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$index.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                bill.name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${bill.usageM3} m3 • Air ${bill.waterBillFormatted} • Sampah ${bill.garbageFeeFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            bill.totalFormatted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.width(8.dp))

        // Large print action button
        IconButton(
            onClick = onPrint,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.Print,
                contentDescription = "Cetak nota ${bill.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Action menu dropdown
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Menu ${bill.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Ubah Unit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hapus Unit", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Period and Garbage Fee Settings Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSettingsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onSaveSettings: (String, Int, Long) -> Unit
) {
    var month by remember(state.settings) { mutableStateOf(state.settings.month) }
    var year by remember(state.settings) { mutableStateOf(state.settings.year.toString()) }
    var fee by remember(state.settings) { mutableStateOf(state.settings.garbageFee.toString()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Periode & Biaya") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
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
                        label = { Text("Uang Sampah (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = IndonesianThousandSeparatorTransformation(),
                        modifier = Modifier.weight(1.3f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val index = ReceiptCore.MONTHS.indexOf(month).let { if (it < 0) 0 else it }
                            val currentYear = year.toIntOrNull() ?: state.settings.year
                            val next = if (index == 0) 11 else index - 1
                            val nextYear = if (index == 0) currentYear - 1 else currentYear
                            month = ReceiptCore.MONTHS[next]
                            year = nextYear.toString()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("‹ Bulan lalu")
                    }

                    OutlinedButton(
                        onClick = {
                            val index = ReceiptCore.MONTHS.indexOf(month).let { if (it < 0) 0 else it }
                            val currentYear = year.toIntOrNull() ?: state.settings.year
                            val next = if (index == 11) 0 else index + 1
                            val nextYear = if (index == 11) currentYear + 1 else currentYear
                            month = ReceiptCore.MONTHS[next]
                            year = nextYear.toString()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bulan depan ›")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(
                        month,
                        year.toIntOrNull() ?: state.settings.year,
                        fee.toLongOrNull() ?: state.settings.garbageFee
                    )
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

/**
 * Tab: Realistic 58mm Thermal Receipt Preview with jagged paper cuts and quick navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptPreviewTab(
    state: UiState,
    onSelectUnit: (String) -> Unit,
    onRefreshPreview: () -> Unit,
    onPrintOne: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentIndex = state.receipts.indexOfFirst { it.id == state.selectedId }
    val selected = state.receipts.getOrNull(currentIndex) ?: state.receipts.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // Quick switcher and selector
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                onSelectUnit(state.receipts[currentIndex - 1].id)
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Unit Sebelumnya")
                    }

                    Box(Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selected?.name ?: "Pilih unit",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Pilih Unit Nota") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
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
                    }

                    IconButton(
                        onClick = {
                            if (currentIndex in 0 until state.receipts.size - 1) {
                                onSelectUnit(state.receipts[currentIndex + 1].id)
                            }
                        },
                        enabled = currentIndex in 0 until state.receipts.size - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Unit Berikutnya")
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshPreview,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Muat Ulang")
                    }

                    Button(
                        onClick = { selected?.let { onPrintOne(it.id) } },
                        enabled = selected != null && !state.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cetak Nota")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Realistic Thermal Receipt Paper
        if (state.preview.isBlank() || selected == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Pilih unit untuk melihat pratinjau nota thermal 58mm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            ThermalPaperReceipt(previewText = state.preview)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Realistic 58mm Thermal Paper Component with jagged / serrated paper cut edges.
 */
@Composable
private fun ThermalPaperReceipt(previewText: String) {
    val paperColor = Color(0xFFFCFDFD)
    val teethColor = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(2.dp))
            .background(paperColor)
    ) {
        // Top Jagged Paper Edge
        JaggedEdgeCanvas(
            isTop = true,
            paperColor = paperColor,
            backgroundColor = teethColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        )

        // Receipt Content (Monospace 32-columns)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = previewText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B), // Thermal ink dark charcoal
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bottom Jagged Paper Edge
        JaggedEdgeCanvas(
            isTop = false,
            paperColor = paperColor,
            backgroundColor = teethColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        )
    }
}

/**
 * Canvas drawing jagged sawtooth / serrated thermal cut edge.
 */
@Composable
private fun JaggedEdgeCanvas(
    isTop: Boolean,
    paperColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val toothWidth = 14f
        val toothHeight = size.height
        val teethCount = (size.width / toothWidth).toInt() + 2

        val path = Path().apply {
            if (isTop) {
                // Background fills cutouts from the top
                moveTo(0f, toothHeight)
                for (i in 0..teethCount) {
                    val x1 = i * toothWidth + (toothWidth / 2f)
                    val y1 = 0f
                    val x2 = (i + 1) * toothWidth
                    val y2 = toothHeight
                    lineTo(x1, y1)
                    lineTo(x2, y2)
                }
                lineTo(size.width, 0f)
                lineTo(0f, 0f)
                close()
            } else {
                // Background fills cutouts from the bottom
                moveTo(0f, 0f)
                for (i in 0..teethCount) {
                    val x1 = i * toothWidth + (toothWidth / 2f)
                    val y1 = toothHeight
                    val x2 = (i + 1) * toothWidth
                    val y2 = 0f
                    lineTo(x1, y1)
                    lineTo(x2, y2)
                }
                lineTo(size.width, toothHeight)
                lineTo(0f, toothHeight)
                close()
            }
        }

        drawPath(path = path, color = backgroundColor)
    }
}

