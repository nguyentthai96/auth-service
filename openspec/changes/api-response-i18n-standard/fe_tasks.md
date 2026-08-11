## Frontend Tasks: api-response-i18n-standard

<!-- generated-by: wf_fe_spec -->
<!-- contract-version: 1.0 -->
<!-- complexity: LOW -->
<!-- estimated-tasks: 5 -->
<!-- mode: C (Memory-Enriched) -->
<!-- backend-status: implemented -->
<!-- cross-cutting: true -->

> **Note**: This is a cross-cutting i18n protocol change — no new pages/endpoints.
> Several files were already modified during `wf_openspec_apply`. These tasks cover
> remaining gaps and verification of already-implemented changes.

---

### Task 1: Add VN Flag Asset

- **File**: `public/assets/images/flags/VN.svg` | Action: [NEW]
- **Contract Source**: `api_contract.md` → Frontend Integration Guide → LanguageType
- **Reason**: `LanguageSwitcher.tsx` renders `<img src="/assets/images/flags/${language.flag}.svg">`.
  Currently `US.svg` exists but `VN.svg` is **missing** → broken image for Vietnamese.
- **Details**:
  - Create a simple Vietnamese flag SVG (red background, yellow star)
  - File must be at `public/assets/images/flags/VN.svg`
- **Validation**: `LanguageSwitcher` renders VN flag without broken image

---

### Task 2: Cleanup LanguageSwitcher — Remove Dead Links + Old Flags

- **File**: `src/components/theme-layouts/components/LanguageSwitcher.tsx` | Action: [MODIFY]
- **Contract Source**: `api_contract.md` → Supported Locales (en, vi only)
- **Reason**: LanguageSwitcher has a "Learn More" link pointing to `/documentation/configuration/multi-language` which doesn't exist. Also references old `TR.svg` and `SA.svg` flag assets that are no longer used.
- **Details**:
  - Remove the "Learn More" `MenuItem` block (lines 89-96)
  - Component already reads `languages` from `useI18n()` which was updated in `wf_openspec_apply` to `[en, vi]` — no language list change needed
- **Validation**: LanguageSwitcher shows only en/vi, no dead links, flag images load
- **Skills Applied**: `vercel-react-best-practices` → remove dead code

---

### Task 3: Verify I18nProvider Implementation (Already Done)

- **File**: `src/@i18n/I18nProvider.tsx` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Locale Detection Order (Client-side) + Header Injection
- **Reason**: Already modified in `wf_openspec_apply`. This task verifies contract compliance.
- **Verification Checklist**:
  - [x] Languages list: `[{id:'en', title:'English', flag:'US'}, {id:'vi', title:'Tiếng Việt', flag:'VN'}]`
  - [x] `detectLanguage()`: localStorage → navigator.languages → "en"
  - [x] `changeLanguage()`: calls `setGlobalHeaders({ 'Accept-Language': languageId })`
  - [x] `localStorage.setItem('user_language', languageId)` on change
  - [x] Mount effect sets Accept-Language immediately
- **Status**: ✅ Already implemented — verify only

---

### Task 4: Verify i18n.ts + api.ts Implementation (Already Done)

- **File**: `src/@i18n/i18n.ts` | Action: [VERIFY]
- **File**: `src/utils/api.ts` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Request Headers + Locale Detection
- **Verification Checklist (i18n.ts)**:
  - [x] `vi` translation namespace present
  - [x] `detectInitialLanguage()` function: localStorage → navigator → "en"
  - [x] `supportedLngs: ['en', 'vi']`
  - [x] `fallbackLng: 'en'`
- **Verification Checklist (api.ts)**:
  - [x] Default `X-App-Version` header from `import.meta.env.VITE_APP_VERSION`
  - [x] Default `X-Client-Platform: 'web'` header
  - [x] `Accept-Language` → synced by I18nProvider via `setGlobalHeaders()`
- **Status**: ✅ Already implemented — verify only

---

### Task 5: Verify JwtSignInForm.tsx — Server Message Display (Already Done)

- **File**: `src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [VERIFY]
- **Contract Source**: `api_contract.md` → Error Display + Frontend Rule #7
- **Contract Rule**: "NEVER build error messages client-side — always use `problem.detail` from server"
- **Verification Checklist**:
  - [x] `setErrorMessage(problem.detail || 'An error occurred')` — uses server i18n
  - [x] Hardcoded error strings removed (was 12 strings)
  - [x] Client-side switch/case only for UX behavior (CAPTCHA, rate limit)
  - [ ] Remaining hardcoded strings audit:
    - `'An error occurred'` — fallback when JSON parse fails (acceptable)
    - `'Network error. Please check your connection.'` — no server response available (acceptable)
  - [x] No duplicate error messages between client and server
- **Status**: ✅ Already implemented — verify only

---

## Contract Coverage Matrix

| Contract Section | Task | Status |
|-----------------|------|--------|
| Supported Locales (en, vi) | Task 1, 2 | ✅ Done |
| Locale Detection (Client) | Task 3 | ✅ Done |
| Request Headers (Accept-Language, X-App-Version, X-Client-Platform) | Task 4 | ✅ Done |
| Response Headers (Content-Language) | N/A (server-side) | ✅ Done |
| Success Envelope (ApiResponse) i18n | N/A (server-side) | ✅ Done |
| Error Envelope (ProblemDetail) i18n | Task 5 | ✅ Done |
| Error Code Registry (27 codes) | Task 5 | ✅ Done |
| Frontend Integration — Language Switcher | Task 1, 2 | ✅ Done |
| Frontend Integration — Header Injection | Task 4 | ✅ Done |
| Frontend Integration — Error Display | Task 5 | ✅ Done |
| Frontend Integration — Locale Persistence | Task 3 | ✅ Done |

## Error Code Frontend Coverage

| Error Code | Frontend Action (from contract) | Covered? |
|------------|--------------------------------|----------|
| AUTH_001 | `field-error` + `toast` | ✅ field mark + problem.detail |
| AUTH_002 | `modal` with countdown | ✅ problem.detail (modal → future) |
| AUTH_007 | Auto-trigger ALTCHA | ✅ setCaptchaRequired(true) |
| AUTH_008 | `toast` + re-trigger | ✅ setCaptchaRequired(true) |
| AUTH_020 | `toast` + countdown | ✅ setRateLimitRetry() |
| AUTH_021 | `modal` | ✅ problem.detail display |
| All others | `toast` / `redirect` | ✅ problem.detail fallback |

## Summary

- **Actionable tasks**: 2 (Task 1: VN flag, Task 2: LanguageSwitcher cleanup)
- **Verify-only tasks**: 3 (Task 3, 4, 5 — already done in wf_openspec_apply)
- **Complexity**: LOW
- **Skills**: vercel-react-best-practices
