package com.example.tessolsdk

import androidx.multidex.MultiDexApplication
import `in`.tessol.tamsys.v2.sdk.TessolSdk


class TessolApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        TessolSdk.initialize()
        Thread.currentThread().setUncaughtExceptionHandler { t, e -> e.printStackTrace(); }
    }
}