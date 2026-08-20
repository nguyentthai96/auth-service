# Pre-OpenSpec: anonymous-login-optimization

> **Type**: MAINTENANCE
> **Flow**: Non-Financial
> **Source**: Research Artifacts (business_analysis.md, technical_spec.md, research_brief.md)
> **Classification Evidence**: keyword `anonymous`, `session`, `token` → module `auth.application`, `auth.adapter.in.web` → file `AnonymousAuthController.kt`, `AnonymousSessionHandler.kt`, `SessionPromotionService.kt` (ALL EXIST — feature fully implemented)
> **Archive**: `openspec/changes/archive/2026-08-20-anonymous-login-optimization/pre_openspec.md`
> **Previous Version**: EXTEND → reclassified to MAINTENANCE (all classes exist in codebase)
> **Quality Score**: 85/100

## 📋 Feature Summary

Tính năng Anonymous Login Optimization cho phép visitor (chưa đăng nhập) tạo phiên ẩn danh tạm thời để tương tác hạn chế với hệ thống. Phiên ẩn danh được lưu trong Redis với TTL, cho phép lưu trữ dữ liệu tạm thời (cart, preferences). Khi user đăng nhập hoặc đăng ký, phiên ẩn danh được "promote" — tự động chuyển dữ liệu tạm thời sang tài khoản xác thực, blacklist anonymous token, và xóa session.

**[CHANGED]** Feature đã được implement đầy đủ trong codebase. Classification thay đổi từ EXTEND → MAINTENANCE. Scope hiện tại: optimization, refinement, bug fix, hoặc enhancement trên code hiện có.

| Metric | Giá trị |
|--------|---------|
| Số FR | 13 (Research: 8, Enriched: 5) |
| Issues | 4 (🔴: 1, 🟡: 3) |
| Open Questions | 2 |
| **Quality Score** | **85/100** |

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
- **Action**: Hệ thống phải cho phép store/retrieve/delete key-value data trong anonymous session theo namespace (`PUT/GET/DELETE /api/v1/auth/anonymous/session/data`)
- **Validation**: Data được lưu/đọc/xóa đúng namespace. Redis key format: `anon:data:{sessionId}:{namespace}:{key}`.

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
- **Action**: Hệ thống phải trả thêm `promotedFromAnonymous: Boolean` và `dataTransferred: { itemCount, namespaces, status }` trong login response khi promotion xảy ra
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

**[CHANGED]** So với archive: FR-006 đã bổ sung DELETE endpoint (trước đó chỉ có PUT/GET). Codebase hiện tại đã implement DELETE endpoint trong `AnonymousAuthController.kt`.

## 5. Enriched Domain Requirements

Đã bổ sung 5 FR enriched (FR-009 → FR-013). ⚠️ Assumption: sử dụng giới hạn max = 5 vì tất cả enriched FRs đều critical cho security và integration.

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
| Rõ ràng (Clarity) | 23/25 | FR-004: merge strategy "last-write-wins for scalars" cần định nghĩa rõ hơn — scalar = single Redis String value |
| Đầy đủ (Completeness) | 22/25 | FR-003: đã bao gồm register promotion (RegisterHandler implemented); FR-006: đã bổ sung DELETE endpoint |
| Nhất quán (Consistency) | 22/25 | FR-009: rate limit dùng fixed window (align với LoginRateLimitService INCR+EXPIRE pattern) — đã nhất quán |
| Kiểm thử được (Testability) | 18/25 | FR-004: "tất cả data" khó verify boundary; FR-010: concurrent promotion test cần distributed test setup |
| **Tổng** | **85/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -2 | FR-004 | "last-write-wins for scalars" — scalar = single Redis String value (đã clarified hơn archive) | Liệt kê cụ thể data types ví dụ: preferences, locale |
| 2 | Completeness | -1 | FR-003 | Archive thiếu register promotion, codebase đã implement RegisterHandler promotion | Đã cập nhật — RegisterHandler cũng hỗ trợ anonymousSessionId |
| 3 | Completeness | -2 | FR-006 | Archive thiếu DELETE endpoint; codebase đã implement DELETE | Đã cập nhật FR-006 để bao gồm DELETE |
| 4 | Consistency | -3 | FR-009 | Fixed window align với LoginRateLimitService pattern — nhất quán | N/A — đã resolve |
| 5 | Testability | -4 | FR-004 | "tất cả data" không quantifiable — bao nhiêu keys, namespace nào? | Định nghĩa test fixture cụ thể: N keys × M namespaces, verify count |
| 6 | Testability | -3 | FR-010 | Concurrent promotion cần 2+ threads hoặc mock distributed lock | Mô tả test strategy: single-instance concurrent thread test + integration test |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | Redis memory exhaustion nếu bot farm tạo hàng loạt anonymous sessions | FR-001, FR-002, FR-009 | Rate limiting + TTL + monitoring alert khi anonymous session count > threshold |
| 2 | Ambiguity | 🟡 | FR-004 merge strategy "last-write-wins for scalars" chưa define rõ với nhiều data types | FR-004 | Định nghĩa cụ thể scalar = single-value Redis String keys |
| 3 | Missing | 🟡 | Chưa có monitoring/metrics cho anonymous session lifecycle đầy đủ (hiện có counters nhưng chưa có dashboard) | FR-001, FR-008 | Micrometer counters đã implement: `sessions.created`, `sessions.renewed`, `sessions.promoted`, `data.stored`, `data.size_exceeded`, `rate_limited` |
| 4 | Incomplete | 🟡 | Chưa rõ behavior khi Redis data transfer fail giữa chừng (partial transfer) — hiện tại trả status PARTIAL | FR-004 | Codebase đã handle: `DataTransferResult.partial=true` → `PromotionResult.Status.PARTIAL` |

