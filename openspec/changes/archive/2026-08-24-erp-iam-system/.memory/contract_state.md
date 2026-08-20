---
# Contract State — Auto-managed by wf_api_contract
# User-editable: gate_rules section
# DO NOT manually edit other sections unless debugging
contract_version: "1.0"
backend_status: drift
frontend_status: pending
last_generated: "2026-08-20T10:30:00+07:00"
last_validated: "2026-08-20T10:42:00+07:00"
endpoints_count: 73
drift_detected: true
drift_log:
  # ========== auth-service ==========
  - endpoint: "POST /api/auth/login"
    issue: "Contract field 'deviceFingerprint' → Backend field 'trustedDeviceHash'"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to match backend naming"
  - endpoint: "POST /api/auth/login"
    issue: "Contract 'LoginResponse.user: UserBrief' → Backend 'AuthResponse' flat structure (userId, username directly)"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Contract has nested UserBrief, backend returns flat AuthResponse. Contract should match backend."
  - endpoint: "POST /api/auth/login"
    issue: "Backend AuthResponse has 'permissions: List<String>', 'promotedFromAnonymous', 'dataTransferred' not in contract"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Add missing backend fields to contract"
  - endpoint: "POST /api/auth/change-password"
    issue: "Contract field 'currentPassword' → Backend field 'oldPassword'"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to match backend naming"
  - endpoint: "POST /api/auth/forgot-password"
    issue: "Contract has 'domainCode' field → Backend 'ForgotPasswordRequestDto' has only 'email'"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Remove domainCode from contract"
  - endpoint: "POST /api/auth/verify-2fa"
    issue: "Contract path '/api/auth/verify-2fa' → Backend path '/api/auth/mfa/verify'"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract path to /api/auth/mfa/verify"
  - endpoint: "POST /api/auth/verify-2fa"
    issue: "Contract field 'partialToken' + 'method' → Backend field 'mfaToken' + 'trustDevice' + 'deviceHash'"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract request interface to match MfaVerifyRequest"
  - endpoint: "POST /api/auth/request-otp"
    issue: "Contract path '/api/auth/request-otp' → Backend path '/api/auth/mfa/resend'"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract path to /api/auth/mfa/resend"
  - endpoint: "POST /api/auth/request-otp"
    issue: "Contract fields 'partialToken, channel' → Backend field 'mfaToken' only"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to match MfaResendRequest"
  - endpoint: "POST /api/mfa/enable"
    issue: "Contract path '/api/mfa/enable' → Backend uses PUT /api/auth/mfa/settings (combined enable/disable)"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Merge enable/disable into PUT /api/auth/mfa/settings"
  - endpoint: "GET /api/mfa/status"
    issue: "Contract endpoint not found in backend"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — keep in contract as 'planned'"
  - endpoint: "POST /api/mfa/disable"
    issue: "Contract separate endpoint → Backend combined with PUT /api/auth/mfa/settings"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Merge with settings endpoint"
  - endpoint: "GET /api/mfa/recovery-codes"
    issue: "Endpoint not found in backend"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — keep in contract as 'planned'"
  # ========== SSO ==========
  - endpoint: "GET /api/sso/providers"
    issue: "Contract path '/api/sso/providers' → Backend path '/api/auth/sso/providers'"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to /api/auth/sso/providers"
  - endpoint: "GET /api/sso/providers"
    issue: "Contract SsoProviderItem has 'code, providerType, logoUrl' → Backend SsoProviderInfoDto has 'id, name, enabled'"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to match backend DTO"
  - endpoint: "GET /api/sso/{provider}/authorize"
    issue: "Endpoint not found in backend"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — keep as 'planned'"
  - endpoint: "POST /api/sso/{provider}/callback"
    issue: "Contract path parameterized → Backend POST /api/auth/sso/callback with provider in body"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to match backend. Provider is request body field, not path param."
  - endpoint: "POST /api/sso/{provider}/callback"
    issue: "Contract field 'state' → Backend has 'redirectUri' instead"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Add redirectUri, review state field"
  - endpoint: "POST /api/sso/link"
    issue: "Contract path '/api/sso/link' → Backend path '/api/auth/sso/link'"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract path"
  - endpoint: "POST /api/sso/link"
    issue: "Contract fields 'providerCode, authorizationCode' → Backend 'provider, code, redirectUri'"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to match SsoLinkRequest"
  - endpoint: "DELETE /api/sso/link/{providerId}"
    issue: "Contract path → Backend DELETE /api/auth/sso/unlink/{provider}"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract path and param name"
  # ========== auth-service sessions ==========
  - endpoint: "POST /api/auth/logout"
    issue: "Contract endpoint → Backend uses DELETE /api/auth/sessions (via SessionController)"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Verify if logout exists separately or is session revocation"
  # ========== account-service ==========
  - endpoint: "POST /api/account/deletion-request"
    issue: "Contract path → Backend path '/api/account/delete-request'"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to /api/account/delete-request"
  - endpoint: "GET /api/account/sessions"
    issue: "Contract path → Backend path '/api/account/sessions/active'"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract path"
  - endpoint: "GET /api/account/sessions"
    issue: "Contract SessionItem has 'deviceName, isCurrent' → Backend ActiveSessionResponse has different structure"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Check SessionDtos.kt for exact fields"
  # ========== system-admin-service ==========
  - endpoint: "POST /api/admin/partners"
    issue: "Contract path '/api/admin/partners' → Backend path '/api/admin/api-partners'"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to /api/admin/api-partners"
  - endpoint: "POST /api/admin/partners"
    issue: "Contract CreatePartnerRequest has 'subscriptionPlanId: string' → Backend has 'domainId: Long, subscriptionPlanId: Long?'"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract types and add domainId"
  - endpoint: "POST /api/admin/partners/{id}/api-keys"
    issue: "Contract path has partnerId in URL → Backend POST /api/admin/api-keys with partnerId in body"
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract — partnerId is body field, not path param"
  - endpoint: "POST /api/admin/partners/{id}/api-keys"
    issue: "Contract GenerateApiKeyRequest has 'expiresAt: ISO 8601' → Backend has 'expiresInDays: Long?'"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract to expiresInDays"
  - endpoint: "POST /api/admin/api-keys/{id}/rotate"
    issue: "Contract POST → Backend PUT"
    severity: LOW
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Update contract method to PUT"
  - endpoint: "Menu CreateMenuRequest"
    issue: "Contract field 'parentId: string?' → Backend has 'parentId: Long?, domainId: Long (required)'"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Add domainId to contract, fix type Long"
  - endpoint: "Menu UserMenuOverrideRequest"
    issue: "Contract has array of overrides → Backend accepts single override"
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Contract has 'overrides: MenuOverride[]' → Backend has flat single item"
  # ========== NOT YET IMPLEMENTED (planned) ==========
  - endpoint: "GET /api/account/profile"
    issue: "No ProfileController found in account-service. Only ProfileKafkaListener exists."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — contract is spec for future. Mark 'planned'."
  - endpoint: "GET /api/account/devices"
    issue: "No DeviceController found in account-service."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — contract is spec for future. Mark 'planned'."
  - endpoint: "GET /api/account/login-history"
    issue: "No LoginHistoryController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/account/preferences"
    issue: "No PreferencesController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/departments/tree"
    issue: "No DepartmentController found in system-admin-service."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/positions"
    issue: "No PositionController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/workflows"
    issue: "No WorkflowController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/configs"
    issue: "No SystemConfigController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/feature-flags"
    issue: "No FeatureFlagController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/audit-logs"
    issue: "No AuditLogController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "GET /api/admin/users"
    issue: "No AdminUserController found."
    severity: HIGH
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Not yet implemented — mark 'planned'."
  - endpoint: "POST /api/permissions/check"
    issue: "No PermissionCheckController found. RbacEngine exists but internal service."
    severity: MEDIUM
    detected_at: "2026-08-20T10:42:00+07:00"
    resolved: false
    resolution: "Internal API — may not need dedicated controller"
consumers:
  - platform: web
    workflow: wf_fe_spec
    status: ready_with_caveats
gate_rules:
  frontend_active: true
  auto_generate: true
  require_validation: true
---
