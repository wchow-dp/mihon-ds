package tachiyomi.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne

/**
 * Compatibility shim for Mihon DS's SyncYomi integration.
 *
 * Upstream removed DatabaseHandler in v0.20.0 and injects [Database] directly. Mihon DS's
 * SyncManager is built around the old call shapes, so this keeps the subset it uses rather
 * than rewriting every sync query.
 */
class DatabaseHandler(
    private val database: Database,
) {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend Database.() -> T): T {
        return if (inTransaction) {
            database.transactionWithResult { database.block() }
        } else {
            database.block()
        }
    }

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>,
    ): List<T> = await(inTransaction) { block().awaitAsList() }

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>,
    ): T = await(inTransaction) { block().awaitAsOne() }
}
