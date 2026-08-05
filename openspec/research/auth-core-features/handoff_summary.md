# Handoff Summary: Auth Core Features → OpenSpec Pipeline

## Feature
**Name**: `auth-core-features`
**Scope**: FR-001 (MFA), FR-002 (SSO/OAuth2), FR-003 (Token RS256), FR-004 (Password Policy)
**Service**: `auth-service`

## Research Verdict
- **Approach**: BUILD — tận dụng codebase hiện có + open source libraries
- **Gap Score**: 100% coverage (10/10 gaps addressed)
- **Risk Level**: MEDIUM (RS256 migration + OAuth2 config là phần phức tạp nhất)

## Key Decisions (cho downstream workflows)

### Libraries để adopt
| Library | Version | Purpose |
|---|---|---|
| `dev.samstevens.totp:totp` | 1.7.1 | TOTP MFA generation/verification |
| `org.passay:passay` | 1.6.4 | Dynamic password validation rules |
| `spring-boot-starter-oauth2-resource-server` | (managed) | JWT RS256 decoding, JWKS |
| `spring-boot-starter-oauth2-client` | (managed) | OAuth2 Login flow with IdPs |
| `spring-boot-starter-data-redis` | (managed) | OTP storage, session, rate limiting |

### Architecture Patterns
1. **Two-Phase Login**: Login → MFA challenge → verify → full JWT
2. **JIT Provisioning**: SSO callback → check DB → create if auto-provision ON → Kafka event
3. **JWKS Endpoint**: `/.well-known/jwks.json` expose public key for resource servers
4. **Pluggable CAPTCHA**: `CaptchaVerifier` interface + adapter per provider
5. **Domain-scoped Policy**: `PasswordPolicyEntity` per domain + Passay factory pattern
6. **Password History**: Separate table, BCrypt.matches() on last N hashes

### Schema Changes
- **New tables**: `user_identities`, `password_policies`, `password_history`
- **Alter**: `users` table thêm: `mfa_enabled`, `mfa_method`, `totp_secret_encrypted`, `trusted_device_hash`, `password_changed_at`

### New API Endpoints (14 total)
- MFA: 5 endpoints (`/mfa/*`)
- SSO: 4 endpoints (`/sso/*`)
- Token: 3 endpoints (`/introspect`, `/jwks`, `/sessions/revoke`)
- Password: 2 endpoints (`/change-password`, `/forgot-password` + `/reset-password`)

## Files to Feed into OpenSpec Pipeline

| File | Content | Feeds Into |
|---|---|---|
| `business_analysis.md` | Use cases, flows, business rules | `pre_openspec.md` (URD source) |
| `technical_spec.md` | Architecture, ERD, sequence diagrams, API spec | `design.md` |
| `comparison_analysis.md` | Library decisions, gap analysis | `brainstorm_notes.md` |
| `opensource_findings.md` | Dependency list | `build.gradle.kts` changes |

## Suggested Next Steps

```
→ /wf_pre_openspec auth-core-features
    (Uses business_analysis.md as URD source)
→ /wf_brainstorm_openspec auth-core-features
    (Deep thinking with research context)
→ /wf_openspec auth-core-features
    (Generate implementation artifacts)
```

## Warnings
- ⚠️ Spring Boot 3.2 KHÔNG hỗ trợ `@EnableMultiFactorAuthentication` (Spring Security 7+). Phải dùng manual two-phase flow.
- ⚠️ RS256 key pair cần generate trước khi deploy. Recommend: `openssl genrsa -out private.pem 2048` + `openssl rsa -in private.pem -pubout -out public.pem`
- ⚠️ TOTP secret phải encrypt at rest (AES-256). Recommend: Spring Cloud Config encryption hoặc Jasypt.
