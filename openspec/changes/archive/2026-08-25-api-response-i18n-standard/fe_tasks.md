## Frontend Tasks: api-response-i18n-standard

<!-- generated-by: wf_openspec -->
<!-- contract-version: 3.0 -->
<!-- complexity: LOW -->
<!-- estimated-tasks: 6 -->
<!-- mode: C (Memory-Enriched) -->
<!-- backend-status: partially-implemented (7 action endpoints pending) -->
<!-- cross-cutting: true -->
<!-- updated: 2026-08-25 — re-evaluated against current implementation state -->

> **Note**: This is a cross-cutting i18n protocol change — no new pages/endpoints.
> Several files were already modified during previous `wf_openspec_apply` runs.
> These tasks cover remaining gaps and verification of already-implemented changes.

---

### Task 1: Add VN Flag Asset

- **File**: `public/assets/images/flags/VN.svg` | Action: [NEW]
- **Contract Source**: `api_contract.md` → Frontend Integration Guide → LanguageType
- **Reason**: `LanguageSwitcher.tsx` renders `<img src="/assets/images/flags/${language.flag}.svg">`.
  Currently `US.svg` exists but `VN.svg` is **missing** → broken image for Vietnamese.
- **Details**:
  - Create a simple Vietnamese flag SVG (red background, yellow star)
  - File must be at `public/assets/images/flags/VN.svg`
  - Dimensions: viewBox `0 0 900 600` (standard 3:2 flag ratio)
- **Validation**: `LanguageSwitcher` renders VN flag without broken image icon

---

### Task 2: Cleanup LanguageSwitcher — Remove Dead Links + Old Flags

- **File**: `src/components/theme-layouts/components/LanguageSwitcher.tsx` | Action: [MODIFY]
- **Contract Source**: `api_contract.md` → Supported Locales (en, vi only)
- **Reason**: LanguageSwitcher has a "Learn More" link pointing to `/documentation/configuration/multi-language` which doesn't exist. Also may reference old `TR.svg` and `SA.svg` flag assets from boilerplate defaults that are no longer used.
- **Details**:
  - Remove the "Learn More" `MenuItem` block (dead link to non-existent route)
  - Component already reads `languages` from `useI18n()` which was updated to `[en, vi]` — no language list change needed
  - Remove any stale imports or references to unused flag assets
- **Validation**: LanguageSwitcher shows only en/vi, no dead links, flag images load correctly
- **Skills Applied**: `vercel-react-best-practices` → remove dead code

---

### Task 3: Verify I18nProvider Implementation

- **File**: `src/@i18n/I18nProvider.tsx` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Locale Detection Order (Client-side) + Header Injection
- **Reason**: Modified in previous `wf_openspec_apply`. This task verifies contract compliance.
- **Verification Checklist**:
  - [ ] Languages list: `[{id:'en', title:'English', flag:'US'}, {id:'vi', title:'Tiếng Việt', flag:'VN'}]`
  - [ ] `detectLanguage()`: localStorage → navigator.languages → "en"
  - [ ] `changeLanguage()`: calls `setGlobalHeaders({ 'Accept-Language': languageId })`
  - [ ] `localStorage.setItem('user_language', languageId)` on language change
  - [ ] Mount effect sets Accept-Language header immediately on app init
  - [ ] No references to removed locales (`tr`, `ar`)
- **Status**: Expected ✅ Already implemented — verify contract compliance

---

### Task 4: Verify i18n.ts + api.ts Implementation

- **File**: `src/@i18n/i18n.ts` | Action: [VERIFY]
- **File**: `src/utils/api.ts` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Request Headers + Locale Detection
- **Verification Checklist (i18n.ts)**:
  - [ ] `vi` translation namespace present (at least basic structure `{}`)
  - [ ] `detectInitialLanguage()` function: localStorage → navigator → "en"
  - [ ] `supportedLngs: ['en', 'vi']`
  - [ ] `fallbackLng: 'en'`
  - [ ] No references to removed locales (`tr`, `ar`)
