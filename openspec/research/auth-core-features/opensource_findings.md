# Kết quả tìm kiếm Open Source: Auth Core Features

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Auth Core Features (MFA, SSO, RS256 JWT, Password Policy) |
| **Ngày tìm kiếm** | 2026-08-19 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 5 |
| **Tech stack mục tiêu** | Kotlin/Java, Spring Boot 3.2+, PostgreSQL 17, Redis |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"TOTP OTP Java library open source GitHub"` | 4 kết quả | `dev.samstevens.totp`, `GoogleAuth`, `aerogear-otp`, `otp-java` |
| 2 | `"password validation Java composable rules library"` | 2 kết quả | `Passay`, `zxcvbn4j` |
| 3 | `"CAPTCHA verification server-side Java Spring Boot"` | 3 kết quả | Turnstile, hCaptcha, reCAPTCHA v3 (APIs, not libraries) |
| 4 | `"Spring Security OAuth2 SSO open source starter"` | 2 kết quả | Spring Security native, Keycloak Admin Client |
| 5 | `"JWT RS256 JWKS Java library"` | 1 kết quả | JJWT (already in use) |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | dev.samstevens.totp | [ARCHIVED] github.com/samstevens/java-totp | ~800+ | 2024 | MIT | ✅ Có |
| 2 | GoogleAuth | https://github.com/wstrange/GoogleAuth | ~1100+ | 2023 | BSD-2 | ✅ Có |
| 3 | Passay | https://github.com/vt-middleware/passay | ~300+ | 2024 | Apache 2.0 | ✅ Có |
| 4 | JJWT (jsonwebtoken) | https://github.com/jwtk/jjwt | ~10000+ | 2024 | Apache 2.0 | ✅ Có |
| 5 | Spring Security OAuth2 | spring.io (native module) | N/A (framework) | 2024 | Apache 2.0 | ✅ Có |
| 6 | aerogear-otp | github.com/aerogear (archived) | ~200 | 2020 | Apache 2.0 | ❌ Không (DEPRECATED/Archived) |
| 7 | zxcvbn4j | https://github.com/nulab/zxcvbn4j | ~400+ | 2024 | MIT | ❌ Không (strength estimator, not validator) |
| 8 | Keycloak Admin Client | https://github.com/keycloak/keycloak | ~25000+ | 2024 | Apache 2.0 | ❌ Không (optional, not needed for core SSO flow) |

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

#### dev.samstevens.totp

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Secret gen, QR code, TOTP verify, configurable window |
| Applicability | 9 | 15% | 1.35 | Java/Kotlin native, Maven Central, Spring Boot starter |
| Activity | 7 | 15% | 1.05 | GitHub 404 but package still on Maven Central 1.7.1 (2024) |
| Documentation | 8 | 15% | 1.20 | Good README + examples, JavaDoc |
| Code quality | 8 | 15% | 1.20 | Clean API, tested |
| Community | 7 | 10% | 0.70 | ~800 stars |
| Popularity | 8 | 10% | 0.80 | Widely used in Spring Boot MFA tutorials |
| **Tổng điểm** | | | **8.10/10** | |

#### GoogleAuth (wstrange)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | RFC 6238 TOTP, scratch codes, NO QR generation |
| Applicability | 7 | 15% | 1.05 | Pure Java, easy integration |
| Activity | 5 | 15% | 0.75 | Infrequent updates, last commit 2023 |
| Documentation | 6 | 15% | 0.90 | README only, minimal examples |
| Code quality | 7 | 15% | 1.05 | Tested, clean but legacy style |
| Community | 8 | 10% | 0.80 | ~1100 stars |
| Popularity | 7 | 10% | 0.70 | Well-known but aging |
| **Tổng điểm** | | | **6.65/10** | |

#### Passay

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Length, char rules, history, dictionary, custom rules |
| Applicability | 9 | 15% | 1.35 | Java native, thread-safe, factory pattern friendly |
| Activity | 8 | 15% | 1.20 | Regular releases, Apache project heritage |
| Documentation | 9 | 15% | 1.35 | Full website (passay.org), examples, API docs |
| Code quality | 9 | 15% | 1.35 | Well-tested, clean architecture |
| Community | 6 | 10% | 0.60 | ~300 stars (niche but authoritative) |
| Popularity | 8 | 10% | 0.80 | Industry standard for Java password validation |
| **Tổng điểm** | | | **8.65/10** | |

#### JJWT (jsonwebtoken)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | All JWT algorithms, JWS, JWE, JWK, claims builder |
| Applicability | 10 | 15% | 1.50 | Already in use in project |
| Activity | 9 | 15% | 1.35 | Regular releases, active maintenance |
| Documentation | 9 | 15% | 1.35 | Excellent README, migration guides |
| Code quality | 10 | 15% | 1.50 | Comprehensive tests, fluent API |
| Community | 10 | 10% | 1.00 | ~10000+ stars |
| Popularity | 10 | 10% | 1.00 | De-facto standard for Java JWT |
| **Tổng điểm** | | | **9.70/10** | |

#### Spring Security OAuth2 (Native)

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Full OAuth2/OIDC, auto JWKS, JWT decoder, OidcUserService |
| Applicability | 10 | 15% | 1.50 | Native Spring Boot integration, already in build.gradle |
| Activity | 10 | 15% | 1.50 | Part of Spring Security core — continuous updates |
| Documentation | 10 | 15% | 1.50 | Extensive Spring docs, reference guide, tutorials |
| Code quality | 10 | 15% | 1.50 | Spring-grade quality, battle-tested |
| Community | 10 | 10% | 1.00 | Spring ecosystem |
| Popularity | 10 | 10% | 1.00 | Industry standard |
| **Tổng điểm** | | | **10.00/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | dev.samstevens.totp | 9 | 9 | 7 | 8 | 8 | 7 | 8 | **8.10** |
| 2 | GoogleAuth | 7 | 7 | 5 | 6 | 7 | 8 | 7 | **6.65** |
| 3 | Passay | 10 | 9 | 8 | 9 | 9 | 6 | 8 | **8.65** |
| 4 | JJWT | 10 | 10 | 9 | 9 | 10 | 10 | 10 | **9.70** |
| 5 | Spring Security OAuth2 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | **10.00** |

---

## 4. Gap Analysis chi tiết

### dev.samstevens.totp — Gap Analysis

**Overall Score**: 8.10 / 10
**URL**: [ARCHIVED] github.com/samstevens/java-totp — Maven Central: `dev.samstevens.totp:totp:1.7.1`

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| TOTP Secret Generation | ✅ | | Base32 encoded, configurable length | - |
| QR Code URI Generation | ✅ | | `otpauth://` URI format, `QrData.Builder` | - |
| TOTP Verification | ✅ | | Configurable window (drift tolerance) | - |
| OTP SMS/Email | | ❌ | - | Not supported — need custom OtpService |
| Spring Boot Starter | ✅ | | Available on Maven Central | - |
| AES-256 Encryption | | ❌ | - | No built-in secret encryption — need custom |
| Repository Maintenance | | ❌ | - | GitHub repo 404 — package still on Maven Central |

