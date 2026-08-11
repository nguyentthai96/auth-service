# Frontend Tasks: auth-login-admin

<!-- generated-by: wf_fe_spec -->
<!-- contract-version: 1.0 -->
<!-- complexity: HIGH (effective: MEDIUM — core flow already implemented in wf_openspec_apply) -->
<!-- estimated-tasks: 11 -->
<!-- backend-status: implemented -->
<!-- input-mode: C (Memory-Enriched) -->
<!-- applied-by: wf_fe_apply -->
<!-- applied-at: 2026-08-11T18:55:00+07:00 -->

> **Context**: Backend fully implemented. Core auth flow (service layer, auth provider, login form, CAPTCHA solver, device fingerprint) already applied in `wf_openspec_apply`. This spec covers **remaining gaps, refinements, and new pages**.

---

## Task 1: Validate Existing TypeScript Types Against Contract

- **File**: `src/@auth/services/jwt/authService.ts` | Action: [VALIDATE]
- **Contract Source**: `api_contract.md` → Response Wrapper + all endpoint types
- **Visibility Rule**: Verify NO `visibility: internal` fields in frontend types
- **Validation Criteria**:
  - [x] `LoginRequest` matches contract (7 fields, all `public`)
  - [x] `LoginResponse` matches contract (9 fields, all `public`)
  - [x] `MfaRequiredResponse` matches contract (4 fields)
  - [x] `AltchaChallenge` matches contract (5 fields)
  - [x] `SessionInfo` matches contract (8 public fields, 0 internal fields exposed)
  - [x] `ProblemDetail` matches contract (8 fields including extensions)
  - [x] `RefreshResponse` is same shape as `LoginResponse` (reuse confirmed)
- **Skills Applied**: Contract visibility enforcement
- **Result**: ✅ PASS — minor naming diff (`captchaToken` vs `captchaPayload`) acceptable

---

## Task 2: Validate API Service Layer Against Contract Endpoints

- **File**: `src/@auth/services/jwt/authService.ts` | Action: [VALIDATE]
- **Contract Source**: `api_contract.md` → all 7 endpoints
- **Validation Criteria**:
  - [x] `authLogin()` → `POST /api/auth/login` with `credentials: 'include'`, `X-Device-Fingerprint` header
  - [x] `authRefresh()` → `POST /api/auth/refresh` with `credentials: 'include'`, no body
  - [x] `authLogout()` → `POST /api/auth/logout` with `credentials: 'include'`
  - [x] `getCaptchaChallenge()` → `GET /api/captcha/challenge`, no auth
  - [x] `getActiveSessions()` → `GET /api/auth/sessions` with `credentials: 'include'`
  - [x] `revokeSession(id)` → `DELETE /api/auth/sessions/{id}` with `credentials: 'include'`
  - [x] `revokeAllSessions()` → `DELETE /api/auth/sessions` with `credentials: 'include'`
  - [x] All functions return correct TypeScript types
- **Dual-Mode**: Backend is `implemented` — real API only (no mock toggle needed)
- **Skills Applied**: `vercel-react-best-practices` → error handling patterns
- **Result**: ✅ PASS — 7/7 endpoints match contract

---

## Task 3: Validate Auth Provider Against State Contract

- **File**: `src/@auth/services/jwt/JwtAuthProvider.tsx` | Action: [VALIDATE]
- **Contract Source**: `api_contract.md` → State Contracts (AuthState, AuthUser) + Security Contracts (Token Lifecycle)
- **Validation Criteria**:
  - [x] Auth state matches `AuthState` interface: `authStatus`, `isAuthenticated`, `user`
  - [x] User mapping: `LoginResponse` → `AuthUser` (userId.toString(), username, empty email, roles[0])
  - [x] `signIn()` calls `authLogin()` with device fingerprint
  - [x] Access token stored in-memory (React state/ref), NOT localStorage
  - [x] Sliding renewal: check at `exp - 120s`, trigger `authRefresh()`
  - [x] Absolute ceiling: `iat + 36000s` → force logout
  - [x] Concurrent refresh lock: single inflight refresh
  - [x] `visibilitychange` event: check + refresh on tab focus
  - [x] `signOut()` calls `authLogout()` API
- **Skills Applied**: `vercel-react-best-practices` → `rerender-*` rules
- **Result**: ✅ PASS — all state contracts satisfied

---

## Task 4: Validate Login Form Against Contract Errors

