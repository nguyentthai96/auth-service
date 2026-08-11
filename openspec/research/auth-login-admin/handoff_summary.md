# Handoff Summary — Auth Login Admin Dashboard

## 1. Feature Summary

**Auth Login cho Admin Dashboard** — xây dựng luồng đăng nhập bảo mật cho admin panel, sử dụng username/password + JWT Bearer Token hybrid model, với các lớp bảo vệ chống brute force và DDoS.

---

## 2. Key Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D-01 | **Dùng HTTPS thay vì client-side encryption** | Industry standard, OWASP recommended. Client-side encryption thêm complexity mà không tăng security thực sự |
| D-02 | **JWT hybrid: short-lived access + stateful refresh** | Giữ scalability (stateless API) + revocation capability (stateful refresh) |
| D-03 | **HttpOnly cookie cho refresh token** | Chống XSS — JavaScript không thể access cookie |
| D-04 | **In-memory cho access token** | Chống XSS + persistence attack. Trade-off: mất khi page refresh → silent refresh bù |
| D-05 | **Bucket4j + Redis cho rate limiting** | Token bucket algorithm, distributed, reuse Redis infrastructure hiện có |
| D-06 | **Giữ BCrypt, defer Argon2** | BCrypt (strength=12) vẫn an toàn. Argon2 migration là improvement, không urgent |
| D-07 | **Username thay email** | Yêu cầu nghiệp vụ — admin account tạo bởi system, username là identifier chính |

---

## 3. Architecture Snapshot

```
Frontend (React 19)                    Backend (Spring Boot)
┌─────────────────────┐                ┌─────────────────────────────┐
│ Login Page           │    HTTPS      │ Rate Limiter (Bucket4j)     │
│ - Username field     │ ──────────▶   │ ↓                           │
│ - Password field     │               │ AuthController              │
│ - CAPTCHA (cond.)    │               │ ↓                           │
│ - Error states       │    ◀──────    │ LoginHandler (CQRS)         │
│                      │   JWT +       │ ↓                           │
│ Token Manager        │   Cookie      │ JwtService (RS256)          │
│ - In-memory access   │               │ ↓                           │
│ - HttpOnly refresh   │               │ PostgreSQL + Redis          │
└─────────────────────┘                └─────────────────────────────┘
```

---

## 4. Phased Implementation Plan

| Phase | Scope | Effort | Dependencies |
|-------|-------|--------|-------------|
| **P1** | FE: Username field + Real API integration | 2-3 days | Backend API available |
| **P2** | BE: Bucket4j rate limiting + FE error handling | 2-3 days | Redis running |
| **P3** | BE+FE: HttpOnly cookie refresh flow | 3-4 days | CORS config |
| **P4** | BE: Security headers (HSTS, CSP, CORS) | 1 day | — |
| **P5** | FE: CAPTCHA widget (reCAPTCHA/hCaptcha) | 1-2 days | CAPTCHA provider account |
| **P6** | BE: Access token TTL 30 → 15 min | 0.5 day | P3 complete |
| **P7** | Docs: WAF/CDN recommendation | 0.5 day | — |

**Total Estimated Effort: 10-14 days**

---

## 5. Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| Token refresh loop (race condition) | High | Medium | Implement refresh lock (single inflight refresh) |
| Page refresh loses access token | Low | High | Silent refresh via HttpOnly cookie (< 200ms) |
| Bucket4j Redis failure | Medium | Low | Fail-open strategy (allow request, log error) |
| CORS misconfiguration | High | Medium | Test với actual domain, strict allowedOrigins |
| CAPTCHA provider downtime | Low | Low | Configurable CAPTCHA (noop provider for dev) |

---

## 6. Files for Implementation

### Frontend (admindashboard)
- `src/@auth/authApi.ts` — API endpoints
- `src/@auth/services/jwt/JwtAuthProvider.tsx` — Auth flow
- `src/app/(public)/(auth)/components/forms/SignInPageForm.tsx` — Login form
- `src/utils/api.ts` — HTTP client config

### Backend (auth-service)
- `shared/config/SecurityConfig.kt` — Security chain
- `shared/config/SecurityProperties.kt` — Config properties
- `auth/adapter/in/web/AuthController.kt` — REST endpoints
- **NEW**: `shared/config/RateLimitConfig.kt` — Bucket4j config
- **NEW**: `auth/adapter/in/web/filter/LoginRateLimitFilter.kt` — Rate limit filter

---

## 7. Next Steps

Pipeline navigation options:

```
✅ CURRENT: /wf_feature_research (complete)
        │
        ▼ (recommended next)
→ /wf_pre_openspec auth-login-admin
    (Convert business analysis → URD artifacts)
→ /wf_openspec auth-login-admin
    (Generate implementation tasks)
→ /wf_openspec_apply auth-login-admin
    (Execute implementation)
```
