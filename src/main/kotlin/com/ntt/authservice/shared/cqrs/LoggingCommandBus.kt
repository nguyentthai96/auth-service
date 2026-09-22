package com.ntt.authservice.shared.cqrs

import com.ntt.eventsourcingutils.lib.cqrs.command.Command
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandBus
import org.slf4j.LoggerFactory

/**
 * Logging decorator for CommandBus — wraps dispatch with timing and error logging.
 * OQ-005: Provides flow tracing for debugging and access monitoring.
 *
 * Logs: command name, execution time (ms), and errors with stack context.
 * Transparent to callers — implements same CommandBus interface.
 */
class LoggingCommandBus(private val delegate: CommandBus) : CommandBus {

    private val log = LoggerFactory.getLogger(LoggingCommandBus::class.java)

    override fun <R> dispatch(command: Command<R>): R {
        val commandName = command::class.simpleName ?: "UnknownCommand"
        log.info(">>> Dispatching command: {}", commandName)
        val startNanos = System.nanoTime()

        return try {
            val result = delegate.dispatch(command)
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            log.info("<<< Command {} completed in {}ms", commandName, elapsedMs)
            result
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            log.error("<<< Command {} FAILED after {}ms: {}", commandName, elapsedMs, e.message)
            throw e
        }
    }
}