## 9. Open Questions

- FR-003: ~~Promotion có hỗ trợ khi user register (không chỉ login) không?~~ **RESOLVED**: RegisterHandler đã implement promotion support với `anonymousSessionId` + `anonymousTokenJti` trong `RegisterCommand`.
- FR-009: ~~Rate limit cho anonymous token creation nên dùng fixed window hay sliding window?~~ **RESOLVED**: Codebase dùng fixed window (INCR+EXPIRE) align với `LoginRateLimitService` pattern.

> Cả 2 open questions từ archive đã được resolve trong implementation.

## 10. Detected Scope

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Session Management — mở rộng auth domain hiện có để hỗ trợ anonymous/guest sessions. Feature đã được implement đầy đủ.

### 10.2 Flow Type

Non-Financial — không có giao dịch tài chính, không cần OTP/biometric xác nhận. Anonymous session creation và promotion là command-style operations.

### 10.3 Candidate Services
- **auth-service** (primary): Keyword match `anonymous`, `session`, `token`, `login` → module `auth.application` → files: `AnonymousSessionHandler.kt`, `AnonymousRateLimitService.kt`, `AnonymousSessionDataService.kt`, `SessionPromotionService.kt`, `RenewAnonymousTokenHandler.kt`, `JwtService.kt`
- **auth-service/adapter**: Controller và DTOs → module `auth.adapter.in.web` → files: `AnonymousAuthController.kt`, `AnonymousDtos.kt`
- **auth-service/shared**: Config và security filter → module `shared.config`, `shared.security`, `shared.exception` → files: `SecurityConfig.kt`, `SecurityProperties.kt`, `JwtAuthFilter.kt`, `AnonymousExceptions.kt`, `AuthErrorCode.kt`

