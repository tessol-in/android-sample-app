package com.example.tessolsdk.ui.screens.scanner

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.tessolsdk.ui.screens.commands.CommandsActivityPublicSDK
import com.example.tessolsdk.ui.theme.TessolTheme
import `in`.tessol.tamsys.v2.sdk.model.BLEPeripheral
import `in`.tessol.tamsys.v2.sdk.scanner.TessolBLEScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScannerActivity : ComponentActivity() {
    private val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }
    private val searchTrigger: Runnable = Runnable {
        Log.d(TAG, "scheduled scanning")
        Log.d(TAG, "trigger scanning")
        if (!searching.value && !isPaused) {
            searching.value = true
            startScanning()
        } else {
            Log.d(
                TAG,
                "could not trigger scanning searching_value ${searching.value} isPaused $isPaused"
            )
        }
    }

    private var isPaused = false
    private var startedSearching = false
    private var collectionJob: Job? = null
    private val tessolBLEScanner: TessolBLEScanner by lazy { TessolBLEScanner.Companion.getInstance(this) }

    private val devices: MutableStateFlow<List<BLEPeripheral>> = MutableStateFlow(emptyList())
    private val query: MutableStateFlow<String> = MutableStateFlow("")

    private val uiDevices: Flow<List<BLEPeripheral>>
        get() = combine(devices, query.debounce(500L)) { devices, query ->
            if (query.isBlank()) devices else devices.filter { it.macId.contains(query) }
        }
    private val searching = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TessolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScannerScreen()
                }
            }
        }

    }


    override fun onResume() {
        super.onResume()
        isPaused = false
        handler.postDelayed(searchTrigger, 100)
    }

    override fun onPause() {
        isPaused = true
        searching.value = false
        if (startedSearching) stopScanning()
        handler.removeCallbacks(searchTrigger)
        super.onPause()
    }


    private fun stopScanning() {
        collectionJob?.cancel()
        tessolBLEScanner.release(this)
        tessolBLEScanner.stopScanning()
    }

    //
    private fun startScanning() {
        collectionJob?.cancel()
        runCatching {
            Log.d(TAG, "start scanning")
            tessolBLEScanner.acquire(this)
            startedSearching = true
            tessolBLEScanner.scanForDevices(this)
        }
            .onSuccess {
                Log.d(TAG, "start collecting")
                collectionJob = it
                    .onEach { newDevices ->
                        devices.update { devices ->
                            (devices + newDevices).toSet().toList()
                        }
                    }
                    .launchIn(lifecycleScope)
            }
            .onFailure {
                Log.d(TAG, "failed to start scanning", it)
                it.printStackTrace()
            }
    }
    private fun startNextActivity(device: BLEPeripheral) {
        startActivity(Intent(this,  CommandsActivityPublicSDK::class.java).apply { putExtra(DEVICE, device.macId)})
        finish()
    }



    @Composable
    fun ScannerScreen() {
        val devices = uiDevices.collectAsStateWithLifecycle(emptyList()).value
        val isScanning = searching.value
        val queryText by query.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            ScannerTopBar()
            SearchBar(
                query = queryText,
                onQueryChange = { query.value = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScannerContent(
                isScanning = isScanning,
                devices = devices,
                onDeviceClick = { device ->
                    lifecycleScope.launch {
                        tessolBLEScanner.stopScanning()
                        startNextActivity(device)
                    }
                }
            )
        }
    }
    @Composable
    private fun ScannerTopBar() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Scan Devices",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nearby Tessol BLE devices",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    @Composable
    private fun SearchBar(
        query: String,
        onQueryChange: (String) -> Unit
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search by device name or MAC ID") },
            shape = RoundedCornerShape(14.dp)
        )
    }
    @Composable
    private fun ScannerContent(
        isScanning: Boolean,
        devices: List<BLEPeripheral>,
        onDeviceClick: (BLEPeripheral) -> Unit
    ) {
        when {
            isScanning && devices.isEmpty() -> {
                ScannerLoading("Searching for devices…")
            }

            !isScanning && devices.isEmpty() -> {
                ScannerEmpty("No devices found")
            }

            else -> {
                DeviceList(devices, onDeviceClick)
            }
        }
    }
    @Composable
    private fun DeviceList(
        devices: List<BLEPeripheral>,
        onDeviceClick: (BLEPeripheral) -> Unit
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(devices) { device ->
                DeviceCard(device) {
                    onDeviceClick(device)
                }
            }
        }
    }
    @Composable
    private fun DeviceCard(
        device: BLEPeripheral,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = device.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.macId,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    @Composable
    private fun ScannerLoading(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(message)
            }
        }
    }
    @Composable
    private fun ScannerEmpty(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    companion object {
        const val TARGET_EXTRA = "target"
        const val DEVICE = "device"
        const val TAG = "SCANNER_SCREEN"
    }
}
