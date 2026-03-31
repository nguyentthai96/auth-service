package com.ntt.authservice.mvc

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  27/03/2025, Thursday
 **/
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2AuthorizationServerApplicationITests {
    private val objectMapper = ObjectMapper()

    @Autowired
    private val mockMvc: MockMvc? = null

    @Test
    @Throws(Exception::class)
    fun performTokenRequestWhenValidClientCredentialsThenOk() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/token")
            .param("grant_type", "client_credentials")
            .param("scope", "message:read")
            .with(httpBasic(CLIENT_ID, CLIENT_SECRET)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isString())
            .andExpect(jsonPath("$.expires_in").isNumber())
            .andExpect(jsonPath("$.scope").value("message:read"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
        // @formatter:on
    }

    @Test
    @Throws(Exception::class)
    fun performTokenRequestWhenMissingScopeThenOk() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/token")
            .param("grant_type", "client_credentials")
            .param("scope", "message:read message:write")
            .with(httpBasic(CLIENT_ID, CLIENT_SECRET)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isString())
            .andExpect(jsonPath("$.expires_in").isNumber())
            .andExpect(jsonPath("$.scope").value("message:read message:write"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
        // @formatter:on
    }

    @Test
    @Throws(Exception::class)
    fun performTokenRequestWhenInvalidClientCredentialsThenUnauthorized() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/token")
            .param("grant_type", "client_credentials")
            .param("scope", "message:read")
            .with(httpBasic("bad", "password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_client"))
        // @formatter:on
    }

    @Test
    @Throws(Exception::class)
    fun performTokenRequestWhenMissingGrantTypeThenUnauthorized() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/token")
            .with(httpBasic("bad", "password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_client"))
        // @formatter:on
    }

    @Test
    @Throws(Exception::class)
    fun performTokenRequestWhenGrantTypeNotRegisteredThenBadRequest() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/token")
            .param("grant_type", "client_credentials")
            .with(httpBasic("login-client", "openid-connect")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"))
        // @formatter:on
    }

    @Test
    @Throws(Exception::class)
    fun performIntrospectionRequestWhenValidTokenThenOk() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/introspect")
            .param("token", getAccessToken())
            .with(httpBasic(CLIENT_ID, CLIENT_SECRET)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value("true"))
            .andExpect(jsonPath("$.aud[0]").value(CLIENT_ID))
            .andExpect(jsonPath("$.client_id").value(CLIENT_ID))
            .andExpect(jsonPath("$.exp").isNumber())
            .andExpect(jsonPath("$.iat").isNumber())
            .andExpect(jsonPath("$.iss").value("http://localhost:9000"))
            .andExpect(jsonPath("$.nbf").isNumber())
            .andExpect(jsonPath("$.scope").value("message:read"))
            .andExpect(jsonPath("$.sub").value(CLIENT_ID))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
        // @formatter:on
    }

    @Test
    @Throws(Exception::class)
    fun performIntrospectionRequestWhenInvalidCredentialsThenUnauthorized() {
        // @formatter:off
        mockMvc!!.perform(post("/oauth2/introspect")
            .param("token", getAccessToken())
            .with(httpBasic("bad", "password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_client"))
        // @formatter:on
    }

    @Throws(Exception::class)
    private fun getAccessToken(): String {
        // @formatter:off
        val mvcResult = mockMvc!!.perform(post("/oauth2/token")
            .param("grant_type", "client_credentials")
            .param("scope", "message:read")
            .with(httpBasic(CLIENT_ID, CLIENT_SECRET)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists())
            .andReturn()

        // @formatter:on
        val tokenResponseJson = mvcResult.response.contentAsString
        val tokenResponse: Map<String, Any> =
            objectMapper.readValue(tokenResponseJson, object : TypeReference() {
            })

        return tokenResponse["access_token"].toString()
    }

    companion object {
        private const val CLIENT_ID = "messaging-client"

        private const val CLIENT_SECRET = "secret"
    }
}