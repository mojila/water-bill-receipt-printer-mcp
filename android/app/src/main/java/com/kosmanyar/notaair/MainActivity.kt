package com.kosmanyar.notaair

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.kosmanyar.notaair.printer.BluetoothPrinter
import com.kosmanyar.notaair.ui.NotaAirScreen
import com.kosmanyar.notaair.ui.NotaAirTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NotaAirTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    if (grants.values.all { it }) {
                        viewModel.refreshPrinters()
                        viewModel.startScan()
                    } else {
                        Toast.makeText(
                            this,
                            "Izin Bluetooth dibutuhkan untuk mencari printer.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    val legacyLocationNeeded =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED

                    if (!BluetoothPrinter.hasPermissions(this@MainActivity) || legacyLocationNeeded) {
                        permissionLauncher.launch(BluetoothPrinter.REQUIRED_PERMISSIONS)
                    }
                }

                NotaAirScreen(
                    state = state,
                    onScan = {
                        if (BluetoothPrinter.hasPermissions(this@MainActivity)) {
                            viewModel.startScan()
                        } else {
                            permissionLauncher.launch(BluetoothPrinter.REQUIRED_PERMISSIONS)
                        }
                    },
                    onStopScan = viewModel::stopScan,
                    onSelectPrinter = viewModel::selectPrinter,
                    onCheckPrinter = viewModel::checkPrinter,
                    onSelectUnit = viewModel::selectUnit,
                    onSaveSettings = viewModel::saveSettings,
                    onSaveUnit = viewModel::upsertUnit,
                    onDeleteUnit = viewModel::removeUnit,
                    onResetUnits = viewModel::resetUnits,
                    onSetPrintDelay = viewModel::setPrintDelay,
                    onPrintOne = viewModel::printOne,
                    onPrintAll = viewModel::printAll,
                    onCancelPrinting = viewModel::cancelPrinting,
                    onRefreshPreview = viewModel::refreshPreview,
                    onMessageShown = viewModel::consumeMessage
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPrinters()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopScan()
    }
}
