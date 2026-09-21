package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.authservice.auth.adapter.out.persistence.entity.LoginSessionEntity
import com.ntt.authservice.auth.application.LoginSessionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import com.ntt.authservice.shared.web.RequestContext
import org.springframework.context.MessageSource
import java.time.Instant

class DeviceControllerTest {

    private lateinit var controller: DeviceController
    private lateinit var loginSessionService: LoginSessionService

    @BeforeEach
    fun setUp() {
        loginSessionService = mock(LoginSessionService::class.java)
        controller = DeviceController(loginSessionService)

        val requestContext = RequestContext().apply {
            userId = 123L
            jti = "current-jti"
        }
        val messageSource = mock(MessageSource::class.java)
        `when`(messageSource.getMessage(anyString(), any(), any())).thenReturn("Success")
        controller.injectBaseDependencies(requestContext, messageSource)

        // Mock security context with user principal "123" and jti in details
        val auth = UsernamePasswordAuthenticationToken("123", null, emptyList())
        auth.details = mapOf("jti" to "current-jti")
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `listDevices returns devices for authenticated user`() {
        val session = LoginSessionEntity().apply {
            this.userId = 123L
            this.deviceType = "DESKTOP"
            this.browserName = "Chrome"
            this.osName = "Windows"
            this.ipAddress = "192.168.1.1"
            this.loginAt = Instant.now()
            this.lastActivityAt = Instant.now()
            this.deviceName = "Chrome / Windows / DESKTOP"
            this.sessionActive = true
            this.accessTokenJti = "current-jti"
        }
        ReflectionTestUtils.setField(session, "id", 1L)
        `when`(loginSessionService.listDevicesForUser(123L)).thenReturn(listOf(session))

        val response = controller.listDevices()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.data?.size)
        assertEquals("Chrome", response.body?.data?.first()?.browserName)
    }

    @Test
    fun `kickDevice returns OK on success`() {
        `when`(loginSessionService.kickDevice(1L, 123L)).thenReturn(true)

        val response = controller.kickDevice(1L)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(loginSessionService).kickDevice(1L, 123L)
    }

    @Test
    fun `kickDevice returns 404 when not found`() {
        `when`(loginSessionService.kickDevice(99L, 123L)).thenReturn(false)

        val response = controller.kickDevice(99L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `kickAllOtherDevices returns OK with kicked count`() {
        `when`(loginSessionService.kickAllOtherDevices(123L, "current-jti")).thenReturn(3)

        val response = controller.kickAllOtherDevices()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3, response.body?.data?.kickedCount)
    }
}
