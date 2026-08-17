# Research Brief: Anonymous Login Optimization

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Anonymous Login Optimization |
| **Ngày tạo** | 2025-01-20 |
| **Input source** | Name + Description (seed prompt) |
| **Input content** | Research anonymous/guest authentication patterns for enterprise IAM systems. Focus on: temporary anonymous sessions, session promotion (merging anonymous session data into authenticated user after login), guest cart management, seamless authentication upgrade flow, and anonymous-to-authenticated data transfer. |
| **Người yêu cầu** | System (headless pipeline) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Hiện tại auth-service chỉ hỗ trợ authenticated users (login bằng email/password). Không có cơ chế cho phép guest/anonymous users tương tác với hệ thống mà không cần đăng ký trước. Điều này gây friction cho người dùng mới — buộc phải tạo tài khoản trước khi thực hiện bất kỳ action nào (ví dụ: duyệt sản phẩm, thêm vào giỏ hàng, lưu preferences).

Anonymous login optimization giải quyết bài toán:
- Giảm friction cho first-time users bằng cách cấp anonymous session
- Cho phép tích lũy dữ liệu tạm thời (cart, preferences, browsing history) dưới anonymous identity
- Khi user quyết định đăng ký/đăng nhập, merge dữ liệu anonymous vào authenticated account (session promotion)
- Bảo mật anonymous sessions để tránh abuse (rate limiting, TTL, fingerprinting)

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Cho phép users truy cập hệ thống với anonymous token mà không cần credentials
- [x] Objective 2: Quản lý lifecycle của anonymous sessions (creation, TTL, expiry, cleanup)
- [x] Objective 3: Hỗ trợ session promotion — merge anonymous data vào authenticated account khi login/register
- [x] Objective 4: Bảo mật anonymous sessions (rate limiting, abuse prevention, token rotation)
- [x] Objective 5: Seamless UX — user không nhận ra sự chuyển đổi từ anonymous → authenticated

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Anonymous token generation (JWT-based) | Social login integration |
| Anonymous session storage (Redis) | Multi-device session sync |
| Session promotion (anonymous → authenticated) | Third-party identity provider federation |
| Temporary data association with anonymous sessions | Complex recommendation engine |
| Rate limiting & abuse prevention for anonymous sessions | Payment processing with anonymous sessions |
| Anonymous session cleanup/expiry | Audit trail for anonymous actions (Phase 2) |
| API endpoints for anonymous auth flow | Admin dashboard for session monitoring |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords

- `anonymous authentication`
- `guest session management`
- `session promotion`
- `anonymous to authenticated migration`
- `guest checkout pattern`

### 3.2 Secondary Keywords

- `anonymous JWT token`
- `session upgrade flow`
- `cart merge anonymous login`
- `temporary session Redis`
- `anonymous user data transfer`
- `guest user token lifecycle`
- `lazy registration pattern`

### 3.3 Domain-Specific Terms

