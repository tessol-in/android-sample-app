package com.example.tessolsdk.ui2.activity

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.tessolsdk.ui2.activity.ScannerActivity.Companion.DEVICE
import com.example.tessolsdk.ui2.activity.core.TessolComponentActivity
import `in`.tessol.tamsys.sdk.data.model.TemperatureData
import `in`.tessol.tamsys.sdk.data.model.TemperatureDump
import `in`.tessol.tamsys.sdk.data.model.TemperatureRecordV1
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class CommandsActivity : TessolComponentActivity() {
    private val tessolDumpState: MutableState<List<TemperatureData>> = mutableStateOf(emptyList())
    private val intervalState: MutableState<Boolean> = mutableStateOf(false)
    private val busyState: MutableState<Boolean> = mutableStateOf(false)
    private val messageState: MutableState<String?> = mutableStateOf(null)
    private val customerIdDialog: MutableState<Boolean> = mutableStateOf(false)

    private val commandController: `in`.tessol.tamsys.sdk.api.TessolCommandController by lazy {
        `in`.tessol.tamsys.sdk.api.TessolCommandController.getInstance(
            context = this
        )
    }

    private val deviceId: String?
        get() = intent.getStringExtra(DEVICE)

    @Composable
    override fun ComposableContent() {
        Dialogs()

        Box(modifier = Modifier.fillMaxSize()) {
            if (tessolDumpState.value.isNotEmpty()) {
                BackHandler {
                    tessolDumpState.value = emptyList()
                }

                TemperatureRecordsComposable(tessolDumpState.value)

            } else {
                CommandsComposable(
                    commands = getCommands(),
                    executeCommand = ::executeCommand
                )
            }


            if (busyState.value) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(listOf(Color.Black, Color.Black)),
                            alpha = 0.2F
                        )
                        .fillMaxSize()
                        .clickable(interactionSource = null, indication = null) { },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    @Composable
    private fun Dialogs() {
        val intervalInput = intervalState.value
        if (intervalInput) {
            val intervalCount = remember { mutableStateOf("") }
            val err = remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { intervalState.value = false },
                confirmButton = {
                    Button(
                        onClick = {
                            val value = intervalCount.value.toIntOrNull() ?: 0
                            val isError = value < 5 //15
                            err.value = isError
                            if (!isError) {
                                updateInterval(value)
                                intervalState.value = false
                            }
                        },
                        content = {
                            Text(text = "OK")
                        }
                    )
                },
                dismissButton = {
                    Button(
                        onClick = { intervalState.value = false },
                        content = { Text(text = "Cancel") }
                    )
                },
                title = { Text(text = "Data Acquisition Interval") },
                text = {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        value = intervalCount.value,
                        onValueChange = {
                            intervalCount.value = it
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        label = { Text(text = "Temperature recording interval in seconds (must be multiples of 5)") },
                        isError = err.value
                    )
                }
            )
        }

        val customerIdDialog = customerIdDialog.value
        if (customerIdDialog) {
            var customerID by remember { mutableIntStateOf(0) }
            var err by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { this@CommandsActivity.customerIdDialog.value = false },
                confirmButton = {
                    Button(
                        onClick = {
                            val isError = customerID !in 0..9999
                            err = isError
                            if (!isError) {
                                updateCustomerId(customerID)
                                this@CommandsActivity.customerIdDialog.value = false
                            }
                        },
                        content = {
                            Text(text = "OK")
                        }
                    )
                },
                dismissButton = {
                    Button(
                        onClick = { this@CommandsActivity.customerIdDialog.value = false },
                        content = { Text(text = "Cancel") }
                    )
                },
                title = { Text(text = "Customer Id") },
                text = {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        value = "$customerID",
                        onValueChange = {
                            it.toIntOrNull()?.let { value -> customerID = value } ?: run { err = true }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        label = { Text(text = "customer id - type a number ranging from 0 to 9999") },
                        isError = err
                    )
                }
            )
        }

        val message = messageState.value
        if (message != null) {
            AlertDialog(
                onDismissRequest = { messageState.value = null },
                confirmButton = {
                    Button(onClick = { messageState.value = null }) {
                        Text(text = "OK")
                    }
                },
                text = {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        text = "$message"
                    )
                }
            )
        }
    }

    @Composable
    private fun TemperatureRecordsComposable(temperatureData: List<TemperatureData>) {
        val record1 = temperatureData.first()
        val mac = record1.macAddress
        val customerId = record1.customerID
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = "Mac : $mac")
            Text(text = "Customer Id : $customerId")
            HorizontalDivider(thickness = 5.dp)
            Spacer(modifier = Modifier.height(15.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F),
                content = {
                    items(temperatureData) { data ->
                        Column(
                            Modifier.fillMaxWidth()) {
                            Text(text = "Time: ${data.epochTime}")
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(text = "Temp: ${data.internalTemp}")
                            VerticalDivider(thickness = 2.dp)
                            Spacer(modifier = Modifier.height(5.dp))
                        }
                        HorizontalDivider(thickness = 5.dp)
                    }
                }
            )
            if (temperatureData.isNotEmpty()){
                Button(modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.CenterHorizontally), onClick = { uploadData() }) {
                    Text("Upload Data")
                }
            }
        }
    }


    @Composable
    private fun CommandsComposable(
        commands: List<TessolCommand>,
        executeCommand: (TessolCommand) -> Unit
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .padding(all = 5.dp)
                .fillMaxSize(),
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            content = {
                items(commands) { command ->
                    val isFatal = command is TessolCommand.FactoryReset
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .border((0.5).dp, Color.Black, RoundedCornerShape(10))
                            .clickable { executeCommand(command) },
                        colors = (if (isFatal) Color.Red else Color.Cyan).let { color ->
                            CardDefaults.cardColors(containerColor = color, disabledContainerColor = color)
                        },
                        shape = RoundedCornerShape(10),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = command.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isFatal) Color.White else Color.Black
                            )
                        }
                    }
                }
            })
    }

    private fun getCommands(): List<TessolCommand> {
        return listOf(
            TessolCommand.SystemInfo,
            TessolCommand.SetInterval,
            TessolCommand.GetCusID,
            TessolCommand.SetCusID,
            TessolCommand.GetTime,
            TessolCommand.SetTime,
            TessolCommand.StartOperation,
            TessolCommand.StopOperation,
            TessolCommand.GetCurrentTemperature,
            TessolCommand.GetStoredSensorData,
            TessolCommand.FactoryReset
        )
    }


    private fun executeCommand(command: TessolCommand) {
        val id = deviceId ?: return
        when (command) {
            TessolCommand.FactoryReset -> {
                withBusyState {
                    val response = onIO { commandController.factoryReset(id) }
                    messageState.value = response.toString()
                }
            }

            TessolCommand.GetCurrentTemperature -> {
                withBusyState {
                    val response: TemperatureRecordV1 =
                        onIO { commandController.getCurrentTemp(id) }
                    messageState.value = response.userMessage()
                }
            }

            TessolCommand.GetStoredSensorData -> {
                busyState.value = true
                val result = commandController.streamRecordsFromDevice(deviceId = id, timeOut = 15_000L)
                processDumpV2(result)
            }

            TessolCommand.GetTime -> {
                withBusyState {
                    val time: Date = onIO { commandController.getTime(id).let(::Date) }
                    Log.d("CommandsActivity", "time is $time ${time.time}")
                    messageState.value = "Device time is $time"
                }
            }

            TessolCommand.SetInterval -> {
                intervalState.value = true
            }

            TessolCommand.SetTime -> {
                getTime { timeInMillis ->
                    withBusyState {
                        val result = onIO { commandController.setTime(id, timeInMillis) }
                        messageState.value = result.toString()
                    }
                }
            }

            TessolCommand.StartOperation -> {
                withBusyState {
                    val response = onIO { commandController.startOperation(id) }
                    messageState.value = response.toString()
                }
            }

            TessolCommand.StopOperation -> {
                withBusyState {
                    val response = onIO { commandController.stopOperation(id) }
                    messageState.value = response.toString()
                }
            }

            TessolCommand.SystemInfo -> {
                withBusyState {
                    val response = onIO { commandController.getSystemInfo(id) }
                    messageState.value = response.toString()
                }
            }

            TessolCommand.GetCusID -> {
                withBusyState {
                    val response = onIO { commandController.getCustomerId(id) }
                    messageState.value = response.getOrNull()?.let { "Customer Id: $it" } ?: "invalid result"
                }
            }

            TessolCommand.SetCusID -> {
                customerIdDialog.value = true
            }

            TessolCommand.UploadData -> Unit
        }
    }

    private fun processDumpV2(flow: Flow<TemperatureDump>) {
        flow
            .buffer(10)
            .onEach {
                if (busyState.value) {
                    busyState.value = false
                }
                when (it) {
                    TemperatureDump.Finished -> {
                        messageState.value = "All items emitted have been read"
                    }

                    is TemperatureDump.TemperatureDataWrapper -> {
                        delay(100)
                        tessolDumpState.value += listOf(it.temperatureData)
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /*private fun processDump(result: TemperatureDumpResult) {
        when (result) {
            is TemperatureDumpResult.Error -> {

            }

            TemperatureDumpResult.NoDataFound -> {

            }

            is TemperatureDumpResult.TemperatureRecordPacket -> {
                tessolDumpState.value = result.temperatureData
            }
        }
    }*/

    private fun updateInterval(intervalInSeconds: Int) {
        val id = deviceId ?: return
        withBusyState {
            val response =
                onIO { commandController.setInterval(id, intervalInSeconds) }
            messageState.value = response.toString()
        }
    }

    private fun updateCustomerId(customerId: Int) {
        val id = deviceId ?: return
        withBusyState {
            onIO { commandController.setCustomerId(deviceId = id, cusId = customerId) }
                .onSuccess { messageState.value = "customer id set successfully" }
                .onFailure { messageState.value = "failed to set customer id" }
        }
    }

    private fun getTime(setTime: (Long) -> Unit) {
//        val timeInMillis = Calendar.getInstance()
//            .apply {
//                timeInMillis = System.currentTimeMillis()
//                add(Calendar.DAY_OF_MONTH, -1)
//            }
//            .timeInMillis
        setTime(System.currentTimeMillis())
    }

    private fun ComponentActivity.withBusyState(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit
    ) {
        lifecycleScope.launch(dispatcher) {
            busyState.value = true
            block()
            busyState.value = false
        }
    }

    private suspend fun <T> onIO(block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.IO + CoroutineExceptionHandler { _, err ->
            err.printStackTrace()
            messageState.value = "Something went wrong!!"
        }, block)
    }


    private fun uploadData() {
        withBusyState {
            val data = ArrayList(tessolDumpState.value)
            commandController.uploadData(data)
        }
    }
}

internal sealed class TessolCommand(val label: String) {
    data object GetTime : TessolCommand("Get Time")
    data object SetTime : TessolCommand("Set Time")
    data object GetCusID : TessolCommand("Get Customer ID")
    data object SetCusID : TessolCommand("Set Customer ID")
    data object StartOperation : TessolCommand("Start Operation")
    data object StopOperation : TessolCommand("Stop Operation")
    data object GetCurrentTemperature : TessolCommand("Get Current Temp")
    data object GetStoredSensorData : TessolCommand("Get Stored Sensor Data")
    data object UploadData : TessolCommand("Upload")
    data object SetInterval : TessolCommand("Set Interval")
    data object FactoryReset : TessolCommand("Factory Reset")
    data object SystemInfo : TessolCommand("System Info")
}