# Specs: erp-iam-system

## API Specifications

### auth-service
- `POST /api/auth/login`: Authenticate and return partial token (if MFA enabled) or full token.
- `POST /api/auth/verify-2fa`: Submit OTP/TOTP.
- `POST /api/auth/introspect`: Validate token and check active session.

### account-service
- `GET /api/account/profile`: Retrieve user profile.
- `PUT /api/account/profile`: Update user profile.
- `GET /api/account/devices`: List active devices.
- `DELETE /api/account/devices/{id}`: Remote logout device.

### system-admin-service
- `GET /api/admin/menus/tree`: Full menu management tree.
- `GET /api/admin/menus/user-tree`: Accessible menu for current user.
- `POST /api/admin/api-partners`: Register new partner.
- `POST /api/admin/api-keys`: Generate API key.
- `GET /api/admin/workflows/instances`: List pending approvals.

## Events (Kafka)
- `Topic: iam.user.created`
- `Topic: iam.session.revoked`
- `Topic: iam.menu.permissions.changed`
