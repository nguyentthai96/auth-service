# Phân tích so sánh: JWT Token Validation

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày phân tích** | 2025-01-20 |
| **Recommendation** | **Build from scratch (continue current custom implementation)** |
| **Rationale** | Current implementation already covers all critical requirements (RS256 + HMAC fallback, claim validation chain, tiered blacklist, JWKS, introspection). No open-source library provides all application-level concerns (blacklist, event recording, multi-token-type). Migration cost outweighs benefits. |
| **Confidence** | **HIGH** — Current implementation aligns with RFC 8725 best practices and exceeds capabilities of any single library |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | JJWT + Custom Layer (current) | In-house + Library | JJWT for JWT parsing, custom ClaimValidatorChain + blacklist + events | Full control, covers all requirements, already integrated | More code to maintain | ✅ | 9.0 |
| 2 | Spring Security Resource Server | Framework | NimbusJwtDecoder + OAuth2TokenValidator composable chain | Less boilerplate, auto JWKS fetch, Spring-native | Opinionated, hard to support custom token types, no blacklist | ⚠️ | 7.5 |
| 3 | Nimbus JOSE+JWT (direct) | Open Source | Replace JJWT with Nimbus for JWT parsing + JWKS client | Most spec-complete, built-in JWKS client | Migration effort, different API style, no application-level features | ⚠️ | 7.0 |
| 4 | Auth0 java-jwt | Open Source | Replace JJWT with Auth0 library | Simpler API | Less features than JJWT, no JWE, no JWKS client | ❌ | 5.5 |
| 5 | Keycloak (managed IAM) | Commercial/OSS | Delegate all token validation to Keycloak | Full IAM platform, zero custom code | Operational complexity, overkill, vendor coupling | ❌ | 4.0 |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | JJWT + Custom (current) | Spring Security RS | Nimbus Direct | Auth0 java-jwt | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| RS256 signature verification | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| HMAC fallback (legacy migration) | ✅ | ❌ | ✅ | ✅ | ⭐ Must |
| Dual-key rotation overlap (RS256) | ✅ | ✅ | ✅ | ❌ | ⭐ Must |
| Clock skew tolerance (configurable) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Claim validation chain (extensible) | ✅ | ✅ | ⚠️ | ❌ | ⭐ Must |
| Issuer claim validation | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Audience claim validation (feature-flagged) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Token type validation (mfa/refresh rejection) | ✅ | ❌ | ❌ | ❌ | ⭐ Must |
| Token blacklist (tiered cache) | ✅ | ❌ | ❌ | ❌ | ⭐ Must |
| Circuit breaker for Redis blacklist | ✅ | ❌ | ❌ | ❌ | Nice to have |
| JWKS endpoint with ETag | ✅ | ✅ | ✅ | ❌ | ⭐ Must |
| RFC 7662 introspection | ✅ | ⚠️ | ❌ | ❌ | ⭐ Must |
| Validation failure event recording | ✅ | ❌ | ❌ | ❌ | ⭐ Must |
| Multi-token-type support (access/mfa/anon/service) | ✅ | ❌ | ⚠️ | ❌ | ⭐ Must |
| Anonymous token handling (ROLE_ANONYMOUS) | ✅ | ❌ | ❌ | ❌ | ⭐ Must |
| Service-to-service token validation | ✅ | ⚠️ | ❌ | ❌ | ⭐ Must |
| **Coverage** | **16/16** | **8/16** | **7/16** | **5/16** | |

