# Web Research: Cache Encryption, Compression & Serialization (Updated)

## Search Iterations

### Iteration 1: Cache Encryption Patterns
**Query**: "Spring Cache Redis encrypted cache data at rest AES encryption two level cache"

**Key Findings**:
1. Client-side encryption là standard — Redis không encrypt per-entry
2. Custom `RedisSerializer` — Implement `RedisSerializer<Object>` để encrypt bằng AES-GCM
3. Key management — NEVER hardcode keys, dùng Vault/env/KeyStore
4. Encrypted data ngăn Redis SORT, INCREMENT operations

### Iteration 2: Partial Field Encryption
**Query**: "Redis partial field encryption selective encryption cache PII GDPR"

**Key Findings**:
1. Field-Level Encryption — encrypt chỉ PII fields (email, SSN, phone)
2. 3 strategies: Field-Level Encryption, Data Masking, Tokenization
3. Custom `@Encrypted` annotation + AOP/serializer intercept
4. GDPR: minimize PII in cache, TLS, ACLs

### Iteration 3: Data Type Serialization
**Query**: "Redis cache optimization data structures Hash vs String vs JSON"

**Key Findings**:
1. Redis Hashes → flat objects (ziplist encoding, memory efficient)
2. Redis Strings (JSON) → complex nested data
3. Formats: JSON (readable), MessagePack (fast), Protobuf (smallest), Kryo (JVM)
4. Compression: LZ4/ZSTD for > 1KB payloads

### Iteration 4: SOTA Compression Libraries (NEW)
**Query**: "best Java JVM compression library 2025 benchmark LZ4 ZSTD Snappy Brotli"

**Key Findings**:
1. **Zstd (zstd-jni:1.5.7-15)** — SOTA: tunable levels (1-22), best ratio/speed balance
2. **LZ4 (lz4-java:1.8.0)** — fastest decompress, lowest ratio
3. Snappy — legacy, outperformed by Zstd/LZ4
4. Brotli — best ratio but slow compress (for static assets, not cache)
5. GZIP — avoid for high-performance cache

**Benchmark Summary** (Source: manishrjain.com, medium.com, journal-jceees.com):
| Algorithm | Compress Speed | Decompress Speed | Ratio |
|-----------|:-----------:|:----------------:|:-----:|
| Zstd L1 | ~500 MB/s | ~1.5 GB/s | 2.5x |
| Zstd L3 | ~300 MB/s | ~1.5 GB/s | 2.8x |
| LZ4 | ~800 MB/s | ~4 GB/s | 2.0x |
| Snappy | ~500 MB/s | ~1.5 GB/s | 2.1x |
| GZIP | ~30 MB/s | ~300 MB/s | 3.0x |

### Iteration 5: Encryption Algorithm Performance (NEW)
**Query**: "AES-GCM vs ChaCha20-Poly1305 vs AES-SIV performance Java JVM"

**Key Findings**:
1. **AES-256-GCM + AES-NI** → fastest on server x86-64 (GB/s throughput)
2. ChaCha20-Poly1305 → faster on mobile/IoT without AES-NI (software-only)
3. AES-SIV → 3-5x slower (2 passes), only for nonce-misuse protection
4. JDK 25 fully supports AES-NI intrinsics
5. All modern server CPUs have AES-NI → **AES-GCM is optimal**

### Iteration 6: Key Management Strategy (NEW)
**Query**: "Java encryption key management strategy KeyStore YAML config AES performance"

**Key Findings**:
1. Strategy Pattern → `EncryptionStrategy` interface → swap algorithms/keys
2. JCEKS/PKCS12 KeyStore → store SecretKey, load once at startup, cache in memory
3. YAML config → keystore path + alias only, password from env var
4. Performance: KeyStore load ~5-20ms (one-time), after that identical to inline key
5. Tink vs JCA: **identical throughput** — Tink wraps JCA internally

### Iteration 7: Google Tink vs JCA Performance (NEW)
**Query**: "Google Tink encryption Java performance benchmark vs JCA Cipher"

**Key Findings**:
1. **Identical core throughput** — Tink delegates to JCE (SunJCE provider)
2. Tink adds ~microseconds for key management overhead — negligible
3. Tink advantages: auto key rotation, safe defaults, prevents nonce reuse
4. JCA advantages: zero dependency, simpler, full control
5. **Recommendation**: For cache encryption (performance-first), JCA direct is sufficient. Tink recommended only if key rotation or cloud KMS is needed.

## Summary

| Topic | Decision | Confidence |
|-------|----------|:----------:|
| Encryption algo | AES-256-GCM (AES-NI) | HIGH |
| Key provider | Strategy: inline (default) + keystore + custom | HIGH |
| Compression | Zstd L3 (SOTA) | HIGH |
| Fast-path compression | LZ4 (optional fallback) | HIGH |
| Serialization | Jackson JSON default, per-cache override | HIGH |
| Tink vs JCA | JCA direct for cache (simpler, same perf) | HIGH |
| Compression threshold | 1KB default | MEDIUM |
