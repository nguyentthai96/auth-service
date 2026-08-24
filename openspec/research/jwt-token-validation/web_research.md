# Kết quả nghiên cứu Internet: JWT Token Validation

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày nghiên cứu** | 2025-01-20 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | JWT validation, RS256, claim validation, token blacklist, JWKS |
| **Keywords phát triển** | RFC 8725, JWT BCP, key rotation overlap, tiered cache blacklist, clock skew, token introspection |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"JWT validation best practices security 2024"` | RFC 8725 (JWT BCP) is the authoritative guide. Key requirements: always validate alg header, use asymmetric keys, validate all claims (iss, aud, exp, nbf, iat), restrict algorithms server-side. | RFC 8725, algorithm confusion, nested JWT |
| 2 | `"JWT token blacklist architecture patterns"` | Three approaches: DB-backed blacklist (simple, slow), Redis TTL-based (fast, eventually consistent), event-driven blacklist propagation (scalable). Best practice: tiered cache with DB source of truth. | event-driven invalidation, tiered cache, JTI-based revocation |
| 3 | `"JWKS key rotation best practices"` | Overlap period is critical — serve both old and new keys during rotation. JWKS endpoints should be cached with ETag/max-age. Kid (Key ID) header is mandatory for rotation. Recommended overlap: 2x token lifetime. | overlap period, kid header, JWKS caching, key rollover |

**Takeaways Iteration 1:**
- RFC 8725 (JWT Best Current Practices) is the definitive reference — covers algorithm restriction, claim validation, key management
- Token blacklist via tiered cache (L1 local + L2 distributed + L3 DB) is industry best practice
- Key rotation requires careful overlap period management

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://datatracker.ietf.org/doc/html/rfc8725 | RFC 8725 — JWT BCP | 15 security recommendations including: 1) Validate alg header 2) Use asymmetric signatures 3) Validate iss/aud/exp 4) Use short-lived tokens 5) Restrict algorithms to allowlist | 10 |
| 2 | https://datatracker.ietf.org/doc/html/rfc7519 | RFC 7519 — JSON Web Token | Core JWT spec: claim definitions, NumericDate format, registered claims (iss, sub, aud, exp, nbf, iat, jti) | 9 |
| 3 | https://datatracker.ietf.org/doc/html/rfc7662 | RFC 7662 — Token Introspection | Introspection endpoint spec: POST with token, response with active/inactive + metadata. Used by resource servers to validate opaque or JWT tokens. | 8 |
| 4 | https://auth0.com/blog/a-look-at-the-latest-draft-for-jwt-bcp/ | Auth0 — JWT Best Current Practices | Practical guide implementing RFC 8725. Key points: 1) Algorithm confusion attack prevention 2) Token binding 3) Audience restriction 4) Clock skew handling | 9 |
| 5 | https://curity.io/resources/learn/jwt-best-practices/ | Curity — JWT Security Best Practices | Enterprise perspective: 1) Use token introspection for opaque tokens 2) JWT for stateless, introspection for stateful 3) Phantom token pattern 4) Token exchange (RFC 8693) | 8 |
| 6 | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | Spring Security — JWT Resource Server | Spring's approach: NimbusJwtDecoder, JwtAuthenticationProvider, composable validators (DelegatingOAuth2TokenValidator), JWKS auto-fetch | 9 |

**Takeaways Iteration 2:**
- Approach 1 (Current — JJWT + custom chain): Flexible, full control over validation pipeline, supports multi-token-type (access, mfa, anonymous, service). Trade-off: more code to maintain.
- Approach 2 (Spring Security Resource Server): Less code, auto JWKS fetch, built-in claim validators. Trade-off: opinionated, harder to support custom token types and blacklist.
- RFC 8725 compliance checklist is critical — the project should map each recommendation.

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"JWT clock skew tolerance recommended value"` (từ gap trong Iter 2) | IETF recommends small tolerance (30-60s). AWS Cognito uses 5 min. Auth0 uses 60s. Current project uses 60s — aligned with Auth0 recommendation. | ✅ |
| 2 | `"JWT token blacklist Redis circuit breaker pattern"` (verify approach) | Netflix/Resilience4j pattern: threshold-based circuit breaker, half-open state for recovery. Current project uses simplified custom circuit breaker — adequate for blacklist use case. | ✅ |
| 3 | `"JWKS ETag caching strategy"` (verify 24h max-age) | RFC 8615 recommends JWKS be cached. Google uses 1h max-age, Auth0 uses 24h with stale-while-revalidate. Current 24h max-age is conservative and acceptable. | ✅ |
| 4 | `"JWT validation failure event security monitoring"` (verify event recording approach) | OWASP recommends logging all authentication failures. Categorizing by failure reason (signature, blacklist, claim mismatch) is best practice for SIEM integration. | ✅ |

