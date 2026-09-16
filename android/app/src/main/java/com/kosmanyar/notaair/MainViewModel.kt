package com.kosmanyar.notaair

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kosmanyar.notaair.data.EscPos
import com.kosmanyar.notaair.data.ReceiptCore
import com.kosmanyar.notaair.data.ReceiptData
import com.kosmanyar.notaair.data.Settings
import com.kosmanyar.notaair.data.Store
import com.kosmanyar.notaair.data.StoreData
import com.kosmanyar.notaair.data.StoredUnit
import com.kosmanyar.notaair.printer.BluetoothPrinter
import com.kosmanyar.notaair.printer.PrinterDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class Totals(
    val units: Int = 0,
    val usageM3: Int = 0,
    val water: Long = 0,
    val garbage: Long = 0,
    val grand: Long = 0
)

data class UiState(
    val settings: Settings = Store.defaultSettings(),
    val units: List<StoredUnit> = emptyList(),
    val receipts: List<ReceiptData> = emptyList(),
    val totals: Totals = Totals(),
    val selectedId: String? = null,
    val preview: String = "",
    val devices: List<PrinterDevice> = emptyList(),
    val selectedPrinterAddress: String? = null,
    val scanning: Boolean = false,
    val busy: Boolean = false,
    val printDelayMs: Long = 1500,
    val dataFile: String = "",
    val message: String? = null,
    val messageIsError: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dataFile: File = Store.defaultFile(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        reload()
        val savedAddress = prefs().getString(KEY_PRINTER_ADDRESS, null)
        _state.update { it.copy(selectedPrinterAddress = savedAddress) }
        refreshPrinters()
    }

    private fun prefs() = getApplication<Application>()
        .getSharedPreferences(PREFS, Application.MODE_PRIVATE)

    /* ---------------- Data ---------------- */

    fun reload() {
        val store = Store.load(dataFile)
        applyStore(store, notify = false)
        _state.update { it.copy(dataFile = dataFile.absolutePath) }
    }

    private fun applyStore(store: StoreData, notify: Boolean) {
        val receipts = Store.receipts(store)
        val totals = Totals(
            units = receipts.size,
            usageM3 = receipts.sumOf { it.usageM3 },
            water = receipts.sumOf { it.waterBill },
            garbage = receipts.sumOf { it.garbageFee },
            grand = receipts.sumOf { it.total }
        )
        val selected = _state.value.selectedId
            ?.takeIf { id -> receipts.any { it.id == id } }
            ?: receipts.firstOrNull()?.id

        _state.update {
            it.copy(
                settings = store.settings,
                units = store.units,
                receipts = receipts,
                totals = totals,
                selectedId = selected,
                preview = receipts.firstOrNull { r -> r.id == selected }?.let(ReceiptCore::buildPreview)
                    ?: ""
            )
        }
        if (notify) _state.update { it.copy(message = "Data tersimpan.", messageIsError = false) }
    }

    private fun persist(store: StoreData, notify: Boolean = true) {
        val saved = Store.save(dataFile, store)
        applyStore(saved, notify)
    }

    fun saveSettings(month: String, year: Int, garbageFee: Long) {
        val store = Store.load(dataFile)
        persist(
            store.copy(
                settings = store.settings.copy(
                    month = month,
                    year = year,
                    period = ReceiptCore.makePeriod(month, year),
                    garbageFee = garbageFee
                )
            )
        )
    }

    fun upsertUnit(existingId: String?, name: String, usageM3: Int, total: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "Nama penghuni wajib diisi.", messageIsError = true) }
            return
        }
        if (usageM3 < 0 || total < 0) {
            _state.update {
                it.copy(message = "Pemakaian dan total tidak boleh negatif.", messageIsError = true)
            }
            return
        }

        val store = Store.load(dataFile)
        val units = store.units.toMutableList()
        val index = when {
            existingId != null -> units.indexOfFirst { it.id == existingId }
            else -> units.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
        }

        val unit = StoredUnit(
            id = existingId ?: ReceiptCore.slugify(trimmed),
            name = trimmed,
            usageM3 = usageM3,
            total = total
        )

        if (index >= 0) {
            units[index] = unit
        } else {
            units.add(unit)
        }

        persist(store.copy(units = units), notify = false)
        _state.update {
            it.copy(
                selectedId = unit.id,
                message = if (index >= 0) "Unit '$trimmed' diperbarui." else "Unit '$trimmed' ditambahkan.",
                messageIsError = false
            )
        }
    }

    fun removeUnit(id: String) {
        val store = Store.load(dataFile)
        val target = store.units.firstOrNull { it.id == id } ?: return
        persist(store.copy(units = store.units.filterNot { it.id == id }), notify = false)
        _state.update { it.copy(message = "Unit '${target.name}' dihapus.", messageIsError = false) }
    }

    fun resetUnits() {
        val store = Store.load(dataFile)
        persist(store.copy(units = Store.defaultStore().units))
        _state.update { it.copy(message = "Daftar unit dikembalikan ke data bawaan.", messageIsError = false) }
    }

    fun selectUnit(id: String) {
        val bill = _state.value.receipts.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(selectedId = id, preview = ReceiptCore.buildPreview(bill)) }
    }

    fun refreshPreview() {
        val s = _state.value
        val bill = s.receipts.firstOrNull { it.id == s.selectedId } ?: return
        _state.update { it.copy(preview = ReceiptCore.buildPreview(bill)) }
    }

    fun setPrintDelay(delayMs: Long) {
        _state.update { it.copy(printDelayMs = delayMs.coerceIn(200, 15_000)) }
    }

    /* ---------------- Bluetooth ---------------- */

    fun refreshPrinters() {
        val bonded = BluetoothPrinter.bondedDevices(getApplication())
        val address = _state.value.selectedPrinterAddress
        val stillValid = address != null && bonded.any { it.address == address }
        _state.update {
            it.copy(
                devices = mergeDevices(it.devices, bonded),
                selectedPrinterAddress = if (stillValid) address else it.selectedPrinterAddress
            )
        }
    }

    fun startScan() {
        if (!BluetoothPrinter.hasPermissions(getApplication())) {
            _state.update {
                it.copy(message = "Izin Bluetooth diperlukan untuk mencari printer.", messageIsError = true)
            }
            return
        }
        if (!BluetoothPrinter.isBluetoothEnabled(getApplication())) {
            _state.update {
                it.copy(message = "Bluetooth sedang nonaktif. Nyalakan Bluetooth terlebih dahulu.", messageIsError = true)
            }
            return
        }

        scanJob?.cancel()
        _state.update { it.copy(scanning = true, message = "Mencari printer Bluetooth...", messageIsError = false) }
        scanJob = viewModelScope.launch {
            try {
                BluetoothPrinter.discoverDevices(getApplication()).collect { found ->
                    _state.update { current ->
                        current.copy(devices = mergeDevices(current.devices, found))
                    }
                }
            } catch (err: Exception) {
                _state.update {
                    it.copy(message = err.message ?: "Gagal mencari printer.", messageIsError = true)
                }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        BluetoothPrinter.cancelDiscovery(getApplication())
        _state.update { it.copy(scanning = false) }
        refreshPrinters()
    }

    private fun mergeDevices(current: List<PrinterDevice>, incoming: List<PrinterDevice>): List<PrinterDevice> {
        val merged = LinkedHashMap<String, PrinterDevice>()
        current.forEach { merged[it.address] = it }
        incoming.forEach { merged[it.address] = it }
        return merged.values.sortedWith(
            compareByDescending<PrinterDevice> { it.bonded }.thenBy { it.displayName.lowercase() }
        )
    }

    fun selectPrinter(address: String) {
        prefs().edit().putString(KEY_PRINTER_ADDRESS, address).apply()
        _state.update { it.copy(selectedPrinterAddress = address) }
    }

    fun checkPrinter() {
        val status = BluetoothPrinter.checkPrinterStatus(
            getApplication(),
            _state.value.selectedPrinterAddress
        )
        val text = if (status.isAvailable) {
            "Siap - ${status.name} (${status.address})"
        } else {
            status.status
        }
        _state.update { it.copy(message = text, messageIsError = !status.isAvailable) }
    }

    /* ---------------- Printing ---------------- */

    fun printOne(id: String) {
        val bill = _state.value.receipts.firstOrNull { it.id == id }
        if (bill == null) {
            _state.update { it.copy(message = "Unit tidak ditemukan.", messageIsError = true) }
            return
        }
        runPrint(listOf(bill))
    }

    fun printAll() {
        val bills = _state.value.receipts
        if (bills.isEmpty()) {
            _state.update { it.copy(message = "Belum ada unit untuk dicetak.", messageIsError = true) }
            return
        }
        runPrint(bills)
    }

    private fun runPrint(bills: List<ReceiptData>) {
        val address = _state.value.selectedPrinterAddress
        if (address.isNullOrBlank()) {
            _state.update {
                it.copy(message = "Pilih printer Bluetooth terlebih dahulu.", messageIsError = true)
            }
            return
        }
        if (!BluetoothPrinter.hasPermissions(getApplication())) {
            _state.update {
                it.copy(message = "Izin Bluetooth belum diberikan.", messageIsError = true)
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "Mencetak...", messageIsError = false) }
            var ok = 0
            var failed = 0

            bills.forEachIndexed { index, bill ->
                _state.update {
                    it.copy(message = "Mencetak ${index + 1}/${bills.size}: ${bill.name}...")
                }

                val payload = EscPos.buildReceipt(bill)
                val result = BluetoothPrinter.printRaw(getApplication(), address, payload)
                result.fold(
                    onSuccess = { ok++ },
                    onFailure = { failed++ }
                )

                if (index < bills.lastIndex && _state.value.printDelayMs > 0) {
                    delay(_state.value.printDelayMs)
                }
            }

            val message = when {
                failed == 0 && bills.size == 1 -> "Nota ${bills.first().name} berhasil dicetak."
                failed == 0 -> "Selesai: $ok nota berhasil dicetak."
                ok == 0 -> "Gagal mencetak: $failed nota gagal."
                else -> "Selesai: $ok berhasil, $failed gagal."
            }
            _state.update { it.copy(busy = false, message = message, messageIsError = failed > 0) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, messageIsError = false) }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    private companion object {
        const val PREFS = "nota_air_prefs"
        const val KEY_PRINTER_ADDRESS = "printer_address"
    }
}
