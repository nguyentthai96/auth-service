# Phân tích so sánh: Anonymous Login Optimization

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login Optimization |
| **Ngày phân tích** | 2025-01-20 |
| **Recommendation** | **Build from scratch (with pattern references from Firebase Auth + Supabase GoTrue)** |
| **Rationale** | No existing open source solution provides an embeddable, Spring Boot-native anonymous session promotion library. All evaluated solutions are either proprietary (Firebase), different tech stack (Supabase/GoTrue in Go), or too basic (Spring Security anonymous filter). The auth-service already has a mature JWT infrastructure, CQRS handlers, and Redis integration — building custom is the lowest-risk path with highest integration quality. |
| **Confidence** | **HIGH** — All 4 evaluated open source projects and 4 commercial products confirm that anonymous session promotion is universally custom-built; no drop-in library exists for JVM/Spring Boot. |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Spring Security (Anonymous Auth) | Open Source | `AnonymousAuthenticationFilter` injects in-memory anonymous principal | Native Spring integration, already in stack, zero extra deps | No JWT, no persistence, no session promotion, no data merge | ⚠️ | 7.90 |
| 2 | Firebase Auth | Proprietary | `signInAnonymously()` → `linkWithCredential()` — creates temporary user with UID, promotes via credential linking | Complete implementation, gold standard API design, battle-tested | Vendor lock-in, proprietary, not embeddable in Spring Boot | ❌ | 7.70 |
| 3 | Keycloak | Open Source | Anonymous user via admin API + client credentials grant with limited scope | Session lifecycle management, SPI extensibility, enterprise-grade | Heavyweight (separate server), no native session promotion, Java but different paradigm | ❌ | 7.50 |
| 4 | Supabase Auth (GoTrue) | Open Source | `is_anonymous=true` flag on user table + identity linking for promotion | Clean JWT claims (`is_anonymous`), PostgreSQL-based, open source | Go implementation, REST API only, no Spring Boot integration | ⚠️ | 6.70 |
| 5 | Custom Build | In-house | Ephemeral Redis sessions + JWT anonymous tokens + CQRS handler for promotion | Perfect integration with existing architecture, full control, no DB pollution | Development effort, no community support | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Spring Security | Firebase Auth | Keycloak | Supabase GoTrue | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| Anonymous token generation (JWT) | ❌ | ✅ | ⚠️ | ✅ | ✅ | ⭐ Must |
| Session promotion (anon→auth) | ❌ | ✅ | ❌ | ✅ | ✅ | ⭐ Must |
| Temporary data storage (Redis) | ❌ | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Data merge on promotion | ❌ | ✅ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Rate limiting for anonymous | ❌ | ✅ | ✅ | ⚠️ | ✅ | ⭐ Must |
| Anonymous session TTL management | ❌ | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Auto-cleanup expired sessions | ❌ | ✅ | ✅ | ✅ | ✅ | Should |
| Spring Boot native integration | ✅ | ❌ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Existing CQRS handler compatibility | ⚠️ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Custom JWT claims (`type=anonymous`) | ❌ | ❌ | ❌ | ✅ | ✅ | ⭐ Must |
| RS256 signing key reuse | N/A | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Concurrent promotion handling | ❌ | ✅ | ❌ | ⚠️ | ✅ | Should |
| **Coverage** | **1/11** | **5/11** | **2/11** | **4/11** | **11/11** | |

