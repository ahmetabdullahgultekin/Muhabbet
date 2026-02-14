package com.muhabbet.app.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.muhabbet.app.db.MuhabbetDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(MuhabbetDatabase.Schema, "muhabbet.db")
    }
}
