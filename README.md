# TessolSampleApp
# Tessol SDK Usage Manual

## Overview

This manual provides guidance on how to interact with Tessol devices using the `TessolCommandController` API and a reference implementation via the `CommandsActivityPublicSDK`. The SDK allows starting/stopping operations, syncing time, accessing temperature data, and more.

---

## Dependency

To include the Tessol SDK in your project, add the following dependency in your `build.gradle`:

```groovy
dependencies {
    implementation "in.tessol.tamsys:tamsys-sdk:1.0.1"
}

repositories {
    google()
    mavenCentral()
}
```



## Initialization

### Initialize sdk
The entry point for initializing the Tessol SDK in your application.
This object is designed to configure and prepare the SDK for use. You must call [initialize] before using any SDK functionality.
```kotlin
TessolSdk.initialize(
    AwsConfigs(
        deviceCertInputStream = context.assets.open("device-certificate.pem"),
        privateKeyInputStream = context.assets.open("private_key_pkcs8.pem")
    )
)
```

### Get an Instance

```kotlin
val controller = TessolCommandController.getInstance(context)
```

### Lifecycle Hooks

Call these methods when your component is active/inactive:

```kotlin
controller.acquire(context)   // Before using any functionality
controller.release(context)   // After you are done
```

---

## Device Operations

All operations require a valid `deviceId: String`.

### Start Operation

```kotlin
val result = controller.startOperation(deviceId)
```

### Stop Operation

```kotlin
val result = controller.stopOperation(deviceId)
```

### Get Device Time

```kotlin
val timeMillis = controller.getTime(deviceId)
```

### Set Device Time

```kotlin
val result = controller.setTime(deviceId, System.currentTimeMillis())
```

---

## Temperature & Sensor Data

### Get Current Temperature

```kotlin
val tempRecord = controller.getCurrentTemp(deviceId)
```

### Retrieve Stored Temperature Records

```kotlin
val result = controller.saveRecordsFromDevice(deviceId, timeOut = 10000L)
```

### Upload Data to Server

The SDK provides a simple method to upload locally stored records from connected devices to the server.

### ✅ Basic Usage

To upload all records from devices without any additional data:

```kotlin
val result = controller.uploadRecordsFromDevice()
```


### ✅ With Extra Data Usage

To upload all records from devices with additional data:

```kotlin
val testExtraData = mapOf(
    "batteryVoltage" to "1",
    "longitude" to "76.6897041",
    "latitude" to "30.7075106"
)

val uploadedRecordCount = controller.uploadRecordsFromDevice(extraData = testExtraData)
```
---

## Configuration

### Set Data Logging Interval

```kotlin
val result = controller.setInterval(deviceId, intervalInSeconds = 60)
```

> Minimum interval should be ≥ 5 seconds.

### Perform Factory Reset

```kotlin
val result = controller.factoryReset(deviceId)
```

---

## Get System Info

Returns metadata such as firmware version, data point count, etc.

```kotlin
val systemInfo = controller.getSystemInfo(deviceId)
```

---

## Reference UI - `CommandsActivityPublicSDK`

This activity demonstrates how to:

- Display command buttons in a grid
- Prompt for interval values via dialogs
- Execute commands with loading indicators
- Show result dialogs for success/failure messages

### Available Commands

| Command                 | Action Taken                                |
|-------------------------|---------------------------------------------|
| SystemInfo              | Fetches and displays system data            |
| SetInterval             | Opens dialog to set interval                |
| GetTime                 | Fetches and displays device time            |
| SetTime                 | Sets device time to current system time     |
| StartOperation          | Starts the device's operation               |
| StopOperation           | Stops the device's operation                |
| GetCurrentTemperature   | Fetches the latest temperature reading      |
| GetStoredSensorData     | Fetches stored temperature data             |
| UploadData              | Uploads stored data                         |
| FactoryReset            | Resets device to factory settings           |

---

## Notes

- Handle all operations within a coroutine scope.
- Always call `acquire()` before use and `release()` after use to avoid leaks.
- Commands like `SetInterval` and `UploadData` provide user feedback via dialogs in the UI layer.

 
