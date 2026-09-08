# Web Research — Argon2id Verify Performance Optimization

## Search Strategy

| Iteration | Query | Results |
|:-:|-------|---------|
| 1 | "argon2id verify performance optimization best practices" | Calibration strategy, parameter tuning |
| 2 | "argon2id parameter tuning memory iterations parallelism OWASP" | OWASP profiles, security trade-offs |
| 3 | "Spring Security Argon2PasswordEncoder BouncyCastle alternative" | Password4j, argon2-jvm options |
| 4 | "argon2-jvm native JNA vs BouncyCastle benchmark" | Native binding performance |
| 5 | "Java 21+ FFM API argon2 native call Panama" | Modern Java FFM approach |
| 6 | "argon2id server side caching avoid repeated verification" | Session-based auth pattern |

## Finding 1: Time-Target Calibration Strategy

**Source**: Industry best practices, Argon2 RFC Draft

**Key Insights**:
- Target latency cho interactive login: **100–500ms** (thay vì chọn parameters tùy ý)
- Strategy: **Maximize memory → benchmark iterations → adjust to target**
- Memory hardness (m parameter) quan trọng nhất cho GPU/ASIC resistance
- Iterations (t) là fine-tuning knob — giảm nếu đã có enough memory

**Relevance**: ⭐⭐⭐ — Trực tiếp applicable cho auth-service

## Finding 2: OWASP 2024 Multiple Profiles

**Source**: OWASP Password Storage Cheat Sheet 2024

**Key Insights**:
- OWASP cung cấp **nhiều** profile, không chỉ 1:
  - Option 1: m=19MiB, t=2, p=1 (minimum)
  - Option 2: m=46MiB, t=1, p=1 (memory-focused)
  - Custom: m=64MiB, t=1, p=1 (max memory, min iterations)
- **Kết luận**: Config hiện tại (m=64MB, t=3) **vượt xa** OWASP minimum
- Có thể giảm t=3→1 mà vẫn trên OWASP minimum

**Relevance**: ⭐⭐⭐ — Trực tiếp cho phép parameter tuning

## Finding 3: Spring Security Alternative — Password4j

**Source**: Spring Security 7.x documentation

**Key Insights**:
- `Argon2Password4jPasswordEncoder` — drop-in replacement cho BouncyCastle-backed impl
- Password4j optimized cho JVM, fluent API
- Performance gain ~5-15% (estimate, parameter-dependent)
- Không cần native libraries

**Relevance**: ⭐⭐ — Viable nhưng gain nhỏ hơn parameter tuning

## Finding 4: Native Binding — argon2-jvm

**Source**: https://github.com/phxql/argon2-jvm

**Key Insights**:
- JNA wrapper cho native C Argon2 implementation
- Supports SIMD (SSE2/AVX2) → better performance than pure Java
- Less GC pressure (off-heap memory)
- Cần manage native libs per platform
- Estimated 10-30% improvement over BouncyCastle

**Relevance**: ⭐⭐ — Good option cho Docker Linux deployment

## Finding 5: Java FFM API (Project Panama)

**Source**: JDK 22+ FFM API documentation

**Key Insights**:
- Modern replacement cho JNI/JNA — finalized in Java 22
- Better performance than JNA (no bridge overhead)
- Type-safe memory management via MemorySegment + Arena
- BUT: Call overhead là negligible so với Argon2 computation time (μs vs ms)
- No existing library cho Argon2 FFM binding

**Relevance**: ⭐ — Overkill cho use case này

## Finding 6: Session-Based Auth (Avoid Re-verify)

**Source**: Industry standard, auth-service already implements

**Key Insights**:
- NEVER re-verify password on every request
- Use JWT/session tokens after initial auth → verify signature instead (~19K ops/s)
- Auth-service ĐÃ implement pattern này correctly
- Rate limiting + CAPTCHA compensates cho slower hash

**Relevance**: ⭐⭐⭐ — Confirms auth-service architecture is already optimized at application level

## Summary of Findings

| Finding | Impact | Effort | Recommendation |
|---------|:---:|:---:|:---:|
| Parameter tuning (t=3→1) | ⭐⭐⭐ High | 🟢 Config only | ✅ **Do first** |
| Password4j | ⭐ Low-medium | 🟡 Medium | 🔄 Consider later |
| argon2-jvm native | ⭐⭐ Medium | 🟡 Medium | 🔄 Consider if needed |
| Java FFM | ⭐ Low | 🔴 High | ❌ Not worth it |
| Session auth | ✅ Already done | - | ✅ Confirmed good |
| Reduce history checks | ⭐ Low | 🟢 Low | ✅ Quick win |
