# Comparison Analysis — Argon2id Verify Performance Optimization

## Tổng quan

Phân tích so sánh các chiến lược tối ưu hiệu năng `argon2id_verify` dựa trên benchmark hiện tại (~3.93 ops/s, ~254ms/op) với cấu hình OWASP 2024 (m=65536 KiB, t=3, p=1).

## Benchmark Baseline (từ JMH report)

| Metric | Giá trị |
|--------|---------|
| `argon2id_hash` | 3.927 ops/s (254.6ms/op) |
| `argon2id_verify` | 3.931 ops/s (254.4ms/op) |
| Memory per op | ~64 MB |
| Semaphore limit | 20 concurrent |
| Max memory footprint | 20 × 64MB = **1.28 GB** |

---

## Chiến Lược Tối Ưu — So Sánh Tổng Hợp

### Comparison Matrix

| Strategy | Throughput Gain | Security Impact | Effort | Risk | Recommendation |
|----------|:-:|:-:|:-:|:-:|:-:|
| **S1: Tune Parameters (OWASP Profile)** | ⭐⭐⭐ (2-3x) | ⚠️ Lower margin | 🟢 Low | 🟡 Medium | ✅ **Khuyến nghị #1** |
| **S2: Native Binding (argon2-jvm)** | ⭐⭐ (10-30%) | ✅ None | 🟡 Medium | 🟡 Medium | 🔄 Xem xét |
| **S3: Password4j Integration** | ⭐ (5-15%) | ✅ None | 🟡 Medium | 🟢 Low | 🔄 Xem xét |
| **S4: Java FFM API (Panama)** | ⭐⭐ (10-30%) | ✅ None | 🔴 High | 🔴 High | ❌ Chưa recommend |
| **S5: Application-Level Optimization** | ⭐⭐⭐ (Indirect) | ✅ None | 🟢 Low | 🟢 Low | ✅ **Khuyến nghị #2** |
| **S6: Async/Offload Hashing** | ⭐⭐ (UX only) | ✅ None | 🟡 Medium | 🟡 Medium | 🔄 Future |

---

## Chi Tiết Từng Chiến Lược

### S1: Tune Argon2id Parameters (OWASP Alternative Profiles)

**Nguyên lý**: OWASP 2024 cung cấp nhiều profile parameter, không chỉ 1 profile duy nhất. Có thể giảm memory/iterations mà vẫn đạt minimum security.

#### Các Profile OWASP 2024 Approved

| Profile | Memory (KiB) | Iterations | Parallelism | Est. Speed | Security Level |
|---------|:---:|:---:|:---:|:---:|:---:|
| **Hiện tại** | 65536 (64MB) | 3 | 1 | ~254ms | 🛡️ High |
| **OWASP Option A** | 47104 (46MB) | 1 | 1 | ~85ms *(est.)* | 🛡️ Standard |
| **OWASP Option B** | 19456 (19MB) | 2 | 1 | ~95ms *(est.)* | 🛡️ Minimum |
| **Balanced** | 65536 (64MB) | 1 | 1 | ~100ms *(est.)* | 🛡️ Good |

> **⚠️ IMPORTANT**: Giảm parameters = tăng tốc **nhưng** giảm brute-force resistance. Cần đánh giá threat model.

**Phân tích trade-off**:
- Config hiện tại (64MB, t=3): **rất an toàn**, vượt xa OWASP minimum
- Chuyển sang 64MB/t=1: **giảm ~66% thời gian** (3x iterations → 1x), vẫn **trên** OWASP minimum
- Chuyển sang 46MB/t=1: **giảm ~66% thời gian + 28% memory**, đạt OWASP standard

**Khuyến nghị cho auth-service**:
- Profile `m=65536, t=1, p=1` — giảm iterations từ 3→1 giữ memory 64MB
- Expected: **~100ms/op → ~10 ops/s** (cải thiện ~2.5x)
- Lý do: Memory hardness (64MB) quan trọng hơn iterations cho server-side (GPU resistance)

**Implementation**:
```yaml
# application-security.yml
password:
  argon2:
    memory-cost: 65536    # Giữ nguyên 64MB — max memory hardness
    iterations: 1         # Giảm từ 3 → 1 (OWASP minimum = 1)
    parallelism: 1        # Giữ nguyên
```

> ⚠️ Cần re-hash tất cả password đã tồn tại. DelegatingPasswordEncoder + PasswordUpgradeService sẽ handle transparent migration.

---

### S2: Native Binding — argon2-jvm (de.mkammerer)

**Nguyên lý**: Spring Security dùng BouncyCastle (pure Java). Native C implementation qua JNA có thể nhanh hơn 10-30% nhờ SIMD optimization.

