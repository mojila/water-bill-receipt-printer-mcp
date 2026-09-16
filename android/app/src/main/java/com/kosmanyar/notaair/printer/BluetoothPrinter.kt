package com.kosmanyar.notaair.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kosmanyar.notaair.data.ReceiptCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class PrinterDevice(
    val name: String,
    val address: String,
    val bonded: Boolean
) {
    val displayName: String get() = name.ifBlank { "Perangkat tanpa nama" }
}

data class PrinterStatus(
    val name: String,
    val isAvailable: Boolean,
    val address: String,
    val bonded: Boolean,
    val status: String
)

/**
 * Bluetooth Classic (RFCOMM/SPP) transport for ESC/POS thermal printers such as the
 * EPPOS EP5821. Replaces the Windows Spooler path used by the desktop and MCP builds.
 */
object BluetoothPrinter {

    /** Well-known Serial Port Profile UUID used by virtually all ESC/POS thermal printers. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val SOCKET_FLUSH_DELAY_MS = 250L

    /** Runtime permissions needed to scan for and connect to a printer. */
    val REQUIRED_PERMISSIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(context: Context): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isBluetoothEnabled(context: Context): Boolean = adapter(context)?.isEnabled == true

    /** Printers already paired in Android Bluetooth settings. */
    fun bondedDevices(context: Context): List<PrinterDevice> {
        if (!hasPermissions(context)) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        return adapter.bondedDevices
            .orEmpty()
            .map { it.toPrinterDevice(bonded = true) }
            .sortedBy { it.displayName.lowercase() }
    }

    fun findBonded(context: Context, address: String?): PrinterDevice? {
        if (address.isNullOrBlank()) return null
        return bondedDevices(context).firstOrNull { it.address == address }
    }

    /**
     * Discover nearby Bluetooth devices as a stream of results. Emits the current set on
     * every discovery event, and completes when discovery finishes.
     */
    fun discoverDevices(context: Context): Flow<List<PrinterDevice>> = callbackFlow {
        if (!hasPermissions(context)) {
            close(SecurityException("Izin Bluetooth belum diberikan."))
            return@callbackFlow
        }

        val adapter = adapter(context)
        if (adapter == null) {
            close(IOException("Perangkat ini tidak memiliki Bluetooth."))
            return@callbackFlow
        }

        val found = LinkedHashMap<String, PrinterDevice>()
        bondedDevices(context).forEach { found[it.address] = it }
        trySend(found.values.toList())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            found[it.address] = it.toPrinterDevice(bonded = it.bondState == BluetoothDevice.BOND_BONDED)
                            trySend(found.values.toList().sortedBy { d -> d.displayName.lowercase() })
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> trySend(
                        found.values.toList().sortedBy { d -> d.displayName.lowercase() }
                    )

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            found[it.address] = it.toPrinterDevice(bonded = it.bondState == BluetoothDevice.BOND_BONDED)
                            trySend(found.values.toList().sortedBy { d -> d.displayName.lowercase() })
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        if (adapter.isDiscovering) adapter.cancelDiscovery()
        adapter.startDiscovery()

        awaitClose {
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // receiver already unregistered
            } catch (_: SecurityException) {
                // permission revoked mid-scan
            }
        }
    }

    /** Stop an in-flight discovery pass. */
    fun cancelDiscovery(context: Context) {
        val adapter = adapter(context) ?: return
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        } catch (_: SecurityException) {
            // ignore
        }
    }

    /** Diagnose whether a given printer is reachable, mirroring `check_printer` on desktop. */
    fun checkPrinterStatus(context: Context, address: String?): PrinterStatus {
        val label = address?.takeIf { it.isNotBlank() } ?: ReceiptCore.DEFAULT_PRINTER_NAME
        if (!hasPermissions(context)) {
            return PrinterStatus(
                name = label,
                isAvailable = false,
                address = address.orEmpty(),
                bonded = false,
                status = "Izin Bluetooth belum diberikan. Buka Pengaturan lalu izinkan akses perangkat sekitar."
            )
        }

        val adapter = adapter(context)
            ?: return PrinterStatus(label, false, address.orEmpty(), false, "Perangkat ini tidak memiliki Bluetooth.")

        if (!adapter.isEnabled) {
            return PrinterStatus(label, false, address.orEmpty(), false, "Bluetooth sedang nonaktif. Nyalakan Bluetooth terlebih dahulu.")
        }

        val target = findBonded(context, address)
            ?: return PrinterStatus(
                label,
                false,
                address.orEmpty(),
                false,
                "Printer belum dipilih atau belum dipasangkan. Tekan 'Cari Printer' lalu pilih EPPOS EP5821."
            )

        return PrinterStatus(
            name = target.displayName,
            isAvailable = true,
            address = target.address,
            bonded = true,
            status = "Terpasang (paired) di Bluetooth"
        )
    }

    /**
     * Open an RFCOMM socket, write the raw ESC/POS payload, then close. The socket is
     * created per job so a stale connection never blocks the next print.
     */
    suspend fun printRaw(context: Context, address: String, payload: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!hasPermissions(context)) {
                return@withContext Result.failure(
                    SecurityException("Izin Bluetooth belum diberikan.")
                )
            }

            val adapter = adapter(context)
                ?: return@withContext Result.failure(IOException("Perangkat ini tidak memiliki Bluetooth."))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(IOException("Bluetooth sedang nonaktif."))
            }

            // Discovery competes with the connection and makes it fail or stall.
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
            } catch (_: SecurityException) {
                // ignore
            }

            val device = try {
                adapter.getRemoteDevice(address)
            } catch (err: IllegalArgumentException) {
                return@withContext Result.failure(
                    IOException("Alamat printer '$address' tidak valid.", err)
                )
            }

            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val output = socket.outputStream
                output.write(payload)
                output.flush()

                // Give the printer time to drain the buffer before the socket closes.
                Thread.sleep(SOCKET_FLUSH_DELAY_MS)

                Result.success(Unit)
            } catch (err: Exception) {
                Result.failure(
                    IOException(
                        "Gagal mengirim data ke printer ${device.nameOrAddress()}: ${err.message}",
                        err
                    )
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                    // closing a broken socket can throw; nothing useful to do
                }
            }
        }

    private fun BluetoothDevice.toPrinterDevice(bonded: Boolean) = PrinterDevice(
        name = try {
            name.orEmpty()
        } catch (_: SecurityException) {
            ""
        },
        address = address,
        bonded = bonded
    )

    private fun BluetoothDevice.nameOrAddress(): String = try {
        name ?: address
    } catch (_: SecurityException) {
        address
    }
}
