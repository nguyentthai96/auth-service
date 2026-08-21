# Kết quả nghiên cứu Internet: JWT Token Validation

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày nghiên cứu** | 2025-07-11 |
| **Số iterations** | 3 |
| **Tổng sources** | 12 unique |
| **Keywords ban đầu** | JWT validation, RS256, JWKS, token introspection, token revocation |
| **Keywords phát triển** | validation pipeline, kid rotation, JTI blacklist, bloom filter, token binding, DPoP, Spring Security NimbusJwtDecoder |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"JWT token validation best practices 2024"` | JWT.io introduction covers structure (header.payload.signature), validation steps, RS256 vs HS256 trade-offs | validation pipeline, signature-first |
| 2 | `"OWASP JWT security cheat sheet"` | OWASP recommends: always validate signature, check exp/nbf/iss/aud, use asymmetric keys, short-lived tokens | DPoP, token binding, JWS compact serialization |
| 3 | `"JWT blacklist Redis token revocation microservices"` | Multiple patterns: JTI blacklist, token versioning, short expiry + no blacklist, Redis SET with TTL | bloom filter, token versioning |

**Takeaways Iteration 1:**
- JWT validation is a well-established pattern with clear RFC standards (7519, 7515, 7517, 7662)
- OWASP emphasizes: signature verification MUST come first, then claims validation, then business rules
- Token revocation in stateless JWT is an inherent challenge — three main strategies: blacklist, short-lived, introspection
- Key rotation and JWKS are critical for production deployments

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://www.jwt.io/introduction | JWT Introduction - jwt.io | JWT structure: Header (alg, typ, kid), Payload (registered + custom claims), Signature. RS256 uses RSA key pair. Validation = verify signature + check claims. | 9 |
| 2 | https://datatracker.ietf.org/doc/html/rfc7519 | RFC 7519 - JSON Web Token | Canonical spec: NumericDate for exp/nbf/iat, StringOrURI for iss/aud, case-sensitive claim names, "MUST reject" tokens that fail validation | 10 |
| 3 | https://datatracker.ietf.org/doc/html/rfc7662 | RFC 7662 - Token Introspection | Introspection endpoint returns {"active": true/false} + metadata. Protected resource POSTs token to authorization server. Used when resource servers cannot validate locally. | 8 |
| 4 | https://auth0.com/blog/critical-vulnerabilities-in-json-web-token-libraries/ | Critical JWT Vulnerabilities (Auth0) | Algorithm confusion attacks: attacker changes alg from RS256 to HS256 and signs with public key. Fix: always specify expected algorithm, never trust header alg. | 10 |
| 5 | https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html | OWASP JWT Cheat Sheet for Java | Validation checklist: (1) Signature, (2) Algorithm whitelist, (3) Expiration, (4) Issuer, (5) Audience, (6) Not-before, (7) JTI uniqueness. Store keys securely (never in code). | 10 |
| 6 | https://datatracker.ietf.org/doc/html/rfc7517 | RFC 7517 - JSON Web Key | JWK format for publishing public keys. JWKS = {"keys": [...]}. kid field for key identification. x5c for X.509 cert chain. | 8 |

**Takeaways Iteration 2:**
- **Algorithm confusion** is the #1 JWT vulnerability — MUST whitelist algorithms, never trust alg header
- **Validation order** should be: (1) Algorithm whitelist check → (2) Signature verification → (3) Expiration → (4) Issuer → (5) Audience → (6) Custom claims → (7) Blacklist
- **RFC 7662 introspection** is for opaque tokens or when resource servers cannot validate locally. For JWT with JWKS, local validation is preferred (lower latency)
- **JWKS rotation**: Use kid header to select correct key, cache keys with reasonable TTL, handle key-not-found by re-fetching JWKS

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"JWT JTI blacklist Redis vs bloom filter performance"` (từ gap trong Iter 1) | Redis SET with TTL is sufficient for <100K entries; Bloom filter for ultra-high-throughput (>1M revocations). For auth-service scale, Redis SET with Caffeine L1 cache is optimal. | ✅ |
| 2 | `"JWKS key rotation zero downtime strategy"` (từ gap trong Iter 2) | Multi-key JWKS: publish new key in JWKS first, wait for cache TTL, then start signing with new key. Old key remains in JWKS until all tokens signed with it expire. Grace period = max token TTL. | ✅ |
| 3 | `"Spring Security NimbusJwtDecoder custom validation"` (verify Spring approach) | NimbusJwtDecoder auto-fetches JWKS, caches keys, selects by kid. Can add custom validators via `jwtDecoder.setJwtValidator(DelegatingOAuth2TokenValidator(...))` | ✅ |
| 4 | `"DPoP (Demonstrating Proof of Possession) JWT RFC 9449"` (từ OWASP recommendation) | DPoP binds tokens to sender's key pair — prevents token replay. Adds DPoP header with proof JWT. Overhead: additional crypto per request. Recommended for high-security scenarios. | ✅ |
| 5 | `"JWT token validation event sourcing audit trail"` (domain-specific) | Log validation events for security monitoring: failed validations (expired, invalid sig, blacklisted) as domain events. Useful for anomaly detection and compliance. | ✅ |