<!-- Legend: ✅ Có đầy đủ | ⚠️ Có nhưng hạn chế | ❌ Không có | ❓ Cần build thêm -->

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 9 | 11% (Spring Security) - 100% (Custom Build) |
| Should | 2 | 0% (Spring Security) - 100% (Custom Build) |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Spring Security | Firebase Auth | Supabase GoTrue | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|:---:|
| Generate anonymous JWT with `type=anonymous` claim | UC-001 | ❌ | ❌ | ✅ | ✅ | Yes — Spring Security, Firebase |
| Store anonymous session data in Redis with TTL | UC-001 | ❌ | ❌ | ❌ | ✅ | Yes — all external solutions |
| Promote anonymous session to authenticated user | UC-002 | ❌ | ✅ | ✅ | ✅ | Yes — Spring Security, Keycloak |
| Transfer Redis data to authenticated user namespace | UC-002 | ❌ | ❌ | ❌ | ✅ | Yes — all external solutions |
| Rate limit anonymous token creation per IP | UC-004 | ❌ | ✅ | ⚠️ | ✅ | Partial — Supabase |
| Integrate with existing CQRS CommandHandler pattern | Architecture | ⚠️ | ❌ | ❌ | ✅ | Yes — all external solutions |
| Use existing RS256 signing infrastructure | Architecture | N/A | ❌ | ❌ | ✅ | Yes — all external solutions |
| Anonymous sessions NOT counted toward maxSessions | Business Rule | N/A | N/A | N/A | ✅ | Yes — not configurable in external solutions |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Token types | Access, Refresh, MFA | Access, Refresh, MFA, **Anonymous** | New token type with shorter TTL and limited claims | HIGH |
| Session tracking | Authenticated sessions only (`login_sessions` table) | Authenticated + **Anonymous sessions** (Redis-only for anonymous) | New anonymous session store in Redis; existing `LoginSessionService` needs awareness | HIGH |
| Security filter chain | JWT auth filter with roles/permissions | JWT auth filter + **anonymous token handling** (different claim parsing) | `JwtAuthFilter` needs to recognize `type=anonymous` tokens and set limited authorities | HIGH |
| Rate limiting | IP/username/device rate limiting for login | Existing + **anonymous token creation rate limiting** per IP | New rate limit dimension for anonymous endpoints | MEDIUM |
| Security config | Public endpoints + authenticated endpoints | Public + authenticated + **anonymous-permitted endpoints** | `SecurityConfig` needs new endpoint patterns for anonymous-accessible resources | MEDIUM |
| Configuration | `SecurityProperties` with JWT, password, MFA, session configs | Existing + **`AnonymousProperties`** (TTL, rate limits, max data size) | New configuration block under `app.security.anonymous` | LOW |
| Data cleanup | No automated session cleanup | **Scheduled cleanup** of expired anonymous Redis keys | New scheduled task or rely on Redis TTL auto-expiry | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse (Best: Firebase pattern reference) | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 5-8 developer-days | N/A (no reusable artifact) | Custom Build (only option) |
| Maintenance burden | Medium — own code to maintain | N/A | Custom Build |
| Feature coverage | 100% — all Must features covered | 45% (Firebase) — no Spring Boot integration, no Redis, no CQRS | Custom Build |
| Integration effort | Low — uses existing JwtService, TokenGenerator, Redis, CQRS | Very High — would require adapter layer + paradigm mismatch | Custom Build |
| Long-term flexibility | High — full control over behavior, configuration, evolution | Low — constrained by external API design | Custom Build |
| Risk | Low — simple feature on proven infrastructure | High — vendor dependency, migration risk | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Spring Security | Firebase Auth (pattern ref) | Supabase GoTrue (pattern ref) | Custom Build |
|----------|----------|:---:|:---:|:---:|:---:|
| Feature coverage | 30% | 1 | 5 | 4 | 10 |
| Integration ease | 25% | 7 | 1 | 1 | 10 |
| Maintenance | 20% | 8 | N/A | N/A | 6 |
| Community/Support | 15% | 9 | 8 | 7 | 2 |
| Learning curve | 10% | 9 | 3 | 3 | 8 |
| **Tổng điểm (weighted)** | | **5.95** | **3.85** | **3.35** | **8.00** |

### Reasoning

**Recommended approach**: Build from scratch — using Firebase Auth's API design and Supabase GoTrue's JWT claims structure as design references.

**Lý do**:
1. **No drop-in solution exists** — Every evaluated open source project and commercial product either lacks session promotion entirely (Spring Security, Keycloak) or operates in a completely different ecosystem (Firebase, Supabase GoTrue). Building custom is not a choice but a necessity.
2. **Existing infrastructure is ideal** — The auth-service already has `JwtService` (RS256 signing, custom claims), `TokenGenerator` (shared token logic), `LoginSessionService` (session tracking), `LoginRateLimitService` (Redis-based rate limiting), and CQRS `CommandHandler` pattern. Adding anonymous token support is an incremental extension, not a greenfield build.
3. **Ephemeral Redis model is optimal** — Based on web research, the Redis-only approach (no anonymous user records in PostgreSQL) avoids DB pollution, leverages existing Redis infrastructure, and auto-cleans via TTL. This aligns with the auth-service's role as a pure authentication service — temporary session data belongs in Redis, not the user table.

**Trade-offs chấp nhận**:
- **No community support** — We build and maintain the anonymous session code ourselves. Mitigated by: simple, well-bounded feature scope (~6 classes).
- **Data loss risk** — Redis-only anonymous sessions are not durable. If Redis restarts, anonymous session data is lost. Accepted because: anonymous sessions are ephemeral by design (1h TTL), and users can simply create a new anonymous session. Critical operations (checkout, save) should require authentication first.

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Anonymous token abuse (bot farm creating millions of tokens) | MEDIUM | HIGH | IP-based rate limiting (max 5 anonymous tokens/IP/hour), CAPTCHA after threshold, Redis memory quota monitoring |
| Race condition during session promotion (two requests try to promote same anonymous session) | LOW | MEDIUM | Redis SETNX-based lock on anonymous session ID during promotion; idempotent promotion handler |
| Redis memory exhaustion from anonymous sessions | LOW | HIGH | Configurable max anonymous data size (default 64KB), TTL auto-cleanup, Redis memory policy (allkeys-lru) |
| Anonymous JWT token reuse after promotion | LOW | MEDIUM | Invalidate anonymous token JTI after successful promotion; blacklist in token blacklist repository |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build | 5-8 days | MEDIUM | LOW (simple, well-bounded) |
| Firebase pattern + adapter | N/A (not feasible) | HIGH | HIGH (paradigm mismatch) |
| Supabase GoTrue fork | 15-20 days (Go→Kotlin port) | HIGH | HIGH (maintain forked codebase) |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis (Phase 1) |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix (Phase 2) |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation + patterns (Phase 3) |

---

> **Next step**: Business Analysis (business_analysis.md)
