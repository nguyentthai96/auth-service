# Kết quả nghiên cứu Internet: Anonymous Login Optimization

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization |
| **Ngày nghiên cứu** | 2025-01-20 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | `anonymous authentication`, `guest session`, `session promotion`, `anonymous JWT`, `lazy registration` |
| **Keywords phát triển** | `progressive authentication`, `ephemeral session Redis`, `anonymous token lifecycle`, `session merge strategy`, `identity linking`, `anonymous abuse prevention` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"anonymous authentication session promotion Spring Boot best practices"` | Spring Security provides `AnonymousAuthenticationFilter` for in-memory anonymous principals; no JWT-based anonymous token or session promotion support out-of-the-box | `AnonymousAuthenticationFilter`, `AnonymousAuthenticationToken` |
| 2 | `"guest session to authenticated user migration JWT Redis patterns"` | Common pattern in e-commerce: generate short-lived anonymous JWT → store temp data in Redis keyed by anonymous session ID → on login, transfer Redis keys to user ID namespace → delete anonymous keys | `session handoff`, `key migration`, `Redis namespace` |
| 3 | `"anonymous user session promotion enterprise IAM patterns"` | Firebase Auth's `signInAnonymously()` + `linkWithCredential()` is the industry gold standard; Supabase GoTrue uses `is_anonymous` flag on user record + identity linking | `identity linking`, `linkWithCredential`, `is_anonymous claim` |

**Takeaways Iteration 1:**
- No standalone library exists for anonymous session promotion in Spring Boot — must build custom
- Firebase's API design (`signInAnonymously` → `linkWithCredential`) is the most mature pattern
- Supabase's `is_anonymous` JWT claim approach is practical and directly applicable
- Redis is the universally recommended store for ephemeral anonymous session data

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://firebase.google.com/docs/auth/web/anonymous-auth | Authenticate with Firebase Anonymously | Firebase creates a temporary anonymous user account with a unique UID; `linkWithCredential()` converts anonymous to permanent; auto-cleanup of old anonymous accounts is configurable; anonymous users get same Firebase ID tokens as authenticated users | 9 |
| 2 | https://supabase.com/docs/guides/auth/auth-anonymous | Anonymous Sign-Ins (Supabase) | `signInAnonymously()` creates a user record with `is_anonymous=true`; JWT includes `is_anonymous` claim; promotion via `updateUser()` with email/password; supports captcha for abuse prevention; RLS policies can differentiate anonymous vs authenticated | 9 |
| 3 | https://docs.spring.io/spring-security/reference/servlet/authentication/anonymous.html | Anonymous Authentication (Spring Security) | `AnonymousAuthenticationFilter` injects `AnonymousAuthenticationToken` when no other auth present; in-memory only; useful for Spring Security ACL/SpEL expressions; does NOT persist anonymous identity or support session promotion | 7 |
| 4 | https://www.keycloak.org/docs/latest/server_admin/#_anonymous_access | Keycloak Anonymous Access | Keycloak supports "anonymous" via unauthenticated client tokens (client credentials grant with limited scope); no true anonymous user concept; requires running Keycloak server | 6 |
| 5 | https://auth0.com/docs/manage-users/user-accounts/user-account-linking | Auth0 Account Linking | Auth0's account linking feature allows merging identities; similar concept to session promotion but focused on linking multiple OAuth providers to one user; provides `Link Accounts` API | 7 |

**Takeaways Iteration 2:**
- **Approach 1 (Firebase model)**: Create anonymous user record in DB → issue JWT with anonymous UID → on login, link credentials to existing anonymous record → all data automatically associated. Pros: simple data model. Cons: creates "garbage" user records that need cleanup.
- **Approach 2 (Supabase model)**: Add `is_anonymous` flag to existing user table → issue standard JWT with `is_anonymous=true` claim → promotion updates the flag and adds credentials. Pros: clean, uses existing user model. Cons: anonymous users pollute user table.
- **Approach 3 (Ephemeral/Redis-only model)**: Do NOT create user record for anonymous sessions → store all anonymous data in Redis with TTL → on login, transfer Redis data to authenticated user → anonymous session auto-expires. Pros: no DB pollution, self-cleaning. Cons: more complex transfer logic, data loss if Redis evicts.
- **Conflicting info**: Firebase creates user records for anonymous users (persisted), while Redis-only approach avoids DB writes. Trade-off: DB persistence gives durability but creates cleanup burden.

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"anonymous session abuse prevention rate limiting"` (security gap) | Best practices: IP-based rate limiting for anonymous token creation (e.g., max 5 anonymous sessions per IP per hour); CAPTCHA on suspicious patterns; short TTL (15-60 min for anonymous tokens, 24h for session data in Redis); resource quotas per anonymous session | ✅ |
| 2 | `"anonymous to authenticated session merge conflict resolution"` (data merge gap) | Three strategies: (1) Last-write-wins — authenticated user data takes precedence, (2) Merge — combine anonymous + existing data, (3) Prompt user — ask user to choose when conflict detected. E-commerce typically uses Merge for cart items and Last-write-wins for preferences | ✅ |
| 3 | `"JWT anonymous token claims structure best practices"` (JWT structure gap) | Recommended claims for anonymous tokens: `sub` = anonymous session ID (UUID), `type` = "anonymous", `iat`, `exp` (short TTL), `jti` for idempotency; do NOT include user roles/permissions; include `session_id` for Redis data lookup | ✅ |

