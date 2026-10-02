# Research Brief: Auth-Service Optimization

> Tài liệu khởi đầu cho quá trình research tối ưu — xác định scope, keywords, context.
> **⚠️ UPDATED** — Cập nhật dựa trên deep codebase scan phát hiện nhiều items đã được implement.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Auth-Service Optimization (13 items từ Multi-Agent Brainstorming Review) |
| **Ngày tạo** | 2026-10-02 |
| **Input source** | file |
| **Input content** | [auth_service_plans_review.md](file:///home/nguyentthai96/.gemini/antigravity-ide/brain/c5fba742-171e-49e4-885f-499bad5d6cd6/auth_service_plans_review.md) |

---

## Input từ người dùng

13 optimization proposals từ review artifact, phân loại P0-P3.

## Phân tích sơ bộ — ⚠️ PHÁT HIỆN QUAN TRỌNG

```
DISCOVERY: Deep codebase scan cho thấy nhiều items trong review ban đầu đã được implement 
nhưng KHÔNG ĐƯỢC PHÁT HIỆN trong lần review đầu:

1. HttpSsoGateway.kt — ĐÃ IMPLEMENT Circuit Breaker (Resilience4j @CircuitBreaker)
   → OPT-03 CẬP NHẬT STATUS: Đã done
   
2. HttpClientConfig.kt — ĐÃ IMPLEMENT configurable timeout (5s connect, 10s read)
   → OPT-02 CẬP NHẬT STATUS: Đã done cho HttpExchange clients
   
3. SsoProviderClient.kt — Declarative HTTP client (@HttpExchange) thay thế RestTemplate
   → OAuth2TokenExchanger.kt là LEGACY code (dùng raw RestTemplate)
   → SsoAdapter.kt vẫn dùng OAuth2TokenExchanger (legacy path)
   → HttpSsoGateway.kt dùng SsoProviderClient (modern path)
   
4. application-core-resilience.yml — Resilience4j config đã tồn tại
   → ssoProvider + captchaProvider instances configured

CONCLUSION: Vấn đề thực sự KHÔNG phải là "cần thêm timeout/circuit breaker"
            mà là "có DUAL IMPLEMENTATION và cần cleanup legacy SSO path"
```

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Auth-service đã hoàn thành 12/12 implementation plans. Multi-agent brainstorming review phát hiện 13 optimization proposals. Tuy nhiên, deep codebase scan cho thấy hệ thống đã tiến hóa xa hơn plans — một số optimizations đã được implement nhưng tạo ra **dual implementation paths** cần cleanup.

### 2.2 Mục tiêu (Objectives)

- [x] ~~OPT-02: RestTemplate timeout~~ → **ĐÃ DONE** (HttpClientConfig.kt)
- [x] ~~OPT-03: Circuit breaker cho SSO~~ → **ĐÃ DONE** (HttpSsoGateway.kt + Resilience4j)
- [ ] OPT-01: Fix SwitchDomain authorization bypass **(P0 — SECURITY)**
- [ ] OPT-NEW: Cleanup legacy SSO path (OAuth2TokenExchanger + SsoAdapter) **(P1 — TECH DEBT)**
- [ ] OPT-04: PermissionChanged targeted cache invalidation **(P1)**
- [ ] OPT-05: Unify SSO user types **(P2 — connected to SSO cleanup)**
- [ ] OPT-06: Extract RecoveryCodeService from MfaService **(P2)**
- [ ] OPT-07: Async audit logging **(P2)**
- [ ] OPT-08: Password history query optimization **(P2)**
- [ ] OPT-09: SSO PKCE support **(P3)**
- [ ] OPT-10: JWKS key rotation automation **(P3)**
- [ ] OPT-11: Kafka topic name config **(P3)**
- [ ] OPT-12: Password policy discovery API **(P3)**
- [ ] OPT-13: QR code image endpoint **(P3)**

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Fix SwitchDomain authorization bypass (P0) | Database schema changes |
| Cleanup legacy SSO dual implementation (P1) | New authentication methods |
| PermissionChanged targeted invalidation (P1) | Frontend changes |
| MfaService SRP split (P2) | Infrastructure changes (Redis Sentinel) |
| Password history query optimization (P2) | Load testing |
| Async audit logging (P2) | JWKS rotation (deferred — ops decision) |
| PKCE support analysis (P3) | Full SSO provider migration |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `domain membership authorization check`
- `IDOR BOLA prevention multi-tenant`
- `SSO dual implementation cleanup`
- `targeted cache invalidation Kafka`
- `MFA service SRP refactoring`

### 3.2 Secondary Keywords
- `async audit logging TransactionalEventListener`
- `findTopN JPA query optimization`
- `PKCE public client Spring Security`
- `JWKS key rotation automation`
- `TOTP QR code server-side generation`

### 3.3 Domain-Specific Terms
- `IDOR`: Insecure Direct Object Reference — truy cập resource không authorized
- `BOLA`: Broken Object Level Authorization — OWASP API Top 10 #1
- `PKCE`: Proof Key for Code Exchange — OAuth2 extension cho public clients
- `JWKS`: JSON Web Key Set — endpoint expose public keys cho JWT verification
- `SRP`: Single Responsibility Principle — mỗi class chỉ có 1 lý do thay đổi

### 3.4 Search Queries (đã thực hiện)

| # | Query | Target | Status |
|---|-------|--------|--------|
| 1 | `"Spring Boot domain membership authorization check multi-tenant IDOR prevention"` | Security | ✅ |
| 2 | `"Spring Boot OAuth2 PKCE support authorization code flow public client"` | Security | ✅ |
| 3 | `"MFA service refactoring SRP recovery codes best practices"` | Architecture | ✅ |
| 4 | `"Spring Boot async audit logging event publisher performance SOC2"` | Performance | ✅ |
| 5 | `"JWKS key rotation Spring Boot automated RS256 key management"` | Security | ✅ |
| 6 | `"Kafka permission event cache invalidation targeted vs full"` | Performance | ✅ |
| 7 | `"Spring Data JPA password history findTopN repository performance"` | Performance | ✅ |
| 8 | `"TOTP QR code server side generation Spring Boot"` | Feature | ✅ |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| SSO Exchange (Legacy) | [OAuth2TokenExchanger](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt) | High | Raw RestTemplate, no timeout, no CB — **LEGACY** |
| SSO Exchange (Modern) | [HttpSsoGateway](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt) | High | @HttpExchange + @CircuitBreaker — **CURRENT** |
| SSO Adapter | [SsoAdapter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | High | Dùng OAuth2TokenExchanger (legacy path) |
| SsoGateway Port | [SsoGateway](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt) | High | Clean port interface |
| HTTP Client Config | [HttpClientConfig](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt) | High | Configurable timeout factory |
| MFA Service | [MfaService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | High | 376 lines, 12 deps — SRP violation |
| Switch Domain | [SwitchDomainHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt) | Critical | TODO: domain membership check |
| Permission Cache | [PermissionChangedConsumer](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt) | High | Full cache clear — needs targeted |
| Password Policy | [PasswordPolicyService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | Med | findAll + take pattern |
| TOTP Service | [TotpService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt) | Med | Trả URI, chưa có QR image endpoint |
| Resilience Config | [application-core-resilience.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application-core-resilience.yml) | High | ssoProvider + captchaProvider instances |

### 4.2 Existing Code Patterns

| Pattern | Implementation | Notes |
|---------|---------------|-------|
| Architecture | Clean Architecture (adapter → application → domain) | Strict layer separation |
| Data Access | Spring Data JPA + Port/Adapter | Repositories behind ports |
| API Style | REST (CQRS commands/queries) | CommandHandler + QueryHandler |
| Error Handling | RFC 7807 ProblemDetail + AuthException hierarchy | GlobalExceptionHandler |
| HTTP Clients | @HttpExchange (modern) + RestTemplate (legacy) | **Dual path — needs cleanup** |
| Circuit Breaker | Resilience4j @CircuitBreaker | On HttpSsoGateway + HttpCaptchaGateway |
| Caching | Spring CacheManager (L1+L2) | permissions + roles caches |
| Audit | AuditLogService (synchronous) | 30+ call sites |
| MFA | Strategy pattern (MfaProviderRegistry) | OTP, TOTP, SMS, Email providers |
| Rate Limiting | Redis INCR + EXPIRE | MfaRateLimitService, OtpService |

### 4.3 Tech Stack Constraints

- **Language**: Kotlin (JVM)
- **Framework**: Spring Boot 4.x (Spring Security, Spring Data JPA)
- **Database**: PostgreSQL + Redis
- **Messaging**: Kafka
- **Build tool**: Gradle (Kotlin DSL)
- **Key dependencies**: Resilience4j 2.2.0, Passay 1.6.4, TOTP (dev.samstevens:1.7.1), Google Tink 1.15.0

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `UserDomainPort` | Port (MISSING) | Cần tạo mới | Cho SwitchDomain membership check |
| `SsoGateway` | Port | [SsoGateway.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt) | Redirect SsoAdapter → dùng port thay vì OAuth2TokenExchanger |
| `CacheManager` | Bean | PermissionChangedConsumer | Cần targeted invalidation logic |
| `PasswordHistoryRepository` | Repository | PasswordPolicyService | Cần thêm `findTopNByUserIdOrderByCreatedAtDesc` |
| `AuditLogService` | Service | Shared | Cần wrap với `@Async` + `@TransactionalEventListener` |
| `MfaRecoveryCodeRepository` | Repository | MfaService | Extract thành RecoveryCodeService |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: OAuth2TokenExchanger và HttpSsoGateway có conflict không? → **Có — dual implementation**
- [x] Q2: Resilience4j đã config chưa? → **Đã config** (application-core-resilience.yml)
- [x] Q3: HttpClient timeout đã config chưa? → **Đã config** (HttpClientConfig.kt 5s/10s)
- [x] Q4: PKCE cần custom code không? → **Không** — Spring Security auto-enable khi `client-auth-method: none`
- [x] Q5: Async audit có risk mất data không? → **Có** — cần `@TransactionalEventListener` + outbox pattern
- [x] Q6: `findTopN` vs `findAll().take(N)` performance difference? → **Significant** — `findTopN` dùng SQL LIMIT
- [x] Q7: QR code generation cần thêm lib không? → **Không** — `dev.samstevens.totp:1.7.1` đã có sẵn
- [x] Q8: JWKS rotation cần custom code nhiều không? → **Phụ thuộc** — nếu dùng Spring Authorization Server thì auto

### 5.2 Assumptions cần verify

- [x] A1: SsoAdapter KHÔNG implement SsoGateway → **CONFIRMED**
- [x] A2: HttpSsoGateway IS implementing SsoGateway → **CONFIRMED**
- [ ] A3: UserDomainPort chưa tồn tại → **Cần verify — có thể nằm trong rbac module**
- [ ] A4: Audit events có @Transactional boundary rõ ràng → **Cần verify per call site**

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Tất cả 13 items đã research | 8/8 search queries done ✅ |
| Codebase analysis | Deep scan phát hiện hidden implementations | 4 new discoveries ✅ |
| Updated priorities | Đánh giá lại P0-P3 dựa trên actual state | Items reclassified ✅ |
| Actionable plan | Mỗi item có implementation approach cụ thể | Pending Phase 5-6 |
| Risk assessment | Identify risks + mitigation | Pending Phase 4 |

---

## 7. Updated Priority Matrix (sau Research)

| Priority | Item | Updated Status | Action |
|----------|------|----------------|--------|
| 🔴 P0 | OPT-01: SwitchDomain bypass | **STILL OPEN — CRITICAL** | Tạo UserDomainPort + membership check |
| 🟡 P1 | OPT-02: RestTemplate timeout | **RESOLVED** ✅ | HttpClientConfig đã config 5s/10s |
| 🟡 P1 | OPT-03: Circuit breaker | **RESOLVED** ✅ | HttpSsoGateway + Resilience4j đã config |
| 🟡 P1 | **NEW**: Cleanup legacy SSO path | **NEW — HIGH** | Remove OAuth2TokenExchanger, migrate SsoAdapter → SsoGateway |
| 🟡 P1 | OPT-04: Permission cache | **STILL OPEN** | Parse JSON + targeted invalidation |
| 🟡 P1 | OPT-05: Unify SSO types | **MERGED WITH SSO CLEANUP** | Part of legacy SSO cleanup |
| 🟢 P2 | OPT-06: Extract RecoveryCodeService | **STILL OPEN** | SRP refactoring |
| 🟢 P2 | OPT-07: Async audit | **STILL OPEN** | @TransactionalEventListener + @Async |
| 🟢 P2 | OPT-08: Password history query | **STILL OPEN** | `findTopNByUserIdOrderByCreatedAtDesc` |
| 🔵 P3 | OPT-09: PKCE | **TRIVIAL** | Config-only: `client-auth-method: none` |
| 🔵 P3 | OPT-10: JWKS rotation | **DEFERRED** | Ops decision needed |
| 🔵 P3 | OPT-11: Kafka topic config | **STILL OPEN** | Extract hardcoded → config |
| 🔵 P3 | OPT-12: Password policy API | **STILL OPEN** | New endpoint |
| 🔵 P3 | OPT-13: QR code endpoint | **STILL OPEN** | Use existing `dev.samstevens.totp` |

> **Next step**: Phase 4 (Comparison Analysis) → Phase 5 (Business Analysis) → Phase 6 (Technical Spec)
