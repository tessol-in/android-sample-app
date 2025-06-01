package com.example.tessolsdk

import androidx.multidex.MultiDexApplication


class TessolApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        Thread.currentThread().setUncaughtExceptionHandler { t, e -> e.printStackTrace(); }
    }
}