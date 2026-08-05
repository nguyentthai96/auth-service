# SRS: erp-iam-system

## 1. System Context
- **Feature Name**: ERP IAM System (auth-service, account-service, system-admin-service)
- **Domain**: IAM, System Administration
- **Flow**: Non-Financial

## 2. Functional Requirements
- **FR-001**: Quản lý Multi-Factor Authentication (OTP, TOTP, CAPTCHA).
- **FR-002**: Hỗ trợ SSO và OAuth2 (Keycloak integration).
- **FR-003**: Token Introspection và JWT RS256.
- **FR-004**: Cấu hình Password Policy.
- **FR-005**: Quản lý User Profile.
- **FR-006**: Quản lý Preferences & Settings.
- **FR-007**: Quản lý Thiết bị (Device Management).
- **FR-008**: Quản lý Phiên Đăng Nhập (Session Management).
- **FR-009**: Account Lifecycle (Deactivation, GDPR Delete).
- **FR-010**: Cấu hình Menu Động (Dynamic Menu & RBAC/PBAC).
- **FR-011**: Cơ Cấu Tổ Chức (Department, Position).
- **FR-012**: API Partner Management (API Key, Rate Limiting).
- **FR-013**: Dynamic Approval Workflow.
- **FR-014**: System Config & Feature Flags.
- **FR-015**: Immutable Audit Trail.
- **FR-016**: Domain/Tenant Configuration.

## 3. Data Entities
- **auth-service**: mfa_configs, otp_tokens, recovery_codes, sso_providers, user_sso_links, password_policies
- **account-service**: user_profiles, user_contacts, login_history, user_preferences, notification_settings, user_devices, active_sessions
- **system-admin-service**: menu_items, menu_permissions, role_menu_permissions, user_menu_overrides, departments, positions, user_positions, api_partners, api_keys, subscription_plans, api_usage_logs, workflow_definitions, system_configs, feature_flags, audit_logs

## 4. Dependencies
- Redis (Rate Limiting, Menu Cache)
- Kafka (Event Streaming)
- Keycloak (Optional Downstream SSO)
