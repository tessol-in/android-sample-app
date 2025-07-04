package com.example.tessolsdk

import androidx.multidex.MultiDexApplication
import `in`.tessol.tamsys.v2.sdk.TessolSdk
import `in`.tessol.tamsys.v2.sdk.data.model.AwsConfigs


class TessolApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        TessolSdk.initialize(
            awsConfigs = AwsConfigs(
                deviceCertInputStream =  assets.open("device-certificate.pem"),
                privateKeyInputStream =  assets.open("private_key_pkcs8.pem")
            )
        )
        Thread.currentThread().setUncaughtExceptionHandler { t, e -> e.printStackTrace(); }
    }
}