| Aspect | BouncyCastle (hiện tại) | argon2-jvm |
|--------|:-:|:-:|
| Implementation | Pure Java | Native C via JNA |
| SIMD Support | ❌ No | ✅ SSE2/AVX2 |
| Portability | ✅ Any JVM | ⚠️ Need native lib |
| GC Pressure | 🟡 Medium | 🟢 Low (off-heap) |
| Spring Integration | ✅ Built-in | ❌ Custom wrapper needed |
| Maintenance | ✅ Spring manages | ⚠️ Self-managed |

**Dependency**: `de.mkammerer:argon2-jvm:2.12` (~200KB + native libs)

**Trade-offs**:
- ✅ Better performance nhờ native C SIMD instructions
- ✅ Less GC pressure (memory allocated off-heap)
- ❌ Cần manage native library cho từng platform (linux-x86_64, macos-aarch64...)
- ❌ Cần custom `PasswordEncoder` wrapper thay vì dùng Spring built-in
- ❌ Có thể break khi nâng Spring Security version

**Verdict**: Nếu đang deploy trong Docker Linux containers (consistent platform), đây là viable option. Nhưng effort cao hơn S1.

---

### S3: Password4j Integration

**Nguyên lý**: Spring Security 7.x hỗ trợ `Argon2Password4jPasswordEncoder` — alternative impl optimized cho JVM.

| Aspect | BouncyCastle | Password4j |
|--------|:-:|:-:|
| JVM Optimization | 🟡 General | ✅ JVM-specific |
| API | Low-level | ✅ Fluent, developer-friendly |
| Spring Support | ✅ Built-in | ✅ Spring Security 7.x native |
| Dependencies | bcprov-jdk18on | password4j |
| Performance | Baseline | ⭐ 5-15% improvement (est.) |

**Trade-offs**:
- ✅ Drop-in replacement trong Spring Security ecosystem
- ✅ Không cần native libraries
- ⚠️ Performance gain nhỏ hơn native binding
- ⚠️ Cần verify compatibility với Spring Boot 4.x

**Verdict**: Low-risk option, nhưng performance gain không lớn bằng parameter tuning.

---

### S4: Java FFM API (Project Panama, Java 22+)

**Nguyên lý**: Java 25 hỗ trợ FFM API (finalized) — modern replacement cho JNI/JNA. Có thể gọi native Argon2 C library trực tiếp không qua JNA overhead.

**Trade-offs**:
- ✅ Better than JNA (no bridge overhead)
- ✅ Type-safe, bounds-checked memory
- ❌ Cần viết FFM binding từ đầu (no library sẵn)
- ❌ High effort, experimental cho production
- ❌ Argon2 computation time >> call overhead → marginal gain

**Verdict**: Overkill cho use case này. Argon2 computation (100-250ms) >> FFM call overhead (~microseconds). Không đáng effort.

---

### S5: Application-Level Optimization ✅

**Nguyên lý**: Giảm số lần gọi `argon2id_verify` thay vì tối ưu từng lần gọi.

#### 5.1 Password History Check Optimization

**Vấn đề hiện tại**: `checkPasswordHistory()` gọi `passwordEncoder.matches()` cho mỗi entry trong history (O(N)):

```kotlin
// PasswordPolicyService.kt:57
return history.none { passwordEncoder.matches(newPassword, it.passwordHash) }
```

Với `historyCount = 5` → **5 lần verify** → **5 × 254ms = ~1.27 giây** chỉ cho password history check!

**Giải pháp: Early-exit + Short-circuit**:
- Logic hiện tại `history.none {}` đã lazy (Kotlin short-circuits nếu match) → ✅ OK
- Nhưng worst case (password chưa dùng) vẫn cần N verifications

**Giải pháp nâng cao: Hash fingerprint caching**:
- Lưu thêm một `hashFingerprint` (first 16 chars of hash) để pre-filter
- Chỉ gọi full verify khi fingerprint match
- **Risk**: Argon2 dùng random salt → mỗi hash khác nhau → fingerprint sẽ KHÔNG match → luôn skip full verify → BUG!
- **❌ Không khả thi** cho memory-hard hash với random salt

**Giải pháp thực tế: Limit history verification count**:
- Giảm `historyCount` từ config nếu quá cao
- Default 3 thay vì 5 → giảm 40% verify calls

#### 5.2 Ensure Single Verify per Login

Login flow hiện tại chỉ gọi verify **1 lần** tại `LoginHandler.kt:105` → ✅ Đã tối ưu

#### 5.3 Session-Based Authentication (Avoid Re-verify)

Hệ thống đã sử dụng JWT → sau login, mọi request chỉ verify JWT signature (~19K ops/s) → ✅ Đã tối ưu

