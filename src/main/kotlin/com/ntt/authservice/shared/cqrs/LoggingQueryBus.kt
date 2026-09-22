package com.ntt.authservice.shared.cqrs

import com.ntt.eventsourcingutils.lib.cqrs.query.Query
import com.ntt.eventsourcingutils.lib.cqrs.query.QueryBus
import org.slf4j.LoggerFactory

/**
 * Logging decorator for QueryBus — wraps dispatch with timing and error logging.
 * Symmetric with LoggingCommandBus for consistent flow tracing.
 */
class LoggingQueryBus(private val delegate: QueryBus) : QueryBus {

    private val log = LoggerFactory.getLogger(LoggingQueryBus::class.java)

    override fun <R> dispatch(query: Query<R>): R {
        val queryName = query::class.simpleName ?: "UnknownQuery"
        log.debug(">>> Dispatching query: {}", queryName)
        val startNanos = System.nanoTime()

        return try {
            val result = delegate.dispatch(query)
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            log.debug("<<< Query {} completed in {}ms", queryName, elapsedMs)
            result
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            log.error("<<< Query {} FAILED after {}ms: {}", queryName, elapsedMs, e.message)
            throw e
        }
    }
}
