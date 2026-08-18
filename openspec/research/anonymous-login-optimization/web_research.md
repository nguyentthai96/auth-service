# Kết quả nghiên cứu Internet: Anonymous Login Optimization

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login / Guest Session Promotion |
| **Ngày nghiên cứu** | 2025-01-20 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | anonymous authentication, guest session, session promotion, anonymous JWT |
| **Keywords phát triển** | progressive authentication, lazy registration, anonymous-to-authenticated merge, account linking, ephemeral session, session upgrade |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"anonymous authentication best practices enterprise IAM"` | Firebase Auth anonymous pattern is the most well-known reference; Spring Security has `AnonymousAuthenticationFilter`; OWASP guidelines on session management | `progressive authentication`, `account linking`, `ephemeral identity` |
| 2 | `"guest session promotion architecture design patterns"` | E-commerce cart merge is the canonical use case; Pattern: "session handoff" or "session upgrade"; Event-driven approach recommended for data transfer | `session handoff`, `cart merge`, `event-driven promotion` |
| 3 | `"anonymous JWT token lifecycle management"` | Short-lived anonymous tokens (24h TTL recommended); Opaque vs JWT debate — JWT preferred for stateless validation; Token should carry `anonymous=true` claim or special role | `anonymous claim`, `token upgrade`, `stateless anonymous validation` |

**Takeaways Iteration 1:**
- Firebase Anonymous Auth is the gold standard reference implementation — supports anonymous sign-in, session persistence, and account linking (promotion to email/password or social)
- Spring Security's `AnonymousAuthenticationFilter` is server-side only — useful for authorization but doesn't generate client-facing tokens
- The "session promotion" pattern is common in e-commerce but lacks standardized implementation in IAM frameworks
- Key architectural decision: whether anonymous sessions are purely Redis-based (ephemeral) or have PostgreSQL backing

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://firebase.google.com/docs/auth/web/anonymous-auth | Firebase Anonymous Auth | Anonymous sign-in creates temporary account; user can later link to email/password or social provider; temporary UID persists across sessions; data associated with UID transfers on link | 9 |
| 2 | https://docs.spring.io/spring-security/reference/servlet/authentication/anonymous.html | Spring Security Anonymous Auth | `AnonymousAuthenticationFilter` populates SecurityContext with anonymous token when no auth present; configurable anonymous principal and authorities; mainly for authorization decisions, not client-facing | 7 |
| 3 | https://www.keycloak.org/docs/latest/server_admin/ | Keycloak Server Admin | Service accounts can act as "anonymous" clients; no built-in anonymous user type; token exchange flow could support session upgrade; custom user storage SPI could implement anonymous users | 6 |
| 4 | https://auth0.com/docs/authenticate/login/auth0-universal-login | Auth0 Universal Login | Auth0 doesn't have explicit anonymous auth; recommends "silent authentication" for returning users; session management with rotating refresh tokens; custom database connections for guest users | 5 |
| 5 | https://martinfowler.com/articles/patterns-of-distributed-systems/idempotent-receiver.html | Idempotent Receiver Pattern | Relevant for session promotion — ensuring promotion is idempotent (same anonymous session promoted twice returns same result); use unique session ID as idempotency key | 7 |

**Takeaways Iteration 2:**
- **Firebase approach**: Create anonymous UID → store data under UID → user links account → UID persists, data stays. Simplest model but Firebase-specific.
- **Custom approach for microservices**: Generate anonymous JWT with embedded `anonSessionId` claim → store session data in Redis keyed by `anonSessionId` → on login/register, transfer data from Redis to user's persistent store → invalidate anonymous token → issue authenticated token.
- **Conflicting info**: Some sources recommend opaque tokens for anonymous (simpler, no sensitive claims), others recommend JWT (stateless validation). For microservice architecture, JWT is preferred for stateless downstream validation.
- **Key insight**: Session promotion must be atomic — either all data transfers or none. Use transactional outbox pattern or event-driven approach.

---

### Iteration 3 — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"anonymous session TTL best practices"` (gap: optimal TTL) | Industry consensus: 24-72 hours for anonymous sessions; activity-based sliding window preferred; Firebase uses 1 hour with persistence; most e-commerce: 30 days for cart | ✅ |
| 2 | `"anonymous session abuse prevention rate limiting"` (security gap) | Rate limit by IP (5-10 sessions/hour); device fingerprinting to link sessions; honeypot detection; CAPTCHA after threshold; max anonymous sessions per IP | ✅ |
| 3 | `"session merge conflict resolution anonymous authenticated"` (data conflict gap) | Three strategies: (1) Anonymous wins (overwrite), (2) Authenticated wins (keep existing), (3) Merge (combine both) — e-commerce typically uses merge; configurable per data type | ✅ |
| 4 | `"Spring Boot Redis anonymous session data storage pattern"` (implementation gap) | Use Redis Hash for structured session data; key pattern: `anon:{sessionId}:{dataType}`; TTL on key matches session TTL; Spring Data Redis `RedisTemplate` for operations | ✅ |

