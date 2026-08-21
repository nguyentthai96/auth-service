## Frontend Tasks: api-response-i18n-standard

<!-- generated-by: wf_openspec -->
<!-- contract-version: 4.0 -->
<!-- complexity: LOW -->
<!-- estimated-tasks: 6 -->
<!-- backend-status: fully-implemented (18/18 action endpoints DONE) -->
<!-- cross-cutting: true -->
<!-- updated: 2026-08-27 — backend 100% complete, frontend tasks focus on header injection + form cleanup -->

> **Note**: This is a cross-cutting i18n protocol change — no new pages/endpoints.
> Several files were already modified during previous `wf_openspec_apply` runs.
> These tasks cover remaining gaps and verification of already-implemented changes.
> All tasks target `admindashboard` project — **outside auth-service workspace**.

---

### Task 1: Add VN Flag Asset

- **File**: `public/assets/images/flags/VN.svg` | Action: [NEW]
- **Contract Source**: `api_contract.md` → Supported Locales
- **Reason**: `LanguageSwitcher.tsx` renders `<img src="/assets/images/flags/${language.flag}.svg">`. Currently `US.svg` exists but `VN.svg` may be **missing** → broken image for Vietnamese.
- **Details**:
  - Create a simple Vietnamese flag SVG (red background, yellow star)
  - File must be at `public/assets/images/flags/VN.svg`
  - Dimensions: viewBox `0 0 900 600` (standard 3:2 flag ratio)
- **Validation**: `LanguageSwitcher` renders VN flag without broken image icon

---

### Task 2: Cleanup LanguageSwitcher — Remove Dead Links

- **File**: `src/components/theme-layouts/components/LanguageSwitcher.tsx` | Action: [MODIFY]
- **Contract Source**: `api_contract.md` → Supported Locales (en, vi only)
- **Reason**: LanguageSwitcher has a "Learn More" link pointing to `/documentation/configuration/multi-language` which doesn't exist.
- **Details**:
  - Remove the "Learn More" `MenuItem` block (dead link to non-existent route)
  - Component already reads `languages` from `useI18n()` — no language list change needed
  - Remove any stale imports or references to unused flag assets
- **Validation**: LanguageSwitcher shows only en/vi, no dead links, flag images load correctly

---

### Task 3: Verify I18nProvider Implementation

- **File**: `src/@i18n/I18nProvider.tsx` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Locale Detection Order (Client-side) + Header Injection
- **Verification Checklist**:
  - [ ] Languages list: `[{id:'en', title:'English', flag:'US'}, {id:'vi', title:'Tiếng Việt', flag:'VN'}]`
  - [ ] `detectLanguage()`: localStorage → navigator.languages → "en"
  - [ ] `changeLanguage()`: calls `setGlobalHeaders({ 'Accept-Language': languageId })`
  - [ ] `localStorage.setItem('user_language', languageId)` on language change
  - [ ] Mount effect sets Accept-Language header immediately on app init
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
- **Verification Checklist (api.ts)**:
  - [ ] Default `X-App-Version` header from `import.meta.env.VITE_APP_VERSION || '0.0.0'`
  - [ ] Default `X-Client-Platform: 'web'` header
  - [ ] `Accept-Language` → synced by I18nProvider via `setGlobalHeaders()`
  - [ ] `setGlobalHeaders()` function exported and usable
- **Status**: Expected ✅ Already implemented — verify contract compliance

---

### Task 5: Verify JwtSignInForm.tsx — Server Message Display

- **File**: `src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Frontend rule #6
- **Contract Rule**: "NEVER build error messages client-side — always use `problem.detail` from server"
- **Verification Checklist**:
  - [ ] `setErrorMessage(problem.detail || 'An error occurred')` — uses server i18n message directly
  - [ ] Hardcoded error strings removed (was ~12 strings in switch/case)
  - [ ] Client-side switch/case only for UX behavior (CAPTCHA trigger AUTH_007, rate limit countdown AUTH_020)
  - [ ] Acceptable fallback strings: `'An error occurred'` (no server response), `'Network error. Please check your connection.'` (no server response)
  - [ ] Error display respects user's selected language
- **Status**: Expected ✅ Already implemented — verify contract compliance

---

### Task 6: Verify Success Message Display for New Endpoints

- **File**: Various form components | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Complete Success Message Keys
- **Reason**: Backend is 100% complete. Frontend components calling action endpoints should display `response.message` for success confirmations.
- **Verification Checklist**:
  - [ ] MFA TOTP confirm flow → shows `response.message`
  - [ ] MFA recovery codes regeneration → shows `response.warning` in user's language
  - [ ] SSO link/unlink → shows `response.message` confirmation
  - [ ] Admin session revoke → shows `response.message` with user ID interpolated
  - [ ] Admin rate limit unlock → shows `response.message` confirmation
  - [ ] Token revoke-all → shows `response.message` confirmation
- **Note**: Some flows may not have dedicated UI components yet (admin features). Verify only for existing components.
- **Status**: 🔲 Pending — depends on frontend using new backend responses

---

## Contract Coverage Matrix

| Contract Section | Task | Status |
|-----------------|------|--------|
| Supported Locales (en, vi) | Task 1, 2 | 🔲 Task 1 (VN flag), ✅ Task 2 (lang list) |
| Locale Detection (Client) | Task 3 | ✅ Verify |
| Request Headers (Accept-Language, X-App-Version, X-Client-Platform) | Task 4 | ✅ Verify |
| Response Headers (Content-Language) | N/A (server-side) | ✅ Implemented |
| Success Envelope i18n | Task 6 | 🔲 Verify after deploy |
| Error Envelope (ProblemDetail) i18n | Task 5 | ✅ Verify |
| Complete Success Message Keys (18 keys) | Task 6 | 🔲 Verify after deploy |
| Frontend — Language Switcher | Task 1, 2 | 🔲 Actionable |
| Frontend — Header Injection | Task 4 | ✅ Verify |
| Frontend — Error Display | Task 5 | ✅ Verify |
| Frontend — Locale Persistence | Task 3 | ✅ Verify |

## Summary

- **Actionable tasks**: 2 (Task 1: VN flag asset, Task 2: LanguageSwitcher cleanup)
- **Verify-only tasks**: 4 (Task 3: I18nProvider, Task 4: i18n.ts + api.ts, Task 5: JwtSignInForm, Task 6: new endpoint messages)
- **Complexity**: LOW
- **Estimated effort**: 0.25 developer-day (actionable) + 0.25 developer-day (verification)
- **Dependencies**: Backend 100% complete — frontend tasks can proceed immediately. Task 6 verification requires deploying backend changes first (already done).
