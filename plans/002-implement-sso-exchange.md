# Plan 002: Implement SsoAdapter.exchangeCodeForUser with Spring OAuth2 Client

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`SsoAdapter.exchangeCodeForUser()` (line 158-177) throws `UnsupportedOperationException` — SSO login is **completely broken**. Every call to `/api/auth/sso/callback`, `/api/auth/sso/link` will crash with an unhandled exception. The build includes `spring-boot-starter-oauth2-client` (build.gradle.kts line 36) and has Google/Microsoft client registrations in application.yml (lines 47-60), but none of this is actually used.

## Current state

- `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`:
  - Line 158-177: `exchangeCodeForUser()` — throws `UnsupportedOperationException`
  - Uses a standalone `RestTemplate()` (line 29) — should use Spring's `OAuth2AuthorizedClientService` instead
  - Data class `IdpUserInfo(sub, email, name)` at line 179
  - The method is `private` — called by `handleCallback()` and `linkIdentity()`

- `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt`:
  - Line 6-9: Port interface with `exchangeAuthorizationCode()` and `getUserInfo()` methods
  - Data class `SsoUserInfo(externalId, email, name, avatarUrl, provider)`
  - **Note**: `SsoAdapter` does NOT implement `SsoGateway` — it uses its own `IdpUserInfo` type. This is an architecture inconsistency.

- `src/main/resources/application.yml`:
  - Lines 47-60: Google and Microsoft OAuth2 client registrations configured
  - `spring.security.oauth2.client.registration.google/microsoft` with `${GOOGLE_CLIENT_ID}` etc.

- `build.gradle.kts` line 36-37: `spring-boot-starter-oauth2-client` and `oauth2-resource-server` are dependencies.

**Brainstorm decision D5** (from `brainstorm_notes.md` line 161-176):
> SSO hybrid approach — Spring OAuth2 Client for token exchange, manual controller for callback.
> Frontend sends POST /api/auth/sso/callback {code, provider, redirectUri}.
> Backend exchanges code → tokens via Spring OAuth2 RestTemplate, parses id_token → extracts claims.

## Commands you will need

| Purpose   | Command                                  | Expected on success |
|-----------|------------------------------------------|---------------------|
| Build     | `./gradlew compileKotlin`                | BUILD SUCCESSFUL    |
| Test      | `./gradlew test`                         | all pass            |

## Scope

**In scope**:
- `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` — implement `exchangeCodeForUser()`
- `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/` (create) — OAuth2 adapter implementation

**Out of scope**:
- `SsoController.kt` — already correct, delegates to SsoAdapter
- `SsoGateway.kt` port — will be addressed in a separate alignment plan
- OAuth2 client registration configuration — already done in application.yml

## Git workflow

- Branch: `advisor/002-implement-sso-exchange`
- Commit: `feat: implement OAuth2 code-to-user exchange in SsoAdapter using Spring OAuth2 Client`
- Do NOT push or open a PR unless instructed.

## Steps

### Step 1: Create OAuth2TokenExchanger adapter

Create `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`:

