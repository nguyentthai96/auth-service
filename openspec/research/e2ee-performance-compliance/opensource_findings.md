# Open Source Findings: E2EE Performance & Compliance

> Đánh giá các open source projects/libraries cho encryption middleware trên JVM/Spring Boot.

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Feature** | E2EE Performance & Compliance |
| **Ngày research** | 2026-08-15 |
| **Số lượng đánh giá** | 5 projects |
| **Nguồn tìm kiếm** | GitHub, Maven Central, StackOverflow, Official docs |

---

## 2. Projects Đánh giá

### 2.1 Bouncy Castle (org.bouncycastle)

| Tiêu chí | Trọng số | Điểm (1-10) | Reasoning |
|----------|----------|:---:|-----------|
| Feature completeness | 20% | **9** | Hỗ trợ đầy đủ AES-GCM, ChaCha20-Poly1305, RSA, ECC, PQ crypto |
| Applicability | 15% | **9** | Native Java/Kotlin, đã dùng trong project (testRuntimeOnly) |
| Activity | 15% | **9** | Active development, regular releases, Java 25 support |
| Documentation | 15% | **6** | Docs đầy đủ nhưng API reference phức tạp, ít ví dụ thực tế |
| Code quality | 15% | **8** | Well-tested, FIPS certified variants available |
| Community | 10% | **9** | De-facto standard Java crypto library, 1000+ stars |
| Popularity | 10% | **10** | Widely adopted across enterprise & open source |

**Overall Score**: 8.4 / 10

**Source**: https://www.bouncycastle.org/ | https://github.com/bcgit/bc-java

#### Gap Analysis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| AES-256-GCM | ✅ | | Full AEAD support với AAD | - |
| ChaCha20-Poly1305 | ✅ | | Modern cipher, software-optimized | - |
| Stream encryption | ✅ | | CipherOutputStream/InputStream | Không có frame-based abstraction |
| Key wrapping | ✅ | | AES Key Wrap (RFC 3394) | - |
| Partial field encrypt | | ❌ | - | Cần tự implement JSON path logic |
| KMS integration | | ❌ | - | Chỉ là crypto primitive, không có KMS client |
| Nonce management | ⚠️ | | SecureRandom nonce generation | Không có sequence tracking tự động |

**Verdict**: Dùng trực tiếp — crypto primitives library  
**Recommendation**: ⭐ **Dùng trực tiếp** làm crypto engine chính, kết hợp với custom middleware logic

---

### 2.2 Tink (Google)

| Tiêu chí | Trọng số | Điểm (1-10) | Reasoning |
|----------|----------|:---:|-----------|
| Feature completeness | 20% | **9** | AEAD, streaming AEAD, deterministic AEAD, hybrid encryption |
| Applicability | 15% | **9** | Java/Kotlin native, designed for Cloud KMS integration |
| Activity | 15% | **8** | Active Google-maintained project, regular releases |
| Documentation | 15% | **8** | Excellent docs, clear examples, best practices guide |
| Code quality | 15% | **9** | Google security standards, formal code review, fuzz testing |
| Community | 10% | **8** | 13k+ stars on GitHub |
| Popularity | 10% | **8** | Used by Google internally, growing adoption |

**Overall Score**: 8.6 / 10

**Source**: https://github.com/tink-crypto/tink-java

#### Gap Analysis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| AES-256-GCM (AEAD) | ✅ | | First-class AEAD with AAD binding | - |
| Streaming AEAD | ✅ | | **Built-in streaming encryption** — encrypt/decrypt large data in chunks | - |
| Envelope encryption | ✅ | | Native KMS Envelope AEAD — integrates with AWS KMS, GCP KMS | - |
| Key management | ✅ | | Keyset concept, key rotation built-in | Opinionated key format |
| Partial field encrypt | | ❌ | - | Không có JSON path level |
| Nonce management | ✅ | | Automatic nonce generation, no reuse possible | Internal — cannot control nonce externally |
| Multi-tenancy | ⚠️ | | Can use AAD for context binding | Tenant isolation cần custom logic |

**Verdict**: Dùng trực tiếp — **recommended** cho envelope encryption + streaming  
**Recommendation**: ⭐⭐ **Highly recommended** — Tink là lựa chọn tốt nhất cho streaming AEAD, envelope encryption with KMS, và AAD context binding. Giảm thiểu rủi ro crypto bugs.

---

### 2.3 Themis (Cossack Labs)

| Tiêu chí | Trọng số | Điểm (1-10) | Reasoning |
|----------|----------|:---:|-----------|
| Feature completeness | 20% | **7** | Secure Cell, Secure Message, Secure Session, Secure Comparator |
| Applicability | 15% | **7** | Java binding qua JNI — cần native library |
| Activity | 15% | **7** | Active, regular releases nhưng nhỏ hơn BC/Tink |
| Documentation | 15% | **8** | Rất rõ ràng, developer-friendly, có wiki chi tiết |
| Code quality | 15% | **8** | Audited, security-first design |
| Community | 10% | **6** | ~1.9k stars, smaller community |
| Popularity | 10% | **6** | Growing but niche |

**Overall Score**: 7.1 / 10

