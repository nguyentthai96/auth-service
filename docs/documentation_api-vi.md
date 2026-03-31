# Authentication/Authorization RBAC & ABAC/PBAC APIs

### 1. Authentication & Authorization APIs

#### 1.1 Đăng nhập & Cấp JWT

- Endpoint: POST /api/auth/login
- Request Body:

```json
{
  "username": "admin",
  "password": "123456"
}
```

- Response: (HTTP 200 OK hoặc 401 Unauthorized)

```json
{
  "status": true,
  "code": "00",
  "msgCode": "AUTH_SUCCESS",
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "dGVzdC1yZWZyZXNo...",
    "expiresAt": "2025-03-03T12:00:00Z"
  }
}
```

#### 1.2 Refresh Token

- Endpoint: POST /api/auth/refresh
- Request Body:

```json
{
  "refreshToken": "dGVzdC1yZWZyZXNo..."
}
```

- Response: (HTTP 200 OK hoặc 401 Unauthorized)

```json
{
  "status": true,
  "code": "00",
  "msgCode": "TOKEN_REFRESHED",
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "new-access-token-xxx...",
    "expiresAt": "2025-03-03T12:30:00Z"
  }
}
```

#### 1.3 Register user account

- Endpoint: POST /api/auth/register
- Request Body: Thông tin đăng ký (username, password, email, ...)

```json
{
  "username": "david",
  "password": "1234#$Abc",
  "email": "1234@bbc"
}
```

- Response: Thông báo đăng ký thành công và hướng dẫn xác thực email.

```json
{
  "status": true,
  "code": "00",
  "msgCode": "TOKEN_REFRESHED",
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "newpackage com.example.authservice.domain
    
    data class User(
        val id: Long,
        val username: String,
        val password: String,
        val email: String,
        val roles: List<String>
    )-access-token-xxx...",
    "expiresAt": "2025-03-03T12:30:00Z"
  }
}
```

#### 1.4 Xác thực Email:

- Endpoint: GET /api/auth/verify-email?token=...
- Response:  (HTTP 200 OK or 400 Bad Request) Xác thực email.

```json
{
  "status": true,
  "code": "00",
  "msgCode": "EMAIL_VERIFIED",
  "message": "Email verified successfully"
}
```

#### 1.5 Đăng xuất & Thu hồi Token:

- Endpoint POST /api/auth/logout
- Request Body:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

- Request Body: Thông tin token cần thu hồi.

```json
{
  "status": true,
  "code": "00",
  "msgCode": "LOGOUT_SUCCESS",
  "message": "Logout successful"
}
```

#### 1.6 Quên mật khẩu & Reset mật khẩu:

- Endpoint: POST /api/auth/forgot-password
- Request Body:

```json
{
  "email": "user@example.com"
}
```

- Response: (HTTP 200 OK)

```json
{
  "status": true,
  "code": "00",
  "msgCode": "RESET_LINK_SENT",
  "message": "Password reset link sent to your email"
}
```

#### 1.7 Reset Password

- Endpoint: POST /api/auth/reset-password
- Request Body:

```json
{
  "token": "reset-token",
  "newPassword": "newPassword123"
}
```

- Response: (HTTP 200 OK or 400 Bad Request)

```json
{
  "status": true,
  "code": "00",
  "msgCode": "PASSWORD_RESET_SUCCESS",
  "message": "Password reset successfully"
}
```

-If the token is invalid or expired:

```json
{
  "status": false,
  "code": "01",
  "msgCode": "INVALID_TOKEN",
  "message": "The reset token is invalid or has expired"
}
```