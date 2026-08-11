## Purpose

Tự động gia hạn access token khi user đang active — transparent UX, không cần user re-login trong phiên làm việc liên tục, với absolute ceiling để đảm bảo security.

## ADDED Requirements

### Requirement: Activity-Based Token Refresh
Frontend MUST tự động refresh access token khi user đang active và token sắp hết hạn.

#### Scenario: Token approaching expiry during active use
- **WHEN** access token còn < 2 phút trước khi hết hạn VÀ user đang active
- **THEN** frontend tự động gửi refresh request trong background
- **THEN** user không bị gián đoạn, không thấy loading state

#### Scenario: Concurrent refresh prevention
- **WHEN** nhiều API calls cùng trigger refresh cùng lúc
- **THEN** chỉ 1 refresh request được gửi, các calls khác chờ kết quả

---

### Requirement: Tab Focus Recovery
Frontend MUST check và refresh token khi tab được focus lại.

#### Scenario: Tab refocus with expired token
- **WHEN** user quay lại tab sau thời gian dài (token đã expired)
- **THEN** frontend trigger refresh ngay lập tức
- **THEN** nếu refresh thành công → continue; nếu thất bại → redirect login

#### Scenario: Tab refocus with valid token
- **WHEN** user quay lại tab và token vẫn valid (> 2 phút remaining)
- **THEN** không trigger refresh

---

### Requirement: Absolute Session Ceiling
Hệ thống MUST enforce thời gian phiên tối đa bất kể activity.

#### Scenario: Ceiling reached
- **WHEN** thời gian từ lúc login (JWT `iat`) vượt quá 10 giờ
- **THEN** frontend force logout, redirect tới login page
- **THEN** hiển thị message "Phiên đăng nhập đã hết hạn"

#### Scenario: Refresh after ceiling
- **WHEN** client gửi refresh request nhưng original login > 10 giờ trước
- **THEN** backend reject refresh, trả HTTP 401

---

### Requirement: Token Storage Model
Access token MUST được lưu in-memory, refresh token MUST được truyền qua HttpOnly cookie.

#### Scenario: Page refresh
- **WHEN** user reload page (F5)
- **THEN** access token mất (in-memory), frontend trigger silent refresh từ cookie
- **THEN** UI hiển thị loading skeleton trong thời gian refresh (< 200ms P95)

#### Scenario: New tab
- **WHEN** user mở tab mới truy cập dashboard
- **THEN** cookie tự động gửi kèm refresh request, nhận access token mới
