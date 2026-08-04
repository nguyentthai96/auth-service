# API Specification: Auth RBAC+PBAC Service

## Base URL
```
http://localhost:8081/api
```

## Authentication
All endpoints except `/api/auth/*` require Bearer JWT token:
```
Authorization: Bearer <access_token>
```

---

## 1. Auth APIs

### POST /api/auth/register
**Request:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123",
  "fullName": "John Doe",
  "phone": "+84912345678",
  "domainCode": "booking"
}
```
**Response (201):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "userId": "uuid",
  "username": "john_doe",
  "activeDomain": "booking",
  "roles": ["VIEWER"],
  "permissions": ["bookings:READ", "rooms:READ"]
}
```

### POST /api/auth/login
```json
{
  "username": "john_doe",
  "password": "SecurePass123",
  "domainCode": "booking"
}
```

### POST /api/auth/refresh
```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

### POST /api/auth/switch-domain
```json
{ "domainCode": "rental" }
```

---

## 2. Domain APIs

### POST /api/domains
```json
{
  "code": "booking",
  "name": "Hotel Booking",
  "description": "Hotel booking management"
}
```
**Response (201):** Auto-creates DOMAIN_ADMIN + VIEWER roles.

### GET /api/domains
**Response (200):**
```json
[
  { "id": "uuid", "code": "booking", "name": "Hotel Booking", "description": "...", "status": "ACTIVE" }
]
```

---

## 3. Role APIs

### POST /api/domains/{domainId}/roles
```json
{
  "code": "RECEPTIONIST",
  "name": "Receptionist",
  "description": "Front desk staff",
  "hierarchyLevel": 1
}
```

---

## 4. Group APIs

### POST /api/domains/{domainId}/groups
```json
{ "name": "Hotel-A Staff", "description": "Staff for Hotel A" }
```

### POST /api/domains/{domainId}/groups/{groupId}/roles
```json
{ "roleId": "uuid-of-receptionist-role" }
```

### POST /api/domains/{domainId}/groups/{groupId}/users
```json
{ "userId": "uuid-of-user" }
```

---

## 5. Resource APIs

### POST /api/domains/{domainId}/resources
```json
{ "code": "bookings", "name": "Bookings", "description": "Booking management" }
```

---

## 6. Permission APIs

### POST /api/domains/{domainId}/roles/{roleId}/permissions
```json
{ "resourceCode": "bookings", "actionCode": "READ" }
```

### POST /api/domains/{domainId}/roles/{roleId}/permissions/bulk
```json
{ "resourceCode": "bookings", "actionCodes": ["READ", "CREATE", "UPDATE"] }
```

---

## 7. Permission Check APIs

### POST /api/permissions/check
```json
{
  "userId": "uuid",
  "domainId": "uuid",
  "resourceCode": "bookings",
  "actionCode": "UPDATE"
}
```
**Response (200):**
```json
{
  "allowed": true,
  "userId": "uuid",
  "resource": "bookings",
  "action": "UPDATE"
}
```

### POST /api/permissions/check-batch
```json
{
  "userId": "uuid",
  "domainId": "uuid",
  "checks": [
    { "resourceCode": "bookings", "actionCode": "READ" },
    { "resourceCode": "payments", "actionCode": "DELETE" }
  ]
}
```

---

## 8. Policy APIs

### POST /api/domains/{domainId}/policies
```json
{
  "name": "Owner-Only Edit",
  "description": "Users can only edit their own resources",
  "resourceId": "uuid-of-bookings-resource",
  "actionId": "uuid-of-update-action",
  "effect": "ALLOW",
  "priority": 10,
  "conditions": [
    {
      "attributePath": "$user.id",
      "operator": "eq",
      "value": "\"$context.resource_owner_id\"",
      "valueType": "CONTEXT"
    }
  ]
}
```

### PUT /api/domains/{domainId}/policies/{id}/activate
No body required. Validates conditions exist before activation.

### PUT /api/domains/{domainId}/policies/{id}/deactivate
No body required.

---

## Error Responses (RFC 7807)
```json
{
  "type": "https://auth-service/errors/invalid_credentials",
  "title": "INVALID_CREDENTIALS",
  "status": 401,
  "detail": "Invalid username or password",
  "errorCode": "INVALID_CREDENTIALS"
}
```

| Error Code | HTTP Status | Description |
|:---|:---:|:---|
| INVALID_CREDENTIALS | 401 | Wrong username/password |
| TOKEN_EXPIRED | 401 | JWT expired |
| PERMISSION_DENIED | 403 | No permission for action |
| WRITE_NOT_ALLOWED | 403 | Read-only user tried to write |
| ACCOUNT_LOCKED | 403 | Too many failed login attempts |
| POLICY_EVALUATION_FAILED | 403 | PBAC evaluation timeout/error |
| *_NOT_FOUND | 404 | Resource not found |
| *_DUPLICATE | 409 | Duplicate resource |
| VALIDATION_ERROR | 400 | Input validation failed |