```kotlin
package com.ntt.authservice.auth.adapter.out.sso

import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.SsoProviderTimeoutException
import com.ntt.authservice.shared.exception.SsoTokenInvalidException
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * Exchanges OAuth2 authorization codes for user info via provider token endpoints.
 * Supports Google and Microsoft out of the box.
 */
@Component
class OAuth2TokenExchanger(
    private val securityProperties: SecurityProperties
) {
    private val log = LoggerFactory.getLogger(OAuth2TokenExchanger::class.java)
    private val restTemplate = RestTemplate()

    data class ExchangeResult(val sub: String, val email: String?, val name: String?)

    fun exchange(provider: String, code: String, redirectUri: String,
                 clientId: String, clientSecret: String): ExchangeResult {
        val tokenEndpoint = getTokenEndpoint(provider)
        val userInfoEndpoint = getUserInfoEndpoint(provider)

        // 1. Exchange code for access_token
        val tokenResponse = exchangeCode(tokenEndpoint, code, redirectUri, clientId, clientSecret)
        val accessToken = tokenResponse["access_token"] as? String
            ?: throw SsoTokenInvalidException("No access_token in provider response")

        // 2. Fetch user info
        return fetchUserInfo(userInfoEndpoint, accessToken, provider)
    }

    // Provider-specific endpoints
    private fun getTokenEndpoint(provider: String): String = when (provider) {
        "google" -> "https://oauth2.googleapis.com/token"
        "microsoft" -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        else -> throw SsoTokenInvalidException("Unsupported SSO provider: $provider")
    }

    private fun getUserInfoEndpoint(provider: String): String = when (provider) {
        "google" -> "https://openidconnect.googleapis.com/v1/userinfo"
        "microsoft" -> "https://graph.microsoft.com/oidc/userinfo"
        else -> throw SsoTokenInvalidException("Unsupported SSO provider: $provider")
    }

    private fun exchangeCode(endpoint: String, code: String, redirectUri: String,
                              clientId: String, clientSecret: String): Map<*, *> {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", redirectUri)
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        try {
            val response = restTemplate.postForEntity(endpoint, HttpEntity(body, headers), Map::class.java)
            return response.body ?: throw SsoTokenInvalidException("Empty token response")
        } catch (e: Exception) {
            when (e) {
                is SsoTokenInvalidException -> throw e
                else -> {
                    log.error("Token exchange failed: {}", e.message)
                    throw SsoTokenInvalidException("Token exchange failed: ${e.message}")
                }
            }
        }
    }

    private fun fetchUserInfo(endpoint: String, accessToken: String, provider: String): ExchangeResult {
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        try {
            val response = restTemplate.exchange(
                endpoint, HttpMethod.GET, HttpEntity<Any>(headers), Map::class.java
            )
            val body = response.body ?: throw SsoTokenInvalidException("Empty userinfo response")
            return ExchangeResult(
                sub = body["sub"] as? String ?: throw SsoTokenInvalidException("Missing 'sub' in userinfo"),
                email = body["email"] as? String,
                name = body["name"] as? String
            )
        } catch (e: Exception) {
            when (e) {
                is SsoTokenInvalidException -> throw e
                else -> {
                    log.error("UserInfo fetch failed for {}: {}", provider, e.message)
                    throw SsoProviderTimeoutException()
                }
            }
        }
    }
}
```

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 2: Update SsoAdapter to use OAuth2TokenExchanger

In `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`:

1. Add constructor parameter: `private val oauth2TokenExchanger: OAuth2TokenExchanger`
2. Replace `exchangeCodeForUser()` implementation (lines 158-177):

```kotlin
private fun exchangeCodeForUser(code: String, provider: String, redirectUri: String): IdpUserInfo {
    // Resolve client credentials from Spring OAuth2 properties
    val clientId = System.getenv("${provider.uppercase()}_CLIENT_ID") ?: ""
    val clientSecret = System.getenv("${provider.uppercase()}_CLIENT_SECRET") ?: ""

    if (clientId.isBlank() || clientSecret.isBlank()) {
        throw SsoTokenInvalidException("OAuth2 client credentials not configured for provider: $provider")
    }

    val result = oauth2TokenExchanger.exchange(provider, code, redirectUri, clientId, clientSecret)
    return IdpUserInfo(sub = result.sub, email = result.email, name = result.name)
}
```

3. Remove the standalone `private val restTemplate = RestTemplate()` (line 29) — no longer needed.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 3: Verify no regression

**Verify**: `./gradlew test` → all pass

## Test plan

- New tests should be added in Plan 008 (MFA and SSO unit tests).
- For this plan, verify compilation and existing tests pass.

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] `./gradlew test` exits 0
- [x] `SsoAdapter.exchangeCodeForUser()` no longer throws `UnsupportedOperationException`
- [x] `OAuth2TokenExchanger` exists in `auth/adapter/out/sso/`
- [x] No files outside the in-scope list are modified

## STOP conditions

- `SsoAdapter.kt` code at lines 158-177 doesn't match the excerpt.
- `spring-boot-starter-oauth2-client` dependency is missing from build.gradle.kts.
- OAuth2 client registrations are missing from application.yml.

## Maintenance notes

- Client credentials are read from environment variables. In production, these must be set via Kubernetes secrets or equivalent.
- The `OAuth2TokenExchanger` uses hardcoded provider endpoints. If Keycloak support is needed, add its token/userinfo endpoints to the `when` blocks, or refactor to read from Spring's `ClientRegistration` metadata.
- `SsoAdapter.IdpUserInfo` and `SsoGateway.SsoUserInfo` are duplicate types — should be unified in a future cleanup plan.
