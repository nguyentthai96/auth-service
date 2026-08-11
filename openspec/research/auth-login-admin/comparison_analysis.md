# Comparison Analysis — Auth Login Admin Dashboard

## 1. Comparison Matrix: Authentication Approaches

| Criteria (Weight) | Pure JWT (hiện tại) | JWT + HttpOnly Cookie (đề xuất) | Session-based | BFF Pattern |
|-------------------|---------------------|----------------------------------|---------------|-------------|
| **Security Score** (30%) | 5/10 | 8/10 | 9/10 | 10/10 |
| **Scalability** (20%) | 10/10 | 9/10 | 5/10 | 8/10 |
| **Complexity** (15%) | 3/10 (low) | 5/10 (moderate) | 4/10 (moderate) | 8/10 (high) |
| **Token Revocation** (15%) | 6/10 (blacklist) | 8/10 (blacklist + cookie) | 10/10 (instant) | 10/10 |
| **XSS Resistance** (10%) | 2/10 (localStorage) | 9/10 (HttpOnly + in-memory) | 9/10 | 10/10 |
| **Migration Effort** (10%) | N/A | 4/10 (moderate) | 8/10 (high) | 9/10 (very high) |
| **Weighted Total** | 5.3 | **7.6** | 7.2 | 8.7 |

### Recommendation: **JWT + HttpOnly Cookie (Option B)**

**Lý do:**
1. Cải thiện security đáng kể (XSS protection) với migration effort vừa phải
2. Không phá vỡ architecture hiện tại (vẫn STATELESS)
3. Backend đã có refresh token rotation — chỉ cần thêm cookie handling
4. BFF quá phức tạp cho phase hiện tại — consider cho Phase 3

---

## 2. Comparison Matrix: Rate Limiting Solutions

| Criteria | Redis INCR (hiện tại - MFA only) | Bucket4j + Redis | Spring Cloud Gateway | Resilience4j |
|----------|----------------------------------|-------------------|----------------------|-------------|
| **Algorithm** | Simple counter | Token bucket | Token bucket + Fixed window | Rate limiter |
| **Distributed** | ✅ Redis | ✅ Redis (Redisson) | ✅ Redis | ❌ In-memory |
| **Granularity** | Per-user | Per-IP + Per-user + Per-endpoint | Per-route | Per-method |
| **Spring Integration** | Manual | Starter available | Native | Native |
| **Burst Handling** | ❌ | ✅ Excellent | ✅ Good | ⚠️ Limited |
| **Existing Compatibility** | ✅ Đã dùng Redis | ✅ Dùng chung Redis | ❌ Cần thêm Gateway | ⚠️ Khác pattern |
| **Recommendation** | Extend cho login | **✅ Best fit** | Overkill cho single service | Không phù hợp |

### Recommendation: **Bucket4j + Redis**

**Lý do:**
1. Token bucket algorithm — tốt nhất cho handling bursts
2. Redis already available — reuse infrastructure
3. Spring Boot starter — declarative config
4. Hỗ trợ multiple rate limit dimensions (IP, username, endpoint)

---

## 3. Comparison Matrix: Credential Protection

| Criteria | HTTPS only (current) | HTTPS + CSP + HSTS (đề xuất) | Client-side RSA | Client-side AES |
|----------|---------------------|-------------------------------|-----------------|-----------------|
| **Security** | 7/10 | 9/10 | 6/10 | 4/10 |
| **Complexity** | 1/10 | 2/10 | 7/10 | 6/10 |
| **Performance** | 10/10 | 10/10 | 7/10 | 8/10 |
| **Maintenance** | 10/10 | 9/10 | 4/10 | 4/10 |
| **Industry Standard** | ✅ | ✅ Best practice | ⚠️ Niche | ❌ Anti-pattern |
| **Recommendation** | Baseline | **✅ Recommended** | ❌ Không cần | ❌ Không cần |

### Recommendation: **HTTPS + CSP + HSTS**

**Lý do:**
1. HTTPS đã encrypt toàn bộ request (bao gồm credentials) — thêm client-side encryption là redundant
2. CSP + HSTS headers phòng thủ thêm layer — low effort, high impact
3. Client-side encryption tạo false sense of security, tăng complexity không cần thiết

---

## 4. Feature Comparison: Current vs Proposed

| Feature | Current State | Proposed State | Priority |
|---------|--------------|----------------|----------|
| Login field | Email | Username | 🔴 P0 |
| API endpoint | Mock (`mock/auth/*`) | Real backend (`/api/auth/login`) | 🔴 P0 |
| Token storage | localStorage | In-memory (access) + HttpOnly cookie (refresh) | 🔴 P0 |
| Rate limiting (login) | ❌ None | Bucket4j + Redis (5 req/min per IP) | 🔴 P0 |
| Account lockout | ✅ Backend only | ✅ Backend + Frontend UI feedback | 🟡 P1 |
| CAPTCHA | ✅ Backend required | ✅ Backend + Frontend integration | 🟡 P1 |
| HTTPS enforcement | ⚠️ Not enforced | ✅ HSTS + CSP headers | 🟡 P1 |
| Password hashing | BCrypt-12 | BCrypt-12 (keep, migrate Argon2 later) | 🟢 P2 |
| Access token TTL | 30 min | 15 min | 🟡 P1 |
| CSRF protection | ❌ Disabled | ✅ SameSite + Anti-CSRF token | 🟡 P1 |
| Error handling (FE) | Generic | Specific (locked, captcha, expired) | 🟡 P1 |
| DDoS edge protection | ❌ None | ⚠️ Recommend WAF (out of scope) | 🟢 P2 |

---

## 5. Gap Analysis Summary

### Gap Coverage Score: 75%

| Gap ID | Description | Solution | Covered |
|--------|-------------|----------|---------|
| G-01 | Email → Username | Change FE form field + validation | ✅ |
| G-02 | Mock API → Real API | Update authApi.ts endpoints | ✅ |
| G-03 | localStorage → Secure storage | HttpOnly cookie + in-memory | ✅ |
| G-04 | No login rate limiting | Bucket4j + Redis | ✅ |
| G-05 | No client-side encryption | HTTPS is sufficient (by design) | ✅ |
| G-06 | No CSRF protection | SameSite cookie + CSRF token | ✅ |
| G-07 | No FE error handling for auth | Map backend errors to UI states | ✅ |
| G-08 | No edge DDoS protection | Out of scope — recommend WAF | ⚠️ Deferred |

### Recommendation Summary

> **BUILD** — Tính năng này cần được xây dựng trên nền tảng code hiện có.
> Backend đã có 80% logic cần thiết (login handler, JWT, lockout, CAPTCHA).
> Frontend cần refactor đáng kể (real API integration, token storage, error handling).
> Thêm Bucket4j cho rate limiting là thay đổi mới duy nhất ở backend.
