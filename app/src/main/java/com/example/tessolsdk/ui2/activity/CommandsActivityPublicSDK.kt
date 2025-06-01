package com.example.tessolsdk.ui2.activity

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import `in`.tessol.tamsys.sdk.data.model.TemperatureRecordV1
import `in`.tessol.tamsys.v2.sdk.TessolCommandController
import `in`.tessol.tamsys.v2.sdk.model.SaveRecordResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class CommandsActivityPublicSDK : TessolComponentActivity() {
    private val intervalState: MutableState<Boolean> = mutableStateOf(false)
    private val busyState: MutableState<Boolean> = mutableStateOf(false)
    private val messageState: MutableState<String?> = mutableStateOf(null)

    private val commandController: TessolCommandController by lazy {
        TessolCommandController.getInstance(
            context = this
        )
    }

    private val deviceId: String?
        get() = intent.getStringExtra(DEVICE)

    @Composable
    override fun ComposableContent() {
        Dialogs()

        Box(modifier = Modifier.fillMaxSize()) {
            CommandsComposable(
                commands = getCommands(),
                executeCommand = ::executeCommand
            )

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
                        text = message
                    )
                }
            )
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
            TessolCommand.GetTime,
            TessolCommand.SetTime,
            TessolCommand.StartOperation,
            TessolCommand.StopOperation,
            TessolCommand.GetCurrentTemperature,
            TessolCommand.GetStoredSensorData,
            TessolCommand.UploadData,
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
                withBusyState {
                    runCatching {
                        messageState.value = "reading data point count"
                        delay(1500L)
                        val dataPointCount = commandController.getSystemInfo(id).dataPointCount
                        messageState.value = "$dataPointCount records in device, reading records"
                        delay(1500L)
                        val savedRecords: Result<SaveRecordResponse> = commandController.saveRecordsFromDevice(id, 10000L)
                        messageState.value = "$savedRecords records are saved out of $dataPointCount records"
                    }.onFailure {
                        messageState.value = "Something went wrong, Please try again later. \n ${it.message} \nPlease take a screen shot of this pop up"
                    }
                }
            }

            TessolCommand.UploadData -> {
                withBusyState {
                    runCatching {
                        messageState.value = "uploading records"
                        val uploadedRecordCount = commandController.uploadData().getOrDefault(defaultValue = 0)
                        messageState.value = "records are uploaded, count = $uploadedRecordCount"
                    }.onFailure {
                        messageState.value = "Something went wrong, Please try again later. \n ${it.message} \nPlease take a screen shot of this pop up"
                    }
                }
            }

            TessolCommand.GetTime -> {
                withBusyState {
                    val time: Date = onIO { commandController.getTime(id).let(::Date) }
                    Log.d("CommandsActivityPublicSDK", "time is $time ${time.time}")
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

            TessolCommand.GetCusID -> Unit

            TessolCommand.SetCusID -> Unit
        }
    }

    private fun updateInterval(intervalInSeconds: Int) {
        val id = deviceId ?: return
        withBusyState {
            val response =
                onIO { commandController.setInterval(id, intervalInSeconds) }
            messageState.value = response.toString()
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
}