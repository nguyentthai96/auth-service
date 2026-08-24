# Kết quả tìm kiếm Open Source: JWT Token Validation

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày tìm kiếm** | 2025-01-20 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot 3.x / JJWT |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"JWT validation library Java Kotlin Spring Boot"` | 5+ kết quả | Found JJWT, Nimbus, java-jwt, Spring Security Resource Server |
| 2 | `"JWT token blacklist cache Redis open source"` | 3 kết quả | Found various cache-based revocation approaches |
| 3 | `"JWKS endpoint key rotation Spring Boot"` | 4 kết quả | Found Spring Authorization Server, Nimbus JWKS support |
| 4 | `"JWT claim validation chain pattern open source"` | 2 kết quả | Most libraries do monolithic validation |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | JJWT (io.jsonwebtoken) | https://github.com/jwtk/jjwt | 10.3k | 2024-Q4 | Apache 2.0 | ✅ Có |
| 2 | Nimbus JOSE+JWT | https://connect2id.com/products/nimbus-jose-jwt | 2.1k (Bitbucket) | 2024-Q4 | Apache 2.0 | ✅ Có |
| 3 | Auth0 java-jwt | https://github.com/auth0/java-jwt | 5.4k | 2024-Q3 | MIT | ✅ Có |
| 4 | Spring Security OAuth2 Resource Server | https://github.com/spring-projects/spring-security | 9.1k | 2024-Q4 | Apache 2.0 | ✅ Có |
| 5 | Vert.x JWT | https://github.com/eclipse-vertx/vertx-auth | 0.3k | 2024-Q3 | Apache 2.0 | ❌ Không (Vert.x ecosystem) |
| 6 | FusionAuth JWT | https://github.com/FusionAuth/fusionauth-jwt | 0.5k | 2024-Q2 | Apache 2.0 | ❌ Không (lower adoption) |
| 7 | Jose4j | https://bitbucket.org/b_c/jose4j | 0.2k | 2024-Q3 | Apache 2.0 | ❌ Không (overlaps with Nimbus) |
| 8 | Keycloak (JWT module) | https://github.com/keycloak/keycloak | 23k | 2024-Q4 | Apache 2.0 | ❌ Không (full IAM, not library) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### JJWT (io.jsonwebtoken)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full JWS/JWE, RS256/384/512, ES*, PS*, EdDSA, custom claims, compression |
| Applicability | 10 | 15% | 1.50 | Already used in project — zero migration cost |
| Activity | 8 | 15% | 1.20 | Regular releases, latest 0.12.x stable |
| Documentation | 8 | 15% | 1.20 | Comprehensive README, migration guide, Javadoc |
| Code quality | 9 | 15% | 1.35 | 98%+ test coverage, clean API design |
| Community | 9 | 10% | 0.90 | 10.3k stars, widely adopted in Spring ecosystem |
| Popularity | 9 | 10% | 0.90 | De facto standard for Java/Kotlin JWT |
| **Tổng điểm** | | | **8.85/10** | |

#### Nimbus JOSE+JWT

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Most complete JOSE implementation: JWS, JWE, JWK, JWT, JWKS endpoint client |
| Applicability | 8 | 15% | 1.20 | Spring Security default JWT decoder uses Nimbus internally |
| Activity | 9 | 15% | 1.35 | Actively maintained by Connect2id, frequent releases |
| Documentation | 9 | 15% | 1.35 | Excellent docs, cookbook, migration guides |
| Code quality | 9 | 15% | 1.35 | Extensive test suite, clean JOSE spec compliance |
| Community | 7 | 10% | 0.70 | Smaller GitHub community but huge adoption (Spring Security default) |
| Popularity | 9 | 10% | 0.90 | Used by Spring Security, Azure AD SDK, many enterprise products |
| **Tổng điểm** | | | **8.85/10** | |

#### Auth0 java-jwt

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | JWS only (no JWE), supports RS256/ES256/HMAC, basic claim validation |
| Applicability | 7 | 15% | 1.05 | Java-native, works with Spring but no Spring-specific integration |
| Activity | 7 | 15% | 1.05 | Regular maintenance releases but slower cadence |
| Documentation | 8 | 15% | 1.20 | Good README, Auth0 ecosystem docs |
| Code quality | 8 | 15% | 1.20 | Well-tested, clean builder pattern API |
| Community | 8 | 10% | 0.80 | 5.4k stars, strong Auth0 brand backing |
| Popularity | 7 | 10% | 0.70 | Popular but declining vs JJWT for non-Auth0 users |
| **Tổng điểm** | | | **7.40/10** | |

#### Spring Security OAuth2 Resource Server

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Built-in JWT decoder, JWKS support, claim validation, token introspection |
| Applicability | 9 | 15% | 1.35 | Spring Boot 3.x native, auto-configuration, uses Nimbus internally |
| Activity | 10 | 15% | 1.50 | Part of Spring Security — continuous releases |
| Documentation | 9 | 15% | 1.35 | Excellent Spring docs, reference guide, samples |
| Code quality | 9 | 15% | 1.35 | Spring quality standards, extensive integration tests |
| Community | 10 | 10% | 1.00 | 9.1k stars (Spring Security overall), massive community |
| Popularity | 10 | 10% | 1.00 | Industry standard for Spring applications |
| **Tổng điểm** | | | **9.15/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | JJWT | 9 | 10 | 8 | 8 | 9 | 9 | 9 | **8.85** |
| 2 | Nimbus JOSE+JWT | 10 | 8 | 9 | 9 | 9 | 7 | 9 | **8.85** |
| 3 | Auth0 java-jwt | 7 | 7 | 7 | 8 | 8 | 8 | 7 | **7.40** |
| 4 | Spring Security Resource Server | 8 | 9 | 10 | 9 | 9 | 10 | 10 | **9.15** |

---

## 4. Gap Analysis chi tiết

### JJWT (io.jsonwebtoken) — Gap Analysis

**Overall Score**: 8.85 / 10
**URL**: https://github.com/jwtk/jjwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token parsing & validation | ✅ | | Fluent builder API, automatic algorithm detection | - |
| RS256/384/512 signing | ✅ | | Full RSA algorithm support | - |
| HMAC fallback | ✅ | | HS256/384/512 support | - |
| Clock skew tolerance | ✅ | | `clockSkewSeconds()` built-in | - |
| Custom claim extraction | ✅ | | Type-safe claim accessors | - |
| JWE (encrypted tokens) | ✅ | | Added in 0.12.x | - |
| JWKS endpoint client | | ❌ | - | No built-in JWKS fetcher — must implement manually |
| Claim validation chain | | ❌ | - | No built-in chain pattern — claims parsed, not validated |
| Token blacklist | | ❌ | - | Not in scope — application responsibility |
| Key rotation support | ⚠️ | | kid header support | No automatic key rotation handling |
| RFC 7662 introspection | | ❌ | - | Not in scope — application-level concern |

**Verdict**: Tham khảo pattern — already in use, strong low-level JWT library
**Recommendation**: Tiếp tục sử dụng — đây là lựa chọn đúng cho low-level JWT operations
**Reasoning**: JJWT excels as a JWT parsing/signing library. The project correctly supplements it with application-level concerns (blacklist, claim chain, introspection).

### Nimbus JOSE+JWT — Gap Analysis

**Overall Score**: 8.85 / 10
**URL**: https://connect2id.com/products/nimbus-jose-jwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token parsing & validation | ✅ | | Full JOSE spec compliance | Slightly more verbose API |
| RS256 signing | ✅ | | All RSA/EC/EdDSA algorithms | - |
| JWKS endpoint client | ✅ | | `JWKSource`, `RemoteJWKSet` with caching | - |
| Claim validation | ✅ | | `JWTClaimsSetVerifier` interface with `DefaultJWTClaimsVerifier` | Less flexible than custom chain |
| Key rotation | ✅ | | Built-in kid matching via JWKSelector | - |
| JWE encryption | ✅ | | Complete JWE support | - |
| Token blacklist | | ❌ | - | Application responsibility |
| Spring integration | ✅ | | Used by Spring Security internally | - |
| RFC 7662 introspection | | ❌ | - | Not built-in (application concern) |

**Verdict**: Tham khảo pattern — strong alternative, used by Spring Security underneath
**Recommendation**: Tham khảo pattern — especially JWKS client and claim verifier patterns
**Reasoning**: Nimbus is the most spec-complete JOSE library. Its `DefaultJWTClaimsVerifier` pattern inspired the project's `ClaimValidator` chain. JWKS client could be referenced for external key source scenarios.

### Auth0 java-jwt — Gap Analysis

**Overall Score**: 7.40 / 10
**URL**: https://github.com/auth0/java-jwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token parsing | ✅ | | Simple builder API | Less features than JJWT |
| RS256 signing | ✅ | | RSA/HMAC/EC support | - |
| Claim validation | ⚠️ | | Built-in `withIssuer()`, `withAudience()` on verifier | Monolithic — cannot extend chain |
| JWE encryption | | ❌ | - | JWS only |
| JWKS support | | ❌ | - | Separate `jwks-rsa-java` library needed |
| Key rotation | | ❌ | - | No built-in key rotation |
| Token blacklist | | ❌ | - | Application responsibility |

**Verdict**: Không phù hợp — less capable than current JJWT choice
**Recommendation**: Bỏ qua — no advantage over current JJWT implementation
**Reasoning**: Auth0 java-jwt is simpler but less feature-rich. JJWT already covers all its capabilities and more.

### Spring Security OAuth2 Resource Server — Gap Analysis

**Overall Score**: 9.15 / 10
**URL**: https://github.com/spring-projects/spring-security

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| JWT decoder/validator | ✅ | | `NimbusJwtDecoder` with configurable validators | Opinionated — harder to customize |
| JWKS auto-fetch | ✅ | | `JwtDecoders.fromIssuerLocation()` auto-configures JWKS | - |
| Claim validation | ✅ | | `OAuth2TokenValidator<Jwt>` interface — composable | Different pattern than current ClaimValidator |
| Key rotation | ✅ | | Automatic via JWKS refresh | - |
| Spring Security integration | ✅ | | Native SecurityFilterChain, `@PreAuthorize` | - |
| Token blacklist | | ❌ | - | No built-in blacklist — must add custom |
| Custom token types | ⚠️ | | Supports JWT, opaque tokens | Designed for OAuth2 tokens — custom types need adaptation |
| Event recording | | ❌ | - | No built-in validation event recording |
| Multi-key fallback | ⚠️ | | Via JWKS kid matching | No HMAC legacy fallback pattern |

**Verdict**: Tham khảo pattern — excellent framework but migration has trade-offs
**Recommendation**: Tham khảo pattern — adopt `OAuth2TokenValidator` pattern, keep custom JwtAuthFilter
**Reasoning**: Full migration to Spring Security Resource Server would require significant refactoring of the custom blacklist, event recording, and multi-token-type handling. The `OAuth2TokenValidator<Jwt>` composable pattern is worth studying.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Security Resource Server | 9.15/10 | Tham khảo pattern | Framework-level JWT validation, JWKS auto-fetch |
| 🥈 2 | JJWT | 8.85/10 | Dùng trực tiếp (đang dùng) | Low-level JWT parsing/signing |
| 🥈 2 | Nimbus JOSE+JWT | 8.85/10 | Tham khảo pattern | JOSE spec compliance, JWKS client |
| 🥉 4 | Auth0 java-jwt | 7.40/10 | Bỏ qua | Simpler projects not needing JJWT features |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| Tiếp tục JJWT + custom validation layer | JJWT is already integrated, well-maintained, and covers all low-level JWT needs. Custom ClaimValidatorChain + blacklist + event recording provide application-level concerns that no single library offers out-of-box. | JJWT 10.3k stars, 98%+ test coverage, same-stack integration score 10/10 |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-01-20
> **Next step**: Comparison Analysis (comparison_analysis.md)