---

### S6: Async/Offload Hashing

**Nguyên lý**: Offload Argon2 computation sang dedicated thread pool hoặc separate service.

**Trạng thái hiện tại**: 
- `ConcurrencyLimitedPasswordEncoder` đã có Semaphore(20) → throttling tốt
- Spring Virtual Threads có thể handle blocking I/O nhưng Argon2 là CPU-bound → Virtual Threads không giúp ích

**Giải pháp**: Dedicated Argon2 worker pool (FixedThreadPoolExecutor) tách biệt khỏi request threads:
- ✅ Không block request thread
- ❌ Thêm complexity
- ❌ Latency vẫn ~254ms (chỉ giảm resource contention)

**Verdict**: Useful ở scale lớn, nhưng ở current load, ConcurrencyLimitedPasswordEncoder đã đủ.

---

## Gap Analysis

| Aspect | Status | Gap |
|--------|:---:|-----|
| OWASP Compliance | ✅ | Vượt mức minimum (có thể giảm) |
| BouncyCastle Performance | ⚠️ | Pure Java, no SIMD, GC pressure |
| Password History N+1 | ⚠️ | O(N) verify calls in change password |
| Login Path | ✅ | Single verify, well optimized |
| Session Auth | ✅ | JWT signature verify (19K ops/s) |
| Concurrency Control | ✅ | Semaphore(20), configurable |

---

## Recommendation Summary

### 🏆 Recommended Strategy: S1 + S5 (Combined)

```
┌──────────────────────────────────────────────────┐
│           RECOMMENDED OPTIMIZATION PLAN          │
├──────────────────────────────────────────────────┤
│                                                  │
│  Phase 1: Parameter Tuning (S1)                  │
│  ├── Giảm iterations: 3 → 1                      │
│  ├── Giữ memory: 64MB (max GPU resistance)       │
│  ├── Expected: ~2.5x throughput improvement      │
│  ├── Risk: Low (still above OWASP minimum)       │
│  └── Effort: Config change only                  │
│                                                  │
│  Phase 2: Application Optimization (S5)          │
│  ├── Audit password history count                │
│  ├── Consider limiting historyCount              │
│  ├── Verify no redundant verify calls            │
│  └── Effort: Minimal code change                 │
│                                                  │
│  Phase 3 (Future): Native Binding (S2)           │
│  ├── Evaluate argon2-jvm in staging              │
│  ├── Benchmark against BouncyCastle              │
│  ├── Only if Phase 1 insufficient                │
│  └── Effort: Medium                              │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Expected Results After Phase 1

| Metric | Before | After (est.) | Improvement |
|--------|:---:|:---:|:---:|
| Verify time | ~254ms | ~85-100ms | **2.5-3x faster** |
| Throughput | ~3.93 ops/s | ~10-12 ops/s | **2.5-3x higher** |
| Memory/op | 64MB | 64MB | Same |
| Max concurrent | 20 | 20 | Same |
| OWASP compliance | ✅ Above min | ✅ Still above min | ✅ |

---

## Security Assessment

### Parameter Change Risk Matrix

| Threat | m=64MB, t=3 (current) | m=64MB, t=1 (proposed) | Impact |
|--------|:-:|:-:|:---:|
| GPU brute-force | 🛡️🛡️🛡️ Excellent | 🛡️🛡️ Very Good | ⬇️ Minor: memory hardness still 64MB |
| ASIC attack | 🛡️🛡️🛡️ Excellent | 🛡️🛡️ Very Good | ⬇️ Minor: t=1 still meets OWASP |
| Dictionary attack | 🛡️🛡️🛡️ | 🛡️🛡️ | ⬇️ Minor: rate limiting compensates |
| Side-channel | 🛡️🛡️ | 🛡️🛡️ | ↔️ Same (Argon2id hybrid mode) |

> **Key insight**: Memory hardness (m parameter) là yếu tố quan trọng nhất chống GPU/ASIC attacks. Giảm iterations từ 3→1 giảm **time cost** nhưng **không giảm memory barrier** — attacker vẫn cần 64MB RAM per thread.

> **Mitigating controls**: Rate limiting (3 attempts/15min lock), CAPTCHA after failures, account lock — đã triển khai trong auth-service.

---

## Sources

| Source | URL | Relevance |
|--------|-----|:-:|
| OWASP Password Storage Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html | ⭐⭐⭐ |
| Argon2 RFC Draft | https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-argon2 | ⭐⭐⭐ |
| Spring Security Argon2 Docs | https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html | ⭐⭐ |
| Password4j Documentation | https://password4j.com | ⭐⭐ |
| argon2-jvm GitHub | https://github.com/phxql/argon2-jvm | ⭐⭐ |
