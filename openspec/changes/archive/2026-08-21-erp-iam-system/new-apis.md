# New APIs: erp-iam-system

_Generated: 2026-08-24_

## account-service APIs

| Method | Path | Purpose | FR |
|--------|------|---------|-----|
| GET | `/api/account/profile` | Get current user profile | FR-005 |
| PUT | `/api/account/profile` | Update user profile | FR-005 |
| GET | `/api/account/devices` | List user devices | FR-007 |
| POST | `/api/account/devices/{id}/trust` | Trust a device (skip MFA 30d) | FR-007 |
| DELETE | `/api/account/devices/{id}` | Remove/revoke a device | FR-007 |
| GET | `/api/account/preferences` | Get all user preferences | FR-006 |
| GET | `/api/account/preferences/{category}` | Get preferences by category | FR-006 |
| PATCH | `/api/account/preferences/{category}` | Merge-update preferences | FR-006 |

## system-admin-service APIs

| Method | Path | Purpose | FR |
|--------|------|---------|-----|
| GET | `/api/admin/departments/tree` | Get department tree | FR-011 |
| POST | `/api/admin/departments` | Create department | FR-011 |
| PUT | `/api/admin/departments/{id}` | Update department | FR-011 |
| DELETE | `/api/admin/departments/{id}` | Delete department | FR-011 |
| PUT | `/api/admin/departments/{id}/move` | Move department in tree | FR-011 |
| POST | `/api/admin/positions` | Create position | FR-011 |
| PUT | `/api/admin/positions/{id}` | Update position | FR-011 |
| DELETE | `/api/admin/positions/{id}` | Delete position | FR-011 |
| POST | `/api/admin/positions/{id}/assign` | Assign user to position | FR-011 |
| GET | `/api/admin/audit-logs` | Search audit logs | FR-015 |
| GET | `/api/admin/audit-logs/export` | Export audit logs | FR-015 |
| GET | `/api/admin/api-usage` | API usage dashboard | FR-012 |
| PUT | `/api/admin/api-partners/{id}/ip-whitelist` | Update IP whitelist | FR-012 |
| GET | `/api/admin/feature-flags` | List feature flags | FR-014 |
| PUT | `/api/admin/feature-flags/{key}` | Toggle feature flag | FR-014 |
| POST | `/api/admin/workflows` | Create workflow definition | FR-013 |
| POST | `/api/admin/workflows/{id}/submit` | Submit for approval | FR-013 |
| POST | `/api/admin/workflows/actions/{id}/approve` | Approve step | FR-013 |
| POST | `/api/admin/workflows/actions/{id}/reject` | Reject step | FR-013 |

## auth-service APIs (New)

| Method | Path | Purpose | FR |
|--------|------|---------|-----|
| GET | `/api/auth/mfa/recovery-codes` | Get MFA recovery codes | FR-001 |
| POST | `/api/auth/mfa/recovery-codes/regenerate` | Regenerate recovery codes | FR-001 |
| POST | `/api/internal/service-token` | Issue service JWT | FR-021 |
| GET | `/api/internal/users/{id}/roles` | Get user roles (internal) | FR-021 |

## Kafka Topics (New)

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `iam.user.registered` | auth-service | account-service | Auto-create profile |
| `iam.user.sso_provisioned` | auth-service | account-service | Auto-create SSO profile |
| `iam.permission.changed` | system-admin-service | auth-service | Cache invalidation |
| `iam.account.deactivated` | auth-service | account-service | Profile deactivation |
| `iam.account.deleted` | auth-service | account-service | PII data purge |
| `iam.audit.event` | auth-service | system-admin-service | Audit trail persistence |