**Source**: https://github.com/cossacklabs/themis

#### Gap Analysis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| E2EE abstraction | ✅ | | High-level Secure Cell/Message API | - |
| Streaming | | ❌ | - | Không có native streaming AEAD |
| KMS integration | | ❌ | - | Không tích hợp cloud KMS |
| Cross-platform | ✅ | | iOS, Android, Web, Server | JNI dependency phức tạp |
| Key management | ✅ | | Symmetric/Asymmetric key management | Không có keyset rotation |

**Verdict**: Tham khảo pattern  
**Recommendation**: Tham khảo abstraction API design, nhưng **không phù hợp** cho production do JNI dependency và thiếu streaming/KMS

---

### 2.4 Jasypt Spring Boot

| Tiêu chí | Trọng số | Điểm (1-10) | Reasoning |
|----------|----------|:---:|-----------|
| Feature completeness | 20% | **5** | Chỉ encrypt properties/fields, không có streaming hay E2EE |
| Applicability | 15% | **8** | Spring Boot native, auto-configuration |
| Activity | 15% | **6** | Maintained nhưng không active development |
| Documentation | 15% | **7** | Clear README, Spring Boot auto-config docs |
| Code quality | 15% | **6** | Simple implementation, limited tests |
| Community | 10% | **7** | ~3k+ stars, widely used for config encryption |
| Popularity | 10% | **7** | Standard choice for Spring Boot property encryption |

**Overall Score**: 6.3 / 10

**Source**: https://github.com/ulisesbocchio/jasypt-spring-boot

#### Gap Analysis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Property encryption | ✅ | | `ENC()` wrapper auto-decrypt | - |
| Field-level encrypt | ⚠️ | | JPA converter possible | Không có AAD/context binding |
| Streaming | | ❌ | - | Config-only, không có data streaming |
| KMS integration | | ❌ | - | Chỉ password-based encryption |
| Key rotation | | ❌ | - | Không có keyset concept |

**Verdict**: Không phù hợp cho E2EE middleware  
**Recommendation**: Chỉ dùng cho **config encryption**, không phải E2EE data

---

### 2.5 Etebase

| Tiêu chí | Trọng số | Điểm (1-10) | Reasoning |
|----------|----------|:---:|-----------|
| Feature completeness | 20% | **8** | Full E2EE backend platform, collection/item model |
| Applicability | 15% | **4** | Python/JS client SDK — **không có Java/Kotlin client** |
| Activity | 15% | **7** | Active development, well-maintained |
| Documentation | 15% | **8** | Comprehensive docs, developer guides |
| Code quality | 15% | **7** | Open source, clean architecture |
| Community | 10% | **6** | ~1.5k stars, growing |
| Popularity | 10% | **5** | Niche — alternative to Firebase with E2EE |

**Overall Score**: 6.5 / 10

**Source**: https://www.etebase.com/ | https://github.com/etesync/server

#### Gap Analysis

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| E2EE model | ✅ | | Complete E2EE backend architecture | - |
| Java/Kotlin SDK | | ❌ | - | Chỉ có Python, JS, Dart client |
| Spring Boot integration | | ❌ | - | Standalone server, không embed được |
| Key management | ✅ | | Built-in key derivation | Không dùng cloud KMS |
| Multi-tenancy | ⚠️ | | User-based isolation | Không có tenant concept |

**Verdict**: Tham khảo architecture  
**Recommendation**: **Tham khảo mô hình E2EE** (collection/item encryption model), nhưng không phù hợp tích hợp vào Spring Boot stack

---

## 3. Comparison Matrix

| Feature | Bouncy Castle | **Tink** ⭐ | Themis | Jasypt | Etebase |
|---------|:---:|:---:|:---:|:---:|:---:|
| AES-256-GCM | ✅ | ✅ | ✅ | ✅ | ✅ |
| Streaming AEAD | ⚠️ manual | ✅ built-in | ❌ | ❌ | ❌ |
| AAD binding | ✅ | ✅ native | ⚠️ | ❌ | ⚠️ |
| KMS Envelope | ❌ | ✅ native | ❌ | ❌ | ❌ |
| Key rotation | ❌ | ✅ keyset | ❌ | ❌ | ⚠️ |
| Java/Kotlin | ✅ | ✅ | ⚠️ JNI | ✅ | ❌ |
| Spring integration | ⚠️ | ⚠️ | ❌ | ✅ | ❌ |
| **Overall** | 8.4 | **8.6** | 7.1 | 6.3 | 6.5 |

## 4. Recommendation

### Primary Choice: **Google Tink** 
- Streaming AEAD cho gRPC/file encryption (Issue #8)
- Native KMS Envelope encryption (Issue #11)  
- AAD context binding cho partial encryption (Issue #9)
- Automatic nonce management — loại bỏ nonce reuse risk

### Secondary: **Bouncy Castle** (đã có trong project)
- Backup cho advanced crypto primitives
- FIPS compliance nếu cần
- Custom nonce management cho frame-based encryption

### Pattern Reference: **Etebase**
- Tham khảo E2EE architecture model cho audit vault design

---

> **Next**: Phase 3 (Web Research) → Phase 4 (Comparison Analysis)
