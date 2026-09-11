package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.application.event.NewDeviceLoginEvent
import com.ntt.notification.client.NotificationPort
import com.ntt.notification.client.NotificationRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant

class NewDeviceMailHandlerTest {

    private lateinit var handler: NewDeviceMailHandler
    private lateinit var notificationPort: NotificationPort

    @BeforeEach
    fun setUp() {
        notificationPort = mock(NotificationPort::class.java)
        handler = NewDeviceMailHandler(notificationPort)
    }

    @Test
    fun `handleNewDeviceLogin enqueues notification with correct template data`() {
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

        verify(notificationPort).enqueue(
            argThat { request: NotificationRequest ->
                request.recipient == "john@example.com" &&
                request.templateCode == "NEW_DEVICE_LOGIN" &&
                request.channel == "EMAIL" &&
                request.sourceService == "auth-service" &&
                request.templateData["userName"] == "john" &&
                request.templateData["deviceName"] == "Chrome on Windows" &&
                request.templateData["ipAddress"] == "192.168.1.1"
            }
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

        verify(notificationPort, never()).enqueue(any())
    }
}

