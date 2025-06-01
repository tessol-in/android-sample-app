package com.example.tessolsdk.ui2.activity.core

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import com.example.tessolsdk.ui2.activity.ScannerActivity.Companion.TARGET_EXTRA
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