- **Verification Checklist (api.ts)**:
  - [ ] Default `X-App-Version` header from `import.meta.env.VITE_APP_VERSION || '0.0.0'`
  - [ ] Default `X-Client-Platform: 'web'` header
  - [ ] `Accept-Language` → synced by I18nProvider via `setGlobalHeaders()`
  - [ ] `setGlobalHeaders()` function exported and usable
- **Status**: Expected ✅ Already implemented — verify contract compliance

---

### Task 5: Verify JwtSignInForm.tsx — Server Message Display

- **File**: `src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Error Display + Frontend Rule #7
- **Contract Rule**: "NEVER build error messages client-side — always use `problem.detail` from server"
- **Verification Checklist**:
  - [ ] `setErrorMessage(problem.detail || 'An error occurred')` — uses server i18n message directly
  - [ ] Hardcoded error strings removed (was ~12 strings in switch/case)
  - [ ] Client-side switch/case only for UX behavior (CAPTCHA trigger AUTH_007, rate limit countdown AUTH_020)
  - [ ] Remaining acceptable hardcoded strings audit:
    - `'An error occurred'` — fallback when JSON parse fails (acceptable — no server response available)
    - `'Network error. Please check your connection.'` — no server response available (acceptable)
  - [ ] No duplicate error messages between client and server
  - [ ] Error display respects user's selected language (via Accept-Language → server renders → client displays)
- **Status**: Expected ✅ Already implemented — verify contract compliance

---

### Task 6: Verify Success Message Display for New Endpoints

- **File**: Various form components | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Success Message Keys (7 new)
- **Reason**: After backend Tasks 3-7 are deployed, frontend components calling these endpoints should display `response.message` or `response.data.message` for success confirmations.
- **Verification Checklist**:
  - [ ] MFA TOTP confirm flow → shows `response.message` ("TOTP setup confirmed successfully" / "Xác nhận cài đặt TOTP thành công")
  - [ ] MFA recovery codes regeneration → shows `response.warning` in user's language
  - [ ] SSO link/unlink → shows `response.message` confirmation
  - [ ] Admin session revoke → shows `response.message` with user ID interpolated
  - [ ] Admin rate limit unlock → shows `response.message` confirmation
  - [ ] Token revoke-all → shows `response.message` confirmation
- **Note**: Some of these flows may not have dedicated UI components yet (admin features). Verify only for components that exist.
- **Status**: 🔲 Pending — depends on backend deployment

---

## Contract Coverage Matrix

| Contract Section | Task | Status |
|-----------------|------|--------|
| Supported Locales (en, vi) | Task 1, 2 | 🔲 Task 1 (VN flag), ✅ Task 2 (lang list) |
| Locale Detection (Client) | Task 3 | ✅ Verify |
| Request Headers (Accept-Language, X-App-Version, X-Client-Platform) | Task 4 | ✅ Verify |
| Response Headers (Content-Language) | N/A (server-side) | ✅ Implemented |
| Success Envelope (ApiResponse) i18n | Task 6 | 🔲 Verify after backend |
| Error Envelope (ProblemDetail) i18n | Task 5 | ✅ Verify |
| Error Code Registry (44 codes) | Task 5 | ✅ Verify |
| New Success Message Keys (7 keys) | Task 6 | 🔲 Verify after backend |
| Frontend Integration — Language Switcher | Task 1, 2 | 🔲 Actionable |
| Frontend Integration — Header Injection | Task 4 | ✅ Verify |
| Frontend Integration — Error Display | Task 5 | ✅ Verify |
| Frontend Integration — Locale Persistence | Task 3 | ✅ Verify |

## Summary

- **Actionable tasks**: 2 (Task 1: VN flag asset, Task 2: LanguageSwitcher cleanup)
- **Verify-only tasks**: 4 (Task 3: I18nProvider, Task 4: i18n.ts + api.ts, Task 5: JwtSignInForm, Task 6: new endpoint messages)
- **Complexity**: LOW
- **Estimated effort**: 0.5 developer-day (actionable) + 0.25 developer-day (verification)
- **Dependencies**: Backend Tasks 3-7 (controller i18n) should be deployed first for Task 6 verification. Frontend tasks 1-5 are independent and can proceed in parallel with backend.
