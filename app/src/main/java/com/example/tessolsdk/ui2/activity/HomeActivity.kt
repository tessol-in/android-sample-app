package com.example.tessolsdk.ui2.activity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.tessolsdk.R
import com.example.tessolsdk.ui2.activity.core.Target
import com.example.tessolsdk.ui2.activity.core.TessolComponentActivity
import com.example.tessolsdk.ui2.activity.core.openActivity
import com.example.tessolsdk.ui2.extension.hasPermission
import com.example.tessolsdk.ui2.extension.toast
import androidx.compose.material3.AlertDialog as MaterialAlertDialog

class HomeActivity : TessolComponentActivity() {
    private val requestFeature: MutableState<Pair<Intent, String>?> = mutableStateOf(null)
    private val requestBLEFeatureTrigger: MutableState<Boolean> = mutableStateOf(false)
    private val requestActivityResultTrigger: MutableState<Pair<Intent, String>?> = mutableStateOf(null)
    private val requestPermissionTrigger: MutableState<List<String>?> = mutableStateOf(null)
    private val _dialogState: MutableState<Dialog> = mutableStateOf(Dialog.None)
    private val dialogState: State<Dialog> = _dialogState

    fun openTarget() {
        openActivity(ScannerActivity::class.java, Target.Command)
        finishAffinity()
    }

    @Composable
    override fun ComposableContent() {
        Content()
        Feature()
        Permission()
        ActivityResult()
        Dialog()
    }

    @Composable
    private fun Content() {
        Column(modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1F)
                    .padding(16.dp)
                    .clip(CircleShape),
                onClick = ::openTarget
            ) {
                Text(text = stringResource(R.string.label_manage_device), fontSize = 32.sp)
            }
        }
    }

    @Composable
    private fun Feature() {
        requestFeature.value?.let { (intent, message) ->
            val dismissDialog: (op: () -> Unit) -> Unit = { operation ->
                requestFeature.value = null
                operation.invoke()
            }
            AlertDialog(
                title = "Feature Request",
                message = message,
                onDismissRequest = {
                    dismissDialog {
                        toast("Unable to function without this feature, quitting app!!")
                        finish()
                    }
                },
                onConfirm = { dismissDialog { startActivity(intent) } }
            )
        }
    }

    @Composable
    private fun Permission() {
        val permissionRequest = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { result -> if (result.values.all { it }) checkAndRequestPermissions() else onPermissionDenied() }
        )
        requestPermissionTrigger.value?.let { permission -> LaunchedEffect(permission) { permissionRequest.launch(permission.toTypedArray()) } }
        requestBLEFeatureTrigger.value.let { shouldTrigger -> LaunchedEffect(shouldTrigger) { if (shouldTrigger) requestBluetooth() } }
        OnLifecycleEvent { _, event ->
            if (event.targetState.isAtLeast(Lifecycle.State.RESUMED)) {
                checkAndRequestPermissions()
            }
        }
    }

    @Composable
    private fun ActivityResult() {
        val activityResult = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = {
                val message = requestActivityResultTrigger.value?.second
                if (it.resultCode != RESULT_OK) {
                    message?.let { safeMessage -> toast(safeMessage) }
                    finish()
                }
            }
        )
        requestActivityResultTrigger.value?.let { (intent, _) -> LaunchedEffect(intent) { activityResult.launch(intent) } }
    }

    @Composable
    private fun Dialog() {
        val dismissDialog: (op: () -> Unit) -> Unit = { operation ->
            _dialogState.value = Dialog.None
            operation.invoke()
        }
        when (val dialog = dialogState.value) {
            Dialog.None -> Unit
            is Dialog.PermissionRationale -> {
                AlertDialog(
                    title = "Permission",
                    message = dialog.message,
                    onDismissRequest = { dismissDialog(::onPermissionDenied) },
                    onConfirm = { dismissDialog { requestPermissionTrigger.value = dialog.permission } }
                )
            }
        }
    }

    private fun onPermissionDenied() {
        toast("Please allow permission(s) in app setting, as the app is unable to function without the required permission(s)!!")
        finish()
    }

    private fun checkAndRequestPermissions() {
        onBluetoothHardwareFound {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissionFlowAPI31Later()
            else permissionFlowAPI30Lower()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun permissionFlowAPI31Later() {
        when {
            hasPermission(*(blePermissionsAPI31.toTypedArray())).not() -> {
                val permissions = blePermissionsAPI31
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)) {
                    _dialogState.value = Dialog.PermissionRationale(permission = permissions, "Please allow this permission to be able to search for Tessol BLE devices")
                } else {
                    requestPermissionTrigger.value = permissions
                }
            }

            else -> requestFeatures31()
        }
    }

    private fun permissionFlowAPI30Lower() {
        when {
            hasPermission(*(blePermission30.toTypedArray())).not() -> {
                val permissions = blePermission30
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)) {
                    _dialogState.value = Dialog.PermissionRationale(permission = permissions, "Please allow this permission to be able to search for Tessol BLE devices")
                } else {
                    requestPermissionTrigger.value = permissions
                }
            }

            else -> requestFeatures()
        }
    }

    private fun requestFeatures31() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestActivityResultTrigger.value = enableBluetoothIntent to "Please turn on bluetooth in android Setting"
        } else {
            requestBluetooth()
        }
    }

    private fun requestFeatures() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (gpsEnabled) requestBluetooth() else requestLocation()
    }

    private fun requestLocation() {
        requestFeature.value = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) to "Please enable location to be able to search and connect to Tessol BLE devices"
    }

    private fun requestBluetooth() {
        val bluetoothManager: BluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter.isEnabled.not())
            requestFeature.value = Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to "Please enable bluetooth to be able to search and connect to Tessol BLE devices"
    }

    private inline fun onBluetoothHardwareFound(op: () -> Unit) {
        // Check to see if the Bluetooth classic feature is available.
        val bluetoothAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        // Check to see if the BLE feature is available.
        val bluetoothLEAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        if (bluetoothAvailable && bluetoothLEAvailable) {
            op.invoke()
        } else {
            toast("This device does not have bluetooth/BLE feature!!")
            finish()
        }
    }

    companion object {
        val blePermissionsAPI31: List<String>
            @RequiresApi(Build.VERSION_CODES.S)
            get() = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        val blePermission30: List<String>
            get() = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

sealed interface Dialog {
    data object None : Dialog
    data class PermissionRationale(val permission: List<String>, val message: String) : Dialog
}

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(androidx.lifecycle.compose.LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event -> eventHandler.value(owner, event) }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun AlertDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { Button(onClick = onConfirm) { Text("OK") } },
        dismissButton = { Button(onClick = onDismissRequest) { Text("Cancel") } },
        title = { Text(title) },
        text = { Text(message) }
    )
}