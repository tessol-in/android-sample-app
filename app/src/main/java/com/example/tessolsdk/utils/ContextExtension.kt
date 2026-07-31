package com.example.tessolsdk.utils

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import com.example.tessolsdk.ui.screens.scanner.ScannerActivity.Companion.TARGET_EXTRA
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface Target : Parcelable {
    @Parcelize data object Command : Target
}

fun <T: Activity> Activity.openActivity(clazz: Class<T>, target: Target) {
    startActivity(Intent(this, clazz).apply {
        putExtra(TARGET_EXTRA, target)
    })
}