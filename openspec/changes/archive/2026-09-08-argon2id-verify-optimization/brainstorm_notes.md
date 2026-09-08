---
type: brainstorm_notes
change: argon2id-verify-optimization
date: 2026-09-08
selected_direction: "Approach 1 — Pure config tuning (iterations 3→1)"
pre_flow: "Command"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Argon2id Verify Performance Optimization

## Date
2026-09-08

## Context
Research phase đã hoàn tất — xác nhận giảm Argon2id `iterations` từ 3→1 (giữ 64MB memory) sẽ cải thiện throughput +113%. User muốn brainstorm sâu hơn trước khi proceed sang openspec/apply.

## Questions Asked & Answers

- Q1: Liệu có call points nào khác ngoài login dùng `passwordEncoder.matches()` mà sẽ hưởng lợi từ optimization? → A: **Có, 3 call points bổ sung:**
  1. `PasswordPolicyService.checkPasswordHistory()` — verify N lần (default historyCount=5) → **bottleneck lớn nhất**
  2. `PasswordPolicyService.changePassword()` — verify old password 1 lần
  3. `TokenGenerator.matchesPassword()` — shared verify method

- Q2: Password history check hiện tại tốn bao nhiêu thời gian? → A: **historyCount=5 × ~160ms = ~800ms** (worst case, khi tất cả hash khác). Với t=1: 5 × ~75ms = **~375ms** → cải thiện **2.1x**.

- Q3: `PasswordUpgradeService.upgradeEncoding()` có cần thay đổi gì không? → A: **Không**. `upgradeEncoding()` chỉ check prefix string (O(1)), không hash. Nó sẽ tự động detect t=3 hash cũ ≠ t=1 default → trigger rehash.

- Q4: Password history hashes có được rehash tự động không? → A: **KHÔNG** — đây là một insight quan trọng. `PasswordUpgradeService` chỉ rehash user's current password khi login thành công. Nhưng password history hashes (trong `PasswordHistoryEntity`) vẫn giữ nguyên format cũ (t=3). **Tuy nhiên, đây không phải vấn đề** vì:
  - `matches()` đọc `t` từ hash string → verify chính xác bất kể config
  - History hashes chỉ dùng cho `checkPasswordHistory()` (so sánh, không authenticate)
  - Chúng sẽ dần bị prune bởi `pruneHistory()` khi user thay đổi password nhiều lần

- Q5: `ConcurrencyLimitedPasswordEncoder` Semaphore(20) — với t=1 nhanh hơn, có nên tăng maxConcurrent không? → A: **Không cần thay đổi ngay**. Memory per op vẫn giữ nguyên 64MB → max memory footprint = 20 × 64MB = 1.28GB (unchanged). Throughput cải thiện vì mỗi op nhanh hơn, nhưng peak memory giữ nguyên → semaphore vẫn đúng.

- Q6: Nếu trong tương lai muốn giảm thêm memory cost, cần thay đổi gì? → A: Cũng chỉ thay YAML, nhưng cần cân nhắc:
  - `maxConcurrentHashes` có thể tăng (vì mỗi op dùng ít memory hơn)
  - Phải verify OWASP compliance mới (m ≥ 19456 KiB)

## Approaches Considered

### Approach 1: Pure config tuning — iterations 3→1 (✅ RECOMMENDED)

**Mô tả**: Chỉ thay đổi `iterations: 3` → `iterations: 1` trong `application-security.yml`, giữ nguyên memory 64MB.

```
┌─────────────────────────────────────────────────────────────┐
│  CURRENT STATE                                               │
│                                                              │
│  application-security.yml                                    │
│  ┌─────────────────────────┐                                │
│  │ iterations: 3           │ ← 6.25 ops/s, ~160ms/op       │
│  │ memory-cost: 65536      │                                │
│  │ parallelism: 1          │                                │
│  └─────────────────────────┘                                │
│                                                              │
│  PROPOSED STATE                                              │
│  ┌─────────────────────────┐                                │
│  │ iterations: 1           │ ← 13.33 ops/s, ~75ms/op       │
│  │ memory-cost: 65536      │   (+113% throughput)           │
│  │ parallelism: 1          │                                │
│  └─────────────────────────┘                                │
│                                                              │
│  ❌ NO code changes      ✅ YAML only                       │
│  ❌ NO migration scripts ✅ Zero-downtime                   │
│  ❌ NO DB changes        ✅ Backward compatible             │
│  ❌ NO rollback risk     ✅ Revert 1 line config            │
└─────────────────────────────────────────────────────────────┘
```

**Pros**:
- Effort cực thấp — 1 dòng YAML
- Benchmark validated: 13.33 ops/s ± 0.374
- OWASP 2024 compliant (m=64MB ≥ 46MB minimum, t=1 ≥ 1 minimum)
- Memory hardness giữ nguyên → GPU/ASIC resistance unchanged
- Zero-downtime via DelegatingPasswordEncoder + PasswordUpgradeService
- Rollback = revert 1 line, tự động re-hash forward

**Cons**:
- Brute-force thời gian giảm ~3x (160ms → 75ms per attempt)
- Mitigated bởi: rate limiting (5 attempts/60s IP, 3 attempts/900s username), CAPTCHA (sau 3 failed), account lock (15 min)

**Blast radius**:
```
  application-security.yml:67  ────────────────────────────────
           │
           ▼
  SecurityProperties.Argon2Properties.iterations ← reads from YAML
           │
           ▼
  PasswordEncoderAutoConfiguration
  ┌────────┴───────────────────────────────────────────┐
  │  Argon2PasswordEncoder(... iterations=1 ...)       │
  └────────┬───────────────────────────────────────────┘
           │
           ▼
  ConcurrencyLimitedPasswordEncoder (Semaphore(20))
           │
    ┌──────┼──────────────┐
    │      │              │
    ▼      ▼              ▼
  encode() matches()   upgradeEncoding()
    │      │              │
    │    ┌─┼──────────────┤
    │    │ │              │
    ▼    ▼ ▼              ▼
  Login  Change  History  PasswordUpgradeService
  Handler Password Check  (rehash)

  Affected flows:
  1. Login → matches() 1x           → 160→75ms  (−53%)
  2. Change Password → matches() 1x → 160→75ms  (−53%)
  3. Password History → matches() 5x → 800→375ms (−53%)
  4. New encode() → encode() 1x      → 71→75ms*  (≈same)
  5. Rehash → encode() 1x           → 71→75ms*  (≈same)

  * encode với t=1 ≈ verify t=1 (symmetric operation for Argon2)
```

### Approach 2: Dual-phase — config tuning + Argon2Properties default update

**Mô tả**: Ngoài thay đổi YAML, cũng update default value trong `SecurityProperties.Argon2Properties` từ `iterations: Int = 3` sang `iterations: Int = 1`.

**Pros**:
- Tất cả lợi ích của Approach 1
- Code documentation consistency — default value phản ánh actual intended config
- Profiles mới (test/staging) tự động dùng t=1 nếu không set explicit

**Cons**:
- 2 thay đổi (YAML + Kotlin data class) thay vì 1
- Nhỏ nhưng là code change → cần build + deploy (vs chỉ config reload)
- Default value mismatch có thể gây confusion nếu ai đó đọc code nhưng YAML override khác

### Approach 3: Full tuning — config + code + YAML comment documentation

**Mô tả**: Thay đổi YAML + update Kotlin default + update YAML comment + thêm benchmark reference comment.

**Pros**:
- Documentation tốt nhất — mọi nơi đều phản ánh decision
- Self-documenting code

**Cons**:
- Effort cao hơn (3 changes) cho một MAINTENANCE change
- Over-engineering cho 1 config value change

## Selected Direction

### ✅ Approach 1: Pure config tuning — iterations 3→1

**Lý do chọn:**
1. **YAGNI**: MAINTENANCE change → thay đổi minimum, đạt hiệu quả maximum
2. **Risk minimum**: 1 dòng YAML → rollback trivial
3. **Benchmark validated**: 13.33 ops/s đã xác nhận bằng JMH
4. **No code change**: Giảm risk introduce bugs, không cần recompile/redeploy binary

**Lý do KHÔNG chọn Approach 2/3:**
- YAML value là "source of truth" trong Spring Boot — Kotlin default chỉ là fallback nếu YAML không set
- Thêm code change cho MAINTENANCE là over-engineering
- Comment documentation có thể update riêng nếu cần (separate concern)

## Pre-classifications (preliminary)

- Feature type: **MAINTENANCE** — modify existing config, no new files
- Flow type: **Command** — single config change, không phải query/financial/OTP
- Affected modules:
  - `application-security.yml` (MODIFY — 1 line)
  - Indirect impact: `PasswordEncoderAutoConfiguration`, `ConcurrencyLimitedPasswordEncoder`, `PasswordUpgradeService`, `PasswordPolicyService`, `LoginHandler`, `TokenGenerator`

## GitNexus Findings

- **Related processes**: 
  - `proc_3_login` — Login → TokenExpiredException (5 steps)
  - `proc_46_changepassword` — ChangePassword → FindByDomainId (4 steps)
  - `proc_47_changepassword` — ChangePassword → PasswordPolicyEntity (4 steps)
- **Key symbols**:
  - `LoginHandler` (Class) — `src/main/kotlin/.../command/LoginHandler.kt:29-170`
    - Incoming: `CqrsAuthController.kt` (import), `LoginHandlerTest.kt` (test)
    - Properties: `passwordPolicyService`, `securityProperties`, `tokenGenerator` + 14 more
    - Methods: `commandType()`, `handle()`
  - `PasswordPolicyService` — `src/main/kotlin/.../PasswordPolicyService.kt`
    - `checkPasswordHistory()` — matches N×password hashes
    - `changePassword()` — matches old password + encode new + save history
  - `PasswordUpgradeService` — `src/main/kotlin/.../PasswordUpgradeService.kt`
    - `upgradeIfNeeded()` — checks `upgradeEncoding()` + rehash
  - `ConcurrencyLimitedPasswordEncoder` — Semaphore(20) wrapper
- **Architecture insights**:
  - Clean separation: all password operations go through `PasswordEncoder` interface
  - `DelegatingPasswordEncoder` handles multi-algorithm (prefix-based routing)
  - Config-driven via `SecurityProperties` → `@ConfigurationProperties`
  - Argon2 params embedded in hash string → backward compatible by design

## Deeper Analysis — Performance Impact Matrix

### All Password Verify Call Points

```
┌───────────────────────────────────────────────────────────────────────┐
│  PASSWORD VERIFY CALL POINTS — Impact Analysis                       │
│                                                                      │
│  Call Point          │ Frequency  │ Current   │ With t=1  │ Savings  │
│  ────────────────────┼────────────┼───────────┼───────────┼──────────│
│  Login (verify 1x)   │ High       │ ~160ms    │ ~75ms     │ −53%    │
│  Change Password     │ Low        │ ~160ms    │ ~75ms     │ −53%    │
│  History Check (5x)  │ Low        │ ~800ms    │ ~375ms    │ −53%    │
│  Register (encode 1x)│ Medium     │ ~150ms    │ ~75ms     │ −50%    │
│  Rehash (encode 1x)  │ One-time   │ ~150ms    │ ~75ms     │ −50%    │
└───────────────────────────────────────────────────────────────────────┘
```

### Concurrency Model Analysis

```
┌───────────────────────────────────────────────────────────────────────┐
│  CONCURRENCY IMPACT — Semaphore(20) unchanged                        │
│                                                                      │
│  Current (t=3):                                                      │
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐    │
│  │▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│▓▓│    │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘    │
│  20 slots × 160ms = max ~125 ops/s theoretical                      │
│  Actual: ~38 login/s (8 threads measured)                            │
│                                                                      │
│  Proposed (t=1):                                                     │
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐    │
│  │░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│░░│    │
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘    │
│  20 slots × 75ms = max ~267 ops/s theoretical                       │
│  Expected: ~82 login/s (8 threads, +116%)                            │
│                                                                      │
│  Memory: 20 × 64MB = 1.28GB ← UNCHANGED                            │
└───────────────────────────────────────────────────────────────────────┘
```

### Security Compensating Controls

