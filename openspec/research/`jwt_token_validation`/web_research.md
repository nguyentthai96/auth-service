# Kết quả nghiên cứu Internet: JWT Token Validation

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày nghiên cứu** | 2026-08-26 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | JWT validation, token blacklist, JWKS rotation, RFC 7519, RFC 8725 |
| **Keywords phát triển** | JTI deduplication, clock skew tolerance, audience restriction, token binding, proof-of-possession |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"JWT token validation best practices production 2024"` | RFC 8725 (JWT BCP) is the definitive guide. Key practices: always validate iss, aud, exp; use RS256 over HS256; never store secrets in claims | `audience restriction`, `algorithm confusion`, `token binding` |
| 2 | `"JWT blacklist revocation Redis high throughput"` | Redis SET with TTL matching token expiry is the standard pattern. Bloom filters for memory optimization at extreme scale. JTI-based blacklist preferred over full-token storage | `bloom filter`, `JTI registry`, `TTL-based eviction` |
| 3 | `"JWKS key rotation Spring Boot"` | Dual-key overlap strategy: generate new key pair, publish both in JWKS, sign with new key, wait for old tokens to expire, remove old key. NimbusJwtDecoder caches JWKS with configurable refresh | `dual-key overlap`, `kid selection`, `JWKS cache refresh` |

**Takeaways Iteration 1:**
- RFC 8725 (JWT Best Current Practices) is the authoritative standard — must-follow for production
- Token blacklist in Redis with TTL auto-eviction is the industry consensus pattern
- JWKS dual-key rotation is well-established; Spring Security's `NimbusJwtDecoder` provides reference caching
- Current system lacks audience (aud) claim validation — a gap per RFC 8725

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://datatracker.ietf.org/doc/html/rfc8725 | RFC 8725: JWT Best Current Practices | Mandatory: validate iss, aud, exp, nbf. Use explicit algorithm allowlist. Reject none algorithm. Use short-lived tokens. | 10 |
| 2 | https://datatracker.ietf.org/doc/html/rfc7662 | RFC 7662: OAuth 2.0 Token Introspection | Standard introspection response format: active, scope, client_id, username, token_type, exp, iat, nbf, sub, aud, iss, jti. Authentication required for introspection endpoint. | 9 |
| 3 | https://datatracker.ietf.org/doc/html/rfc7519 | RFC 7519: JSON Web Token | Core JWT specification. Claims: iss, sub, aud, exp, nbf, iat, jti. All are OPTIONAL but SHOULD be validated when present. NumericDate = seconds since epoch. | 9 |
| 4 | https://auth0.com/blog/a-look-at-the-latest-draft-for-jwt-bcp/ | Auth0: JWT BCP Analysis | Algorithm confusion attacks, key confusion prevention, nested JWT handling, explicit typing. Recommends RS256+ over HS256. | 8 |
| 5 | https://curity.io/resources/learn/jwt-best-practices/ | Curity: JWT Best Practices | Token binding via DPoP (RFC 9449), proof-of-possession tokens, phantom token pattern (opaque externally, JWT internally). Short access token TTL (5-15min). | 8 |
| 6 | https://redis.io/docs/manual/patterns/jwt-token-revocation/ | Redis: JWT Token Revocation Patterns | SET-based blacklist with EXPIRE matching token TTL. SISMEMBER O(1) lookup. Bloom filter alternative for memory. Pipeline batch blacklist checks. | 9 |

**Takeaways Iteration 2:**
- **Approach 1: JTI-based Redis Blacklist** — Store revoked JTIs in Redis SET with TTL = token remaining lifetime. O(1) SISMEMBER check. Trade-off: Redis dependency on critical path, but sub-millisecond latency.
- **Approach 2: Bloom Filter Blacklist** — Memory-efficient probabilistic structure. False positives acceptable (fail-safe — revoked tokens never pass). Trade-off: false positive rate needs tuning, no deletion possible (use counting BF or time-partitioned BFs).
- **Approach 3: Dual-Key JWKS Rotation** — Publish new + old key in JWKS. Sign with new key. Set overlap period = max token lifetime. Remove old key after overlap. kid header selects correct key for verification.
- **Conflicting info**: Clock skew tolerance — RFC 7519 says implementations SHOULD allow small clock skew. JJWT allows configurable clock skew (default 0). Spring Security JwtTimestampValidator defaults to 60 seconds.

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"JJWT clock skew configuration"` (từ gap trong Iter 2) | JJWT `setAllowedClockSkewSeconds()` on parser builder. Default 0 seconds. Recommended 30-120 seconds for distributed systems. | ✅ |
| 2 | `"JWT audience claim validation multi-service"` (từ gap — no aud validation in current system) | aud claim should contain service identifier. Each service validates aud contains its own identifier. Prevents token reuse across services in microservices. | ✅ |
| 3 | `"Redis vs DB JWT blacklist performance benchmark"` (verify assumption A1) | Redis SISMEMBER: ~0.1ms. PostgreSQL indexed query: ~1-5ms under load. At 1000+ req/s, DB becomes bottleneck. Redis recommended for >500 req/s validation throughput. | ✅ |
| 4 | `"Caffeine cache JWT validation local cache pattern"` (targeted for two-tier) | L1 Caffeine cache for blacklist with short TTL (10-30s). Write-through to Redis L2. Read: L1 → L2 → DB. Eventual consistency acceptable for blacklist (fail-safe: a briefly un-blacklisted token is a minor risk within 30s window). | ✅ |