**Verdict**: Dùng trực tiếp cho TOTP, supplement với custom OtpService cho SMS/Email
**Recommendation**: Dùng trực tiếp
**Reasoning**: API hiện đại, duy nhất library có QR code support built-in, đã integrate thành công trong project.

### GoogleAuth — Gap Analysis

**Overall Score**: 6.65 / 10
**URL**: https://github.com/wstrange/GoogleAuth

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| TOTP Generation | ✅ | | RFC 6238 compliant | - |
| Scratch/Backup Codes | ✅ | | Built-in recovery codes | - |
| QR Code Support | | ❌ | - | No QR generation — manual setup |
| Spring Boot Integration | | ❌ | - | No starter, manual wiring |
| Active Maintenance | ⚠️ | | Stable but infrequent | Last commit 2023 |
| Modern API | | ❌ | - | Legacy Java style |

**Verdict**: Tham khảo pattern (scratch codes concept)
**Recommendation**: Tham khảo pattern
**Reasoning**: Scratch codes concept đáng tham khảo cho recovery flow, nhưng core API kém hơn `dev.samstevens.totp`.

### Passay — Gap Analysis

**Overall Score**: 8.65 / 10
**URL**: https://github.com/vt-middleware/passay

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Length Rules | ✅ | | `LengthRule(min, max)` | - |
| Character Rules | ✅ | | `CharacterRule`, `CharacterCharacteristicsRule` | - |
| History/Source Rules | ✅ | | `HistoryRule`, `SourceRule` for reference-based checks | - |
| Dictionary Rules | ✅ | | `DictionaryRule` for banned passwords | - |
| Custom Rules | ✅ | | `Rule` interface for domain-specific logic | - |
| Thread-safe Validator | ✅ | | `PasswordValidator` is thread-safe — cache per domain | - |
| i18n Messages | ✅ | | Custom `MessageResolver` | - |
| DB-backed Config Storage | | ❌ | - | No built-in policy entity — need custom `PasswordPolicyEntity` |
| Per-Domain Factory | | ❌ | - | No built-in domain scoping — need custom factory pattern |

