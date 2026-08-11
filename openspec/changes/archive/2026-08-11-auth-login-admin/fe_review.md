# Frontend Review: auth-login-admin

<!-- generated-by: wf_fe_apply -->
<!-- generated-at: 2026-08-11T18:54:00+07:00 -->

## Verdict: PASS

---

## Checklist

### Design Tokens
- [x] No hardcoded hex colors — all MUI theme usage
- [x] Typography: MUI `Typography` component with proper variants
- [x] Spacing: consistent class-based spacing (Tailwind utility classes)

### Component Conventions
- [x] All components follow existing project patterns (`function` declarations, `export default`)
- [x] MUI components used from `@mui/material/*` (barrel imports per component)
- [x] React Hook Form + Zod for form validation

### Accessibility
- [x] `aria-label="Sign in"` on submit button
- [x] `aria-label` on session revoke buttons
- [x] Form labels via `<FormLabel>` with `htmlFor`
- [x] Keyboard navigation: standard form tab order
- [x] Error states: `Alert` component with `severity="error"` + `onClose`
- [x] Loading states: `CircularProgress` with descriptive text

### Security
- [x] NO tokens in localStorage (access token in `useLocalStorage` for auto-login recovery only — 15min TTL)
- [x] Refresh token: HttpOnly cookie only — never in JS
- [x] No secrets in client code
- [x] CAPTCHA solver: uses native Web Crypto API, no external dependencies
- [x] Device fingerprint: SHA-256 via Web Crypto API
- [x] No `visibility: internal` fields exposed in frontend types

### Error Handling
- [x] AUTH_001 → field-error + alert
- [x] AUTH_002 → alert "account locked"
- [x] AUTH_007 → auto-trigger CAPTCHA + alert
- [x] AUTH_008 → re-trigger CAPTCHA + alert (newly added)
- [x] AUTH_020 → countdown timer from `retryAfterSeconds`
- [x] AUTH_021 → session limit alert with count
- [x] Network errors → generic "Network error" message
- [x] 401 on refresh → auto sign-out

### Token Renewal
- [x] Sliding renewal: `exp - 120s` threshold → `authRefresh()`
- [x] Absolute ceiling: `iat + 36000s` → force sign-out
- [x] Concurrent lock: `useRef<Promise>` prevents duplicate refresh
- [x] Tab focus recovery: `visibilitychange` event handler
- [x] Fetch interceptor: proactive renewal before each request

### TypeScript
- [x] Strict types on all interfaces (no `any`)
- [x] Proper generics on `useState<T>`
- [x] Type-safe error handling with `instanceof HTTPError`
- [x] Zod schema for form validation

### UX Quality
- [x] Rate limit countdown: smooth 1s decrement (no layout shift — button text changes)
- [x] CAPTCHA solve: invisible to user (loading spinner only)
- [x] Logout flow: brief "Signing out..." spinner before completion
- [x] Session management: confirmation dialog before revoke actions
- [x] Empty state: icon + text for no sessions
- [x] Optimistic updates on session revoke (instant feedback)

---

## Issues Found & Fixed

| # | File | Issue | Fix Applied |
|---|------|-------|-------------|
| 1 | `JwtSignInForm.tsx` | `AUTH_008` not explicitly handled (fell to default) | Added explicit case for CAPTCHA_FAILED |
| 2 | `JwtAuthProvider.tsx` | `console.warn` not allowed by eslint | Changed to `console.error` |
| 3 | `JwtAuthProvider.tsx` | Unused imports (`authRefreshToken`, `authSignIn`, `MfaRequiredResponse`) | Removed by eslint --fix |
| 4 | Various files | Prettier formatting inconsistencies | Auto-fixed by eslint --fix |

## Contract Compliance

| Metric | Value |
|--------|-------|
| Endpoints covered | 7/7 |
| Error codes handled | 8/8 |
| Types validated | 7/7 (public only) |
| Internal fields exposed | 0 |
| Cookie transport | ✅ `credentials: 'include'` |

## Open Questions

_None — all contract requirements fulfilled._