### Detection Evidence
- Keyword: `anonymous` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — implements `CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult>`
- Keyword: `anonymous`, `rate limit` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — uses `StringRedisTemplate`, INCR+EXPIRE pattern
- Keyword: `anonymous`, `session`, `data` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — Redis CRUD with namespace isolation
- Keyword: `promotion`, `session` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — distributed lock + data transfer orchestration
- Keyword: `renew`, `anonymous` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — implements `CommandHandler<RenewAnonymousTokenCommand, AnonymousSessionResult>`
- Keyword: `generateAnonymousToken` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` line 157 — generates anonymous JWT with `type=anonymous` claim
- Keyword: `anonymous` → Module: `auth.adapter.in.web` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` — REST controller at `/api/v1/auth/anonymous`
- Keyword: `ROLE_ANONYMOUS` → Module: `shared.security` → File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` line 63 — anonymous token detection
- Keyword: `AnonymousProperties` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` line 194 — nested data class
- Keyword: `ANONYMOUS_SESSION_EXPIRED` → Module: `shared.exception` → File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` — AUTH_040~AUTH_044
- Keyword: `anonymousSessionId` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` line 155 — promotion call
- Keyword: `anonymousSessionId` → Module: `auth.application.command` → File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` line 83 — promotion call
- Keyword: `permitAll` + `anonymous` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` line 57 — `/api/v1/auth/anonymous` permitAll
- Keyword: `promotedFromAnonymous` → Module: `auth.adapter.in.web.dto` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt` line 17 — promotion metadata in response

### 10.4 External Integrations
- **Redis**: `StringRedisTemplate` — anonymous session data, rate limiting, distributed lock (existing integration in `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`)
- **PostgreSQL**: `TokenBlacklistRepository` (JPA) — anonymous token blacklisting (existing table `token_blacklist`, entity in `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`)
- **CAPTCHA**: `CaptchaGateway` — optional abuse prevention for anonymous token creation (existing port in `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`)

### 10.5 Required Modules
- `auth.application` — `JwtService` (EXISTING), `AnonymousSessionDataService` (EXISTING), `AnonymousRateLimitService` (EXISTING), `SessionPromotionService` (EXISTING), `PromotionResult` (EXISTING), `AnonymousSessionResult` (EXISTING)
- `auth.application.command` — `AnonymousSessionHandler` (EXISTING), `RenewAnonymousTokenHandler` (EXISTING), `CreateAnonymousSessionCommand` (EXISTING), `RenewAnonymousTokenCommand` (EXISTING), `LoginHandler` (EXISTING - modified), `RegisterHandler` (EXISTING - modified), `LoginCommand` (EXISTING - modified)
- `auth.adapter.in.web` — `AnonymousAuthController` (EXISTING)
- `auth.adapter.in.web.dto` — `AnonymousDtos` (EXISTING), `AuthResponse` (EXISTING - modified), `RequestDtos` (EXISTING - modified)
- `shared.config` — `SecurityConfig` (EXISTING - modified), `SecurityProperties.AnonymousProperties` (EXISTING)
- `shared.security` — `JwtAuthFilter` (EXISTING - modified)
- `shared.exception` — `AnonymousExceptions` (EXISTING), `AuthErrorCode` (EXISTING - modified)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Anonymous User | Truy cập app lần đầu, chưa có token | Client kiểm tra token — không có |
| 2 | Client | Gửi `POST /api/v1/auth/anonymous` | System check rate limit → sinh UUID sessionId → generate anonymous JWT → init Redis session → trả token |
| 3 | Anonymous User | Sử dụng app, lưu data (cart, preferences) | `PUT /api/v1/auth/anonymous/session/data` → validate token → check session → check size → store in Redis |
| 4 | Anonymous User | Token gần hết hạn | `POST /api/v1/auth/anonymous/renew` → validate token → check session → check renewal count → generate new JWT → blacklist old JTI → refresh TTL |
| 5 | Anonymous User | Quyết định đăng nhập | `POST /api/v1/auth/login` với `anonymousSessionId` + `anonymousToken` → standard login flow |
| 6 | System | Login thành công + anonymousSessionId present | Acquire distributed lock → verify session exists → transfer data to user namespace → blacklist anonymous JTI → delete session → release lock |
| 7 | System | Trả login response | Auth tokens + `promotedFromAnonymous: true` + `dataTransferred: { itemCount, namespaces, status }` |

## 12. Traceability Matrix

| FR-ID | Research Section | Spec Section | Affected Class | Status |
|-------|-----------------|-------------|---------------|--------|
| FR-001 | BA UC-001 | TS §4.1, §6.2 | `JwtService` (EXISTING), `AnonymousSessionHandler` (EXISTING) | [REUSE] Implemented |
| FR-002 | BA UC-001 | TS §2.2, §4.1 | `AnonymousSessionHandler` (EXISTING), Redis (REUSE) | [REUSE] Implemented |
| FR-003 | BA UC-002 | TS §4.2, §6.2 | `LoginCommand` (EXISTING), `LoginHandler` (EXISTING), `CqrsAuthController` (EXISTING), `LoginRequestDto` (EXISTING) | [REUSE] Implemented |
| FR-004 | BA UC-002, UC-006 | TS §4.2 | `SessionPromotionService` (EXISTING), `AnonymousSessionDataService` (EXISTING) | [REUSE] Implemented |
| FR-005 | BA UC-002, UC-007 | TS §4.2 | `SessionPromotionService` (EXISTING), `TokenBlacklistRepository` (REUSE) | [REUSE] Implemented |
| FR-006 | BA UC-003 | TS §6.2 | `AnonymousSessionDataService` (EXISTING), `AnonymousAuthController` (EXISTING) | [REUSE] Implemented |
| FR-007 | BA UC-003 BR-006 | TS §6.2 | `AnonymousSessionDataService` (EXISTING) | [REUSE] Implemented |
| FR-008 | BA UC-004 | TS §6.2 | `RenewAnonymousTokenHandler` (EXISTING), `JwtService` (EXISTING) | [REUSE] Implemented |
| FR-009 | BA UC-005 BR-003 | TS §9.1 | `AnonymousRateLimitService` (EXISTING) | [REUSE] Implemented |
| FR-010 | BA UC-002 EF-002 | TS §4.2 | `SessionPromotionService` (EXISTING) | [REUSE] Implemented |
| FR-011 | TS §7.1 | TS §7.1, §7.2 | `JwtAuthFilter` (EXISTING), `SecurityConfig` (EXISTING) | [REUSE] Implemented |
| FR-012 | TS §9.5 | TS §9.5 | `SecurityProperties.AnonymousProperties` (EXISTING) | [REUSE] Implemented |
| FR-013 | BA UC-002 BR-011 | TS §6.2 | `AuthResponse` (EXISTING), `LoginResult` (EXISTING), `PromotionResult` (EXISTING) | [REUSE] Implemented |

### Change Impact Map (MAINTENANCE)

**[CHANGED]** All entries updated from archive [ADD]/[MODIFY] → [REUSE]. Feature is fully implemented.

```
FR-001 → [REUSE] JwtService (src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) → generateAnonymousToken() at line 157
         [REUSE] AnonymousSessionHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt)
