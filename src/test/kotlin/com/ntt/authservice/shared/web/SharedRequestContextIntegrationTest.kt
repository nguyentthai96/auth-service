package com.ntt.authservice.shared.web

import com.ntt.basecore.context.RequestContextHolder
import com.ntt.basecore.context.UserContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import java.util.UUID

class SharedRequestContextIntegrationTest {

    @AfterEach
    fun tearDown() {
        RequestContextHolder.clear()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `requestContext adapter delegates smoothly to RequestContextHolder`() {
        val correlationId = "corr-" + UUID.randomUUID()
        val baseContext = com.ntt.basecore.context.RequestContext(
            correlationId = correlationId,
            clientIp = "192.168.1.50",
            appVersion = "2.1.0",
            clientPlatform = "Android",
            userContext = UserContext(
                userId = 777L,
                username = "bob",
                roles = setOf("ROLE_USER"),
                authenticated = true
            )
        )
        RequestContextHolder.set(baseContext)

        val adapter = RequestContext()

        assertEquals(correlationId, adapter.correlationId)
        assertEquals("192.168.1.50", adapter.clientIp)
        assertEquals("2.1.0", adapter.appVersion)
        assertEquals("Android", adapter.clientPlatform)
        assertEquals(777L, adapter.userId)
        assertEquals(777L, adapter.requireUserId())
        assertEquals("bob", adapter.username)
        assertTrue(adapter.hasRole("ROLE_USER"))
        assertFalse(adapter.isAdmin())
    }

    @Test
    fun `requestContext adapter works in unit test mode without active web context`() {
        RequestContextHolder.clear()
        assertNull(RequestContextHolder.getOrNull())

        val adapter = RequestContext().apply {
            userId = 888L
            jti = "token-jti-888"
            roles = setOf("ROLE_ADMIN")
        }

        assertEquals(888L, adapter.requireUserId())
        assertEquals("token-jti-888", adapter.requireJti())
        assertTrue(adapter.isAdmin())
    }

    @Test
    fun `requestContextFilter enriches RequestContextHolder from SecurityContext`() {
        val baseContext = com.ntt.basecore.context.RequestContext(correlationId = "corr-sec-1")
        RequestContextHolder.set(baseContext)

        val auth = UsernamePasswordAuthenticationToken(
            "999",
            "jti-credentials-value",
            listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_ADMIN"))
        )
        SecurityContextHolder.getContext().authentication = auth

        val requestContext = RequestContext()
        val provider = object : ObjectProvider<RequestContext> {
            override fun getObject(vararg args: Any?): RequestContext = requestContext
            override fun getObject(): RequestContext = requestContext
            override fun getIfAvailable(): RequestContext = requestContext
            override fun getIfUnique(): RequestContext = requestContext
        }

        val filter = RequestContextFilter(provider)
        val request = MockHttpServletRequest("GET", "/api/auth/profile")
        val response = MockHttpServletResponse()

        val filterChain = FilterChain { _: ServletRequest, _: ServletResponse ->
            val currentBase = RequestContextHolder.get()
            assertEquals(999L, currentBase.userId)
            assertEquals("999", currentBase.username)
            assertTrue(currentBase.hasRole("ROLE_ADMIN"))
            assertTrue(currentBase.isAdmin())
            assertEquals("jti-credentials-value", currentBase.jti)
        }

        filter.doFilter(request, response, filterChain)

        assertEquals(999L, requestContext.requireUserId())
        assertEquals("jti-credentials-value", requestContext.requireJti())
        assertTrue(requestContext.isAdmin())
    }
}
