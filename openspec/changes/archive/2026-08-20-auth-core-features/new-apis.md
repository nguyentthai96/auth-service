# New APIs: Auth Core Features

> Generated automatically from implementation.

## MFA Endpoints

| # | Method | Path | Auth | Description |
|---|--------|------|------|-------------|
| 1 | POST | `/api/auth/mfa/verify` | Public (mfaToken) | Verify OTP/TOTP code |
| 2 | POST | `/api/auth/mfa/totp/setup` | JWT | Get TOTP QR code |
| 3 | POST | `/api/auth/mfa/totp/confirm` | JWT | Confirm TOTP setup |
| 4 | POST | `/api/auth/mfa/resend` | Public (mfaToken) | Resend OTP SMS/Email |
| 5 | PUT | `/api/auth/mfa/settings` | JWT | Enable/disable MFA |

## SSO Endpoints

| # | Method | Path | Auth | Description |
|---|--------|------|------|-------------|
| 6 | POST | `/api/auth/sso/callback` | Public | OAuth2 callback |
| 7 | GET | `/api/auth/sso/providers` | Public | List SSO providers |
| 8 | POST | `/api/auth/sso/link` | JWT | Link SSO identity |
| 9 | DELETE | `/api/auth/sso/unlink/{provider}` | JWT | Unlink SSO identity |

## Token Endpoints

| # | Method | Path | Auth | Description |
|---|--------|------|------|-------------|
| 10 | POST | `/api/auth/introspect` | Internal | Token introspection (RFC 7662) |
| 11 | GET | `/.well-known/jwks.json` | Public | JWKS public key |
| 12 | POST | `/api/auth/sessions/{userId}/revoke-all` | ADMIN | Force logout |

## Password Endpoints

| # | Method | Path | Auth | Description |
|---|--------|------|------|-------------|
| 13 | POST | `/api/auth/change-password` | JWT | Change password |
| 14 | POST | `/api/auth/forgot-password` | Public | Initiate password reset |

**Total: 14 new endpoints**
