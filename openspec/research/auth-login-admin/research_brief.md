# Research Brief — Auth Login Admin Dashboard

## 1. Feature Overview

| Field | Value |
|-------|-------|
| **Feature Name** | Auth Login / Sign-In cho Admin Dashboard |
| **Input Mode** | Name + Description |
| **Source** | User request trực tiếp |
| **Date** | 2026-08-11 |

### Mô tả tính năng
Xây dựng luồng đăng nhập (login/sign-in) cho Admin Dashboard frontend, sử dụng:
- **Username/Password** authentication (thay vì email hiện tại)
- **JWT Bearer Token** kết hợp session management (hybrid model)
- **Bảo mật truyền tải** username/password từ frontend lên server
- **Chống DDoS** và **chống brute-force** (dò password) từ bên ngoài

## 2. Research Keywords

1. JWT Bearer Token + Session Hybrid Authentication
2. Spring Boot Security Login Brute Force Protection
3. Client-side credential encryption RSA/AES frontend
4. Bucket4j Redis distributed rate limiting Spring Boot
5. HttpOnly cookie vs localStorage token storage React
6. CSRF XSS protection React admin dashboard
7. BCrypt vs Argon2 password hashing Spring Security
8. Account lockout mechanism Spring Security
9. CAPTCHA integration login brute force
10. DPoP (Demonstrating Proof-of-Possession) Spring Security

## 3. Search Queries (Perplexity-style)

| # | Query | Purpose |
|---|-------|---------|
| 1 | "Spring Boot JWT bearer token + session hybrid authentication best practices 2025" | Hiểu mô hình hybrid auth |
| 2 | "encrypt username password client-side before sending to server RSA AES frontend security" | Đánh giá khả thi client-side encryption |
| 3 | "Spring Boot rate limiting brute force protection DDoS prevention login endpoint 2025" | Tìm giải pháp chống brute force |
| 4 | "React frontend login security CSRF XSS protection token storage HttpOnly cookie vs localStorage" | Frontend security patterns |
| 5 | "Argon2 vs BCrypt password hashing Spring Boot 2025 performance security comparison" | So sánh password hashing |
| 6 | "Bucket4j Spring Boot Redis distributed rate limiting tutorial configuration" | Rate limiting implementation |

## 4. Current System Analysis

### 4.1 Related Features (Đã tồn tại)

| Component | File | Status |
|-----------|------|--------|
| **JWT Auth Provider (FE)** | `src/@auth/services/jwt/JwtAuthProvider.tsx` | ✅ Có — dùng email + mock API |
| **Auth API (FE)** | `src/@auth/authApi.ts` | ✅ Có — gọi `mock/auth/*` endpoints |
| **JWT Sign-In Form (FE)** | `src/app/(public)/(auth)/components/tabs/sign-in/JwtSignInTab.tsx` | ✅ Có — chỉ render form |
| **Sign-In Page Form (FE)** | `src/app/(public)/(auth)/components/forms/SignInPageForm.tsx` | ✅ Có — dùng email field, chưa có username |
| **LoginHandler (BE)** | `auth/application/command/LoginHandler.kt` | ✅ Có — CQRS handler với username |
| **AuthService.login (BE)** | `auth/application/AuthService.kt` | ✅ Có — login logic đầy đủ |
| **JwtService (BE)** | `auth/application/JwtService.kt` | ✅ Có — RS256 + HMAC fallback |
| **JwtAuthFilter (BE)** | `shared/security/JwtAuthFilter.kt` | ✅ Có — Bearer token validation |
| **SecurityConfig (BE)** | `shared/config/SecurityConfig.kt` | ✅ Có — STATELESS session |
| **MfaRateLimitService (BE)** | `auth/application/MfaRateLimitService.kt` | ✅ Có — Redis rate limit cho MFA |
| **SecurityProperties (BE)** | `shared/config/SecurityProperties.kt` | ✅ Có — config cho JWT, password, MFA |

### 4.2 Existing Patterns

| Pattern | Mô tả |
|---------|--------|
| **CQRS** | LoginCommand → LoginHandler (write-side) |
| **Clean Architecture** | domain/application/adapter layers |
| **JWT RS256** | Primary signing, HMAC fallback |
| **BCrypt** | Password hashing (strength=12) |
| **Account Lockout** | 3 failed attempts → 15 min lock |
| **CAPTCHA** | Required khi đạt threshold failed login |
| **MFA** | OTP + TOTP support |
| **Token Blacklist** | JTI-based via repository |
| **Refresh Token Rotation** | Hash-based, revoke old on refresh |
| **Multi-provider Auth (FE)** | JWT / AWS / Firebase auth providers |
| **Ky HTTP Client (FE)** | `ky` library với global headers |
| **localStorage Token (FE)** | JWT access token stored in localStorage |

### 4.3 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 19 + Vite 8 + TypeScript 6 |
| **UI Library** | MUI 9 (Material UI) |
| **Form** | react-hook-form 7 + Zod 4 |
| **HTTP Client** | Ky 2 |
| **State** | React Context + TanStack Query 5 |
| **Auth Libraries (FE)** | jwt-decode, crypto-js (có nhưng chưa dùng cho auth) |
| **Backend** | Kotlin + Spring Boot |
| **Security** | Spring Security + JJWT |
| **Database** | PostgreSQL + Flyway |
| **Cache** | Redis (rate limiting, session) |
| **Event Sourcing** | eventsourcing-utils (CQRS) |
| **Password** | BCrypt (strength=12) |
| **Build** | Gradle Kotlin DSL |

### 4.4 Gaps Identified (Sơ bộ)

| # | Gap | Severity |
|---|-----|----------|
| G-01 | Frontend sign-in form dùng **email** thay vì **username** | 🔴 HIGH |
| G-02 | Frontend gọi **mock API** (`mock/auth/*`), chưa kết nối backend thật | 🔴 HIGH |
| G-03 | Token stored in **localStorage** — vulnerable to XSS | 🟡 MEDIUM |
| G-04 | **Không có rate limiting** cho login endpoint ở tầng application | 🔴 HIGH |
| G-05 | **Không có client-side encryption** cho credentials (HTTPS đủ) | 🟢 LOW |
| G-06 | **Không có CSRF protection** (CSRF disabled vì STATELESS) | 🟡 MEDIUM |
| G-07 | Frontend **không handle** CaptchaRequired / AccountLocked responses | 🟡 MEDIUM |
| G-08 | **Không có DDoS protection** ở edge level (WAF/Gateway) | 🟡 MEDIUM |
