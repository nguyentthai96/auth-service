# Web Research — Auth Login Admin Dashboard

## Iteration 1: JWT + Session Hybrid Authentication

### Findings

**Mô hình Hybrid Auth (2025/2026 best practice):**

Không nên trộn lẫn Session ID và JWT trong cùng request. Thay vào đó, dùng **short-lived JWT** cho authorization + **stateful refresh token** cho session management.

| Component | Role | TTL |
|-----------|------|-----|
| **Access Token (JWT)** | Stateless authorization cho API calls | 5-15 min (recommended), hiện tại 30 min |
| **Refresh Token** | Session management — stored server-side (Redis/DB) | 7 days |

**Key Patterns:**
1. **Token Rotation**: Mỗi lần dùng refresh token → issue mới + invalidate cũ → detect compromise
2. **Opaque Refresh Token**: Dùng random string (không JWT) cho refresh token → không bị decode nếu leak
3. **Token Storage**: `HttpOnly` + `Secure` + `SameSite=Strict` cookies cho refresh token. Access token giữ in-memory.

**Tại sao không mix Session + JWT:**
- Conflicting state — 2 flow auth → tăng surface attack
- Redundant — OAuth2/OIDC đã cung cấp session-like features
- Spring Security 7+ khuyến nghị `SessionCreationPolicy.STATELESS`

> **Kết luận**: Hệ thống hiện tại (STATELESS + JWT + refresh token hash in DB) đã đúng pattern. Cần cải thiện: (1) giảm access token TTL từ 30 min → 15 min, (2) chuyển token storage từ localStorage → in-memory + HttpOnly cookie cho refresh.

---

## Iteration 2: Bảo mật truyền tải Credentials

### Findings

**Client-side encryption cho username/password là KHÔNG KHUYẾN NGHỊ:**

| Approach | Đánh giá | Lý do |
|----------|----------|-------|
| RSA encryption client-side | ⚠️ Over-engineering | Key management phức tạp, attacker inspect source code thấy logic |
| AES encryption client-side | ❌ Không an toàn | Key phải hardcode hoặc fetch — đều expose |
| HTTPS/TLS (industry standard) | ✅ **Recommended** | Encrypt toàn bộ request, battle-tested, managed by browser |

**Best Practice cho bảo mật credentials:**
1. **Enforce HTTPS**: TLS encrypt toàn bộ request body (bao gồm password)
2. **POST method**: Không bao giờ gửi credentials qua GET (appear in logs, history)
3. **Server-side hashing**: BCrypt/Argon2 — không lưu plaintext
4. **HSTS Header**: Force browser dùng HTTPS, ngăn downgrade attack

**Khi nào cần client-side encryption:**
- Zero-Knowledge applications (password manager, E2E encrypted messaging)
- Không áp dụng cho standard login flow

> **Kết luận**: HTTPS + POST + BCrypt đã đủ bảo mật cho credential transmission. Không cần thêm client-side encryption layer. Focus vào: enforce HTTPS, implement HSTS, và CSP headers.

---

## Iteration 3: Chống DDoS và Brute Force

### Findings

**Layered Security Approach (Defense in Depth):**

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Edge (L1)** | Cloudflare / AWS WAF / API Gateway | Stop large-scale DDoS trước khi đến app |
| **Application (L2)** | Bucket4j + Redis | Distributed rate limiting per IP/username |
| **Auth Logic (L3)** | Account lockout + CAPTCHA | Prevent password guessing |
| **Hardening (L4)** | HTTPS + CSRF + Input Validation | Prevent session hijacking, injection |

**Bucket4j (Industry Standard cho Spring Boot):**
- Token-bucket algorithm — handle bursts + steady rate
- Distributed via Redis (Redisson) — work across multiple instances
- Spring Boot Starter available — declarative config trong `application.yml`
- Granular: different limits per endpoint type

**Account Lockout (đã có trong backend):**
- 3 failed attempts → lock 15 min ✅
- CAPTCHA required khi gần threshold ✅
- Audit logging ✅

**Thiếu:**
- Application-level rate limiting cho `/api/auth/login` endpoint
- IP-based throttling (hiện chỉ có user-based)
- Edge protection (WAF/CDN)

---

## Iteration 4: Frontend Security Patterns

### Findings

**Token Storage Best Practice:**

| Method | XSS Safe | CSRF Safe | Recommended |
|--------|----------|-----------|-------------|
| localStorage | ❌ | ✅ | ❌ |
| sessionStorage | ❌ | ✅ | ❌ |
| HttpOnly Cookie | ✅ | ❌ (cần SameSite) | ✅ (cho refresh) |
| In-memory (React state) | ✅ | ✅ | ✅ (cho access) |

**Recommended Hybrid Storage:**
- **Refresh Token**: `HttpOnly` + `Secure` + `SameSite=Strict` cookie
- **Access Token**: In-memory (React state/closure) — lost on page refresh → silent refresh via cookie

**XSS Protection:**
- React auto-escape JSX ✅
- Avoid `dangerouslySetInnerHTML`
- Content Security Policy (CSP) header
- DOMPurify cho user-generated content

**CSRF Protection:**
- `SameSite=Strict` cookie → block cross-site requests
- Anti-CSRF token pattern (cookie-to-header): server sets non-HttpOnly CSRF cookie → frontend reads + sends as custom header

---

## Iteration 5: Password Hashing Comparison

### Findings

| Feature | BCrypt (hiện tại) | Argon2id (recommended) |
|---------|-------------------|----------------------|
| Status | Veteran, stable (1999) | Modern, state-of-art (PHC winner) |
| GPU/ASIC Resistance | CPU-hard only | Memory-hard + CPU-hard |
| Configuration | 1 param (work factor) | 3 params (time, memory, parallelism) |
| Password Limit | 72 bytes | No limit |
| Spring Support | Native | Cần BouncyCastle |
| **Recommendation** | Đủ tốt cho production | Tốt hơn cho new projects |

**Kết luận**: Backend hiện dùng BCrypt (strength=12) — vẫn an toàn. Argon2id tốt hơn nhưng migration không urgent. Giữ BCrypt, plan migration cho phase sau.

---

## Source Summary

| # | Source | Type | Verified |
|---|--------|------|----------|
| 1 | OWASP Authentication Cheatsheet | Best Practice Guide | ✅ |
| 2 | Spring Security Reference Documentation | Official Docs | ✅ |
| 3 | Bucket4j GitHub + Baeldung Tutorial | Library + Tutorial | ✅ |
| 4 | NIST SP 800-63B Digital Identity Guidelines | Standard | ✅ |
| 5 | React Security Best Practices (2025) | Community Research | ✅ |
| 6 | MDN Web Docs - HTTPS/TLS | Reference | ✅ |
