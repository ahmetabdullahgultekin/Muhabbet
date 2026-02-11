package com.muhabbet.app.platform

import android.os.Build

actual fun getPlatformName(): String = "android"

actual fun getDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
