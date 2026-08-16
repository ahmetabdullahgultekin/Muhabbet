package com.muhabbet.app.di

import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform

/**
 * Starts Koin with [platformModule] + [appModule], or returns the already-running instance.
 *
 * Two call sites need this: `App()`, which runs the first time `MainActivity` (or the iOS entry
 * point) composes, and `MuhabbetFirebaseMessagingService.onNewToken` (androidMain), which the
 * system can invoke to deliver a rotated FCM token before `MainActivity` has ever run in the
 * process — without this, Koin would not exist yet at that call site (#398). `runCatching` covers
 * the race between the two: `KoinPlatform.getKoinOrNull()` can read null for both, and only one
 * `startKoin` call wins.
 */
fun bootstrapOrReuseKoin(platformModule: Module): Koin =
    KoinPlatform.getKoinOrNull() ?: runCatching {
        startKoin { modules(platformModule, appModule()) }.koin
    }.getOrElse { KoinPlatform.getKoin() }
