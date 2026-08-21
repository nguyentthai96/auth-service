package com.ntt.authservice.auth.domain.event

/**
 * Discriminator for TokenIssuedEvent — identifies the trigger context for token issuance.
 * Used to differentiate login, registration, refresh, MFA, and SSO token issuances
 * within a single unified event type.
 */
enum class IssuanceContext {
    LOGIN,              // Direct login (password auth)
    REGISTRATION,       // First token after user registration
    TOKEN_REFRESH,      // Token rotation via refresh endpoint
    MFA_COMPLETION,     // Token after MFA verification (reserved)
    SSO                 // Token after SSO/OAuth2 flow (reserved)
}
