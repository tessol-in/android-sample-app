package com.example.tessolsdk.ui2.activity


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