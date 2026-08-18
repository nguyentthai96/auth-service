# Phân tích so sánh: Anonymous Login Optimization

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Anonymous Login / Guest Session Promotion |
| **Ngày phân tích** | 2025-01-20 |
| **Recommendation** | **Build from scratch (on top of existing Spring Security + Redis infrastructure)** |
| **Rationale** | No existing open source solution provides the complete anonymous-session-promotion-data-transfer pattern. The project already has mature JWT, session management, Redis, and CQRS infrastructure. Building custom anonymous auth is architecturally consistent and avoids heavy external dependencies. |
| **Confidence** | **HIGH** — Strong alignment between project architecture and required features; well-understood patterns from Firebase/Keycloak references. |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Spring Security Anonymous Auth | Open Source | Server-side anonymous principal via filter chain | Already integrated, minimal overhead | No client-facing tokens, no promotion flow | ⚠️ | 8.55 |
| 2 | Keycloak | Open Source | External IAM server with service accounts | Enterprise-proven, comprehensive | Heavy external dependency, conflicts with existing auth-service | ❌ | 7.85 |
| 3 | Firebase Auth | Commercial (Free tier) | Anonymous sign-in + account linking | Turnkey solution, well-documented | Vendor lock-in, not self-hosted, not embeddable | ❌ | N/A |
| 4 | Custom Build | In-house | Anonymous JWT + Redis session + promotion hooks | Full control, architecturally consistent, extensible | Development effort ~5-8 dev-days | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Spring Security | Keycloak | Firebase Auth | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Anonymous token generation (JWT) | ❌ | ⚠️ | ✅ | ✅ | ⭐ Must |
| Session promotion (anon→auth) | ❌ | ❌ | ✅ | ✅ | ⭐ Must |
| Data transfer hooks | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Anonymous session TTL management | ❌ | ⚠️ | ✅ | ✅ | ⭐ Must |
| Rate limiting (anon creation) | ❌ | ✅ | ✅ | ✅ | ⭐ Must |
| Redis session storage | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| Merge conflict resolution | ❌ | ❌ | ❌ | ✅ | Nice to have |
| Event-driven promotion | ❌ | ❌ | ❌ | ✅ | Nice to have |
| CQRS integration | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Spring Boot native | ✅ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Self-hosted | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| **Coverage** | **2/8** | **1/8** | **3/8** | **8/8** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 8 | 12.5% (Keycloak) - 100% (Custom) |
| Nice to have | 2 | 0% (all external) - 100% (Custom) |
| Optional | 1 | 0% - 100% |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Spring Security | Keycloak | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Generate anonymous JWT with `anonSessionId` claim | UC-001 | ❌ | ⚠️ | ✅ | Có — Spring Security, Keycloak |
| Store anonymous session data in Redis with TTL | UC-001, UC-002 | ❌ | ❌ | ✅ | Có — all external |
| Promote anonymous session to authenticated user | UC-002 | ❌ | ❌ | ✅ | Có — all external |
| Transfer data from Redis to user's persistent store | UC-002 | ❌ | ❌ | ✅ | Có — all external |
| Rate limit anonymous session creation (IP-based) | NFR-001 | ❌ | ✅ | ✅ | Có — Spring Security |
| Cleanup expired anonymous sessions | NFR-002 | ❌ | ⚠️ | ✅ | Có — Spring Security |
| Abuse prevention (fingerprinting, CAPTCHA escalation) | NFR-003 | ❌ | ✅ | ✅ | Có — Spring Security |
| CQRS command handler pattern | Architecture | ❌ | ❌ | ✅ | Có — all external |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Anonymous access | No anonymous tokens; unauthenticated requests get 401 | Anonymous JWT tokens issued on-demand; limited access | Must build anonymous token generation | HIGH |
| Session types | Only authenticated login sessions (`LoginSessionEntity`) | Authenticated + Anonymous sessions, with anonymous lifecycle | Must extend session model | HIGH |
| Token types | `access`, `refresh`, `mfa` | `access`, `refresh`, `mfa`, `anonymous` | Must add `anonymous` token type to JwtService | MEDIUM |
| Redis usage | OTP, MFA state, rate limits, cipher key sessions | + Anonymous session data storage | Extend Redis key namespace | LOW |
| Data transfer | N/A — no temporary data concept | Hook-based data transfer on session promotion | Must build data transfer framework | MEDIUM |
| SecurityConfig | Anonymous requests blocked (except login/register) | Anonymous endpoints permitted; anonymous tokens validated | Must update SecurityConfig | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Spring Security Anonymous | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 5-8 dev-days | 1-2 dev-days (partial, needs heavy customization anyway) | Custom Build (complete solution) |
| Maintenance burden | Moderate — own code to maintain | Low initial, but needs custom extensions that add complexity | Custom Build (simpler long-term) |
| Feature coverage | 100% (all Must features) | 25% (only anonymous principal) | Custom Build |
| Integration effort | Low — follows existing patterns | Low — already integrated | Tie |
| Long-term flexibility | High — full control over extension | Low — constrained by Spring Security model | Custom Build |
| Risk | Low — well-understood patterns | Low — proven framework | Tie |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Spring Security | Keycloak | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 2 | 2 | 10 |
| Integration ease | 25% | 8 | 3 | 9 |
| Maintenance | 20% | 7 | 4 | 7 |
| Community/Support | 15% | 9 | 9 | 5 |
| Learning curve | 10% | 9 | 4 | 8 |
| **Tổng điểm (weighted)** | | **5.95** | **3.80** | **8.35** |

### Reasoning

**Recommended approach**: Build from scratch (Custom Build on existing infrastructure)

**Lý do**:
1. **No existing solution covers the full requirement** — Session promotion with data transfer hooks is not available in any evaluated open source project. Spring Security only provides server-side anonymous principals without client-facing tokens. Keycloak/CAS are external IAM servers that conflict with the existing architecture.
2. **Existing infrastructure is sufficient** — The project already has JwtService (multi-type token generation), LoginSessionService (session tracking), Redis (caching/state), and CQRS (command handlers). Building anonymous auth follows the same patterns with minimal new infrastructure.
3. **Architectural consistency** — Custom build follows Clean Architecture, CQRS command/handler pattern, and Hexagonal adapter structure already established in the codebase. External solutions would introduce architectural friction.

**Trade-offs chấp nhận**:
- No community support for anonymous-specific code — chấp nhận vì the patterns are well-understood and the implementation is bounded in scope
- Development effort of 5-8 dev-days — chấp nhận vì this is a one-time investment with long-term flexibility

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Redis memory pressure from high anonymous session volume | MED | MED | Configure aggressive TTL (24h), set Redis maxmemory-policy to allkeys-lru, monitor memory usage |
| Anonymous session abuse (bot-created sessions) | MED | HIGH | Multi-dimensional rate limiting (IP + device fingerprint), CAPTCHA escalation after threshold |
| Data loss during promotion (race condition) | LOW | HIGH | Atomic promotion with Redis transaction + PostgreSQL transaction; idempotency key on promotion request |
| Anonymous token leak/replay | LOW | MED | Short TTL, token rotation on access, fingerprint binding |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build | 5-8 | MED | LOW |
| Spring Security + heavy customization | 3-5 (partial coverage only) | MED | MED (ongoing gap-filling) |
| Keycloak integration | 8-15 | HIGH | HIGH (operational overhead) |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix + gap analysis |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
