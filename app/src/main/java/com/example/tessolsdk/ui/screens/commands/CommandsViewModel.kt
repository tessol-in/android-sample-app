package com.example.tessolsdk.ui.screens.commands

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.tessolsdk.ui.screens.scanner.ScannerActivity
import com.example.tessolsdk.utils.TessolCommand
import `in`.tessol.tamsys.v2.sdk.TessolCommandController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date



class CommandsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val deviceController: TessolCommandController
) : ViewModel() {
    private val deviceId: String =
        savedStateHandle[ScannerActivity.Companion.DEVICE] ?: error("Missing deviceId")

    var isLoading by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var showIntervalDialog by mutableStateOf(false)
        private set

    fun showMessage(value: String?) {
        message = value
    }

    fun showIntervalDialog(show: Boolean) {
        showIntervalDialog = show
    }

    fun <T> run(
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true

            block()
                .onSuccess { result ->
                    onSuccess(result)
                }
                .onFailure { error ->
                    message = error.message ?: "Something went wrong"
                }

            isLoading = false
        }
    }


    private suspend fun <T> onIO(block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.IO + CoroutineExceptionHandler { _, err ->
            err.printStackTrace()
            showMessage("Something went wrong!!")
        }, block)
    }

    fun updateInterval(intervalInSeconds: Int) {
        showIntervalDialog(false)
        run(
            block = { deviceController.setInterval(deviceId, intervalInSeconds)  },
            onSuccess = { data ->
                message = data.toString()
            }
        )
    }


    private var periodicUploadJob: Job? = null
    // --- UI states for Compose dialog --
    var isUploading by mutableStateOf(false)
        private set
    var nextUploadInSeconds by mutableStateOf(0)
        private set
    var uploadMessage by mutableStateOf<String?>(null)
        private set
    fun startPeriodicUpload() {
        stopPeriodicUpload() // cancel previous if any
        isUploading = true

        periodicUploadJob = viewModelScope.launch {
            while (isActive) {
                try {
                    var countdown = 60 // seconds
                    nextUploadInSeconds = 0
                    // Execute periodic upload
                    uploadMessage = "Uploading..."
                    val uploadedCount = executePeriodicUpload(deviceId)
                    uploadMessage = "Upload completed: $uploadedCount records"
                    delay(5000L)
                    uploadMessage = "Waiting..."
                    while (countdown > 0 && isActive) {
                        nextUploadInSeconds = countdown
                        delay(1000L)
                        countdown--
                    }
                } catch (e: Exception) {
                    uploadMessage = "Error: ${e.message ?: "Unknown"}"
                }
            }
        }
    }

    private suspend fun executePeriodicUpload(deviceId: String): Int {
        var  message = "Reading system info"
        Log.i("periodicTask", message)
        val systemInfo = deviceController.getSystemInfo(deviceId).getOrThrow()
        val dataPointCount = systemInfo.dataPointCount
        message = "$dataPointCount records found, saving from device"
        delay(5000L)
        Log.i("periodicTask", message)
        val savedCount = deviceController.saveRecordsFromDevice(deviceId, 10_000L).getOrThrow()
        message = "$savedCount records saved, uploading to server"
        Log.i("periodicTask", message)
        val dummyExtraData = mapOf(
            "batteryVoltage" to "1",
            "longitude" to "76.6897041",
            "latitude" to "30.7075106"
        )

        return deviceController.uploadRecordsFromDevice(dummyExtraData).getOrThrow()
    }



    fun stopPeriodicUpload() {
        periodicUploadJob?.cancel()
        periodicUploadJob = null
        isUploading = false
        nextUploadInSeconds = 0
        uploadMessage = null
    }

    fun executeCommand(command: TessolCommand) {
        val id = deviceId
        when (command) {

            TessolCommand.SystemInfo -> {
                run(
                    block = { deviceController.getSystemInfo(id) },
                    onSuccess = { data ->
                        message = data.toString()
                    }
                )
            }
            TessolCommand.SetInterval -> {
                showIntervalDialog(true)
            }
            TessolCommand.PeriodicUpload -> {
                startPeriodicUpload()
            }


            TessolCommand.GetTime -> {
                run(
                    block = { deviceController.getTime(id) },
                    onSuccess = { result ->
                        message = "Device time is ${Date(result.value)}"
                    }
                )
            }

            TessolCommand.StartOperation -> {
                run(
                    block = { deviceController.startOperation(id) },
                    onSuccess = { data ->
                        message = data.toString()
                    }
                )
            }

            TessolCommand.StopOperation -> {
                run(
                    block = { deviceController.stopOperation(id) },
                    onSuccess = { data ->
                        message = data.toString()
                    }
                )
            }
            TessolCommand.GetCurrentTemperature -> {
                run(
                    block = { deviceController.getCurrentTemp(id) },
                    onSuccess = { data ->
                        message = data.toString()
                    }
                )
            }

            TessolCommand.SetTime -> {
                getTime { timeInMillis ->
                    run(
                        block = {
                            deviceController.setTime(id, timeInMillis)
                        },
                        onSuccess = { data ->
                            message = data.toString()
                        }
                    )
                }
            }
            TessolCommand.FactoryReset -> {
                run(
                    block = {
                        deviceController.factoryReset(id)
                    },
                    onSuccess = {data ->
                        message = data.toString()
                    }
                )
            }
            TessolCommand.GetStoredSensorData -> {
                run(
                    block = {
                        message = "reading data point count"
                        delay(1500L)
                        deviceController.getSystemInfo(id)
                            .onSuccess { result ->
                                val dataPointCount = result.dataPointCount
                                message =  "$dataPointCount records in device, reading records"
                                delay(1500L)
                                deviceController.saveRecordsFromDeviceV2(id,10000L)
                                    .onSuccess { savedRecords ->
                                        message = "$savedRecords records are saved out of $dataPointCount records"
                                    }
                            }
                            .onFailure { error ->
                                message = error.message ?: "Something went wrong"
                            }
                    },
                    onSuccess = { result ->

                    }
                )
            }

            TessolCommand.UploadData -> {
                run(
                    block = {
                        message = "uploading records"
                        deviceController.uploadRecordsFromDevice()
                    },
                    onSuccess = {uploadedRecordCount ->
                        message =  "records are uploaded, count = $uploadedRecordCount"
                    }
                )
            }
            TessolCommand.UploadCurrentTemperature -> {
                run(
                    block = {
                        val dummyExtraData = mapOf(
                            "batteryVoltage" to "1",
                            "longitude" to "76.6897041",
                            "latitude" to "30.7075106"
                        )
                        deviceController.uploadCurrentTemperature(id,dummyExtraData) },
                    onSuccess = { data ->
                        message = data.toString()
                    }
                )
            }
            else -> {

            }
        }
    }

    private fun getTime(setTime: (Long) -> Unit) {
        val timeInMillis = Calendar.getInstance()
            .apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_MONTH, -1)
            }
            .timeInMillis
        setTime(System.currentTimeMillis())
    }
}


class CommandsViewModelFactory(
    private val deviceId: String,
    private val controller: TessolCommandController
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        if (modelClass.isAssignableFrom(CommandsViewModel::class.java)) {
            val savedStateHandle = extras.createSavedStateHandle()
            // Inject initial state
            savedStateHandle["device_id"] = deviceId
            @Suppress("UNCHECKED_CAST")
            return CommandsViewModel(savedStateHandle, controller) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}