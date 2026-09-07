package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.application.event.NewDeviceLoginEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant

class NewDeviceMailHandlerTest {

    private lateinit var handler: NewDeviceMailHandler
    private lateinit var mailQueueService: MailQueueService

    @BeforeEach
    fun setUp() {
        mailQueueService = mock(MailQueueService::class.java)
        handler = NewDeviceMailHandler(mailQueueService)
    }

    @Test
    fun `handleNewDeviceLogin enqueues mail with correct template data`() {
        val event = NewDeviceLoginEvent(
            userId = 1L,
            username = "john",
            email = "john@example.com",
            deviceFingerprint = "fp-abc123",
            deviceName = "Chrome on Windows",
            deviceType = "DESKTOP",
            browserName = "Chrome",
            osName = "Windows",
            ipAddress = "192.168.1.1",
            loginAt = Instant.now()
        )

        handler.handleNewDeviceLogin(event)

        verify(mailQueueService).enqueue(
            eq("john@example.com"),
            eq("NEW_DEVICE_LOGIN"),
            argThat { map ->
                map["userName"] == "john" &&
                map["deviceName"] == "Chrome on Windows" &&
                map["ipAddress"] == "192.168.1.1"
            },
            eq(1L)
        )
    }

    @Test
    fun `handleNewDeviceLogin skips when email is null`() {
        val event = NewDeviceLoginEvent(
            userId = 1L,
            username = "anon",
            email = null,
            deviceFingerprint = "fp-abc123",
            deviceName = "Unknown",
            ipAddress = "192.168.1.1",
            loginAt = Instant.now()
        )

        handler.handleNewDeviceLogin(event)

        verify(mailQueueService, never()).enqueue(any(), any(), any(), any())
    }
}
