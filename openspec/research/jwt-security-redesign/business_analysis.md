# Business Analysis: JWT OAuth2 Security Redesign

> Phân tích nghiệp vụ chi tiết — use case decomposition, business rules, flows.

## 1. Tổng quan nghiệp vụ

### 1.1 Bối cảnh kinh doanh

Hệ thống cần cơ chế xác thực JWT mạnh mẽ, bao gồm:
- Quản lý vòng đời token đầy đủ (issue → validate → refresh → revoke → blacklist)
- Nhận diện và phân biệt thiết bị/trình duyệt
- Quản lý danh sách thiết bị đang hoạt động
- Thông báo email khi phát hiện thiết bị mới đăng nhập
- Cơ chế kick out / reject thiết bị từ xa

### 1.2 Stakeholders

| Stakeholder | Vai trò | Mối quan tâm |
|-------------|---------|---------------|
| End User | Người dùng hệ thống | Biết được ai đang đăng nhập, kick out device lạ |
| Admin | Quản trị viên | Quản lý sessions toàn hệ thống, force revoke |
| Security Team | Bảo mật | Audit trail, token lifecycle, device binding |
| Frontend Team | Client integration | API contract, headers, device fingerprint |

---

## 2. Use Cases

### UC-001: JWT Token Lifecycle Management

**Actor**: System  
**Mô tả**: Quản lý vòng đời hoàn chỉnh của JWT token từ khi issue đến khi expire/revoke.

**Basic Flow**:
1. User login thành công → System issue access token (15 min) + refresh token (7 days)
2. Access token có `jti` (UUID), `sub` (userId), `iss`, `aud`, `device_fingerprint` claim
3. Mỗi request → JwtAuthFilter validate: signature → expiry → blacklist check → claim validation → fingerprint validation
4. Khi access token sắp hết hạn → Client gọi refresh API
5. System revoke old refresh token → blacklist old access token JTI → issue new pair
6. Khi user logout → System blacklist current access token JTI + revoke refresh token

**Exception Flows**:
- **E1**: Access token expired → Return 401, client phải refresh
- **E2**: Refresh token expired/revoked → Return 401, client phải login lại
- **E3**: Token JTI in blacklist → Return 401 ngay lập tức
- **E4**: Device fingerprint mismatch → Return 401 + record suspicious event

**Business Rules**:
- BR-001: Access token TTL = 15 phút (configurable)
- BR-002: Refresh token TTL = 7 ngày (configurable)
- BR-003: Blacklist JTI có TTL = remaining lifetime của token
- BR-004: Token rotation bắt buộc khi refresh (revoke old → issue new)

---

### UC-002: Device Fingerprint Binding

**Actor**: System, User  
**Mô tả**: Gắn JWT token với device fingerprint để ngăn chặn token theft.

**Basic Flow**:
1. Client (browser/mobile) thu thập device signals → hash SHA-256 → gửi qua header `X-Device-Fingerprint`
2. Server nhận fingerprint, validate format (hex string, 64 chars)
3. Server ghi `device_fingerprint` claim vào JWT khi issue token
4. Mỗi request → JwtAuthFilter so sánh `X-Device-Fingerprint` header vs `device_fingerprint` claim trong token
5. Nếu match → tiếp tục. Nếu không match → reject + record event

**Server-side Fingerprint Composition** (khi client không gửi):
```
fingerprint = SHA-256(
  User-Agent +
  Accept-Language +
  IP-Prefix(/24 cho IPv4, /48 cho IPv6) +
  SEC-CH-UA (Client Hints nếu có)
)
```

**Exception Flows**:
- **E1**: Client không gửi `X-Device-Fingerprint` → Server tự tính từ request headers
- **E2**: Fingerprint thay đổi giữa request (VPN switch, browser update) → Cho phép mismatch nhẹ (configurable tolerance)
- **E3**: Fingerprint spoofing detected → Log warning, increment suspicious counter

**Business Rules**:
- BR-005: Fingerprint là required claim trong access token
- BR-006: Fingerprint validation có thể disable qua config (dev environment)
- BR-007: Strict mode: reject nếu fingerprint mismatch. Lenient mode: warn only

---

### UC-003: Active Device Management

**Actor**: User (authenticated)  
**Mô tả**: User xem danh sách thiết bị đang hoạt động và quản lý (kick out, clear).

**Basic Flow**:
1. User gọi `GET /api/auth/devices` → Server trả danh sách active sessions
2. Mỗi device hiển thị: device type, browser, OS, IP, login time, last active
3. User gọi `DELETE /api/auth/devices/{sessionId}` → Kick out device cụ thể
4. System revoke session + blacklist tất cả JTI liên quan
5. User gọi `DELETE /api/auth/devices` → Clear all other devices (trừ current)

**Exception Flows**:
- **E1**: sessionId không tồn tại → 404 Not Found
- **E2**: sessionId không thuộc user → 403 Forbidden
- **E3**: Kick out current device → Response 200 + instruction to re-login

**Sub-flows**:
- **SF1**: Admin kick out user device → `DELETE /api/auth/admin/devices/{userId}/{sessionId}`
- **SF2**: Khi kick out → gửi email thông báo cho user

**Business Rules**:
- BR-008: User chỉ thấy/quản lý device của mình
- BR-009: Admin có thể quản lý device của bất kỳ user
- BR-010: Kick out = revoke session + blacklist token + gửi notification
- BR-011: Current session được đánh dấu `isCurrent: true` trong response

---

### UC-004: New Device Login Email Notification

**Actor**: System → User  
**Mô tả**: Khi phát hiện device mới đăng nhập, hệ thống gửi email thông báo cho user.