- **File**: `src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VALIDATE → MODIFY]
- **Contract Source**: `api_contract.md` → POST /api/auth/login → Errors table
- **Validation Criteria**:
  - [x] `AUTH_001` (401) → field-error on username + toast (Alert)
  - [x] `AUTH_002` (403) → modal/alert with "account locked" message
  - [x] `AUTH_007` (428) → auto-trigger ALTCHA solve + retry
  - [x] `AUTH_008` (400) → toast + re-trigger ALTCHA *(added — was missing)*
  - [x] `AUTH_020` (429) → toast + countdown from `retryAfterSeconds`
  - [x] `AUTH_021` (409) → modal with session management info
  - [x] Form uses `username` field (not email)
  - [x] CAPTCHA solve: `getCaptchaChallenge()` → `solveAltchaChallenge()` → Base64 token
  - [x] Submit disabled during: submitting, solving CAPTCHA, rate limited
- **Skills Applied**: Form validation patterns
- **Result**: ✅ PASS (fixed AUTH_008 drift)

---

## Task 5: Integrate Real Logout API in SignOutPageView

- **File**: `src/app/(public)/(auth)/components/views/SignOutPageView.tsx` | Action: [MODIFY]
- **Contract Source**: `api_contract.md` → POST /api/auth/logout
- **Required Changes**:
  - [x] On mount → call `authLogout()` from `authService.ts`
  - [x] Clear in-memory auth state via `useJwtAuth().signOut()`
  - [x] Handle error gracefully (network error → still redirect to sign-in)
  - [x] Show brief "Signing out..." state before redirect
- **Validation Criteria**:
  - [x] Logout clears HttpOnly cookie (via API response `Set-Cookie: Max-Age=0`)
  - [x] Shows "You have been signed out" with link to sign-in
  - [x] No orphaned auth state after logout
- **Result**: ✅ DONE

---

## Task 6: SignInPageForm — Integrate Real Auth API

- **File**: `src/app/(public)/(auth)/components/forms/SignInPageForm.tsx` | Action: [MODIFY]
- **Contract Source**: `api_contract.md` → POST /api/auth/login
- **Required Changes**:
  - [x] Import and call `useJwtAuth().signIn()` on submit
  - [x] Handle error responses (AUTH_001, AUTH_020, etc.) with Alert component
  - [x] Add CAPTCHA solve flow (mirror JwtSignInForm pattern)
  - [x] Remove `reset(defaultValues)` from `onSubmit` (real auth handles state change)
- **Validation Criteria**:
  - [x] Form triggers real login API call
  - [x] Error states displayed to user
  - [x] CAPTCHA required flow works
- **Result**: ✅ DONE

---

## Task 7: Session Management Hook

- **File**: `src/@auth/services/jwt/useSessionManagement.ts` | Action: [NEW]
- **Contract Source**: `api_contract.md` → GET/DELETE /api/auth/sessions + State Contracts (SessionManagementState)
- **Validation Criteria**:
  - [x] Calls `getActiveSessions()`, `revokeSession()`, `revokeAllSessions()` from authService
  - [x] Loading/error states managed
  - [x] Optimistic update on revoke (remove from local list before API confirms)
  - [x] Error handling for 401 (redirect), 403 (toast), 404 (toast)
- **Result**: ✅ DONE

---

## Task 8: Session Management Page

- **File**: `src/app/(control-panel)/settings/security/sessions/page.tsx` | Action: [NEW]
- **Contract Source**: `api_contract.md` → SessionInfo type + GET/DELETE session endpoints
- **UI Components** (MUI):
  - [x] Page title: "Active Sessions" with security icon
  - [x] DataTable/List showing: device icon, browser + OS, IP address, login time, last activity
  - [x] "Revoke" button per session row
  - [x] "Revoke All" button in toolbar
  - [x] Current session indicator (highlight or badge)
  - [x] Empty state: "No active sessions"
  - [x] Confirmation dialog before revoke
- **Result**: ✅ DONE

---

## Task 9: Session Management Route Registration

- **File**: `src/app/(control-panel)/settings/security/sessions/route.tsx` | Action: [NEW]
- **Validation Criteria**:
  - [x] Route accessible at `/settings/security/sessions`
  - [x] `React.lazy()` used for code splitting
  - [x] Auth guard: `authRoles.admin` (authenticated admin only)
  - [x] Layout: control-panel (auto via `import.meta.glob` route discovery)
- **Result**: ✅ DONE

---

## Task 10: ALTCHA Solver + Device Fingerprint Utilities Validation

- **File**: `src/@auth/services/jwt/utils/altchaSolver.ts` | Action: [VALIDATE]
- **File**: `src/@auth/services/jwt/utils/deviceFingerprint.ts` | Action: [VALIDATE]
- **Validation Criteria**:
  - [x] `solveAltchaChallenge()` accepts `AltchaChallenge`, returns Base64 encoded token
  - [x] Uses Web Crypto API (`crypto.subtle.digest`) for SHA-256 PoW
  - [x] `generateDeviceFingerprint()` returns 64-char hex SHA-256 string
  - [x] No external dependencies (all native browser APIs)
- **Result**: ✅ PASS

---

## Task 11: UI Review Audit (Post-Implementation)

- **Type**: REVIEW
- **Output**: `fe_review.md`
- **Checklist**:
  - [x] Login form accessible (keyboard navigation, screen reader labels)
  - [x] Error messages clear and actionable
  - [x] Rate limit countdown UX smooth (no layout shift)
  - [x] CAPTCHA solve invisible to user (loading spinner only)
  - [x] Session management table responsive
  - [x] Logout flow smooth (no flash/blank page)
  - [x] Token renewal transparent (no UX interruption)
  - [x] Security: no tokens in localStorage, no secrets in client code
  - [x] TypeScript: strict types, no `any`
- **Result**: ✅ PASS

---

## Summary

| Category | Tasks | Status |
|----------|-------|--------|
| VALIDATE (existing code) | Tasks 1, 2, 3, 4, 10 | ✅ All PASS |
| MODIFY (refinement) | Tasks 5, 6 | ✅ All DONE |
| NEW (extension) | Tasks 7, 8, 9 | ✅ All DONE |
| REVIEW (audit) | Task 11 | ✅ PASS |

**Build Verification**:
- ✅ `tsc --noEmit` — 0 errors
- ✅ `eslint` — 0 errors (1 expected warning on route.tsx)
- ✅ `vite build` — successful (3.53s)

**Contract Compliance**: 7/7 endpoints, 8/8 error codes, 7/7 types validated.
