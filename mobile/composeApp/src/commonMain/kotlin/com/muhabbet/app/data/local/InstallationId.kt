package com.muhabbet.app.data.local

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The stable id of this app installation, created on first use (#882). See
 * [TokenStorage.getInstallationId] for where it lives and why it outlives a sign-out.
 */
@OptIn(ExperimentalUuidApi::class)
fun TokenStorage.installationId(): String =
    getInstallationId() ?: Uuid.random().toString().also { setInstallationId(it) }
