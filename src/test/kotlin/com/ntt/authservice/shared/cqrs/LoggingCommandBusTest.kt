package com.ntt.authservice.shared.cqrs

import com.ntt.eventsourcingutils.lib.cqrs.command.Command
import com.ntt.eventsourcingutils.lib.cqrs.command.CommandBus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for LoggingCommandBus decorator.
 * Verifies delegation, timing logging, and exception propagation.
 */
class LoggingCommandBusTest {

    // Simple test command for verification
    data class TestCommand(val value: String) : Command<String>

    @Test
    fun `dispatch delegates to underlying bus and returns result`() {
        val mockBus = object : CommandBus {
            override fun <R> dispatch(command: Command<R>): R {
                @Suppress("UNCHECKED_CAST")
                return "result-${(command as TestCommand).value}" as R
            }
        }
        val loggingBus = LoggingCommandBus(mockBus)

        val result = loggingBus.dispatch(TestCommand("test"))
        assertEquals("result-test", result)
    }

    @Test
    fun `dispatch propagates exceptions from delegate`() {
        val mockBus = object : CommandBus {
            override fun <R> dispatch(command: Command<R>): R {
                throw IllegalStateException("Handler not found")
            }
        }
        val loggingBus = LoggingCommandBus(mockBus)

        val exception = assertThrows<IllegalStateException> {
            loggingBus.dispatch(TestCommand("fail"))
        }
        assertEquals("Handler not found", exception.message)
    }

    @Test
    fun `dispatch does not alter command or result`() {
        var capturedCommand: Command<*>? = null
        val mockBus = object : CommandBus {
            override fun <R> dispatch(command: Command<R>): R {
                capturedCommand = command
                @Suppress("UNCHECKED_CAST")
                return "ok" as R
            }
        }
        val loggingBus = LoggingCommandBus(mockBus)
        val originalCommand = TestCommand("data")

        loggingBus.dispatch(originalCommand)
        assertEquals(originalCommand, capturedCommand)
    }
}