**Stop reason**: All critical questions answered; remaining gaps are implementation-specific details

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Firebase Anonymous Auth | https://firebase.google.com/docs/auth/web/anonymous-auth | Gold standard for anonymous → authenticated flow; `signInAnonymously()` + `linkWithCredential()` pattern | 9 | Complete implementation, battle-tested at Google scale | Proprietary, not embeddable |
| 2 | Docs | Supabase Anonymous Sign-Ins | https://supabase.com/docs/guides/auth/auth-anonymous | `is_anonymous` JWT claim, identity linking for promotion, RLS policy differentiation | 9 | Open source (GoTrue), PostgreSQL-based, clean design | Go implementation, REST API only |
| 3 | Docs | Spring Security Anonymous Auth | https://docs.spring.io/spring-security/reference/servlet/authentication/anonymous.html | `AnonymousAuthenticationFilter`, `AnonymousAuthenticationToken`, SpEL integration | 7 | Native Spring integration, familiar pattern | No JWT support, no persistence, no promotion |
| 4 | Docs | Auth0 Account Linking | https://auth0.com/docs/manage-users/user-accounts/user-account-linking | Identity linking API, merging multiple auth providers | 7 | Enterprise-grade, well-documented | SaaS-only, different use case (multi-provider vs anonymous) |
| 5 | Docs | Keycloak Anonymous Access | https://www.keycloak.org/docs/latest/server_admin/ | Client credentials approach for anonymous access, session management | 6 | Production-proven at enterprise scale | Heavyweight, requires dedicated server |
| 6 | Article | Redis Session Management Patterns | https://redis.io/docs/latest/develop/use/patterns/ | Redis key patterns for session data, TTL management, key expiration notifications | 8 | Directly applicable to ephemeral session storage | General Redis patterns, not auth-specific |
| 7 | Docs | Spring Data Redis | https://docs.spring.io/spring-data/redis/reference/redis.html | RedisTemplate, @RedisHash, TTL configuration, key serialization | 8 | Already in tech stack, native Spring Boot support | Requires careful key design |
| 8 | Docs | JJWT Library | https://github.com/jwtk/jjwt | Custom claims support, RS256 signing, token parsing | 8 | Already used in project, supports custom claims | No anonymous-specific features |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Firebase Auth | Creates temporary anonymous user → issues Firebase ID token → `linkWithCredential()` upgrades to permanent account | Anonymous sign-in, identity linking, auto-cleanup, cross-platform SDKs | Battle-tested at Google scale, seamless UX | Complete solution, excellent documentation | Vendor lock-in, proprietary, requires Firebase project | Cannot embed in Spring Boot, different JWT format |
| Supabase Auth (GoTrue) | Creates user record with `is_anonymous=true` → standard JWT with anonymous claim → `updateUser()` for promotion | Anonymous sign-in, `is_anonymous` JWT claim, RLS integration, captcha support | Open source, PostgreSQL-based, clean API design | Self-hostable, standard JWT, DB-backed | Go implementation, separate deployment needed | REST API only, no native Spring integration |
| Auth0 | Account linking API → merge multiple identities to one user profile | Identity linking, profile merging, management API | Enterprise-grade, SSO support | Well-documented APIs, extensive integrations | SaaS pricing, vendor lock-in | No native anonymous concept, different problem domain |
| Spring Security | `AnonymousAuthenticationFilter` injects anonymous principal for unauthenticated requests | Anonymous filter, SpEL expressions, security context population | Native Spring Boot integration, already in stack | Zero additional dependencies, familiar API | In-memory only, no JWT, no persistence | No session promotion, no data merge, no lifecycle management |