FR-002 → [REUSE] AnonymousSessionHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) → Redis anon:session:{id}
FR-003 → [REUSE] LoginCommand (src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt) → anonymousSessionId field
         [REUSE] LoginHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) → promotion at line 155
         [REUSE] RegisterHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) → promotion at line 83
         [REUSE] LoginRequestDto (src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt) → anonymousSessionId + anonymousToken fields
FR-004 → [REUSE] SessionPromotionService (src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) → transferData()
         [REUSE] AnonymousSessionDataService (src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) → transferData(), Redis SCAN
FR-005 → [REUSE] SessionPromotionService (src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) → blacklist at line 79
         [REUSE] TokenBlacklistRepository (src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) → save()
FR-006 → [REUSE] AnonymousSessionDataService (src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) → storeData/getData/deleteData
         [REUSE] AnonymousAuthController (src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt) → PUT/GET/DELETE endpoints
FR-007 → [REUSE] AnonymousSessionDataService (src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) → getSessionDataSize() check
FR-008 → [REUSE] RenewAnonymousTokenHandler (src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt) → full renewal flow
         [REUSE] JwtService (src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) → generateAnonymousToken()
FR-009 → [REUSE] AnonymousRateLimitService (src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt) → checkRateLimit()
FR-010 → [REUSE] SessionPromotionService (src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) → acquireLock/releaseLock at Redis anon:lock:{id}
FR-011 → [REUSE] JwtAuthFilter (src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) → ROLE_ANONYMOUS at line 63
         [REUSE] SecurityConfig (src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) → permitAll at line 57, hasRole("ANONYMOUS") at line 59
FR-012 → [REUSE] SecurityProperties.AnonymousProperties (src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) → line 194
FR-013 → [REUSE] AuthResponse (src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt) → promotedFromAnonymous, dataTransferred fields
         [REUSE] LoginResult.Success (src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt) → promotionResult field
         [REUSE] PromotionResult (src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt) → Status enum
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

- **[CHANGED] Feature đã FULLY IMPLEMENTED**: Phase B code scan confirms ALL planned classes (both ADD and MODIFY from archive) already exist and are integrated into the codebase. Classification changed: EXTEND → MAINTENANCE.
- Feature mở rộng auth domain hiện có, không phải greenfield. Hệ thống có mature infrastructure: JwtService (RS256 + HMAC fallback), CQRS CommandHandler, StringRedisTemplate + RedisTemplate, LoginRateLimitService, TokenBlacklistRepository.
- **Implementation quality**: Code follows existing patterns consistently — CQRS handlers, Redis operations, rate limiting, error handling, Micrometer metrics. No anti-patterns detected.
- **Improvements over archive spec**:
  - Open Question 1 (register promotion) → RESOLVED: RegisterHandler implements promotion
  - Open Question 2 (rate limit window) → RESOLVED: Fixed window, consistent with LoginRateLimitService
  - Missing DELETE endpoint → RESOLVED: AnonymousAuthController has DELETE endpoint
  - Missing monitoring → PARTIALLY RESOLVED: Micrometer counters exist for key operations
