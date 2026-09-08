# Impact Analysis: argon2id-verify-optimization

_Generated: 2026-09-08 | Type: MAINTENANCE | Change: config only_

## 1. Core Files

| File | Action | Reason |
|------|:---:|--------|
| [application-security.yml](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application-security.yml#L67) | [MODIFY] | `iterations: 3` → `iterations: 1` |

## 2. Call Tree — Password Verify Path

```
application-security.yml:67 (iterations)
    │
    ▼
SecurityProperties.Argon2Properties.iterations
    │  (read at bean creation)
    ▼
PasswordEncoderAutoConfiguration.passwordEncoder()
    │  (creates Argon2PasswordEncoder(... iterations ...))
    ▼
ConcurrencyLimitedPasswordEncoder (Semaphore(20) wrapper)
    │
    ├── encode()        ← used by: register, change password, rehash
    ├── matches()       ← used by: login, change password, history check
    └── upgradeEncoding() ← used by: PasswordUpgradeService (O(1), no semaphore)
```

### matches() Callers (d=1)

| Caller | File | Frequency | Impact |
|--------|------|:---:|:---:|
| `TokenGenerator.matchesPassword()` | [TokenGenerator.kt:129](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt#L129) | High (login) | 160→75ms |
| `PasswordPolicyService.changePassword()` | [PasswordPolicyService.kt:69](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt#L69) | Low | 160→75ms |
| `PasswordPolicyService.checkPasswordHistory()` | [PasswordPolicyService.kt:57](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt#L57) | Low (5× per call) | 800→375ms |

### encode() Callers (d=1)

| Caller | File | Frequency | Impact |
|--------|------|:---:|:---:|
| `TokenGenerator.encodePassword()` | [TokenGenerator.kt:133](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt#L133) | Medium (register) | 150→75ms |
| `PasswordPolicyService.changePassword()` | [PasswordPolicyService.kt:86](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt#L86) | Low | 150→75ms |
| `PasswordUpgradeService.upgradeIfNeeded()` | [PasswordUpgradeService.kt:50](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordUpgradeService.kt#L50) | One-time | 150→75ms |

## 3. Blast Radius

| Depth | Symbols Affected | Risk Level |
|:---:|:---:|:---:|
| d=0 | `application-security.yml` (1 line) | 🟢 Config only |
| d=1 | `SecurityProperties.Argon2Properties` → `PasswordEncoderAutoConfiguration` | 🟢 Auto-reads |
| d=2 | `ConcurrencyLimitedPasswordEncoder` → all callers (6 methods) | 🟢 Transparent |

**Overall risk**: 🟢 **LOW** — Config-only change, no code modifications. All consumers transparently benefit.

## 4. Reuse Map

| Requirement | Decision | Reason |
|---|:---:|---|
| FR-001: Config change | [REUSE] existing YAML | Chỉ thay đổi value |
| FR-002: Backward compat | [REUSE] `Argon2PasswordEncoder.matches()` | Built-in behavior (reads params from hash) |
| FR-003: Transparent rehash | [REUSE] `PasswordUpgradeService` | Already handles parameter migration |
| FR-004: Benchmark validation | [REUSE] `Argon2idBenchmark.kt` | Already has balanced profile |
| FR-005: OWASP compliance | [REUSE] N/A | Verification only |
| FR-006: Rollback capability | [REUSE] YAML revert | Config-level rollback |

**No [NEW] or [EXTRACT] items** — all requirements satisfied by existing infrastructure.

## 5. Context Snapshot

| Component | Current State | After Change |
|---|---|---|
| `iterations` | 3 | **1** |
| `memory-cost` | 65536 (64MB) | 65536 (unchanged) |
| `parallelism` | 1 | 1 (unchanged) |
| Verify throughput | 6.25 ops/s | **13.33 ops/s** (+113%) |
| Verify latency | ~160ms | **~75ms** (−53%) |
| Semaphore | 20 | 20 (unchanged) |
| Peak memory | 1.28 GB | 1.28 GB (unchanged) |
| OWASP compliance | Exceeds | Meets minimum |
