package com.muhabbet.messaging.domain.port.out

/**
 * The messaging module's name for the shared transaction-boundary port. The port itself moved to
 * [com.muhabbet.shared.transaction.TransactionRunner] when the auth module needed the same boundary
 * (#886: no SMS provider call inside a transaction); an alias keeps every messaging import valid
 * and leaves exactly one type, so one Spring bean satisfies both modules.
 */
typealias TransactionRunner = com.muhabbet.shared.transaction.TransactionRunner
