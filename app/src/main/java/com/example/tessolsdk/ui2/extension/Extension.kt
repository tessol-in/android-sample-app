package com.example.tessolsdk.ui2.extension

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast


fun Context.toast(message: String, duration: Int = Toast.LENGTH_LONG) = Toast.makeText(this, message, duration).show()

fun Context.hasPermission(vararg permissions: String): Boolean = permissions.all { permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }