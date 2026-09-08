# Comparison Analysis — Password Hashing Libraries cho auth-service

## 1. Scoring Matrix

| Criteria (Weight) | Spring Security Argon2 | de.mkammerer argon2-jvm | BouncyCastle Direct |
|---|---|---|---|
| **Spring Integration** (25%) | ⭐⭐⭐⭐⭐ (native) | ⭐⭐ (manual) | ⭐⭐⭐ (partial) |
| **Cross-platform** (20%) | ⭐⭐⭐⭐⭐ (pure Java) | ⭐⭐⭐ (JNA native) | ⭐⭐⭐⭐⭐ (pure Java) |
| **Performance** (15%) | ⭐⭐⭐⭐ (good) | ⭐⭐⭐⭐⭐ (fastest) | ⭐⭐⭐⭐ (good) |
| **Maintenance** (15%) | ⭐⭐⭐⭐⭐ (Spring team) | ⭐⭐⭐ (community) | ⭐⭐⭐⭐⭐ (BC team) |
| **API Simplicity** (15%) | ⭐⭐⭐⭐⭐ (PasswordEncoder) | ⭐⭐⭐ (custom API) | ⭐⭐ (low-level) |
| **Ecosystem Fit** (10%) | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| **Total** | **4.65/5** | **3.15/5** | **3.80/5** |

## 2. Recommendation

> **🏆 Spring Security `Argon2PasswordEncoder`** — best fit cho auth-service

### Lý do:
1. **Zero-friction integration**: Implements `PasswordEncoder` interface → plug-and-play, tất cả consumers không cần thay đổi
2. **DelegatingPasswordEncoder**: Built-in support cho BCrypt backward compatibility
3. **BouncyCastle already in project**: `bcprov-jdk18on:1.80` đã có (cần promote từ test → implementation)
4. **Spring team maintained**: Security patches theo Spring release cycle
5. **Pure Java**: Không cần native binary — phù hợp Docker/K8s deployment

### Trade-offs:
- Chậm hơn native C implementation ~10-15% (nhưng đây là intentional cost, không phải bottleneck optimization)
- BouncyCastle dependency (~6MB) — đã có sẵn

## 3. BCrypt vs Argon2id Feature Comparison

| Feature | BCrypt (current) | Argon2id (proposed) |
|---------|-----------------|-------------------|
| **Algorithm Type** | CPU-hard only | Memory-hard + CPU-hard |
| **GPU/ASIC Resistance** | ⚠️ Moderate | ✅ Strong (memory-bound) |
| **Configurability** | 1 param (rounds) | 3 params (memory, time, parallelism) |
| **OWASP Status 2024** | "Acceptable" | **"Recommended"** |
| **Hash Format** | `$2a$12$...` | `$argon2id$v=19$m=65536,t=3,p=1$...` |
| **Spring Support** | `BCryptPasswordEncoder` | `Argon2PasswordEncoder` |
| **Memory Usage** | ~4KB fixed | Configurable (19MB-64MB per hash) |
| **Latency (estimated)** | 252ms (strength=12) | ~250-400ms (tunable) |

## 4. Gap Analysis

| Gap | Impact | Mitigation |
|-----|--------|-----------|
| DB hashes no prefix | HIGH — DelegatingPasswordEncoder cần prefix | Migration: thêm `{bcrypt}` prefix cho existing rows |
| BouncyCastle scope | LOW — test → implementation | 1-line Gradle change |
| Memory overhead | MEDIUM — Argon2id uses 64MB per hash | Monitor heap, limit concurrent auth |
| Password history | MEDIUM — Old BCrypt hashes in history | DelegatingPasswordEncoder handles both |
| Config schema change | LOW — New YAML properties | Backward-compatible addition |

## 5. Validation Phase 4

- [x] Comparison matrix complete (3 libraries)
- [x] Feature comparison table complete (BCrypt vs Argon2id)
- [x] Gap analysis documented (5 gaps)
- [x] Recommendation provided: Spring Security Argon2PasswordEncoder