<!-- Legend: ✅ Có đầy đủ | ⚠️ Có nhưng hạn chế | ❌ Không có | ❓ Cần build thêm -->

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 14 | 36% (Auth0) - 100% (Current) |
| Nice to have | 2 | 0% (all others) - 100% (Current) |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | JJWT + Custom | Spring Security RS | Nimbus | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| RS256 + HMAC dual signing | FR-006 | ✅ | ❌ | ✅ | Spring Security RS — no HMAC fallback |
| Claim validation chain (fail-fast + collect-all) | FR-004, FR-009 | ✅ | ⚠️ | ❌ | Nimbus — no chain pattern; Spring — different interface |
| Tiered blacklist (Caffeine+Redis+DB) | FR-001, FR-003 | ✅ | ❌ | ❌ | All external — application concern |
| Token type validation | FR-004 | ✅ | ❌ | ❌ | All external — custom token types not supported |
| Validation event recording | FR-012 | ✅ | ❌ | ❌ | All external — no built-in event recording |
| RFC 7662 introspection | FR-007 | ✅ | ⚠️ | ❌ | Spring — opaque token introspection only |
| JWKS with ETag | FR-008 | ✅ | ✅ | ✅ | No gap for top solutions |
| Key rotation overlap | FR-006 | ✅ | ✅ | ✅ | No gap |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| JWT parsing | JJWT 0.12.x with RS256 + HMAC fallback | Same — stable and well-tested | None | LOW |
| Claim validation | Custom ClaimValidatorChain (3 validators) | Same + potential new validators (nbf, custom) | Minor — extensible via interface | LOW |
| Blacklist | 3-tier cache with custom circuit breaker | Same — consider Resilience4j integration | Optional improvement | LOW |
| JWKS | Custom endpoint with ETag + 24h max-age | Same — aligned with industry standards | None | LOW |
| Introspection | RFC 7662 compliant endpoint | Same — covers active/inactive + metadata | None | LOW |
| Event recording | Custom via TokenEventRecorder → Kafka | Same — consider structured event schema | Minor — schema evolution | LOW |
| RFC 8725 compliance | Partial — most recommendations followed | Full compliance checklist verification | Need formal audit | MED |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build (Current) | Reuse Spring Security RS | Winner |
|--------|:---:|:---:|:---:|
| Time to market | Already complete (0 days) | 5-10 days migration | Custom Build |
| Maintenance burden | Medium (custom code) | Low (framework maintained) | Spring Security RS |
| Feature coverage | 16/16 (100%) | 8/16 (50%) | Custom Build |
| Integration effort | Zero (already integrated) | High (refactor filter chain) | Custom Build |
| Long-term flexibility | High (full control) | Medium (framework constraints) | Custom Build |
| Risk | Low (proven in production) | Medium (migration risk) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | JJWT + Custom | Spring Security RS | Nimbus Direct |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 10 (3.0) | 5 (1.5) | 4.5 (1.35) |
| Integration ease | 25% | 10 (2.5) | 4 (1.0) | 5 (1.25) |
| Maintenance | 20% | 7 (1.4) | 9 (1.8) | 6 (1.2) |
| Community/Support | 15% | 8 (1.2) | 10 (1.5) | 8 (1.2) |
| Learning curve | 10% | 10 (1.0) | 6 (0.6) | 5 (0.5) |
| **Tổng điểm (weighted)** | | **9.1** | **6.4** | **5.5** |

### Reasoning

**Recommended approach**: Build from scratch (continue current custom implementation)

**Lý do**:
1. **100% feature coverage** — Current implementation covers all 16 requirements including custom token types, tiered blacklist, event recording, and multi-mode claim validation — no external solution covers more than 50%.
2. **Zero migration cost** — Implementation is already in production, well-tested (JwtServiceAnonymousTest, TokenIntrospectionIntegrationTest, MfaServiceTest), and stable. Any migration introduces regression risk.
3. **RFC compliance** — Current architecture aligns with RFC 8725 (JWT BCP): asymmetric keys (RS256), algorithm restriction (no alg header confusion), claim validation chain, clock skew tolerance, key rotation support.

**Trade-offs chấp nhận**:
- Higher maintenance burden vs Spring Security RS — chấp nhận vì feature coverage gap (50%) makes migration impractical
- Custom circuit breaker vs Resilience4j — chấp nhận vì simplicity; custom implementation is adequate for blacklist use case

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| JJWT library deprecation | LOW | HIGH | Nimbus JOSE+JWT as drop-in replacement; Spring Security uses Nimbus internally |
| Custom code security vulnerability | LOW | HIGH | Follow RFC 8725 checklist, regular security audits, OWASP JWT cheat sheet compliance |
| Redis blacklist cache inconsistency | MED | MED | Circuit breaker + DB fallback ensures correctness; eventual consistency window is configurable (Caffeine TTL 30s) |
| Key rotation failure | LOW | HIGH | Dual-key overlap period, JWKS endpoint with ETag, monitoring for key mismatch events |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Continue current (maintain) | 0 (already built) | LOW | MED |
| Spring Security RS migration | 8-12 | HIGH | LOW |
| Nimbus direct migration | 5-8 | MED | MED |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
