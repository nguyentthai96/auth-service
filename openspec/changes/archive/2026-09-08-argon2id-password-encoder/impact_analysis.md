# Impact Analysis — argon2id-password-encoder

## 1. Core Files

| File | Action | Risk | Reason |
|------|--------|------|--------|
| [`SecurityConfig.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | MODIFY (remove bean) | 🟢 Low | Remove `passwordEncoder()` bean — moved to AutoConfiguration |
| [`SecurityProperties.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | MODIFY (add fields) | 🟢 Low | Add `algorithm`, `argon2` properties — backward compatible defaults |
| `PasswordEncoderAutoConfiguration.kt` | NEW | 🟢 Low | New file — no existing code affected |
| `PasswordUpgradeService.kt` | NEW | 🟢 Low | New file — no existing code affected |
| [`LoginHandler.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | MODIFY (inject + call) | 🟡 Medium | Add `PasswordUpgradeService` dependency + call after password verify |
| [`build.gradle.kts`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts) | MODIFY | 🟢 Low | Promote BouncyCastle scope |
| `application-security.yml` | MODIFY | 🟢 Low | Add argon2 config section |
| `V__add_password_hash_prefix.sql` | NEW | 🟡 Medium | DB migration — irreversible prefix addition |

## 2. Call Tree

```
passwordEncoder() @Bean [SecurityConfig.kt:111]
  │
  ├── TokenGenerator.passwordEncoder [inject]
  │   ├── TokenGenerator.matchesPassword() [line 129]
  │   │   └── LoginHandler.handle() [line 103] ◄── MODIFY (add rehash)
  │   └── TokenGenerator.encodePassword() [line 133]
  │       └── RegisterHandler.handle() [line 68]
  │
  └── PasswordPolicyService.passwordEncoder [inject]
      ├── checkPasswordHistory() [line 57] — matches()
      ├── changePassword() [line 69] — matches() (old password verify)
      └── changePassword() [line 86] — encode() (new password hash)
```

## 3. Blast Radius

| Depth | Symbol | File | Impact |
|-------|--------|------|--------|
| d=0 | `passwordEncoder()` @Bean | SecurityConfig.kt:111 | REMOVE → move to AutoConfiguration |
| d=1 | `TokenGenerator.passwordEncoder` | TokenGenerator.kt:39 | 🟢 No change — DI resolves new bean |
| d=1 | `PasswordPolicyService.passwordEncoder` | PasswordPolicyService.kt:29 | 🟢 No change — DI resolves new bean |
| d=2 | `LoginHandler` (via TokenGenerator) | LoginHandler.kt:48 | 🟡 MODIFY — inject PasswordUpgradeService |
| d=2 | `RegisterHandler` (via TokenGenerator) | RegisterHandler.kt:41 | 🟢 No change |
| d=2 | `AccountLifecycleController` (via PasswordPolicyService) | AccountLifecycleController.kt | 🟢 No change |

**Total blast radius: 6 symbols, 2 modifications, 0 breaking changes**

## 4. Reuse Map

| Logic Block | Match | Decision | Action |
|-------------|-------|----------|--------|
| `DelegatingPasswordEncoder` | 100% Spring Security | **REUSE** | Import Spring Security class |
| `Argon2PasswordEncoder` | 100% Spring Security | **REUSE** | Import Spring Security class |
| `upgradeEncoding()` | 100% DelegatingPasswordEncoder | **REUSE** | Call built-in method |
| Semaphore pattern | ~60% (no existing in project) | **NEW** | Inline in AutoConfiguration |
| `AuditLogService` | 100% existing | **REUSE** | Import for rehash audit logging |
| Flyway migration | 100% existing pattern | **REUSE** | Follow existing V__*.sql convention |

## 5. Context Snapshot

- **PasswordEncoder interface**: Injected in 2 production services (TokenGenerator, PasswordPolicyService)
- **BCryptPasswordEncoder**: Single bean definition at SecurityConfig.kt:111
- **No custom PasswordEncoder wrapper**: Currently direct BCryptPasswordEncoder instantiation
- **Virtual Threads**: Enabled — unbounded thread pool → Argon2id memory risk
- **BouncyCastle 1.80**: Already in project (testImplementation) — needs promotion
- **Column size**: `password_hash VARCHAR(255)` — sufficient for Argon2id (~110 chars)
- **Password history table**: `password_history.password_hash VARCHAR(255)` — same constraint
