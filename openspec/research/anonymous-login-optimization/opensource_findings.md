# Kết quả tìm kiếm Open Source: Anonymous Login Optimization

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization (anonymous sessions, session promotion, data merge) |
| **Ngày tìm kiếm** | 2025-01-20 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Spring Boot, Kotlin, Redis, PostgreSQL, JWT (RS256) |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"anonymous authentication open source GitHub repository"` | 5 kết quả relevant | Keycloak, Firebase emulators, Spring Security samples |
| 2 | `"guest session promotion library framework Spring Boot"` | 3 kết quả relevant | Mostly articles, few libraries |
| 3 | `"anonymous JWT token session merge open source"` | 4 kết quả relevant | Custom implementations, no standalone library |
| 4 | `"progressive authentication lazy registration IAM open source"` | 3 kết quả relevant | Auth0 SDKs, Keycloak extensions |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Keycloak | https://github.com/keycloak/keycloak | 25k+ | Active (daily) | Apache 2.0 | ✅ Có |
| 2 | Spring Security (anonymous auth) | https://github.com/spring-projects/spring-security | 9k+ | Active (daily) | Apache 2.0 | ✅ Có |
| 3 | Firebase Auth (Emulator Suite) | https://github.com/firebase/firebase-tools | 4k+ | Active (weekly) | MIT | ✅ Có |
| 4 | Supabase Auth (GoTrue) | https://github.com/supabase/auth | 2k+ | Active (weekly) | MIT | ✅ Có |
| 5 | FusionAuth | https://github.com/FusionAuth/fusionauth-issues | 1k+ | Active (monthly) | Proprietary (OSS core) | ❌ Không (proprietary core) |
| 6 | Ory Kratos | https://github.com/ory/kratos | 11k+ | Active (weekly) | Apache 2.0 | ❌ Không (Go-based, no anonymous sessions) |
| 7 | Authelia | https://github.com/authelia/authelia | 22k+ | Active (weekly) | Apache 2.0 | ❌ Không (proxy auth, no anonymous concept) |
| 8 | SuperTokens | https://github.com/supertokens/supertokens-core | 13k+ | Active (weekly) | Apache 2.0 | ❌ Không (no session promotion feature) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | No anonymous auth support | Basic anonymous tokens | Full anonymous + promotion + data merge |
| **Applicability** (phù hợp tech stack) | 15% | Different language/framework | Partial fit (REST API) | Same stack (Spring Boot + Kotlin), easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly/daily commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples + guides |
| **Code quality** | 15% | No tests, messy code | Some tests | Well-tested, clean architecture |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars, active discussions |
| **Popularity** | 10% | Few users | Growing adoption | Widely adopted, production-proven |

### Kết quả đánh giá

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 5 | 20% | 1.00 | Has anonymous user concept but limited — treats anonymous as pre-registered users, no true session promotion |
| Applicability | 4 | 15% | 0.60 | Java-based but heavyweight — requires running Keycloak server, not embeddable in Spring Boot but different language stack |
| Activity | 8 | 15% | 1.20 | Active development, weekly commits |
| Documentation | 7 | 15% | 1.05 | Good API docs, guides for anonymous auth, migration guides |
| Code quality | 7 | 15% | 1.05 | Clean Go code, good test coverage |
| Community | 7 | 10% | 0.70 | 2k+ stars, growing community |
| Popularity | 7 | 10% | 0.70 | Growing adoption, alternative to Firebase |
| **Tổng điểm** | | | **6.70**/10 | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Spring Security | 3 | 10 | 9 | 8 | 9 | 9 | 10 | **7.90** |
| 2 | Keycloak | 5 | 4 | 9 | 9 | 8 | 10 | 10 | **7.50** |
| 3 | Firebase Auth | 9 | 3 | 8 | 9 | 8 | 8 | 9 | **7.70** |
| 4 | Supabase Auth (GoTrue) | 7 | 4 | 8 | 7 | 7 | 7 | 7 | **6.70** |

---

## 4. Gap Analysis chi tiết

### Keycloak — Gap Analysis

**Overall Score**: 7.50 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous user creation | ✅ | | Users can be created without email/password | Requires Keycloak admin API |
| Session promotion (anon→auth) | | ❌ | - | No built-in "link anonymous to real user" — requires custom SPI |
| Temporary data storage | | ❌ | - | No concept of anonymous session data store |
| JWT anonymous tokens | ⚠️ | | Can issue tokens with limited scopes | Token doesn't carry `type=anonymous` semantics |
| Rate limiting | ✅ | | Brute force detection built-in | Not specific to anonymous abuse |
| Session lifecycle | ✅ | | Configurable session TTL, idle timeout | Session management is server-side |
| Integration (Spring Boot) | ⚠️ | | OIDC/OAuth2 integration exists | Requires running Keycloak server — heavyweight |
| Scalability | ✅ | | Clustered deployment, Infinispan cache | Operational complexity |

**Verdict**: Tham khảo pattern — Keycloak's session model and SPI extensibility offer design inspiration, but it's too heavyweight to adopt directly.
**Recommendation**: Tham khảo pattern
**Reasoning**: Keycloak lacks native session promotion and requires running a separate server. The auth-service already has its own JWT infrastructure. Better to reference Keycloak's session lifecycle patterns and build custom.

### Spring Security (Anonymous Authentication) — Gap Analysis

**Overall Score**: 7.90 / 10
**URL**: https://github.com/spring-projects/spring-security

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous authentication filter | ✅ | | `AnonymousAuthenticationFilter` provides `AnonymousAuthenticationToken` | In-memory only, no persistence |
| Session promotion (anon→auth) | | ❌ | - | No built-in promotion mechanism — authentication replaces anonymous principal |
| Temporary data storage | | ❌ | - | No anonymous data store concept |
| JWT anonymous tokens | | ❌ | - | Anonymous auth uses in-memory token, not JWT |
| Rate limiting | | ❌ | - | No built-in rate limiting (delegated to app) |
| Session lifecycle | ⚠️ | | Session management exists | Anonymous sessions not tracked |
| Integration (Spring Boot) | ✅ | | Native — already in our stack | - |
| Scalability | ✅ | | Stateless design possible | Depends on implementation |

**Verdict**: Tham khảo pattern + extend — Use Spring Security's `AnonymousAuthenticationFilter` concept as foundation, but build JWT-based anonymous tokens on top.
**Recommendation**: Tham khảo pattern
**Reasoning**: Spring Security's anonymous auth is too basic for our needs (no JWT, no session promotion, no data merge). However, the filter chain integration pattern and `AnonymousAuthenticationToken` concept provide a solid foundation to extend.

### Firebase Auth (Anonymous Authentication) — Gap Analysis

**Overall Score**: 7.70 / 10
**URL**: https://firebase.google.com/docs/auth/web/anonymous-auth

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous user creation | ✅ | | `signInAnonymously()` creates temporary user | Proprietary Google service |
| Session promotion (anon→auth) | ✅ | | `linkWithCredential()` upgrades anonymous to permanent | Excellent pattern design |
| Temporary data storage | ✅ | | Firestore/RTDB per-user data | Tied to Firebase ecosystem |
| JWT anonymous tokens | ✅ | | Issues Firebase ID token for anonymous users | Non-standard JWT format |
| Rate limiting | ✅ | | Built-in abuse prevention | Google-managed quotas |
| Session lifecycle | ✅ | | Auto-cleanup of old anonymous accounts | Configurable retention |
| Integration (Spring Boot) | | ❌ | - | Firebase Admin SDK exists but different paradigm |
| Scalability | ✅ | | Google-scale infrastructure | Vendor lock-in |

**Verdict**: Tham khảo pattern — Firebase's `signInAnonymously()` → `linkWithCredential()` flow is the gold standard for anonymous → authenticated promotion. We should replicate this pattern with our own JWT infrastructure.
**Recommendation**: Tham khảo pattern
**Reasoning**: Firebase Auth has the most complete anonymous authentication implementation with seamless promotion. We cannot adopt it directly (proprietary, different ecosystem), but the API design (`signInAnonymously`, `linkWithCredential`, auto-cleanup) should be our primary design reference.

### Supabase Auth (GoTrue) — Gap Analysis

**Overall Score**: 6.70 / 10
**URL**: https://github.com/supabase/auth

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Anonymous user creation | ✅ | | `signInAnonymously()` creates user record with `is_anonymous=true` | Go implementation |
| Session promotion (anon→auth) | ✅ | | Update user with email/password to promote | Clean approach via identity linking |
| Temporary data storage | ⚠️ | | User metadata JSONB field | Limited to user record, no separate session data store |
| JWT anonymous tokens | ✅ | | Standard JWT with `is_anonymous` claim | Good pattern |
| Rate limiting | ⚠️ | | Basic rate limiting | Not anonymous-specific |
| Session lifecycle | ✅ | | Configurable session refresh, TTL | Good lifecycle management |
| Integration (Spring Boot) | | ❌ | - | Go-based, REST API only |
| Scalability | ✅ | | PostgreSQL-based, horizontally scalable | Depends on GoTrue deployment |

**Verdict**: Tham khảo pattern — Supabase's approach of adding `is_anonymous` column to users table and using identity linking for promotion is clean and practical. Their JWT claims structure (`is_anonymous: true`) is directly applicable.
**Recommendation**: Tham khảo pattern
**Reasoning**: Supabase's GoTrue has a practical anonymous auth implementation with `is_anonymous` flag in JWT. The PostgreSQL-based approach aligns with our tech stack. Good reference for JWT claims and database schema design.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Security | 7.90/10 | Tham khảo pattern | Foundation for anonymous auth filter chain integration |
| 🥈 2 | Firebase Auth | 7.70/10 | Tham khảo pattern | API design reference for signInAnonymously + linkWithCredential flow |
| 🥉 3 | Keycloak | 7.50/10 | Tham khảo pattern | Session lifecycle and TTL management patterns |
| 4 | Supabase Auth (GoTrue) | 6.70/10 | Tham khảo pattern | JWT claims structure (`is_anonymous`) and DB schema design |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Build from scratch** (with pattern references) | No open source project provides a complete, embeddable anonymous session promotion library for Spring Boot + Kotlin. All solutions are either too heavyweight (Keycloak), proprietary (Firebase), or different tech stack (Supabase/GoTrue). However, the patterns from Firebase (API design) and Supabase (JWT claims, DB schema) provide excellent blueprints. | Firebase Auth docs, Supabase Auth source code, Spring Security anonymous auth documentation |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)