**Stop reason**: All critical questions answered, sufficient architectural direction established.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Firebase Anonymous Auth | https://firebase.google.com/docs/auth/web/anonymous-auth | Gold standard for anonymous → authenticated promotion; account linking pattern | 9 | Clear API, well-documented lifecycle | Firebase-specific, not directly portable |
| 2 | Docs | Spring Security Anonymous Authentication | https://docs.spring.io/spring-security/reference/servlet/authentication/anonymous.html | Server-side anonymous principal; configurable authorities; SecurityContext population | 7 | Already in project, native integration | No client-facing tokens, no promotion |
| 3 | Docs | Keycloak Server Admin | https://www.keycloak.org/docs/latest/server_admin/ | Token exchange concepts; service accounts; session management at scale | 6 | Enterprise-proven patterns | Heavy external dependency |
| 4 | Article | OWASP Session Management Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html | Session ID generation, fixation prevention, timeout management | 8 | Security best practices, authoritative | General-purpose, not anonymous-specific |
| 5 | Article | Idempotent Receiver Pattern (Martin Fowler) | https://martinfowler.com/articles/patterns-of-distributed-systems/idempotent-receiver.html | Idempotent session promotion; deduplication keys | 7 | Architectural pattern, well-explained | Not auth-specific |
| 6 | Docs | Spring Data Redis Reference | https://docs.spring.io/spring-data/redis/reference/redis.html | RedisTemplate, Hash operations, TTL management, pub/sub | 8 | Already in stack, well-documented | Need to design data model ourselves |
| 7 | Docs | JWT RFC 7519 | https://tools.ietf.org/html/rfc7519 | Token claims specification; custom claims for anonymous flag; nbf/exp for lifecycle | 7 | Standard, authoritative | Low-level spec |
| 8 | Article | OWASP Authentication Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html | Progressive authentication, credential management, session binding | 7 | Security best practices | General-purpose |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Firebase Auth | Anonymous sign-in creates temp UID; account linking merges to permanent | Anonymous auth, account linking, session persistence, multi-provider | Turnkey solution, well-documented | Simple API, handles edge cases | Vendor lock-in, not self-hosted, limited customization | Not embeddable, proprietary |
| AWS Cognito | Unauthenticated identity pool; guest access with limited IAM role | Identity pools, guest access, developer-authenticated identities | AWS-native, scalable | Integrates with AWS services | Complex IAM policies, AWS lock-in, limited anonymous data storage | No session promotion, no data transfer hooks |
| Supabase Auth | GoTrue-based auth server; no explicit anonymous but can create temp users | Email/password, social, magic link | Open source (GoTrue), self-hostable | PostgreSQL-based, RLS | No anonymous auth built-in, would need custom implementation | Missing anonymous auth entirely |

### So sánh tính năng chi tiết

| Feature | Firebase Auth | AWS Cognito | Supabase Auth | Custom Build |
|---------|:---:|:---:|:---:|:---:|
| Anonymous token generation | ✅ | ⚠️ | ❌ | ✅ |
| Session promotion (anon→auth) | ✅ | ❌ | ❌ | ✅ |
| Data transfer on promotion | ⚠️ | ❌ | ❌ | ✅ |
| Redis session storage | ❌ | ❌ | ❌ | ✅ |
| JWT-based tokens | ✅ | ✅ | ✅ | ✅ |
| Rate limiting (anon creation) | ✅ | ✅ | ❌ | ✅ |
| Self-hosted | ❌ | ❌ | ✅ | ✅ |
| Spring Boot integration | ❌ | ⚠️ | ❌ | ✅ |
| Customizable merge strategy | ❌ | ❌ | ❌ | ✅ |
| **Coverage** | **4/9** | **2/9** | **1/9** | **9/9** |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Firebase-style Account Linking** | Create anonymous user record in DB → assign UID → on auth, link UID to real account → data persists under same UID | Simple model, UID continuity | Requires DB record for anonymous users, cleanup burden | Small-medium scale, when UID continuity matters | Firebase docs |
| 2 | **Ephemeral Redis Session** | Generate anonymous token with `anonSessionId` → store all data in Redis under that ID → on promotion, read Redis data and write to user's persistent store → delete Redis keys | No DB pollution, natural expiry via TTL, high performance | Data loss if Redis restarts (acceptable for anonymous data), Redis memory pressure | High-volume anonymous traffic, ephemeral data | Custom pattern |
| 3 | **Token Exchange (OAuth2-style)** | Anonymous token is exchanged for authenticated token via token exchange endpoint → old token invalidated → new token inherits session context | Standards-based (RFC 8693), clean token lifecycle | Complex implementation, overhead for simple use case | Enterprise environments, OAuth2-heavy architectures | Keycloak, OAuth2 Token Exchange RFC |
| 4 | **Event-Driven Promotion** | On login/register, publish `SessionPromotedEvent(anonSessionId, userId)` → interested services subscribe and migrate their own data | Loose coupling, extensible, each service owns its data transfer | Eventually consistent, complex error handling, needs message broker | Microservice architecture with many data types | Martin Fowler distributed patterns |
| 5 | **Hybrid (Ephemeral Redis + Event-Driven)** | Combine approach 2 (Redis session data) with approach 4 (event-driven promotion). Auth-service manages anonymous token lifecycle in Redis; on promotion, publishes event; downstream services read Redis and persist. | Best of both worlds — ephemeral storage, loose coupling, extensible | Slightly more complex than pure Redis approach | This project — auth-service manages identity, downstream owns data | Composite pattern |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | What is the exact memory impact of anonymous Redis sessions at 100k concurrent sessions? | ✅ | Depends on data stored per session; need benchmarking with actual data shapes | Medium — affects Redis sizing |
| 2 | How do other services in the boilerplate handle anonymous context propagation? | ✅ | Only auth-service in scope; other services not analyzed | Low — out of scope for this research |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)
