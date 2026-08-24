# Business Analysis: Core Microservices Features

## 1. Auth Service (`auth-service`)
### Tính năng cần thiết:
1. **Đăng nhập (Login)**: Hỗ trợ username/password, OTP, hoặc OAuth2 Social Login. Sinh JWT.
2. **Quản lý Token (Token Management)**: Refresh token, Revoke token (Logout).
3. **Quản lý Credentials**: Đổi mật khẩu, Quên mật khẩu, Khóa tài khoản do brute-force.
4. **MFA (Multi-Factor Auth)**: Hỗ trợ TOTP hoặc SMS OTP.

### UC-AUTH-01: Đăng nhập
- **Basic Flow**: User gửi credentials -> Auth kiểm tra hash -> Sinh Access Token & Refresh Token -> Trả về client.
- **Exception Flow**: Sai mật khẩu 5 lần -> Cập nhật trạng thái `locked` -> Thông báo tài khoản bị khóa.

## 2. Account Service (`account-service`)
### Tính năng cần thiết:
1. **Quản lý Hồ Sơ (Profile Management)**: Xem/Sửa thông tin cá nhân (Avatar, tên, ngày sinh).
2. **Định danh (KYC)**: Flow upload giấy tờ tùy thân, duyệt KYC.
3. **Quản lý thiết bị (Device Management)**: Xem lịch sử đăng nhập, các thiết bị đang active, remote logout.
4. **Cài đặt người dùng**: Tùy chọn thông báo, ngôn ngữ, timezone.

### UC-ACC-01: Quản lý thiết bị đăng nhập
- **Basic Flow**: Lấy danh sách device_id đã login -> User chọn "Đăng xuất thiết bị X" -> Call tới auth-service để revoke token của device đó -> Update status device.

## 3. System Admin Service (`system-admin-service`)
### Tính năng cần thiết:
1. **Quản lý phân quyền (RBAC)**: Tạo/Sửa/Xóa Role. Gán Permission cho Role. Gán Role cho User (Admin).
2. **User Support (Quản lý người dùng)**: Xem danh sách user, block/unblock, force password reset.
3. **Audit Log**: Ghi log mọi hành động nhạy cảm của Admin (Ai làm gì, lúc nào).
4. **System Configuration**: Quản lý các tham số hệ thống (Feature flags, maintenance mode).

### UC-ADM-01: Gán quyền cho người dùng
- **Basic Flow**: Admin chọn User -> Chọn Role (vd: SUPPORT_STAFF) -> Lưu vào bảng `user_roles`. Khi user login, auth-service call API hoặc đọc DB chung để lấy Roles đưa vào JWT claims.
