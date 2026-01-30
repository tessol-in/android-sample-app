package com.example.tessolsdk.ui.screens.commands

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.tessolsdk.ui.screens.scanner.ScannerActivity
import com.example.tessolsdk.ui.theme.TessolTheme
import com.example.tessolsdk.utils.TessolCommand
import `in`.tessol.tamsys.v2.sdk.TessolCommandController

class CommandsActivityPublicSDK : ComponentActivity() {
    private val deviceId: String?
        get() = intent.getStringExtra(ScannerActivity.Companion.DEVICE)
    private val vm by viewModels<CommandsViewModel> { CommandsViewModelFactory(
        deviceId = intent.getStringExtra(ScannerActivity.Companion.DEVICE) ?: "-1",
        TessolCommandController.Companion.getInstance(
            context = applicationContext
        )
    ) }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TessolTheme {
                Surface(Modifier.fillMaxSize()) {
                    ComposableContent()
                }
            }
        }
    }

    @Composable
    private fun ComposableContent() {
        if (vm.showIntervalDialog){
            IntervalDialog(
                intervalState = vm.showIntervalDialog,
                onUpdateInterval = { newValue -> vm.updateInterval(newValue) },
                onDismiss = {
                    vm.showIntervalDialog(false)
                }
            )
        }

        vm.message?.let { msg ->
            MessageDialog(message = msg) {
                vm.showMessage(null)
            }
        }

        if(vm.isUploading){
            PeriodicUploadDialog(vm) {
                // Dialog dismissed
                vm.stopPeriodicUpload()
            }
        }


        Box(modifier = Modifier.Companion.fillMaxSize()) {
            CommandsComposable(
                commands = getCommands(),
                executeCommand = {
                    vm.executeCommand(it)
                }
            )

            if (vm.isLoading) {
                Box(
                    modifier = Modifier.Companion
                        .background(
                            brush = Brush.Companion.linearGradient(
                                listOf(
                                    Color.Companion.Black,
                                    Color.Companion.Black
                                )
                            ),
                            alpha = 0.2F
                        )
                        .fillMaxSize()
                        .clickable(interactionSource = null, indication = null) { },
                    contentAlignment = Alignment.Companion.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }


    @Composable
    fun IntervalDialog(
        intervalState: Boolean,
        onUpdateInterval: (Int) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (intervalState) {
            var intervalCount by remember { mutableStateOf("") }
            var hasError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { onDismiss() },
                confirmButton = {
                    Button(onClick = {
                        val value = intervalCount.toIntOrNull() ?: 0
                        hasError = value < 5 // minimum interval
                        if (!hasError) {
                            onUpdateInterval(value)
                        }
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    Button(onClick = { onDismiss() }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Data Acquisition Interval") },
                text = {
                    Column {

                        TextField(
                            modifier = Modifier.Companion
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            value = intervalCount,
                            onValueChange = {
                                intervalCount = it
                            },
                            keyboardOptions = KeyboardOptions.Companion.Default.copy(keyboardType = KeyboardType.Companion.Number),
                            singleLine = true,
                            label = { Text(text = "Temperature recording interval in seconds (must be multiples of 5)") },
                            isError = hasError
                        )

                        if (hasError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Interval must be at least 5 seconds",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun MessageDialog(
        message : String,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = {onDismiss() },
            confirmButton = {
                Button(onClick = { onDismiss()}) {
                    Text("OK")
                }
            },
            title = { Text("Message") },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    @Composable
    fun PeriodicUploadDialog(
        viewModel: CommandsViewModel,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = { /* prevent dismiss */ },
            title = { Text("Periodic Upload") },
            text = {
                Column {
                    vm.uploadMessage?.let { Text(it) }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (vm.nextUploadInSeconds > 0){
                        Text("Next upload in: ${vm.nextUploadInSeconds} seconds")
                    }

                    if (vm.uploadMessage == "Uploading..."){
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }else{
                        LinearProgressIndicator(
                            progress = (60 - vm.nextUploadInSeconds) / 60f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.stopPeriodicUpload()
                    onDismiss()
                }) {
                    Text("Stop")
                }
            }
        )
    }

    @Composable
    private fun CommandsComposable(
        commands: List<TessolCommand>,
        executeCommand: (TessolCommand) -> Unit
    ) {
        LazyVerticalGrid(
            modifier = Modifier.Companion
                .padding(all = 5.dp)
                .fillMaxSize(),
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            content = {
                items(commands) { command ->
                    val isFatal = command is TessolCommand.FactoryReset
                    Card(
                        modifier = Modifier.Companion
                            .aspectRatio(1f)
                            .border((0.5).dp, Color.Companion.Black, RoundedCornerShape(10))
                            .clickable { executeCommand(command) },
                        colors = (if (isFatal) Color.Companion.Red else Color.Companion.Cyan).let { color ->
                            CardDefaults.cardColors(
                                containerColor = color,
                                disabledContainerColor = color
                            )
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier.Companion
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.Companion.Center
                        ) {
                            Text(
                                text = command.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Companion.Medium,
                                color = if (isFatal) Color.Companion.White else Color.Companion.Black
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
            TessolCommand.PeriodicUpload,
            TessolCommand.FactoryReset
        )
    }
}