# Research Brief: E2EE Performance & Compliance

> Tài liệu khởi đầu cho quá trình research nhóm tính năng bảo mật nâng cao — hiệu năng, giới hạn kỹ thuật, pháp lý và kiểm toán.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | E2EE Performance & Compliance (Issues #8–#12 + Checklist) |
| **Ngày tạo** | 2026-08-15 |
| **Input source** | name (mô tả trực tiếp từ user) |
| **Input content** | 5 vấn đề (gRPC Streaming, Partial Encryption, Time Skew, HSM/KMS, Audit Vault) + 4 câu hỏi checklist (Fail-fast, Stateless/Stateful, Multi-tenancy, CI/CD Refresh) |
| **Người yêu cầu** | nguyenthanhthai |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Hệ thống E2EE middleware hiện tại có các thiếu sót nghiêm trọng:

1. **gRPC Streaming**: Encrypt full/partial body nhưng chưa xử lý Server Streaming, file upload lớn, WebSocket. Nếu dùng 1 key + 1 nonce cho toàn bộ stream → **nonce reuse** (thảm họa bảo mật).
2. **Partial Encryption**: Parse JSON/Protobuf để lấy từng field tốn CPU gấp ~10× so với encrypt full body. Chưa có cơ chế chống **cut-and-paste attack** (copy encrypted field giữa user A → B).
3. **Client Time Skew**: Dùng timestamp chống replay, nhưng đồng hồ điện thoại user có thể lệch server vài phút → reject hợp lệ hoặc accept replay.
4. **Data Residency & HSM**: Key lưu plaintext trong DB. SQL Injection → lộ toàn bộ key. Cần HSM/KMS + envelope encryption.
5. **Audit Log**: Audit log không ghi plaintext nhưng cần giải mã để điều tra sự cố → cần "Decryption Vault" với break-glass procedure.

### 2.2 Mục tiêu (Objectives)
- [x] Thiết kế frame-based encryption cho gRPC streaming
- [x] Thiết kế partial encryption tối ưu với AAD context binding
- [x] Giải quyết client time skew với server time offset
- [x] Thiết kế HSM/KMS integration cho key management
- [x] Thiết kế Decryption Vault cho audit forensics
- [x] Trả lời 4 câu hỏi checklist kiến trúc

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| gRPC streaming frame encryption | WebSocket realtime (tách riêng) |
| Partial field encryption optimization | Full homomorphic encryption |
| Client time skew offset | NTP server tự host |
| HSM/KMS envelope encryption | Physical HSM hardware setup |
| Audit Decryption Vault architecture | SOC compliance certification process |
| Cipher version negotiation | Post-quantum cryptography migration |
| Multi-tenancy key isolation | Per-user E2EE (Signal Protocol) |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `gRPC streaming encryption`
- `frame-based encryption nonce`
- `AES-256-GCM partial field encryption`
- `AAD context binding`
- `client time skew synchronization`
- `HSM KMS envelope encryption`
- `audit decryption vault break-glass`
- `cipher version negotiation`

### 3.2 Secondary Keywords
- `nonce reuse attack`
- `cut-and-paste attack AES-GCM`
- `chunked transfer encryption`
- `JSON field masking encryption`
- `struct tags sensitive annotation`
- `GDPR data residency key regionalization`
- `multi-tenant key isolation SaaS`
- `fail-fast vs fail-open encryption`

### 3.3 Domain-Specific Terms
- `AAD (Additional Authenticated Data)`: Dữ liệu xác thực bổ sung được gắn vào ciphertext để chống tampering
- `Envelope Encryption`: Mẫu mã hóa 2 lớp — DEK mã hóa data, KEK mã hóa DEK
- `AEAD (Authenticated Encryption with Associated Data)`: Thuật toán mã hóa kết hợp xác thực (VD: AES-GCM)
- `Break-glass procedure`: Quy trình khẩn cấp truy cập dữ liệu nhạy cảm, cần 2+ người approve
- `Crypto-agility`: Khả năng chuyển đổi thuật toán mã hóa mà không ảnh hưởng backward compatibility
- `DEK (Data Encryption Key)`: Key dùng trực tiếp để mã hóa dữ liệu
- `KEK (Key Encryption Key)`: Key dùng để mã hóa DEK, thường nằm trong HSM/KMS
- `Nonce Reuse`: Lỗi dùng lại nonce với cùng key → phá hoàn toàn AES-GCM
- `Time Skew Tolerance`: Dung sai cho phép lệch giờ giữa client và server

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"gRPC streaming encryption frame-based nonce per message security"` | Architecture | High |
| 2 | `"AES-256-GCM partial field encryption AAD context binding"` | Security | High |
| 3 | `"client server time skew synchronization replay attack tolerance"` | Security | High |
| 4 | `"HSM KMS envelope encryption Spring Boot integration"` | Infrastructure | High |
| 5 | `"audit log encrypted decryption vault break-glass procedure"` | Compliance | High |
| 6 | `"E2EE cipher version negotiation backward compatibility"` | Architecture | High |
| 7 | `"multi-tenant encryption key isolation SaaS TenantID AAD"` | Multi-tenancy | Medium |
| 8 | `"large file encryption streaming chunked vs tunnel encryption"` | Performance | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| AES-256-GCM encryption (TOTP) | `auth/application/TotpService.kt` | **High** | Đã implement AES-256-GCM cho TOTP secret — cơ sở pattern tái sử dụng |
| JWT Auth Filter | `shared/security/JwtAuthFilter.kt` | **Medium** | Filter chain hiện tại — điểm tích hợp middleware E2EE |
| Security Properties | `shared/config/SecurityProperties.kt` | **High** | Centralized config — cần mở rộng cho E2EE settings |
| Redis Integration | `shared/config/RedisConfig.kt` | **Medium** | Dùng cho rate limiting, session — có thể cache key |
| Rate Limiting | `SecurityProperties.LoginRateLimitProperties` | **Low** | Mẫu thiết kế config tham khảo |
| Session Management | `SecurityProperties.SessionProperties` | **Medium** | Stateful session policy — ảnh hưởng E2EE key lifecycle |

### 4.2 Existing Code Patterns

- **Architecture**: Handler-based (CQRS pattern), Clean Architecture layers
- **Data Access**: JPA/Hibernate, Spring Data JPA Repository pattern
- **API Style**: REST (Spring MVC `@RestController`), handler-based MID
- **Error Handling**: Exception hierarchy, `@ControllerAdvice` (inferred from base-core starters)
- **Encryption Pattern**: AES-256-GCM, random IV per operation, Base64 encode (iv + ciphertext)
- **Key Storage**: **⚠️ Env var plaintext** (`TOTP_ENCRYPTION_KEY` Base64), validated at startup
- **Logging**: SLF4J, structured logging (via `common-log` module)
- **Testing**: JUnit 5, Mockito, ArchUnit, H2 in-memory DB

### 4.3 Tech Stack Constraints

- **Language**: Kotlin (JVM)
- **Framework**: Spring Boot 4.x (with base-core starters)
- **Database**: PostgreSQL (Flyway migrations), H2 for tests
- **Cache**: Redis (Spring Data Redis), Caffeine (L1)
- **Build tool**: Gradle (Kotlin DSL)
- **Key dependencies**: JJWT, BouncyCastle, Passay, TOTP library, Spring Security, Spring OAuth2, Jackson, EventSourcing utils
- **Testing**: JUnit 5, Mockito, ArchUnit, Spring Test

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `JwtAuthFilter` | Filter Chain | `shared/security/JwtAuthFilter.kt` | E2EE middleware sẽ thêm sau filter này |
| `SecurityConfig` | Config | `shared/config/SecurityConfig.kt` | Cần register E2EE filter |
| `SecurityProperties` | Config | `shared/config/SecurityProperties.kt` | Thêm E2EE config properties |
| `TotpService` | Service | `auth/application/TotpService.kt` | Tái sử dụng AES-GCM pattern |
| `RedisConfig` | Config | `shared/config/RedisConfig.kt` | Cache cho key, nonce tracking |
| DB Migration | SQL | `db/migration/` | New tables cho key storage, audit |
| `base-web-starter` | Module | Platform BOM | Base interceptor/filter chain |
| `eventsourcing-utils` | Module | CQRS | Event pattern cho audit log |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Frame-based encryption cho gRPC streaming cần cấu trúc frame như thế nào? (Sequence number, nonce generation, frame size)
- [x] Q2: Partial encryption với AAD binding — cách tối ưu parse JSON path mà không tốn CPU quá mức?
- [x] Q3: Time skew tolerance ±5 phút có đủ? Cơ chế offset calculation ra sao?
- [x] Q4: HSM/KMS envelope encryption — workflow encrypt/decrypt key với Cloud KMS?
- [x] Q5: Decryption Vault — architecture tách biệt service, JIT access, break-glass 2-person approval?
- [x] Q6: Cipher version negotiation — header format, fallback logic, sunset timeline?
- [x] Q7: Multi-tenancy — TenantID trong AAD context ngăn cross-tenant access?
- [x] Q8: Fail-fast vs fail-open — impact lên availability khi KMS unreachable?

### 5.2 Assumptions cần verify
- [x] A1: Spring Boot 4.x hỗ trợ custom filter chain ordering tốt cho E2EE middleware
- [x] A2: Cloud KMS latency < 100ms cho envelope decrypt — acceptable cho hot path
- [x] A3: Partial encryption bằng JSON path matching có thể cached → amortize CPU cost
- [x] A4: gRPC chưa được dùng trong project hiện tại (REST only) → cần thêm dependency

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đủ thông tin cho tất cả 5 vấn đề + 4 checklist | ≥ 10 sources |
| Open source options | Đánh giá thư viện mã hóa Java/Kotlin | ≥ 3 projects evaluated |
| Gap analysis | Xác định gaps giữa hiện tại và yêu cầu | All critical gaps identified |
| Business analysis | Tất cả Use Cases documented | All 5+ UCs documented |
| Technical spec | Agent-ready cho implementation | Architecture diagrams + API spec |
| Decision checklist | 4 câu hỏi trả lời rõ ràng | 4/4 answered with reasoning |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