**Stop reason**: All primary questions answered, diminishing returns on further searches

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | RFC | RFC 7519 - JSON Web Token | https://datatracker.ietf.org/doc/html/rfc7519 | Canonical JWT spec: claims, validation requirements | 10 | Authoritative, complete | Dense reading |
| 2 | Cheat Sheet | OWASP JWT for Java Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html | Validation checklist, security best practices, Java-specific | 10 | Actionable, security-focused | May not cover newest patterns |
| 3 | Article | Critical JWT Vulnerabilities (Auth0) | https://auth0.com/blog/critical-vulnerabilities-in-json-web-token-libraries/ | Algorithm confusion attack, none algorithm bypass | 10 | Eye-opening security issues | Focuses on known vulns only |
| 4 | RFC | RFC 7662 - Token Introspection | https://datatracker.ietf.org/doc/html/rfc7662 | Introspection endpoint spec, active/inactive semantics | 8 | Standard, interoperable | Less relevant for local JWT validation |
| 5 | RFC | RFC 7517 - JSON Web Key | https://datatracker.ietf.org/doc/html/rfc7517 | JWK/JWKS format, kid, key types | 8 | Standard for key distribution | Narrow scope |
| 6 | Docs | JWT.io Introduction | https://www.jwt.io/introduction | JWT structure, when to use, signature mechanics | 9 | Clear, visual, beginner-friendly | Surface-level |
| 7 | RFC | RFC 9449 - DPoP | https://datatracker.ietf.org/doc/html/rfc9449 | Sender-constrained tokens, proof-of-possession | 7 | Advanced security | Complexity overhead |
| 8 | Article | JWT Revocation Strategies | https://medium.com/@abderrahimaissi/jwt-revocation-strategies-2024 | Blacklist, token versioning, short-lived pattern comparison | 8 | Comprehensive comparison | Opinion-based |
| 9 | Docs | Spring Security JWT Docs | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | NimbusJwtDecoder, custom validators, JWKS | 9 | Official, well-maintained | Spring-specific |
| 10 | Article | NIST SP 800-63B Authentication | https://pages.nist.gov/800-63-3/sp800-63b.html | Token binding, session management, federal guidelines | 7 | Authoritative government standard | Verbose, US-specific |
| 11 | Blog | Token Validation Pipeline Design | https://blog.cloudflare.com/moving-to-jwt-tokens/ | Cloudflare's JWT migration, validation pipeline, performance at scale | 8 | Real-world production experience | Infrastructure-specific |
| 12 | Docs | GeeksforGeeks JWT Overview | https://www.geeksforgeeks.org/web-tech/json-web-token-jwt/ | JWT structure explained, use cases, flow diagrams | 6 | Beginner-friendly tutorial | Surface-level, academic |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| jwt.io Debugger | Online JWT decode/verify/debug | Decode, verify signature, view claims | Quick debugging, share tokens | Free, browser-based | Not for production validation | No programmatic API |
| Keycloak Token Verifier | Standalone token verification via Keycloak adapter | Signature verify, issuer check, JWKS fetch | Production-ready, battle-tested | Part of Keycloak ecosystem | Requires Keycloak dependency | Heavy dependency for just validation |
| Spring Security Resource Server | Framework-level JWT validation + JWKS | NimbusJwtDecoder, auto-JWKS, custom validators | Native Spring integration | Already in project deps | Opinionated, may conflict with custom filter | Custom token types need adaptation |

