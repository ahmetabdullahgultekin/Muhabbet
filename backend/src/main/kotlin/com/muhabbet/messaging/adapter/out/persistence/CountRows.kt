package com.muhabbet.messaging.adapter.out.persistence

import java.util.UUID

/**
 * A JPQL `SELECT id, COUNT(x) ... GROUP BY id` projection comes back as untyped tuples, so every
 * batch-count adapter has to unpack the same two columns. Ids whose count is zero produce no row
 * at all, which is why callers still have to supply their own default for a missing key.
 */
internal fun List<Array<Any>>.toCountById(): Map<UUID, Int> =
    associate { row -> (row[0] as UUID) to (row[1] as Long).toInt() }
