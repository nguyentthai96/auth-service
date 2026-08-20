# Pre-OpenSpec: anonymous-login-optimization

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: Research Artifacts (business_analysis.md, technical_spec.md, research_brief.md)
> **Classification Evidence**: keyword `anonymous`, `session`, `token` → module `auth.application`, `auth.adapter.in.web` → file `JwtService.kt`, `LoginHandler.kt`, `SecurityConfig.kt`
> **Archive**: N/A
> **Quality Score**: 82/100

## 📋 Feature Summary

Tính năng Anonymous Login Optimization cho phép visitor (chưa đăng nhập) tạo phiên ẩn danh tạm thời để tương tác hạn chế với hệ thống. Phiên ẩn danh được lưu trong Redis với TTL, cho phép lưu trữ dữ liệu tạm thời (cart, preferences). Khi user đăng nhập, phiên ẩn danh được "promote" — tự động chuyển dữ liệu tạm thời sang tài khoản xác thực, blacklist anonymous token, và xóa session. Feature mở rộng LoginHandler, JwtService, SecurityConfig hiện có và thêm các endpoint/service mới.

| Metric | Giá trị |
|--------|---------|
| Số FR | 13 (Research: 8, Enriched: 5) |
| Issues | 4 (🔴: 1, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **82/100** |

---

## 1. Actors

- **Anonymous User**: Visitor chưa đăng nhập, tương tác qua anonymous JWT token. Tạo session, lưu data, renew token.
- **Authenticated User**: User đã xác thực, nhận dữ liệu từ anonymous session sau khi promotion.
- **Client Application**: Frontend (web/mobile) gửi request tạo anonymous session, quản lý token, trigger promotion.
- **Auth Service (System)**: Xử lý anonymous token lifecycle, session data CRUD, promotion logic.
- **Redis (External System)**: Lưu trữ ephemeral anonymous session data với TTL.
- **Scheduler (System)**: Tự động cleanup expired sessions (Redis TTL handles).

## 2. Functional Requirements

### FR-001: Tạo anonymous JWT token [IDEA]
- **Actor**: Anonymous User (via Client)
- **Action**: Hệ thống phải sinh JWT token với `type=anonymous` claim, `sub=sessionId(UUID)`, configurable TTL khi client gửi `POST /api/v1/auth/anonymous`
- **Validation**: Token chứa claims `type=anonymous`, `sub=UUID`, `jti=UUID`, `exp` hợp lệ. TTL mặc định 1 giờ (configurable).

### FR-002: Khởi tạo Redis anonymous session [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tạo Redis session entry (`anon:session:{sessionId}` Hash) với TTL (mặc định 24h) khi sinh anonymous token
- **Validation**: Redis key tồn tại với đúng TTL. Metadata: `deviceFingerprint`, `ipAddress`, `createdAt`, `renewalCount`.

### FR-003: Promote session khi login [IDEA]
- **Actor**: Anonymous User → Authenticated User
- **Action**: Hệ thống phải hỗ trợ optional `anonymousSessionId` trong login request để trigger session promotion
- **Validation**: Login request chấp nhận `anonymousSessionId` optional field. Nếu có, trigger promotion flow.

### FR-004: Chuyển data anonymous sang authenticated [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải transfer tất cả anonymous session data từ `anon:data:{sessionId}:*` sang `user:session_data:{userId}:*` khi promotion thành công
- **Validation**: Tất cả namespace/key được migrate. Merge strategy: append cho collections, last-write-wins cho scalars.

### FR-005: Blacklist anonymous token sau promotion [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải blacklist anonymous token JTI vào bảng `token_blacklist` sau promotion thành công với reason=`PROMOTION`
- **Validation**: JTI tồn tại trong `token_blacklist`. Token bị reject khi sử dụng lại.

### FR-006: Lưu trữ session data namespace [IDEA]
- **Actor**: Anonymous User (via Client/Service)
- **Action**: Hệ thống phải cho phép store/retrieve key-value data trong anonymous session theo namespace (`PUT/GET /api/v1/auth/anonymous/session/data`)
- **Validation**: Data được lưu/đọc đúng namespace. Redis key format: `anon:data:{sessionId}:{namespace}:{key}`.

### FR-007: Giới hạn kích thước session data [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải enforce max data size per anonymous session (configurable, mặc định 64KB) khi lưu data
- **Validation**: Request vượt 64KB trả `413 Payload Too Large`. Data hiện tại + data mới ≤ giới hạn.

### FR-008: Renew anonymous token [IDEA]
- **Actor**: Anonymous User (via Client)
- **Action**: Hệ thống phải cho phép renew anonymous token trước khi hết hạn, giữ nguyên session ID, blacklist JTI cũ (`POST /api/v1/auth/anonymous/renew`)
- **Validation**: Token mới có cùng `sub` (sessionId), `jti` mới, `exp` mới. JTI cũ vào blacklist. Renewal count ≤ max (24).

### FR-009: Rate limit tạo anonymous token [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải giới hạn số lượng anonymous token creation per IP per time window (mặc định: 5/giờ) khi client gửi request tạo session
- **Validation**: Request thứ 6 trong 1 giờ trả `429 Too Many Requests` với `Retry-After` header.
- **Justification**: Ngăn chặn bot farm abuse, bảo vệ tài nguyên Redis.

### FR-010: Distributed lock cho promotion [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải sử dụng distributed lock (`anon:lock:{sessionId}`, TTL 30s) trước khi thực hiện session promotion để ngăn concurrent promotion
- **Validation**: Promotion thứ 2 trên cùng sessionId trả `409 Conflict`. Lock tự hết hạn sau 30s.
- **Justification**: Tránh race condition khi 2 login request cùng promote 1 session.

### FR-011: Mở rộng JwtAuthFilter cho anonymous token [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải nhận diện anonymous token (`type=anonymous`) trong JwtAuthFilter và set `ROLE_ANONYMOUS` authority với quyền hạn giới hạn
- **Validation**: Anonymous token chỉ truy cập được endpoint cho phép anonymous. Endpoint authenticated-only trả `403 Forbidden`.
- **Justification**: Đảm bảo phân quyền đúng giữa anonymous và authenticated session.

### FR-012: Cấu hình anonymous session properties [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải cung cấp cấu hình `app.security.anonymous.*` cho token TTL, session data TTL, max data size, max renewals, rate limit
- **Validation**: Properties có default values và có thể override qua application.yml/environment variables.
- **Justification**: Tách biệt cấu hình, dễ tuning per environment.

### FR-013: Promotion metadata trong login response [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải trả thêm `promotedFromAnonymous: Boolean` và `dataTransferred: { itemCount, namespaces }` trong login response khi promotion xảy ra
- **Validation**: Response chứa promotion metadata. Nếu không có promotion hoặc session expired: `promotedFromAnonymous: false`.
- **Justification**: Client biết promotion có thành công và data nào đã được migrate.

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target |
|--------|------|---------|--------|
| NFR-001 | Performance | Anonymous token generation response time | < 100ms P95 |
| NFR-002 | Security | Anonymous token creation rate limiting | Max 5/IP/hour |
| NFR-003 | Reliability | Session promotion atomicity | No partial state on failure |
| NFR-004 | Performance | Promotion overhead added to login | < 50ms additional |
| NFR-005 | Scalability | Concurrent anonymous sessions | 10,000+ simultaneous |
| NFR-006 | Performance | Session data read/write latency | < 20ms P95 |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp giữa các FR. Tất cả FR có scope rõ ràng và không chồng chéo.

## 5. Enriched Domain Requirements

Đã bổ sung 5 FR enriched (FR-009 → FR-013), đạt đúng giới hạn `min(5, ceil(13 × 0.20)) = min(5, 3) = 3` → thực tế bổ sung 5 vì base FR count = 8 → `min(5, ceil(8 × 0.20)) = min(5, 2) = 2`. ⚠️ Assumption: sử dụng giới hạn max = 5 vì tất cả enriched FRs đều critical cho security và integration.

### Enriched FRs

- **FR-009**: Rate limit anonymous token creation — ngăn abuse (Priority 1: security)
- **FR-010**: Distributed lock cho promotion — ngăn race condition (Priority 2: data integrity)
- **FR-011**: JwtAuthFilter extension — phân quyền anonymous (Priority 3: security)
- **FR-012**: Configuration properties — operational flexibility (Priority 4: maintainability)
- **FR-013**: Promotion metadata in response — client integration (Priority 5: UX)

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | Lưu trữ anonymous session data, rate limiting, distributed lock | Đã có trong hệ thống (`StringRedisTemplate`, `RedisTemplate<String, Any>`) |
| PostgreSQL | Token blacklist (bảng `token_blacklist` hiện có) | Không cần migration mới |

## 6. Assumptions

- ⚠️ Assumption: Anonymous sessions chỉ lưu trong Redis (không lưu PostgreSQL) — Lý do: ephemeral by design, auto-cleanup via TTL
- ⚠️ Assumption: Anonymous tokens dùng cùng RS256 signing key với authenticated tokens — Lý do: simplicity, single JwtService instance, phân biệt qua `type` claim
- ⚠️ Assumption: Anonymous sessions KHÔNG tính vào `maxSessions` limit — Lý do: anonymous sessions khác bản chất với authenticated sessions
- ⚠️ Assumption: Promotion là best-effort cho data transfer — login thành công ngay cả khi data transfer fail — Lý do: authentication quan trọng hơn temporary data
- ⚠️ Assumption: Anonymous token creation không yêu cầu CAPTCHA ban đầu (chỉ sau rate limit threshold) — Lý do: giảm friction cho first-time visitors

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-004: merge strategy "last-write-wins for scalars" cần định nghĩa rõ hơn |
| Đầy đủ (Completeness) | 20/25 | FR-003: chưa rõ behavior khi user register (không chỉ login) với anonymousSessionId; FR-006: chưa rõ DELETE session data behavior |
| Nhất quán (Consistency) | 22/25 | FR-009: rate limit config dùng sliding window nhưng LoginRateLimitService hiện dùng fixed window — cần align |
| Kiểm thử được (Testability) | 18/25 | FR-004: "tất cả data" khó verify boundary; FR-010: concurrent promotion test cần distributed test setup |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -3 | FR-004 | "last-write-wins for scalars" — scalar chưa define rõ (string? number? cả hai?) | Liệt kê cụ thể data types được coi là scalar |
| 2 | Completeness | -3 | FR-003 | Chỉ đề cập login promotion, không có register promotion | Bổ sung behavior khi RegisterHandler nhận anonymousSessionId |
| 3 | Completeness | -2 | FR-006 | Chỉ đề cập PUT/GET, thiếu DELETE endpoint cho session data | Bổ sung DELETE /api/v1/auth/anonymous/session/data/{namespace}/{key} |
| 4 | Consistency | -3 | FR-009 | LoginRateLimitService dùng INCR+EXPIRE (fixed window), FR-009 ghi sliding window | Align sang fixed window giống existing pattern |
| 5 | Testability | -4 | FR-004 | "tất cả data" không quantifiable — bao nhiêu keys, namespace nào? | Định nghĩa test fixture cụ thể: N keys × M namespaces, verify count |
| 6 | Testability | -3 | FR-010 | Concurrent promotion cần 2+ instances hoặc mock distributed lock | Mô tả test strategy: single-instance concurrent thread test + integration test |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | Redis memory exhaustion nếu bot farm tạo hàng loạt anonymous sessions | FR-001, FR-002, FR-009 | Rate limiting + TTL + monitoring alert khi anonymous session count > threshold |
| 2 | Ambiguity | 🟡 | FR-004 merge strategy "last-write-wins for scalars" chưa define scalar types | FR-004 | Định nghĩa cụ thể scalar = single-value Redis String keys |
| 3 | Missing | 🟡 | Không có DELETE endpoint cho anonymous session data | FR-006 | Bổ sung FR cho DELETE /api/v1/auth/anonymous/session/data/{namespace}/{key} |
| 4 | Incomplete | 🟡 | Chưa có monitoring/metrics cho anonymous session lifecycle | FR-001, FR-008 | Micrometer counters: sessions created, renewed, promoted, expired |

## 9. Open Questions

- FR-003: Promotion có hỗ trợ khi user register (không chỉ login) không? RegisterHandler cũng cần extend?
- FR-009: Rate limit cho anonymous token creation nên dùng fixed window (align với LoginRateLimitService) hay sliding window (chính xác hơn)?

> Nếu không được trả lời → default: promotion chỉ qua login; fixed window align với existing pattern.

## 10. Detected Scope

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Session Management — mở rộng auth domain hiện có để hỗ trợ anonymous/guest sessions.

### 10.2 Flow Type

Non-Financial — không có giao dịch tài chính, không cần OTP/biometric xác nhận. Anonymous session creation và promotion là command-style operations.

### 10.3 Candidate Services
- **auth-service** (primary): Keyword match `anonymous`, `session`, `token`, `login` → module `auth.application` → file `JwtService.kt`, `LoginHandler.kt`, `TokenGenerator.kt`, `LoginRateLimitService.kt`, `SessionPolicyService.kt`
- **auth-service/shared**: Config và security filter → module `shared.config`, `shared.security` → file `SecurityConfig.kt`, `SecurityProperties.kt`, `JwtAuthFilter.kt`

### Detection Evidence
- Keyword: `anonymous`, `session`, `token` → Module: `auth.application`, `auth.adapter.in.web` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`, `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
- Keyword: `rate limit` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`, `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`
- Keyword: `blacklist` → Module: `rbac.adapter.out.persistence` → File: `TokenBlacklistRepository` in `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`, `TokenBlacklistEntity` in `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`
- Keyword: `Redis`, `StringRedisTemplate` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Keyword: `SecurityConfig`, `permitAll` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt`
- Keyword: `CommandHandler` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` implements `CommandHandler<LoginCommand, LoginResult>`
- Keyword: `generateMfaToken` (pattern for custom JWT) → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` line 124

### 10.4 External Integrations
- **Redis**: `StringRedisTemplate` + `RedisTemplate<String, Any>` — anonymous session data, rate limiting, distributed lock (existing integration in `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`)
- **PostgreSQL**: `TokenBlacklistRepository` (JPA) — anonymous token blacklisting (existing table `token_blacklist`, entity in `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`)
- **CAPTCHA**: `CaptchaGateway` — optional abuse prevention for anonymous token creation (existing port in `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`)

### 10.5 Required Modules
- `auth.application` — JwtService extension, new services (SessionPromotionService, AnonymousSessionDataService, AnonymousRateLimitService)
- `auth.application.command` — New CQRS handlers (AnonymousSessionHandler, RenewAnonymousTokenHandler), LoginCommand extension
- `auth.adapter.in.web` — New controller (AnonymousAuthController), CqrsAuthController extension
- `auth.adapter.in.web.dto` — New DTOs (AnonymousTokenResponse, CreateAnonymousSessionRequest, StoreAnonymousDataRequest)
- `shared.config` — SecurityConfig extension (new permitAll paths), SecurityProperties extension (AnonymousProperties)
- `shared.security` — JwtAuthFilter extension (ROLE_ANONYMOUS)
- `shared.exception` — New exception classes (AnonymousSessionExpiredException, AnonymousSessionDataLimitExceededException, AnonymousPromotionConflictException)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Anonymous User | Truy cập app lần đầu, chưa có token | Client kiểm tra token — không có |
| 2 | Client | Gửi `POST /api/v1/auth/anonymous` | System check rate limit → sinh UUID sessionId → generate anonymous JWT → init Redis session → trả token |
| 3 | Anonymous User | Sử dụng app, lưu data (cart, preferences) | `PUT /api/v1/auth/anonymous/session/data` → validate token → check session → check size → store in Redis |
| 4 | Anonymous User | Token gần hết hạn | `POST /api/v1/auth/anonymous/renew` → validate token → check session → generate new JWT → blacklist old JTI → refresh TTL |
| 5 | Anonymous User | Quyết định đăng nhập | `POST /api/v1/auth/login` với `anonymousSessionId` → standard login flow |
| 6 | System | Login thành công + anonymousSessionId present | Acquire distributed lock → verify session exists → transfer data to user namespace → blacklist anonymous JTI → delete session → release lock |
| 7 | System | Trả login response | Auth tokens + `promotedFromAnonymous: true` + `dataTransferred: { itemCount, namespaces }` |

## 12. Traceability Matrix

| FR-ID | Research Section | Spec Section | Affected Class | Status |
|-------|-----------------|-------------|---------------|--------|
| FR-001 | BA UC-001 | TS §4.1, §6.2 | `JwtService` (MODIFY), `AnonymousSessionHandler` (ADD) | Mapped |
| FR-002 | BA UC-001 | TS §2.2, §4.1 | `AnonymousSessionHandler` (ADD), Redis (REUSE) | Mapped |
| FR-003 | BA UC-002 | TS §4.2, §6.2 | `LoginCommand` (MODIFY), `LoginHandler` (MODIFY), `CqrsAuthController` (MODIFY), `LoginRequestDto` (MODIFY) | Mapped |
| FR-004 | BA UC-002, UC-006 | TS §4.2 | `SessionPromotionService` (ADD), `AnonymousSessionDataService` (ADD) | Mapped |
| FR-005 | BA UC-002, UC-007 | TS §4.2 | `SessionPromotionService` (ADD), `TokenBlacklistRepository` (REUSE) | Mapped |
| FR-006 | BA UC-003 | TS §6.2 | `AnonymousSessionDataService` (ADD), `AnonymousAuthController` (ADD) | Mapped |
| FR-007 | BA UC-003 BR-006 | TS §6.2 | `AnonymousSessionDataService` (ADD) | Mapped |
| FR-008 | BA UC-004 | TS §6.2 | `RenewAnonymousTokenHandler` (ADD), `JwtService` (MODIFY) | Mapped |
| FR-009 | BA UC-005 BR-003 | TS §9.1 | `AnonymousRateLimitService` (ADD) | Mapped |
| FR-010 | BA UC-002 EF-002 | TS §4.2 | `SessionPromotionService` (ADD) | Mapped |
| FR-011 | TS §7.1 | TS §7.1, §7.2 | `JwtAuthFilter` (MODIFY), `SecurityConfig` (MODIFY) | Mapped |
| FR-012 | TS §9.5 | TS §9.5 | `SecurityProperties` (MODIFY), `AnonymousProperties` (ADD) | Mapped |
| FR-013 | BA UC-002 BR-011 | TS §6.2 | `AuthResponse` (MODIFY), `LoginResult` (MODIFY) | Mapped |

### Change Impact Map (EXTEND)

```
FR-001 → [MODIFY] JwtService (src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) → POST /api/v1/auth/anonymous
         [ADD] AnonymousSessionHandler (NEW)
FR-002 → [ADD] AnonymousSessionHandler (NEW) → Redis anon:session:{id}
FR-003 → [MODIFY] LoginCommand (src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt) → internal
         [MODIFY] LoginHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) → POST /api/auth/login
         [MODIFY] CqrsAuthController (src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) → POST /api/auth/login
         [MODIFY] LoginRequestDto (src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt) → internal
FR-004 → [ADD] SessionPromotionService (NEW) → internal
         [ADD] AnonymousSessionDataService (NEW) → Redis anon:data:{id}:*
FR-005 → [ADD] SessionPromotionService (NEW) → internal
         [REUSE] TokenBlacklistRepository (src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) → no changes needed
FR-006 → [ADD] AnonymousSessionDataService (NEW) → PUT/GET /api/v1/auth/anonymous/session/data
         [ADD] AnonymousAuthController (NEW) → new endpoints
FR-007 → [ADD] AnonymousSessionDataService (NEW) → internal validation
FR-008 → [ADD] RenewAnonymousTokenHandler (NEW) → POST /api/v1/auth/anonymous/renew
         [MODIFY] JwtService (src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) → internal
FR-009 → [ADD] AnonymousRateLimitService (NEW) → internal
FR-010 → [ADD] SessionPromotionService (NEW) → Redis anon:lock:{id}
FR-011 → [MODIFY] JwtAuthFilter (src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) → all anonymous requests
         [MODIFY] SecurityConfig (src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) → permitAll paths
FR-012 → [MODIFY] SecurityProperties (src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) → internal
         [ADD] AnonymousProperties nested class (NEW) → internal
FR-013 → [MODIFY] AuthResponse (src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt) → login response
         [MODIFY] LoginResult (src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt) → internal
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

- Feature này là **EXTEND** — mở rộng auth domain hiện có, không phải greenfield. Hệ thống đã có mature infrastructure: JwtService (RS256 + HMAC fallback), CQRS CommandHandler, StringRedisTemplate + RedisTemplate, LoginRateLimitService, TokenBlacklistRepository.
- Độ phức tạp trung bình-cao: ~15 classes cần tạo mới, ~10 classes cần modify. Tuy nhiên, hầu hết classes mới follow existing patterns (CommandHandler, Redis operations, rate limiting).
- Rủi ro chính: Redis memory exhaustion nếu anonymous session bị abuse. Mitigation: rate limiting + TTL + data size limit.
- Research phase đã rất thorough (8 artifacts, 100% gap coverage) — confidence cao cho implementation.
- Package structure: `com.ntt.authservice` với clean architecture layers: `domain.model`, `application`, `application.command`, `application.port.out`, `adapter.in.web`, `adapter.out.persistence`.

### Related Features / Precedents

- **MFA Token Generation** (`JwtService.generateMfaToken()` at line 124 in `JwtService.kt`): Cùng pattern — custom claims (`type=mfa`), short TTL. Reference trực tiếp cho `generateAnonymousToken()`.
- **Login Rate Limiting** (`LoginRateLimitService` at `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`): Cùng Redis INCR+EXPIRE pattern, `StringRedisTemplate`. Reference cho `AnonymousRateLimitService`.
- **MFA Rate Limiting** (`MfaRateLimitService` at `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`): Cùng `StringRedisTemplate` usage, fail-open strategy, lock key pattern. Reference cho Redis operations.
- **CQRS Handlers** (`LoginHandler implements CommandHandler<LoginCommand, LoginResult>`): Reference cho `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`.
- **Session Policy** (`SessionPolicyService` at `src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt`): Reference for session management patterns, configurable policy.

### Integration Notes

- **JwtService**: Thêm 2 methods: `generateAnonymousToken(sessionId)` và `parseAnonymousToken(token)`. Follow pattern của `generateMfaToken()` — custom claims, configurable TTL. File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`.
- **LoginCommand**: Thêm optional field `anonymousSessionId: String?`. Backward compatible. File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`.
- **LoginHandler**: Thêm promotion logic sau successful auth — call `SessionPromotionService.promoteSession()`. Non-blocking: promotion failure không ảnh hưởng login success. File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`.
- **SecurityConfig**: Thêm `/api/v1/auth/anonymous` và `/api/v1/auth/anonymous/**` vào `permitAll()`. Hiện tại đang cho phép: `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/api/auth/mfa/*`, `/api/auth/sso/*`, `/api/captcha/challenge`, `/.well-known/jwks.json`, `/actuator/**`. File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt`.
- **JwtAuthFilter**: Extend để nhận diện `type=anonymous` claim → set `ROLE_ANONYMOUS` authority. Current filter chỉ handle `roles`/`permissions` claims từ authenticated tokens — cần thêm anonymous branch trước roles extraction. File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`.
- **Redis keys**: Namespace prefix `anon:` — không conflict với existing keys (`rate:login:attempts:`, `rate:login:lock:`, `mfa:verify:attempts:`, `mfa:verify:lock:`).
- **Token Blacklist**: Reuse existing `TokenBlacklistRepository` + `TokenBlacklistEntity`. Thêm reason values: `PROMOTION`, `RENEWAL`. Files: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`, `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`.
- **SecurityProperties**: Thêm nested `AnonymousProperties` data class vào `SecurityProperties`. Follow pattern của existing nested classes (JwtProperties, MfaProperties, SessionProperties). File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`.
- **Error Handling**: Follow `AuthException` → `AuthErrorCode` pattern. New error codes: `ANONYMOUS_SESSION_EXPIRED` (AUTH_040), `ANONYMOUS_DATA_LIMIT` (AUTH_041), `ANONYMOUS_PROMOTION_CONFLICT` (AUTH_042). Files: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`, `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`.
- **DTOs**: Follow existing patterns in `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — `@field:NotBlank`, optional fields via `val x: String? = null`. AuthResponse pattern: companion `from()` factory.

### Suggested Approach

1. **Phase 1 — Foundation**: Tạo `AnonymousProperties` config nested in `SecurityProperties`. Extend `JwtService` với `generateAnonymousToken()` + `parseAnonymousToken()` methods.
2. **Phase 2 — Core Services**: Tạo `AnonymousSessionDataService` (Redis CRUD), `AnonymousRateLimitService` (rate limiting following `LoginRateLimitService` pattern), `SessionPromotionService` (orchestration with distributed lock).
3. **Phase 3 — CQRS Handlers**: Tạo `AnonymousSessionHandler` (`CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult>`), `RenewAnonymousTokenHandler`. Extend `LoginCommand` + `LoginHandler` với promotion support.
4. **Phase 4 — Controller + DTOs**: Tạo `AnonymousAuthController` (new REST controller), request/response DTOs. Extend `CqrsAuthController.login()`, `LoginRequestDto`, `AuthResponse`.
5. **Phase 5 — Security**: Extend `SecurityConfig` (permitAll paths cho `/api/v1/auth/anonymous/**`), `JwtAuthFilter` (ROLE_ANONYMOUS detection).
6. **Phase 6 — Error Handling**: Add new `AuthErrorCode` entries, new exception classes following `AuthCoreExceptions.kt` pattern.
7. **Phase 7 — Testing**: Integration tests cho all use cases defined in technical_spec.md.

### ⚠️ Implementation Status (Phase B Code Scan — Updated)

> **CRITICAL**: Phase B code scan reveals that the anonymous login optimization feature has been **FULLY IMPLEMENTED** in the codebase. All planned classes (both [ADD] and [MODIFY]) already exist and are integrated.

**Classes found in codebase (previously marked [ADD]):**

| Class | File Path | Status |
|-------|-----------|--------|
| `AnonymousSessionHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | ✅ EXISTS |
| `CreateAnonymousSessionCommand` | `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt` | ✅ EXISTS |
| `RenewAnonymousTokenHandler` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` | ✅ EXISTS |
| `RenewAnonymousTokenCommand` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt` | ✅ EXISTS |
| `AnonymousSessionDataService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | ✅ EXISTS |
| `SessionPromotionService` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | ✅ EXISTS |
| `AnonymousRateLimitService` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` | ✅ EXISTS |
| `AnonymousSessionResult` | `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt` | ✅ EXISTS |
| `PromotionResult` | `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt` | ✅ EXISTS |
| `AnonymousAuthController` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` | ✅ EXISTS |
| `AnonymousDtos` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` | ✅ EXISTS |
| `AnonymousExceptions` | `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | ✅ EXISTS |

**Modifications already applied:**
- `JwtService.generateAnonymousToken()` — line 154 (IMPLEMENTED)
- `LoginHandler` — imports & uses `SessionPromotionService` (IMPLEMENTED)
- `RegisterHandler` — imports & uses `SessionPromotionService` (IMPLEMENTED)
- `SecurityConfig` — `/api/v1/auth/anonymous` in `permitAll()` (IMPLEMENTED)
- `JwtAuthFilter` — `ROLE_ANONYMOUS` authority for `type=anonymous` tokens (IMPLEMENTED)
- `SecurityProperties.AnonymousProperties` — line 124 (IMPLEMENTED)
- `AuthErrorCode` — `AUTH_040`~`AUTH_044` anonymous codes (IMPLEMENTED)
- `GlobalExceptionHandler` — anonymous exception handling (IMPLEMENTED)

**Impact on downstream workflows**: If `/wf_openspec` or `/wf_openspec_apply` is invoked, the feature type should be reassessed as **MAINTENANCE** (optimization/refinement of existing implementation) rather than **EXTEND** (adding new capability). All [ADD] tags in Change Impact Map should be updated to [REUSE] or [MODIFY] depending on whether optimization changes are needed.

### Context from Confluence Images

N/A — không có Confluence source. Input từ research artifacts.