**Basic Flow**:
1. LoginSessionService phát hiện `isNewDevice = true`
2. Emit `NewDeviceLoginEvent` (đã có)
3. Event listener nhận event → tạo record trong bảng `mail_queue`
4. Record gồm: recipient (user email), template_code, template_data (JSON), status=PENDING
5. Mail Job Scheduler poll bảng `mail_queue` mỗi 5s
6. Lấy batch PENDING records, render template, gửi email
7. Cập nhật status = SENT hoặc FAILED

**Email Template Content**:
```
Subject: [Security Alert] New Device Login Detected

Chào {username},

Chúng tôi phát hiện đăng nhập từ thiết bị mới:
- Browser: {browserName}
- OS: {osName}
- IP: {ipAddress}
- Thời gian: {loginAt}

Nếu đây không phải bạn, hãy:
1. Đổi mật khẩu ngay
2. Đăng xuất tất cả thiết bị: {logoutAllLink}

Trân trọng,
{appName} Security Team
```

**Exception Flows**:
- **E1**: User không có email → Skip, log warning
- **E2**: Email gửi thất bại → status = FAILED, retry tối đa 3 lần
- **E3**: Mail server timeout → Circuit breaker, retry sau

**Business Rules**:
- BR-012: Email gửi async, không block login flow
- BR-013: Mail queue record được tạo trong cùng transaction với session record
- BR-014: Template code phải map đến template có sẵn
- BR-015: Retry tối đa 3 lần với exponential backoff

---

### UC-005: Mail Queue & Template Job

**Actor**: System (Background Job)  
**Mô tả**: Background job tuần tự lấy mail từ queue và gửi theo template.

**Basic Flow**:
1. Job scheduler chạy `@Scheduled(fixedDelay = 5000)` — mỗi 5 giây
2. Query: `SELECT ... FROM mail_queue WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 10 FOR UPDATE SKIP LOCKED`
3. Cho mỗi record:
   a. Load template bằng `template_code`
   b. Merge template với `template_data` (JSON → Map)
   c. Gọi JavaMailSender / SMTP
   d. Nếu thành công: status = SENT, sent_at = now
   e. Nếu thất bại: retry_count++, nếu retry_count >= max → status = FAILED
4. Cleanup job: Archive SENT records older than 30 days

**Database Schema** (`mail_queue` table):
```sql
CREATE TABLE mail_queue (
    id              BIGINT PRIMARY KEY,
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(500),
    template_code   VARCHAR(100) NOT NULL,
    template_data   JSONB,
    body_rendered   TEXT,
    status          VARCHAR(20) DEFAULT 'PENDING',
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    error_message   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ,
    created_by      BIGINT
);

CREATE INDEX idx_mail_queue_status ON mail_queue (status, next_retry_at);
```

**Business Rules**:
- BR-016: `FOR UPDATE SKIP LOCKED` để nhiều instance không xử lý trùng
- BR-017: Exponential backoff: retry_delay = 30s * 2^retry_count
- BR-018: Status transitions: PENDING → PROCESSING → SENT | FAILED
- BR-019: Template rendering phải support i18n (user's preferred language)

---

### UC-006: Session-Token Lifecycle Synchronization

**Actor**: System  
**Mô tả**: Đồng bộ lifecycle giữa session, refresh token, và access token.

**Basic Flow**:
1. Login → create session + store refresh token hash + issue access token
2. Session entity giữ `refreshTokenId` → liên kết session ↔ refresh token
3. Refresh → revoke old refresh token + blacklist old access JTI + issue new pair + update session
4. Logout → revoke session + revoke refresh token + blacklist access JTI
5. Kick out device → same as logout nhưng từ remote
6. Session cleanup scheduler → revoke inactive sessions + blacklist associated JTIs

**State Diagram**:
```
Session:  ACTIVE → REVOKED (logout/kick/policy/inactive)
Token:    VALID  → BLACKLISTED (revoke) / EXPIRED (natural)
Refresh:  VALID  → REVOKED (rotation/logout/kick)
```

**Business Rules**:
- BR-020: Revoke session PHẢI đồng thời blacklist access token JTI
- BR-021: Revoke refresh token PHẢI đồng thời invalidate session
- BR-022: Session timeout = 30 phút không hoạt động (configurable)
- BR-023: Absolute session ceiling = 10 giờ (configurable)

---

## 3. Traceability Matrix

| Use Case | Business Rules | Entities | APIs |
|----------|---------------|----------|------|
| UC-001 | BR-001~004 | AccessToken, RefreshToken, TokenBlacklist | /auth/login, /auth/refresh, /auth/logout |
| UC-002 | BR-005~007 | LoginSession, DeviceFingerprint | Internal (filter) |
| UC-003 | BR-008~011 | LoginSession | /auth/devices, /auth/devices/{id} |
| UC-004 | BR-012~015 | MailQueue, MailTemplate | Event-driven (internal) |
| UC-005 | BR-016~019 | MailQueue | Scheduled job (internal) |
| UC-006 | BR-020~023 | LoginSession, RefreshToken, TokenBlacklist | All auth APIs |

## 4. Non-Functional Requirements

| NFR | Target | Measurement |
|-----|--------|-------------|
| Blacklist check latency | < 1ms (L1), < 5ms (L2) | Micrometer timer |
| Device fingerprint validation | < 0.5ms | Hash comparison |
| Mail queue throughput | ≥ 100 emails/min | Job metrics |
| Session list API latency | < 50ms | P95 |
| Token revocation propagation | < 30s worst case (Caffeine TTL) | Config |

---

> **Next step**: Phase 6 (Technical Specification)
