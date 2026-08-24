# Kết quả tìm kiếm Open Source: JWT Refresh Token Rotation

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Refresh Token Rotation with Reuse Detection |
| **Ngày tìm kiếm** | 2025-01-27 |
| **Số dự án tìm thấy** | 6 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin / Spring Boot / PostgreSQL / Redis |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"refresh token rotation reuse detection Java Spring Boot GitHub"` | 12+ kết quả | Filtered for Spring/JVM implementations |
| 2 | `"token family tracking OAuth2 open source"` | 8+ kết quả | Focus on family-based rotation pattern |
| 3 | `"Spring Security OAuth2 refresh token rotation"` | 6+ kết quả | Spring ecosystem specific |
| 4 | `"Keycloak refresh token rotation source code"` | 3+ kết quả | Reference implementation |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Keycloak | https://github.com/keycloak/keycloak | 24k+ | Active (daily) | Apache 2.0 | ✅ Có |
| 2 | Spring Authorization Server | https://github.com/spring-projects/spring-authorization-server | 5k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 3 | Supertokens | https://github.com/supertokens/supertokens-core | 13k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 4 | Ory Hydra | https://github.com/ory/hydra | 16k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 5 | Authelia | https://github.com/authelia/authelia | 22k+ | Active (daily) | Apache 2.0 | ❌ Không (Go-based, no token family) |
| 6 | Lucia Auth | https://github.com/lucia-auth/lucia | 10k+ | Active | MIT | ❌ Không (TypeScript, session-based not JWT rotation) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Basic rotation only | Rotation + revocation | Full rotation + reuse detection + family tracking |
| **Applicability** (phù hợp tech stack) | 15% | Different stack | JVM but different framework | Kotlin/Spring Boot compatible |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly+ commits |
| **Documentation** | 15% | No docs | README + basic docs | Full docs + examples + migration guides |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean architecture |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Full token rotation with reuse detection, family tracking via `rootAuthenticationSessionId`, automatic family revocation |
| Applicability | 5 | 15% | 0.75 | Java-based, Quarkus framework (not Spring Boot). Pattern reusable, code not directly importable |
| Activity | 10 | 15% | 1.50 | Daily commits, major project by Red Hat |
| Documentation | 9 | 15% | 1.35 | Extensive docs, admin console, migration guides |
| Code quality | 9 | 15% | 1.35 | High test coverage, modular SPI architecture |
| Community | 10 | 10% | 1.00 | 24k+ stars, massive community |
| Popularity | 10 | 10% | 1.00 | Industry standard IdP |
| **Tổng điểm** | | | **8.95/10** | |

#### Spring Authorization Server

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Supports refresh token rotation via `OAuth2TokenCustomizer`. No built-in reuse detection or family tracking — delegated to implementor |
| Applicability | 9 | 15% | 1.35 | Spring Boot native, Java/Kotlin compatible, same ecosystem |
| Activity | 9 | 15% | 1.35 | Weekly commits, official Spring project |
| Documentation | 8 | 15% | 1.20 | Good reference docs, samples, Spring guides |
| Code quality | 9 | 15% | 1.35 | Excellent test coverage, Spring-quality code |
| Community | 8 | 10% | 0.80 | 5k+ stars, growing adoption |
| Popularity | 8 | 10% | 0.80 | Standard for new Spring OAuth2 servers |
| **Tổng điểm** | | | **8.25/10** | |

#### Supertokens

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full refresh token rotation with reuse detection ("token theft detection"). Uses rotating refresh token approach with DB-backed token families. Automatic session revocation on reuse. |
| Applicability | 4 | 15% | 0.60 | Java core, but tightly coupled self-contained auth service. Not designed as embeddable library. |
| Activity | 8 | 15% | 1.20 | Weekly commits, active company backing |
| Documentation | 8 | 15% | 1.20 | Good docs on rotation concept, API references |
| Code quality | 7 | 15% | 1.05 | Tests present, monolithic structure |
| Community | 9 | 10% | 0.90 | 13k+ stars, growing fast |
| Popularity | 8 | 10% | 0.80 | Popular in startup ecosystem |
| **Tổng điểm** | | | **7.55/10** | |

#### Ory Hydra

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Refresh token rotation via OAuth2 token endpoint. Configurable rotation policy. No explicit "token family" concept but tracks parent-child via `request_id`. |
| Applicability | 3 | 15% | 0.45 | Go-based, different tech stack entirely. Pattern reference only. |
| Activity | 9 | 15% | 1.35 | Weekly commits, backed by Ory company |
| Documentation | 9 | 15% | 1.35 | Excellent docs, guides, reference architecture |
| Code quality | 9 | 15% | 1.35 | Excellent Go code, comprehensive tests |
| Community | 9 | 10% | 0.90 | 16k+ stars |
| Popularity | 9 | 10% | 0.90 | Industry-grade, used by enterprises |
| **Tổng điểm** | | | **7.90/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Keycloak | 10 | 5 | 10 | 9 | 9 | 10 | 10 | **8.95** |
| 2 | Spring Auth Server | 7 | 9 | 9 | 8 | 9 | 8 | 8 | **8.25** |
| 3 | Supertokens | 9 | 4 | 8 | 8 | 7 | 9 | 8 | **7.55** |
| 4 | Ory Hydra | 8 | 3 | 9 | 9 | 9 | 9 | 9 | **7.90** |

---

## 4. Gap Analysis chi tiết

### Keycloak — Gap Analysis

**Overall Score**: 8.95 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token Rotation | ✅ | | Full rotation with configurable policy | - |
| Reuse Detection | ✅ | | Automatic detection + session revocation | - |
| Token Family Tracking | ✅ | | Via `rootAuthenticationSessionId` linking all tokens to original session | Tightly coupled to Keycloak session model |
| Grace Period | ⚠️ | | Handles via session locking | Not configurable as standalone parameter |
| Event Sourcing Integration | | ❌ | - | No event sourcing support — state-based model |
| Spring Boot Integration | | ❌ | - | Quarkus-based, separate deployment |
| CQRS Pattern | | ❌ | - | Traditional service layer, not CQRS |
| Custom Domain Events | | ❌ | - | Uses admin events, not domain events pattern |

**Verdict**: Tham khảo pattern — rotation and reuse detection logic is reference-quality
**Recommendation**: Tham khảo pattern
**Reasoning**: Keycloak's rotation/reuse detection is gold-standard but its architecture (Quarkus, separate IdP deployment, no event sourcing) makes direct adoption impossible. Use as reference for the token family concept and reuse detection algorithm.

### Spring Authorization Server — Gap Analysis

**Overall Score**: 8.25 / 10
**URL**: https://github.com/spring-projects/spring-authorization-server

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token Rotation | ✅ | | Built-in rotation support via `OAuth2TokenCustomizer` | Requires full OAuth2 authorization server setup |
| Reuse Detection | | ❌ | - | Not built-in — implementor responsibility |
| Token Family Tracking | | ❌ | - | No family concept — uses authorization/session model |
| Grace Period | | ❌ | - | No built-in grace period |
| Spring Boot Integration | ✅ | | Native Spring Boot, same ecosystem | Requires OAuth2 authorization server paradigm |
| CQRS Pattern | | ❌ | - | Traditional Spring pattern |
| Event Sourcing | | ❌ | - | No event sourcing support |
| Embeddable | ⚠️ | | Can be embedded as library | Heavy — full OAuth2 server with consent, clients, etc. |

**Verdict**: Tham khảo pattern — Spring-native rotation mechanism
**Recommendation**: Tham khảo pattern
**Reasoning**: Same Spring ecosystem, but it's a full OAuth2 authorization server with client management, consent flows, etc. Our auth-service uses a simpler JWT-based approach. The rotation pattern in `OAuth2RefreshTokenAuthenticationProvider` is useful as reference, but adopting the full server is overengineering.

### Supertokens — Gap Analysis

**Overall Score**: 7.55 / 10
**URL**: https://github.com/supertokens/supertokens-core

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token Rotation | ✅ | | Automatic rotation on every refresh | - |
| Reuse Detection | ✅ | | "Token theft detection" — detects reuse and revokes session | Tightly coupled to Supertokens session model |
| Token Family Tracking | ✅ | | Tracks via `parentRefreshTokenHash` chain | Chain-based (not family ID), harder to query |
| Grace Period | | ❌ | - | No configurable grace period |
| Spring Boot Integration | | ❌ | - | Separate microservice, not embeddable |
| Custom Domain Events | | ❌ | - | Webhook-based, not domain events |
| CQRS / Event Sourcing | | ❌ | - | Traditional CRUD |

**Verdict**: Tham khảo pattern — reuse detection algorithm
**Recommendation**: Tham khảo pattern
**Reasoning**: Supertokens' "token theft detection" algorithm (using parent token hash chain) is directly relevant. Their approach: if a refresh token is used that was already rotated, they revoke the entire session. This is the exact pattern we need. However, their chain-based tracking (each token points to parent) is less efficient for batch revocation than a family ID approach.

### Ory Hydra — Gap Analysis

**Overall Score**: 7.90 / 10
**URL**: https://github.com/ory/hydra

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token Rotation | ✅ | | Configurable rotation policy | - |
| Reuse Detection | ⚠️ | | Partial — revoked tokens return error, but no automatic family revocation | - |
| Token Family Tracking | ⚠️ | | Via `request_id` linking tokens to OAuth2 request | Not explicit family concept |
| Grace Period | | ❌ | - | No built-in grace period |
| Spring Boot Integration | | ❌ | - | Go-based, REST API only |
| Event Sourcing | | ❌ | - | Traditional SQL storage |

**Verdict**: Tham khảo pattern — token lifecycle management
**Recommendation**: Tham khảo pattern
**Reasoning**: Ory Hydra's token lifecycle management and rotation policy configuration are well-documented. The `request_id` concept for linking related tokens is a good reference. However, Go-based implementation and OAuth2 server paradigm make direct code reuse impossible.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Keycloak | 8.95/10 | Tham khảo pattern | Token family concept, reuse detection algorithm, revocation strategy |
| 🥈 2 | Spring Authorization Server | 8.25/10 | Tham khảo pattern | Spring-native rotation mechanism, token store patterns |
| 🥉 3 | Ory Hydra | 7.90/10 | Tham khảo pattern | Token lifecycle management, rotation policy configuration |
| 4 | Supertokens | 7.55/10 | Tham khảo pattern | Token theft detection algorithm, parent hash chain approach |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **Build from scratch** (tham khảo patterns từ Keycloak + Supertokens) | No open source project provides embeddable token family rotation that fits our Event Sourcing + CQRS architecture. All projects are either full IdP servers (Keycloak, Ory Hydra), full OAuth2 servers (Spring Auth Server), or tightly-coupled services (Supertokens). Our existing `RefreshTokenHandler` already has 80% of the logic — we need to extend it with family tracking and reuse detection, following patterns from Keycloak (family ID) and Supertokens (reuse detection). | All 4 evaluated projects are full auth servers, not embeddable libraries. Our CQRS+ES architecture requires custom integration. |

---

> **Sources**: All project URLs verified as of 2025-01-27
> **Next step**: Comparison Analysis (comparison_analysis.md)