- `Session Promotion`: Quá trình chuyển đổi anonymous session thành authenticated session, merge dữ liệu tạm thời
- `Lazy Registration`: Pattern cho phép user sử dụng hệ thống trước, đăng ký sau
- `Anonymous Token`: JWT token cấp cho guest users, chứa session ID thay vì user ID
- `Data Merge Strategy`: Chiến lược merge dữ liệu khi anonymous session được promote (overwrite, union, conflict resolution)
- `Fingerprinting`: Kỹ thuật nhận diện thiết bị/browser để liên kết anonymous sessions
- `Token Elevation`: Nâng cấp quyền từ anonymous token lên authenticated token

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"anonymous authentication session promotion best practices"` | General | High |
| 2 | `"guest user session management Spring Boot Redis"` | Implementation | High |
| 3 | `"anonymous to authenticated session merge pattern"` | Architecture | High |
| 4 | `"anonymous JWT token generation lifecycle management"` | Implementation | High |
| 5 | `"guest checkout cart merge login e-commerce"` | Use Case | Medium |
| 6 | `"anonymous session security rate limiting abuse prevention"` | Security | Medium |
| 7 | `"lazy registration pattern enterprise IAM"` | Architecture | Medium |
| 8 | `"session promotion open source GitHub"` | Open Source | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| User Login (email/password) | `application.usecase.auth.LoginUseCase` | High | Hiện tại chỉ hỗ trợ authenticated login. Anonymous login sẽ extend flow này |
| User Registration | `application.usecase.auth.RegisterUseCase` | High | Session promotion sẽ trigger register flow + data merge |
| JWT Token Generation | `infrastructure.security.JwtTokenProvider` | High | Cần extend để generate anonymous tokens với claims khác (session ID thay vì user ID) |
| Security Config | `infrastructure.security.SecurityConfig` | High | Cần cấu hình thêm anonymous endpoints |
| User Entity | `domain.entity.User` | Medium | Anonymous users có thể không cần User entity (chỉ dùng Redis session) |

### 4.2 Existing Code Patterns

**Architecture**: Clean Architecture with 3 layers:
- `presentation.controller` — REST controllers with `@Valid` request DTOs
- `application.usecase.auth` — Use case classes as `@Service`
- `domain.entity` / `domain.repository` — JPA entities + Spring Data repositories
- `infrastructure.security` — JWT provider, Security config

**Auth Flow**: Stateless JWT-based authentication. No server-side session storage. Tokens contain user ID + roles in claims.

**Patterns in Use**:
- Use Case pattern (1 class per action: LoginUseCase, RegisterUseCase)
- DTO pattern (Request/Response data classes)
- Repository pattern (Spring Data JPA)
- Password encoding (BCrypt)
- JWT with HS512 signing

### 4.3 Tech Stack Constraints

- Language: Kotlin 1.9.25
- Framework: Spring Boot 3.4.1
- Database: PostgreSQL
- Security: Spring Security 6.x (stateless, JWT-based)
- JWT Library: jjwt 0.12.6 (io.jsonwebtoken)
- Build tool: Gradle (Kotlin DSL)
- Java: 21
- Cache: **Not yet configured** — Redis needs to be added as dependency

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `JwtTokenProvider` | Base Class | `infrastructure.security.JwtTokenProvider` | Extend to support anonymous token type with different claims structure |
| `SecurityConfig` | Config | `infrastructure.security.SecurityConfig` | Add anonymous endpoints to permitAll, add filter for anonymous token validation |
| `AuthController` | API | `presentation.controller.AuthController` | Add anonymous login endpoint |
| `UserRepository` | Repository | `domain.repository.UserRepository` | Session promotion needs to link anonymous session to user |
| `AuthResponse` | DTO | `application.dto.response.auth.AuthResponse` | May need to extend with session metadata |
| Redis (new) | Cache/Store | To be added | Primary storage for anonymous session data |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: How do enterprise IAM systems (Keycloak, Auth0, Firebase Auth) handle anonymous/guest users with temporary sessions?
- [x] Q2: What are best practices for session promotion (anonymous → authenticated) — conflict resolution strategies?
- [x] Q3: How to seamlessly transfer temporary data (cart, preferences) after login without data loss?
- [x] Q4: Anonymous token generation and lifecycle management patterns — how long should anonymous tokens live? Rotation strategy?
- [x] Q5: Security considerations for anonymous sessions — rate limiting, abuse prevention, fingerprinting?
- [x] Q6: Should anonymous users be stored in the database or only in Redis? What are the trade-offs?
- [x] Q7: How to handle multiple anonymous sessions for the same device/browser?
- [x] Q8: What happens to anonymous session data when it expires without promotion?

### 5.2 Assumptions cần verify

- [x] A1: Anonymous sessions sẽ dùng Redis làm primary storage (không cần PostgreSQL cho anonymous data)
- [x] A2: Anonymous token sẽ dùng JWT format tương tự authenticated token nhưng với role `ROLE_ANONYMOUS`
- [x] A3: Session promotion sẽ atomic — hoặc merge thành công hoàn toàn, hoặc rollback
- [x] A4: Anonymous sessions có TTL cố định (e.g., 24h-72h) và tự động cleanup

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đã tìm hiểu đầy đủ patterns, best practices, và security considerations | ≥ 5 sources |
| Open source options | Đánh giá các giải pháp open source có sẵn | ≥ 3 repos evaluated |
| Gap analysis | Xác định gaps giữa giải pháp hiện có và yêu cầu project | All critical gaps identified |
| Business analysis | Đặc tả use cases cho anonymous login flow | All UCs documented |
| Technical spec | Thiết kế kỹ thuật đủ chi tiết để implement | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)