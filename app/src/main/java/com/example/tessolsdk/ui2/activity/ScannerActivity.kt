package com.example.tessolsdk.ui2.activity

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.tessolsdk.ui2.activity.core.TessolComponentActivity
import `in`.tessol.tamsys.sdk.data.model.BLEPeripheral
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScannerActivity : TessolComponentActivity() {

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
            Log.d(TAG, "could not trigger scanning searching_value ${searching.value} isPaused $isPaused")
        }
    }

    private var isPaused = false
    private var startedSearching = false
    private var collectionJob: Job? = null
    private val tessolBLEScanner: `in`.tessol.tamsys.sdk.api.TessolBLEScanner by lazy { `in`.tessol.tamsys.sdk.api.TessolBLEScanner.getInstance(this) }
    private val devices: MutableStateFlow<List<BLEPeripheral>> = MutableStateFlow(emptyList())
    private val query: MutableStateFlow<String> = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val uiDevices: Flow<List<BLEPeripheral>>
        get() = combine(devices, query.debounce(500L)) { devices, query ->
            if (query.isBlank()) devices else devices.filter { it.macId.contains(query) }
        }
    private val searching = mutableStateOf(false)


    @Composable
    override fun ComposableContent() {
        ScannerComposable()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        handler.postDelayed(searchTrigger, 5000)
    }

    override fun onPause() {
        isPaused = true
        searching.value = false
        if (startedSearching) stopScanning()
        handler.removeCallbacks(searchTrigger)
        super.onPause()
    }

    @Composable
    private fun ScannerComposable(
        modifier: Modifier = Modifier
    ) {
        val deviceList = uiDevices.collectAsStateWithLifecycle(emptyList()).value
        val query = query.collectAsStateWithLifecycle().value

        Column(
            modifier = modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val content = @Composable {
                when {
                    !searching.value -> {
                        CircularProgressIndicator()
                        Text("Preparing scanner!!!")
                    }

                    deviceList.isEmpty() && query.isEmpty() -> {
                        Text(text = "Searching...")
                        Spacer(modifier = Modifier.height(10.dp))
                        CircularProgressIndicator()
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1F)
                                .background(Color.Cyan)
                                .padding(16.dp),
                            content = {
                                items(deviceList) { device: BLEPeripheral ->
                                    Text(
                                        text = "${device.name} \n ${device.macId}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .height(60.dp)
                                            .fillParentMaxWidth()
                                            .padding(5.dp)
                                            .border(1.dp, Color.Black, RoundedCornerShape(10))
                                            .clickable {
                                                lifecycleScope.launch {
                                                    tessolBLEScanner.stopScanning()
                                                    startNextActivity(device)
                                                }
                                            },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        )
                    }
                }
            }
            val queryBox = @Composable {
                var query by remember { mutableStateOf("") }
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    value = query,
                    onValueChange = {
                        this@ScannerActivity.query.value = it
                        query = it
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    placeholder = { Text("Search Device") },
                )
            }

            if (devices.collectAsStateWithLifecycle().value.isNotEmpty()) queryBox()
            content()
        }
    }

    private fun startNextActivity(device: BLEPeripheral) {
        /*val ignored: Target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(TARGET_EXTRA, Target::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(TARGET_EXTRA)
        }.let(::requireNotNull)*/

        val destination = CommandsActivityPublicSDK::class.java
        startActivity(Intent(this, destination).apply { putExtra(DEVICE, device.macId) })
        finish()
    }

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
                    .onEach { newDevices -> devices.update { devices -> (devices + newDevices).toSet().toList() } }
                    .launchIn(lifecycleScope)
            }
            .onFailure {
                Log.d(TAG, "failed to start scanning", it)
                it.printStackTrace()
            }
    }

    private fun stopScanning() {
        collectionJob?.cancel()
        tessolBLEScanner.release(this)
        tessolBLEScanner.stopScanning()
    }

    companion object {
        const val TARGET_EXTRA = "target"
        const val DEVICE = "device"
        const val TAG = "SCANNER_SCREEN"
    }
}