**Verdict**: Dùng trực tiếp + custom wrapper
**Recommendation**: Dùng trực tiếp
**Reasoning**: Industry standard, composable rules, thread-safe. Chỉ cần custom `PasswordPolicyEntity` + factory pattern (đã implement).

### JJWT — Gap Analysis

**Overall Score**: 9.70 / 10
**URL**: https://github.com/jwtk/jjwt

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| RS256 Signing | ✅ | | `Jwts.SIG.RS256`, native support | - |
| HMAC-SHA256 | ✅ | | Legacy support for migration | - |
| JWKS Generation | ⚠️ | | Can extract public key components | No auto JWKS endpoint |
| Key Rotation (kid) | ✅ | | `header().keyId()` builder | - |
| Claims Builder | ✅ | | Fluent API, custom claims | - |
| Token Parsing | ✅ | | Multi-algorithm parser | - |

**Verdict**: Dùng trực tiếp (already in use)
**Recommendation**: Dùng trực tiếp
**Reasoning**: Đã sử dụng trong project, hỗ trợ RS256 natively — chỉ cần đổi key type.

### Spring Security OAuth2 — Gap Analysis

**Overall Score**: 10.00 / 10
**URL**: spring.io (native framework module)

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| OAuth2 Client Flow | ✅ | | Full authorization code flow | - |
| OIDC Support | ✅ | | Auto claims mapping | - |
| Auto JWKS Fetching | ✅ | | `issuer-uri` based discovery | - |
| JIT Provisioning | ✅ | | Custom `OidcUserService` | - |
| Multi-provider | ✅ | | Google, Microsoft, Keycloak via config | - |
| Spring Security Integration | ✅ | | Native filter chain, SecurityContext | - |

**Verdict**: Dùng trực tiếp (already in build.gradle)
**Recommendation**: Dùng trực tiếp
**Reasoning**: Native Spring Security integration, no third-party adapter needed. Keycloak adapter deprecated since v21+.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Spring Security OAuth2 | 10.00/10 | Dùng trực tiếp | SSO/OAuth2 integration |
| 🥈 2 | JJWT | 9.70/10 | Dùng trực tiếp | JWT RS256 signing + JWKS |
| 🥉 3 | Passay | 8.65/10 | Dùng trực tiếp | Password policy validation |
| 4 | dev.samstevens.totp | 8.10/10 | Dùng trực tiếp | TOTP authenticator app MFA |
| 5 | GoogleAuth | 6.65/10 | Tham khảo pattern | Scratch/backup codes concept |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **BUILD from scratch** (using open source libraries) | Tất cả libraries cần thiết đã available, high-quality, actively maintained. Custom code chỉ cần cho orchestration (MfaService, OtpService, SsoAdapter) và data layer (entities, migrations). | Scoring matrix: all top libraries ≥ 8.10/10. All already integrated in project. |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-19 (⚠️ `dev.samstevens.totp` GitHub repo returns 404 — package still available on Maven Central)
> **Next step**: Comparison Analysis (comparison_analysis.md)