### So sánh tính năng chi tiết

| Feature | Firebase Auth | Supabase Auth | Auth0 | Spring Security | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| Anonymous token generation (JWT) | ✅ | ✅ | ❌ | ❌ | ✅ | ⭐ Must |
| Session promotion (anon→auth) | ✅ | ✅ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Temporary data storage (Redis) | ❌ | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| Data merge on promotion | ✅ | ⚠️ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Rate limiting for anonymous | ✅ | ⚠️ | ✅ | ❌ | ✅ | ⭐ Must |
| Anonymous session TTL | ✅ | ✅ | N/A | ❌ | ✅ | ⭐ Must |
| Auto-cleanup expired sessions | ✅ | ✅ | N/A | ❌ | ✅ | Should |
| Spring Boot native integration | ❌ | ❌ | ⚠️ | ✅ | ✅ | ⭐ Must |
| Existing CQRS handler compatinclude_webibility | ❌ | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Custom JWT claims (`type=anonymous`) | ❌ | ✅ | ❌ | ❌ | ✅ | ⭐ Must |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Firebase Model** (DB-backed anonymous users) | Create a real user record for each anonymous session with `is_anonymous=true`; issue JWT with user ID; on promotion, update user record with credentials | Simple data model — anonymous user IS a user; no data transfer needed; durable across Redis restarts | Pollutes user table with temporary records; requires periodic cleanup job; higher DB write load | Need long-lived anonymous sessions (days/weeks); anonymous users need to interact with domain entities that reference user ID | https://firebase.google.com/docs/auth/web/anonymous-auth |
| 2 | **Ephemeral Model** (Redis-only anonymous sessions) | Do NOT create user record; generate anonymous JWT with session UUID; store all anonymous data in Redis with TTL; on login, transfer Redis data to authenticated user namespace | Zero DB pollution; self-cleaning via Redis TTL; fast token generation (no DB write); horizontally scalable | Data loss if Redis evicts; more complex merge logic; anonymous session data not durable; can't join anonymous data with DB queries | Short-lived anonymous sessions (minutes/hours); minimal anonymous data (cart items, preferences); high anonymous traffic volume | Redis patterns documentation |
| 3 | **Hybrid Model** (Redis data + optional DB reference) | Store anonymous session metadata in lightweight DB table (not full user); store actual data in Redis; on promotion, create/link user record + transfer Redis data | Balance of durability and performance; can track anonymous session metrics; cleaner than full user records | More complex architecture; two data stores to manage; requires careful cleanup of both | Need analytics on anonymous sessions; regulatory requirement to track sessions; medium-lived anonymous sessions (hours/days) | Supabase GoTrue + Redis patterns |
| 4 | **Progressive Authentication** (multi-level access) | Define access levels (anonymous → basic → verified → admin); each level unlocks more features; authentication "upgrade" is incremental, not binary | Fine-grained access control; smooth UX — users aren't forced to full auth immediately; supports partial auth (email verified but no password) | Complex permission model; harder to reason about security; more edge cases in authorization logic | Enterprise applications with many user types; platforms where users can do meaningful work before full signup | Auth0 documentation on progressive profiling |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | What is the optimal anonymous token TTL for this specific application's use case? | ✅ | Depends on business requirements — ranges from 15 min (high security) to 7 days (e-commerce). Recommendation: start with 1 hour, configurable via properties. | Low — configurable at deployment |
| 2 | Should anonymous sessions survive server restarts? | ✅ | Trade-off between Redis-only (faster, auto-cleanup) and DB-backed (durable). Recommend Redis-only for auth-service scope since session data belongs to downstream services. | Medium — affects architecture choice |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)
