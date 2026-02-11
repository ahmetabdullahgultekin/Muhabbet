package com.muhabbet.app.platform

import platform.UIKit.UIDevice

actual fun getPlatformName(): String = "ios"

actual fun getDeviceModel(): String = UIDevice.currentDevice.model