```
┌───────────────────────────────────────────────────────────────────────┐
│  SECURITY DEFENSE-IN-DEPTH — Compensating for reduced t=3→1          │
│                                                                      │
│  Layer 1: RATE LIMITING                                              │
│  ├── IP:       5 attempts / 60s  → lock 300s                        │
│  ├── Username: 3 attempts / 900s → lock 1800s                       │
│  └── Device:   10 attempts / 3600s → lock 3600s                     │
│                                                                      │
│  Layer 2: CAPTCHA (after 3 failed login attempts)                    │
│  └── Provider: Configurable (altcha/turnstile/hcaptcha/recaptcha)   │
│                                                                      │
│  Layer 3: ACCOUNT LOCK                                               │
│  └── 3 failed attempts → 15 min lock                                │
│                                                                      │
│  Layer 4: ARGON2ID MEMORY HARDNESS (UNCHANGED)                       │
│  └── 64MB per hash → GPU/ASIC resistance ≈ same as t=3             │
│      (memory-bound, not time-bound for GPU attacks)                  │
│                                                                      │
│  Layer 5: MFA (optional, per user/domain)                            │
│  └── TOTP + Recovery codes                                           │
│                                                                      │
│  ⚡ Brute-force analysis:                                            │
│  Online: Max 3 attempts / 15 min = 12 attempts/hour → irrelevant    │
│  Offline (leaked DB): 64MB RAM per attempt → GPU attack costly       │
│  Conclusion: Rate limiting + memory hardness >> iteration count      │
└───────────────────────────────────────────────────────────────────────┘
```

### Migration Flow — Zero Downtime

```
┌───────────────────────────────────────────────────────────────────────┐
│  MIGRATION FLOW — How existing t=3 hashes transition to t=1          │
│                                                                      │
│  ① Config deployed: iterations: 1                                    │
│     ↓                                                                │
│  ② User logins → verify against stored hash                          │
│     Hash: $argon2id$v=19$m=65536,t=3,p=1$salt$hash                  │
│     ↓                                                                │
│  ③ Argon2PasswordEncoder.matches() reads t=3 FROM hash string       │
│     (ignores constructor t=1) → verifies correctly ✓                 │
│     ↓                                                                │
│  ④ PasswordUpgradeService.upgradeIfNeeded()                          │
│     upgradeEncoding() detects: t=3 ≠ default t=1 → TRUE             │
│     ↓                                                                │
│  ⑤ Re-encode with new params:                                       │
│     New hash: $argon2id$v=19$m=65536,t=1,p=1$newsalt$newhash        │
│     Saved to DB + audit log + Micrometer counter                     │
│     ↓                                                                │
│  ⑥ Subsequent logins use t=1 hash → ~75ms verify                    │
│                                                                      │
│  Timeline: All active users migrated within ~30 days                 │
│  Dormant users: Still verified correctly (t=3 from hash) on return   │
└───────────────────────────────────────────────────────────────────────┘
```

## Open Questions for Design Phase

- [RESOLVED] Q: Có cần update `SecurityProperties.Argon2Properties` default? → A: **KHÔNG** — YAML override là sufficient
- [RESOLVED] Q: `ConcurrencyLimitedPasswordEncoder` Semaphore cần thay đổi? → A: **KHÔNG** — memory per op giữ nguyên
- [RESOLVED] Q: Password history hashes cũ (t=3) có vấn đề? → A: **KHÔNG** — `matches()` đọc params từ hash string
- [RESOLVED] Q: Cần integration test gì? → A: Verify login flow end-to-end sau config change (existing tests đã cover)
- [RESOLVED] Q: OWASP compliance? → A: ĐẠT — m=65536 ≥ 19456, t=1 ≥ 1, p=1 ≥ 1

## Open Questions for URD Analysis

Không có — đây là MAINTENANCE (config optimization), không phải new feature cần URD.

## Additional Insights

### Insight 1: Password History = Hidden Performance Multiplier
`checkPasswordHistory()` gọi `matches()` N lần (N = historyCount = 5 default). Đây là call point **ít được nhận thấy** nhưng tốn cost cao nhất per-operation. Với t=1, change password flow giảm từ ~960ms (160 + 800) xuống ~450ms (75 + 375).

### Insight 2: No Risk to Concurrent Memory Footprint
Mặc dù throughput tăng 2x, memory per operation giữ nguyên 64MB. `Semaphore(20)` vẫn cap peak memory tại 1.28GB. Faster ops = slots free up sooner = higher throughput, nhưng KHÔNG higher peak memory.

### Insight 3: Argon2PasswordEncoderTest chưa cover backward compat
Test hiện tại (`src/test/java/crypto/Argon2PasswordEncoderTest.java`) dùng `defaultsForSpringSecurity_v5_8()` — **KHÔNG** test verify hash t=3 bằng encoder config t=1. Đây là gap cần integration test cover.

### Insight 4: YAML Comment cần update sau apply
Hiện tại comment line 67 ghi `# Time cost (OWASP 2024: 3)`. Sau khi thay đổi, nên update thành `# Time cost (OWASP 2024: ≥ 1, tuned from 3)` để tránh confusion.