**Stop reason**: All questions answered, diminishing returns on further search.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | RFC | RFC 8725: JWT BCP | https://datatracker.ietf.org/doc/html/rfc8725 | Definitive JWT security guidelines | 10 | Authoritative, comprehensive | Dense reading |
| 2 | RFC | RFC 7662: Token Introspection | https://datatracker.ietf.org/doc/html/rfc7662 | Standard introspection format | 9 | Industry standard | OAuth2-focused, needs adaptation |
| 3 | RFC | RFC 7519: JWT | https://datatracker.ietf.org/doc/html/rfc7519 | Core JWT claims specification | 9 | Foundation spec | General, not implementation-specific |
| 4 | Docs | Redis JWT Revocation Patterns | https://redis.io/docs/manual/patterns/ | Redis-based blacklist with TTL | 9 | Practical, production-proven | Redis dependency |
| 5 | Blog | Auth0 JWT BCP Analysis | https://auth0.com/blog/a-look-at-the-latest-draft-for-jwt-bcp/ | Algorithm confusion, key management | 8 | Practical examples | Auth0-biased |
| 6 | Blog | Curity JWT Best Practices | https://curity.io/resources/learn/jwt-best-practices/ | DPoP, phantom token, token binding | 8 | Advanced patterns | Some patterns overkill for current scope |
| 7 | Docs | JJWT Documentation | https://github.com/jwtk/jjwt#readme | JJWT API, configuration | 8 | Current library docs | Library-specific |
| 8 | Docs | Spring Security JWT Configuration | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | Spring-native JWT support | 8 | Framework integration | May conflict with custom filter |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Redis (blacklist cache) | JTI-based SET with TTL auto-eviction | SISMEMBER O(1), EXPIRE auto-cleanup, pub/sub for invalidation | Sub-millisecond blacklist checks | Already in tech stack, proven pattern | Critical-path Redis dependency | Need L1 cache fallback |
| Caffeine (L1 cache) | In-process cache for hot validation data | Auto-eviction, size-bounded, time-bounded | Sub-microsecond reads, no network hop | Already in tech stack, zero latency | Eventual consistency, per-instance | Cache coherence across instances |

### So sánh tính năng chi tiết

| Feature | Redis Blacklist | DB Blacklist (current) | Caffeine L1 + Redis L2 | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| Sub-millisecond lookup | ✅ | ❌ | ✅ | ⭐ Must |
| Auto-eviction on token expiry | ✅ | ❌ | ✅ | ⭐ Must |
| Cross-instance consistency | ✅ | ✅ | ⚠️ | ⭐ Must |
| No network hop | ❌ | ❌ | ✅ (L1) | Nice to have |
| Persistence / durability | ⚠️ | ✅ | ⚠️ | Nice to have |
| Horizontal scalability | ✅ | ⚠️ | ✅ | ⭐ Must |
| Memory efficiency | ⚠️ | ✅ | ⚠️ | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | JTI Redis Blacklist + TTL | Store revoked JTIs in Redis SET, EXPIRE = token remaining TTL. Check via SISMEMBER on each request. | O(1) lookup, auto-cleanup, proven pattern | Redis on critical auth path, temporary inconsistency if Redis down | Medium-high throughput (500-10K req/s) | Redis docs |
| 2 | Two-Tier Blacklist (Caffeine L1 + Redis L2) | L1 Caffeine cache (TTL 15-30s) for recently checked JTIs. On miss → L2 Redis. On revocation → write-through L1+L2. | Near-zero latency for repeated checks, reduces Redis load | Eventual consistency window (15-30s), more complex invalidation | High throughput with repeated token validation | Curity, internal pattern |
| 3 | Dual-Key JWKS Rotation | Generate new RSA key pair, publish both old+new in JWKS, sign new tokens with new key, remove old key after max token lifetime. | Zero-downtime rotation, standards-compliant | Requires orchestration, JWKS cache refresh timing | Key rotation requirements, compliance | RFC 7517, Spring Security |
| 4 | Claim Validation Pipeline | Compose validators: IssuerValidator → AudienceValidator → TimestampValidator → BlacklistValidator → TypeValidator. Chain of responsibility. | Modular, testable, extensible | Slight overhead per validator | Clean architecture, multiple validation rules | RFC 8725 |
| 5 | Phantom Token Pattern | External: opaque tokens. Internal: JWT. Gateway translates opaque→JWT via introspection. | JWT never exposed externally, reduced attack surface | Requires API gateway support, added complexity | High-security environments | Curity |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | What is the exact latency impact of DB-backed TokenBlacklistRepository at current production load? | ✅ | Requires production metrics / load test — not available via research | Medium — affects cache migration priority |
| 2 | Does base-cache-starter's TwoLevelCacheManager support write-through for blacklist entries? | ✅ | Internal library, not publicly documented | Low — can implement custom if needed |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-26
> **Next step**: Comparison Analysis (comparison_analysis.md)
