# Open Source Findings — Argon2id Verify Performance Optimization

## Context

Research các thư viện và approach để tối ưu Argon2id verify performance trong JVM ecosystem.

## 1. BouncyCastle (Current — org.bouncycastle:bcprov-jdk18on)

**Repository**: https://github.com/bcgit/bc-java
**Score**: 7.5/10

| Tiêu chí | Score | Reasoning |
|----------|:---:|-----------|
| Feature completeness | 9/10 | Full Argon2 (i/d/id) + complete crypto suite |
| Applicability | 10/10 | Already in use, Spring Security built-in |
| Activity | 9/10 | Active development, regular releases |
| Documentation | 7/10 | API docs good, Argon2-specific examples sparse |
| Code quality | 8/10 | Well-tested, FIPS certified |
| Community | 8/10 | ~1000+ stars, widely adopted |
| Popularity | 9/10 | Industry standard |

**Gap Analysis**:
- ✅ Full Argon2id support
- ✅ Spring Security integration
- ❌ No SIMD optimization (pure Java)
- ❌ GC pressure from heap memory allocation
- ⚠️ Not specifically optimized for JVM password hashing

**Verdict**: Production-ready, nhưng không phải performance-optimal choice

## 2. argon2-jvm (de.mkammerer)

**Repository**: https://github.com/phxql/argon2-jvm
**Score**: 6.8/10

| Tiêu chí | Score | Reasoning |
|----------|:---:|-----------|
| Feature completeness | 8/10 | Full Argon2 support, encode/verify |
| Applicability | 6/10 | Need custom PasswordEncoder wrapper |
| Activity | 5/10 | Less active recently |
| Documentation | 6/10 | Good README, few examples |
| Code quality | 7/10 | Has tests, clean code |
| Community | 6/10 | ~280 stars |
| Popularity | 5/10 | Niche usage |

**Gap Analysis**:
- ✅ Native C via JNA → SIMD support
- ✅ Off-heap memory → less GC pressure
- ❌ Need custom Spring PasswordEncoder wrapper
- ❌ Native library per platform
- ❌ Less active maintenance
- ⚠️ JNA overhead (minor)

**Verdict**: Tham khảo pattern, không dùng trực tiếp. Risk quá cao cho production auth.

## 3. Password4j

**Repository**: https://github.com/Password4j/password4j
**Score**: 7.2/10

| Tiêu chí | Score | Reasoning |
|----------|:---:|-----------|
| Feature completeness | 9/10 | Argon2, BCrypt, SCrypt, PBKDF2 |
| Applicability | 8/10 | Spring Security 7.x support |
| Activity | 7/10 | Regular updates |
| Documentation | 8/10 | Good docs + examples |
| Code quality | 7/10 | Tests, clean API |
| Community | 6/10 | ~400+ stars |
| Popularity | 6/10 | Growing adoption |

**Gap Analysis**:
- ✅ JVM-optimized pure Java
- ✅ Spring Security integration (`Argon2Password4jPasswordEncoder`)
- ✅ No native dependencies
- ✅ Fluent API
- ⚠️ Performance gain small vs BouncyCastle (~5-15%)
- ⚠️ Cần verify compatibility với Spring Boot 4.x

**Verdict**: Viable upgrade path, low risk. Nhưng parameter tuning cho gain lớn hơn.

## Overall Recommendation

| Approach | Verdict |
|----------|---------|
| Keep BouncyCastle + **tune parameters** | ✅ **Best ROI** |
| Switch to Password4j | 🔄 Consider nếu cần thêm gain |
| Switch to argon2-jvm native | ❌ Too risky cho production auth |
| Build FFM binding | ❌ Overkill |