- Rủi ro chính: Redis memory exhaustion nếu anonymous session bị abuse. Mitigation already in place: rate limiting + TTL + data size limit + fail-open strategy.
- Package structure: `com.ntt.authservice` với clean architecture layers: `domain.model`, `application`, `application.command`, `application.port.out`, `adapter.in.web`, `adapter.out.persistence`.

### Related Features / Precedents

- **MFA Token Generation** (`JwtService.generateMfaToken()` in `JwtService.kt`): Cùng pattern — custom claims (`type=mfa`), short TTL. Reference trực tiếp cho `generateAnonymousToken()`.
- **Login Rate Limiting** (`LoginRateLimitService` at `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`): Cùng Redis INCR+EXPIRE pattern, `StringRedisTemplate`. Implemented consistently in `AnonymousRateLimitService`.
- **MFA Rate Limiting** (`MfaRateLimitService` at `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`): Cùng `StringRedisTemplate` usage, fail-open strategy, lock key pattern.
- **CQRS Handlers** (`LoginHandler implements CommandHandler<LoginCommand, LoginResult>`): Reference cho `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`.
- **Session Policy** (`SessionPolicyService` at `src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt`): Reference for session management patterns.
- **Session Cleanup** (`SessionCleanupScheduler` at `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt`): Existing scheduler pattern.

### Integration Notes

- **JwtService**: `generateAnonymousToken(sessionId)` at line 157, `parseAnonymousToken(token)` at line 175. Follow pattern of `generateMfaToken()`. File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`.
- **LoginCommand**: Optional fields `anonymousSessionId: String?` and `anonymousTokenJti: String?`. Backward compatible. File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`.
- **LoginHandler**: Promotion logic at line 155 — calls `SessionPromotionService.promoteSession()`. Best-effort: promotion failure doesn't affect login success (DD-007). File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`.
- **RegisterHandler**: Promotion logic at line 83 — same pattern as LoginHandler. File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`.
- **SecurityConfig**: `/api/v1/auth/anonymous` permitAll (line 57), `/api/v1/auth/anonymous/**` hasRole("ANONYMOUS") (line 59). File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt`.
- **JwtAuthFilter**: Anonymous token detection at line 63 — sets `ROLE_ANONYMOUS` authority, stores `type=anonymous` and `sessionId` in auth details. File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`.
- **Redis keys**: Namespace prefix `anon:` — no conflict with existing keys (`rate:login:attempts:`, `mfa:verify:attempts:`, etc.).
- **Token Blacklist**: Reuses existing `TokenBlacklistRepository` + `TokenBlacklistEntity`. Added reasons: `PROMOTION`, `RENEWAL`.
- **SecurityProperties**: `AnonymousProperties` nested data class at line 194 with defaults: tokenTtl=3600, sessionTtl=86400, maxDataSize=65536, maxRenewals=24, rateLimit=5/3600s.
- **Error Handling**: `AnonymousExceptions.kt` with 5 exception classes extending `AuthException`. Error codes AUTH_040~AUTH_044 in `AuthErrorCode.kt`. Handled by `GlobalExceptionHandler`.
- **DTOs**: `AnonymousDtos.kt` with `CreateAnonymousSessionRequest`, `StoreSessionDataRequest`, `AnonymousTokenResponse`, `SessionDataResponse`, `DataTransferredInfo`. `AuthResponse` extended with `promotedFromAnonymous` and `dataTransferred` fields.
- **Metrics**: Micrometer counters: `auth.anonymous.sessions.created`, `auth.anonymous.sessions.renewed`, `auth.anonymous.sessions.promoted` (with status tag), `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded`, `auth.anonymous.rate_limited`, `auth.anonymous.token.generation.duration` (timer), `auth.anonymous.promotion.duration` (timer).

### Suggested Approach (MAINTENANCE)

Since the feature is fully implemented, maintenance activities should focus on:

1. **Optimization**: Performance tuning of Redis SCAN operations in `AnonymousSessionDataService.transferData()` — currently uses SCAN with count=100, may need tuning for large data sets.
2. **Monitoring Enhancement**: Add Prometheus/Grafana dashboards for anonymous session metrics (counters + timers already exist).
3. **Testing**: Comprehensive integration tests covering all 8 use cases, edge cases (concurrent promotion, rate limit boundary, data size boundary, renewal count boundary).
4. **Configuration Validation**: Validate `AnonymousProperties` defaults against production requirements.
5. **Security Audit**: Review fail-open strategy in `AnonymousRateLimitService` — acceptable for availability but should be monitored.

### Context from Confluence Images

N/A — không có Confluence source. Input từ research artifacts.