### So sánh tính năng chi tiết

| Feature | jwt.io | Keycloak Adapter | Spring Security RS | Custom (jjwt) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Signature Verification | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Algorithm Whitelist | ❌ | ✅ | ✅ | ⚠️ (manual) | ⭐ Must |
| Claims Validation (exp/iss/aud) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| JWKS Auto-fetch + Cache | ❌ | ✅ | ✅ | ❌ (build) | ⭐ Must |
| Token Blacklist | ❌ | ❌ | ❌ | ✅ (app-level) | ⭐ Must |
| Custom Token Types | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Token Introspection (RFC 7662) | ❌ | ✅ | ✅ | ✅ (built) | Nice to have |
| DPoP Support | ❌ | ⚠️ | ❌ | ❌ | Optional |
| Event Sourcing Integration | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Key Rotation Management | ❌ | ✅ | ✅ | ⚠️ (manual) | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Signature-First Validation Pipeline** | Validate signature → algorithm whitelist → exp → iss → aud → custom claims → blacklist | Fail-fast on invalid tokens, minimal processing for bad tokens | More complex pipeline | Always — industry standard | OWASP, RFC 7519 |
| 2 | **JTI Blacklist with Redis TTL** | Store revoked JTI in Redis SET with TTL = token remaining lifetime | Simple, self-cleaning, no manual cleanup | Memory grows with revocations | < 100K concurrent tokens | Medium article, practice |
| 3 | **Short-Lived + No Blacklist** | Access tokens < 5 min, no blacklist needed. Refresh tokens revocable. | Simplest, truly stateless | Users can use stolen token for TTL duration | Low-security applications | Auth0, JWT best practices |
| 4 | **JWKS Multi-Key Rotation** | Publish new key in JWKS → wait for cache flush → sign with new key → retire old after max-TTL | Zero downtime rotation | Requires careful TTL coordination | Production key rotation | RFC 7517, Keycloak pattern |
| 5 | **Caffeine L1 + Redis L2 Blacklist Cache** | Check Caffeine first (sub-ms), fallback to Redis, write-through on blacklist | Ultra-low latency, resilient to Redis outage | Stale cache window (configurable TTL) | High-throughput services | Cloud architecture patterns |
| 6 | **DPoP (Demonstrating Proof of Possession)** | Client generates key pair, includes proof JWT in DPoP header, server validates binding | Prevents token relay/theft | Complexity, client must manage keys | High-security financial/medical | RFC 9449 |
| 7 | **Token Type Discriminator Pattern** | Use `type` claim to dispatch to type-specific validators | Clean separation, extensible | Requires validator registry | Multi-token-type systems | Custom pattern |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | What is the optimal Caffeine L1 cache TTL for blacklist entries? | ✅ | Depends on acceptable stale window — needs benchmarking with real load | Low — configurable at runtime |
| 2 | Should service tokens migrate from HMAC to RS256? | ✅ | Trade-off: RS256 overhead vs security gain for internal-only traffic | Medium — security architecture decision |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-11
> **Next step**: Comparison Analysis (comparison_analysis.md)
