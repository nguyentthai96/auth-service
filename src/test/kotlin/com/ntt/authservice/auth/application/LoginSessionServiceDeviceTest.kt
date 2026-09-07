package com.ntt.authservice.auth.application

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.adapter.out.persistence.repository.LoginSessionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.*

class LoginSessionServiceDeviceTest {

    private lateinit var loginSessionService: LoginSessionService
    private lateinit var loginSessionRepository: LoginSessionRepository
    private lateinit var eventPublisher: ApplicationEventPublisher

    @BeforeEach
    fun setUp() {
        loginSessionRepository = mock(LoginSessionRepository::class.java)
        eventPublisher = mock(ApplicationEventPublisher::class.java)
        loginSessionService = LoginSessionService(loginSessionRepository, eventPublisher)
    }

    @Test
    fun `listDevicesForUser returns active sessions`() {
        val session = mock(LoginSessionEntity::class.java)
        `when`(session.id).thenReturn(1L)
        `when`(session.deviceType).thenReturn("DESKTOP")
        `when`(session.browserName).thenReturn("Chrome")
        `when`(session.osName).thenReturn("Windows")
        `when`(session.ipAddress).thenReturn("10.0.0.1")
        `when`(session.deviceName).thenReturn("Chrome on Windows")
        `when`(session.sessionActive).thenReturn(true)
        `when`(loginSessionRepository.findByUserIdAndSessionActiveTrue(1L)).thenReturn(listOf(session))

        val devices = loginSessionService.listDevicesForUser(1L)

        assertEquals(1, devices.size)
        assertEquals("Chrome", devices[0].browserName)
    }

    @Test
    fun `kickDevice deactivates session and returns true`() {
        val session = LoginSessionEntity().apply {
            userId = 1L
            accessTokenJti = "jti-abc"
            sessionActive = true
        }
        `when`(loginSessionRepository.findById(10L)).thenReturn(Optional.of(session))
        `when`(loginSessionRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = loginSessionService.kickDevice(10L, 1L)

        assertTrue(result)
        assertFalse(session.sessionActive)
        verify(loginSessionRepository).save(session)
    }

    @Test
    fun `kickDevice returns false when session not found`() {
        `when`(loginSessionRepository.findById(99L)).thenReturn(Optional.empty())

        val result = loginSessionService.kickDevice(99L, 1L)

        assertFalse(result)
    }

    @Test
    fun `kickDevice returns false when userId does not match`() {
        val session = LoginSessionEntity().apply {
            userId = 2L // different user
            sessionActive = true
        }
        `when`(loginSessionRepository.findById(10L)).thenReturn(Optional.of(session))

        val result = loginSessionService.kickDevice(10L, 1L)

        assertFalse(result)
    }

    @Test
    fun `kickAllOtherDevices deactivates all except current`() {
        val currentSession = LoginSessionEntity().apply {
            accessTokenJti = "current-jti"
            sessionActive = true
            userId = 1L
            loginAt = Instant.now()
        }
        val otherSession = LoginSessionEntity().apply {
            accessTokenJti = "other-jti"
            sessionActive = true
            userId = 1L
            loginAt = Instant.now()
        }

        `when`(loginSessionRepository.findByUserIdAndSessionActiveTrue(1L))
            .thenReturn(listOf(currentSession, otherSession))
        `when`(loginSessionRepository.save(any())).thenAnswer { it.arguments[0] }

        val kicked = loginSessionService.kickAllOtherDevices(1L, "current-jti")

        assertEquals(1, kicked)
        // Only otherSession should be deactivated
        assertFalse(otherSession.sessionActive)
        // currentSession should remain active
        assertTrue(currentSession.sessionActive)
    }
}
