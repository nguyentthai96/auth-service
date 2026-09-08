# Design: argon2id-verify-optimization

> **Type**: MAINTENANCE | **Flow**: Command | **Version**: 1.0

## 1. Overview

Config-only change: giảm Argon2id `iterations` từ 3→1 trong `application-security.yml`.
Không thay đổi code, database, hoặc architecture.

## 2. Component Mapping

### 2.1 Affected Component — Config Layer

```
┌──────────────────────────────────────────────────────────────┐
│  CONFIG LAYER (1 file, 1 line)                               │
│                                                              │
│  application-security.yml                                    │
│  L67: iterations: 3 → 1                                     │
│  L67 comment: update OWASP note                             │
│                                                              │
│  Reads by: SecurityProperties.Argon2Properties (auto-bind)  │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Transparent Impact Chain (no modifications needed)

```
SecurityProperties.Argon2Properties ─── reads iterations=1 from YAML
        │
        ▼
PasswordEncoderAutoConfiguration ────── creates Argon2PasswordEncoder(t=1)
        │
        ▼
ConcurrencyLimitedPasswordEncoder ──── wraps with Semaphore(20)
        │
        ├── matches() ── TokenGenerator.matchesPassword() ── LoginHandler
        ├── matches() ── PasswordPolicyService.changePassword()
        ├── matches() ── PasswordPolicyService.checkPasswordHistory() (5x)
        ├── encode()  ── TokenGenerator.encodePassword() ── Register flow
        ├── encode()  ── PasswordPolicyService.changePassword()
        └── encode()  ── PasswordUpgradeService.upgradeIfNeeded()
```

## 3. Design Decision

### 3.1 Chosen Approach: Pure Config Tuning (from Brainstorm)

| Decision | Rationale |
|---|---|
| Chỉ thay YAML, không touch code | YAGNI — MAINTENANCE change, 1 config value |
| Không update Kotlin default (=3) | YAML override là source of truth trong Spring Boot |
| Giữ Semaphore(20) | Memory per op unchanged (64MB) |
| Giữ memory-cost 64MB | GPU/ASIC resistance = memory-bound, not time-bound |
| Không thay đổi historyCount | Policy concern, separate from perf optimization |

### 3.2 Migration Strategy: Zero-Downtime (Existing Infrastructure)

Không cần migration script. Sử dụng existing infrastructure:

1. **Backward compat**: `Argon2PasswordEncoder.matches()` đọc `t` từ hash string → verify đúng cả t=3 và t=1
2. **Forward migration**: `PasswordUpgradeService.upgradeIfNeeded()` detect `upgradeEncoding()` → rehash on login
3. **History hashes**: Vẫn giữ format cũ (t=3), dần bị prune bởi `pruneHistory()`
4. **Rollback**: Revert 1 dòng YAML → `PasswordUpgradeService` auto-rehash forward lại

## 4. Specific Changes

### 4.1 [MODIFY] application-security.yml

```diff
# Line 67
-        iterations: 3                 # Time cost (OWASP 2024: 3)
+        iterations: 1                 # Time cost (OWASP 2024: ≥ 1, tuned from 3 for perf)
```

Không có file nào khác cần thay đổi.

## 5. Security Considerations

### 5.1 Brute-force Impact Assessment

| Scenario | Current (t=3) | Proposed (t=1) | Risk |
|---|:---:|:---:|:---:|
| Online brute-force per attempt | ~160ms | ~75ms | 🟢 Mitigated by rate limiting |
| Online max throughput | 3 attempts / 15 min (locked) | Same | 🟢 No change |
| Offline (leaked DB) per attempt | ~160ms | ~75ms | 🟡 Lower but memory-bound |
| GPU parallel attack | 64MB/thread limit | Same (64MB) | 🟢 No change |

### 5.2 Compensating Controls (5 layers)

1. **Rate Limiting**: IP (5/60s), Username (3/900s), Device (10/3600s)
2. **CAPTCHA**: After 3 failed attempts
3. **Account Lock**: 15 min after max failures
4. **Memory Hardness**: 64MB per hash (unchanged) — GPU resistance
5. **MFA**: Optional TOTP per user/domain

## 6. Testing Strategy

### 6.1 Existing Tests (should pass without changes)
- `Argon2PasswordEncoderTest` — basic encode/verify (uses Spring defaults)
- `LoginHandlerTest` — login flow (mocked `passwordEncoder`)
- Integration tests — full stack verification

### 6.2 Post-deployment Validation
- Verify login with existing user (hash t=3) succeeds
- Verify `password.migration.rehash.total` counter increases
- Verify new user registration creates hash with t=1
- Verify change password flow completes within target latency

## 7. Monitoring

| Metric | Expected After Deploy |
|---|---|
| `password.migration.rehash.total` | Increasing (as users login) |
| Login p99 latency | Decreasing from ~160ms to ~75ms |
| Semaphore queue depth | Decreasing (faster op release) |
| Memory usage | Unchanged (64MB per op) |
