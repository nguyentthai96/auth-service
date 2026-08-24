# Phân tích so sánh: JWT Refresh Token Rotation

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Refresh Token Rotation with Reuse Detection |
| **Ngày phân tích** | 2025-01-27 |
| **Recommendation** | **Build from scratch** (extending existing RefreshTokenHandler with Family ID pattern from Keycloak/Auth0) |
| **Rationale** | Current codebase already has 80% of rotation logic. No embeddable open source library fits our Event Sourcing + CQRS architecture. All open source projects are full IdP/auth servers, not composable libraries. Extension is minimal — add familyId, reuse detection, and grace period to existing code. |
| **Confidence** | **HIGH** — battle-tested pattern (Auth0/Keycloak), minimal implementation gap, existing architecture supports it natively |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Keycloak | Open Source IdP | Full identity provider with built-in rotation + reuse detection via session families | Gold-standard implementation, battle-tested at scale | Separate deployment, Quarkus-based, over-engineered for our needs | ❌ | 8.95 |
| 2 | Spring Auth Server | Open Source OAuth2 | Full OAuth2 authorization server with rotation hooks | Spring-native, same ecosystem | No built-in reuse detection, requires full OAuth2 server setup | ❌ | 8.25 |
| 3 | Supertokens | Open Source Auth | Standalone auth service with "token theft detection" | Good reuse detection algorithm | Separate microservice, not embeddable, different architecture | ❌ | 7.55 |
| 4 | Custom Build | In-house | Extend existing RefreshTokenHandler with family ID, reuse detection, grace period | Perfect architecture fit, minimal change, Event Sourcing native | Custom maintenance, no community support | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Keycloak | Spring Auth Server | Supertokens | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Basic Token Rotation | ✅ | ✅ | ✅ | ✅ (existing) | ⭐ Must |
| Token Family Tracking | ✅ | ❌ | ✅ (chain-based) | ✅ (to build) | ⭐ Must |
| Reuse Detection | ✅ | ❌ | ✅ | ✅ (to build) | ⭐ Must |
| Automatic Family Revocation | ✅ | ❌ | ✅ | ✅ (to build) | ⭐ Must |
| Configurable Grace Period | ⚠️ | ❌ | ❌ | ✅ (to build) | ⭐ Must |
| Event Sourcing Integration | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| CQRS Pattern | ❌ | ❌ | ❌ | ✅ (existing) | ⭐ Must |
| Spring Boot Native | ❌ | ✅ | ❌ | ✅ (existing) | ⭐ Must |
| Redis Cache Integration | ⚠️ | ❌ | ✅ | ✅ (existing) | Nice to have |
| Domain Events for Security | ⚠️ | ❌ | ❌ | ✅ (to build) | Nice to have |
| Audit Trail (Event Sourcing) | ❌ | ❌ | ❌ | ✅ (existing) | Nice to have |
| Clean Architecture Ports/Adapters | ❌ | ⚠️ | ❌ | ✅ (existing) | Nice to have |
| **Coverage** | **4/8 Must** | **2/8 Must** | **4/8 Must** | **8/8 Must** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 8 | 25% (Spring Auth) - 100% (Custom) |
| Nice to have | 4 | 0% (Keycloak/Spring/Supertokens) - 100% (Custom) |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Keycloak | Spring Auth Server | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| FR-01: Token family tracking (familyId) | UC-001 | ✅ | ❌ | ✅ | Có — Spring Auth Server |
| FR-02: Reuse detection on revoked token | UC-002 | ✅ | ❌ | ✅ | Có — Spring Auth Server |
| FR-03: Automatic family revocation | UC-002 | ✅ | ❌ | ✅ | Có — Spring Auth Server |
| FR-04: Configurable grace period | UC-003 | ⚠️ | ❌ | ✅ | Có — all external |
| FR-05: Domain events for reuse detection | UC-002 | ❌ | ❌ | ✅ | Có — all external |
| FR-06: Event sourcing integration | NFR | ❌ | ❌ | ✅ | Có — all external |
| FR-07: CQRS command/handler pattern | NFR | ❌ | ❌ | ✅ | Có — all external |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Token rotation | Basic: revoke old → issue new | Family-aware: revoke old in family → issue new in same family | Add familyId to RefreshTokenEntity and TokenStore | **MED** |
| Reuse detection | None — revoked token returns same error as expired | Detect reuse → revoke entire family → record security event | New logic in RefreshTokenHandler + new port methods | **HIGH** |
| Grace period | None — immediate revocation | Configurable grace window (default 10s) allowing old token during transition | Add grace period logic + config property | **MED** |
| Domain events | TokenRevokedEvent(ROTATION) + TokenIssuedEvent(TOKEN_REFRESH) | + TokenReuseDetectedEvent + RevocationType.FAMILY_REVOKE | New event type, new revocation type | **LOW** |
| DB schema | refresh_tokens: userId, tokenHash, expiresAt, revoked | + family_id, + replaced_at, + revoked_at columns | Flyway migration | **LOW** |
| Redis cache | Token blacklist only (access tokens) | + Token family active token cache (Redis hash) | New Redis operations in TokenStore | **LOW** |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Keycloak (Best OS Score) | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 3-5 developer-days | 10+ days (integration, migration, learning) | Custom Build |
| Maintenance burden | Low (extends existing code) | High (separate Keycloak deployment, version upgrades) | Custom Build |
| Feature coverage | 100% (built to spec) | 50% (missing ES, CQRS, grace period) | Custom Build |
| Integration effort | Low (same codebase, same patterns) | Very High (different deployment, different architecture) | Custom Build |
| Long-term flexibility | High (full control, evolves with project) | Low (constrained by Keycloak's architecture) | Custom Build |
| Risk | Low (small change to existing proven code) | High (new dependency, architecture mismatch) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Keycloak | Spring Auth Server | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 5 | 3 | 10 |
| Integration ease | 25% | 2 | 5 | 10 |
| Maintenance | 20% | 3 | 5 | 9 |
| Community/Support | 15% | 10 | 9 | 3 |
| Learning curve | 10% | 3 | 6 | 9 |
| **Tổng điểm (weighted)** | | **4.30** | **5.05** | **8.85** |

### Reasoning

**Recommended approach**: Build from scratch (extend existing RefreshTokenHandler)

**Lý do**:
1. **80% done already** — RefreshTokenHandler already implements basic rotation with event recording. The extension (family ID, reuse detection, grace period) is incremental, not greenfield.
2. **Architecture fit** — Our Event Sourcing + CQRS + Clean Architecture pattern is unique. No open source project matches this architecture. Custom build follows existing patterns exactly.
3. **Industry-validated pattern** — The Family ID approach (Auth0/Keycloak) is battle-tested at massive scale. We're implementing a proven algorithm, not inventing something new.
4. **Minimal risk** — Changes are additive (new column, new port methods, new logic in existing handler). No existing functionality is removed or modified destructively.

**Trade-offs chấp nhận**:
- No community support for custom code — chấp nhận vì codebase is already custom, team maintains it
- Must write own tests — chấp nhận vì testing infrastructure (JUnit 5, Mockito, H2) already in place

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Grace period creates security window for stolen tokens | LOW | MED | Short default (10s), configurable, logged via domain events |
| Concurrent rotation race condition despite grace period | LOW | LOW | Redis-backed family state ensures consistency; grace period handles 99% of cases |
| Family ID column migration on existing data | LOW | LOW | Default family_id = UUID for existing rows; Flyway handles migration |
| Token family table grows unbounded | MED | LOW | Scheduled cleanup job (existing pattern for token_blacklist) |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build (recommended) | 3-5 | LOW | LOW |
| Keycloak adoption | 15-20 | HIGH | HIGH |
| Spring Auth Server migration | 10-15 | HIGH | MED |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