**Stop reason**: All research questions answered — diminishing returns from additional queries.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | RFC | RFC 8725 — JWT BCP | https://datatracker.ietf.org/doc/html/rfc8725 | Authoritative JWT security guide | 10 | Definitive standard | Spec language, no code examples |
| 2 | RFC | RFC 7519 — JWT | https://datatracker.ietf.org/doc/html/rfc7519 | Core JWT claims and validation rules | 9 | Foundational spec | Theoretical |
| 3 | RFC | RFC 7662 — Token Introspection | https://datatracker.ietf.org/doc/html/rfc7662 | Introspection endpoint specification | 8 | Standard compliance | Limited to opaque/JWT |
| 4 | Blog | Auth0 — JWT Best Current Practices | https://auth0.com/blog/a-look-at-the-latest-draft-for-jwt-bcp/ | Practical RFC 8725 implementation guide | 9 | Code examples, practical | Auth0-centric |
| 5 | Blog | Curity — JWT Security Best Practices | https://curity.io/resources/learn/jwt-best-practices/ | Enterprise JWT patterns (phantom token, exchange) | 8 | Enterprise context | Product-focused |
| 6 | Docs | Spring Security — JWT Resource Server | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | Spring's composable JWT validation architecture | 9 | Framework integration | Spring-specific |
| 7 | GitHub | JJWT Documentation | https://github.com/jwtk/jjwt#readme | JJWT API usage, migration guide, security features | 8 | Detailed API docs | Library-specific |
| 8 | OWASP | OWASP JWT Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html | Security checklist for JWT implementation | 9 | Security-focused | Java-centric |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Auth0 Platform | Managed JWT validation as-a-service | JWT issuance, validation, JWKS hosting, introspection | Zero maintenance for JWT infra | Fully managed, well-documented | Vendor lock-in, cost at scale, limited customization | No custom token types (mfa, anonymous) |
| AWS Cognito | Managed identity with JWT tokens | OIDC-compliant JWT, JWKS endpoint, built-in validation | AWS ecosystem integration | Auto key rotation, high availability | AWS-specific, 5-min clock skew, limited claim customization | No custom blacklist, no event recording |
| Keycloak (self-hosted) | Full IAM platform with JWT support | Token issuance, validation, introspection, JWKS, admin UI | Open source, highly customizable | Full control, enterprise features, active community | Operational complexity, resource-heavy, Java-only | Overkill for token validation only |

### So sánh tính năng chi tiết

| Feature | Auth0 | AWS Cognito | Keycloak | Current Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| RS256 JWT validation | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Claim validation (iss, aud, type) | ✅ | ⚠️ | ✅ | ✅ | ⭐ Must |
| Token blacklist/revocation | ⚠️ | ❌ | ✅ | ✅ | ⭐ Must |
| JWKS endpoint | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Key rotation | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Custom token types (mfa, anonymous) | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Validation event recording | ❌ | ⚠️ | ⚠️ | ✅ | ⭐ Must |
| Tiered cache (L1+L2+L3) | ❌ | ❌ | ❌ | ✅ | Nice to have |
| Circuit breaker for cache | ❌ | ❌ | ❌ | ✅ | Nice to have |
| RFC 7662 introspection | ✅ | ❌ | ✅ | ✅ | ⭐ Must |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | Chain of Responsibility Validation | Composable validators (ClaimValidator interface) chained together, supporting fail-fast and collect-all modes | Extensible, testable, open-closed principle | More classes, slight overhead | Need custom claim validation, multiple token types | Project codebase, Spring Security OAuth2TokenValidator |
| 2 | Monolithic Validator | Single class validates all claims in one method | Simple, fewer files | Hard to extend, violates SRP | Simple JWT validation without custom claims | Auth0 java-jwt built-in verification |
| 3 | Tiered Cache Blacklist | L1 local (Caffeine) → L2 distributed (Redis) → L3 database, with circuit breaker | Sub-ms latency, resilient to Redis failures | Complex, eventual consistency window | High-throughput APIs needing fast token validation | Netflix/AWS patterns, project codebase |
| 4 | Event-Driven Blacklist | Publish revocation events (Kafka/Redis Pub-Sub), each service maintains local blacklist | Eventual consistency across services, scalable | Propagation delay, more infra | Microservice architectures with multiple resource servers | Curity blog, distributed systems patterns |
| 5 | Dual-Key Rotation Overlap | Serve current + previous RSA keys during rotation period, sign only with current key | Zero-downtime rotation, backward compatible | Must manage two keys, overlap period planning | Any system with key rotation needs | JWKS spec, Google's approach |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Should the project adopt token binding (DPoP — RFC 9449)? | ✅ | DPoP is emerging standard, not yet widely adopted in Spring ecosystem. Current project doesn't need it yet. | Low — future enhancement |
| 2 | Is the custom circuit breaker sufficient vs Resilience4j? | ✅ | Custom circuit breaker is simpler (threshold + timer). Resilience4j offers half-open state, sliding window, metrics. Trade-off: simplicity vs features. | Low — current approach is adequate |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)
