buildscript {
    repositories {
        google()
        mavenCentral()
        maven {
            setUrl("https://repo.eclipse.org/content/repositories/paho-snapshots/")
        }
    }

    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.firebase.crashlytics.gradle)
        classpath(libs.google.services)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.osacky.doctor) apply false
}
