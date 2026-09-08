# Business Analysis — Argon2id Verify Performance Optimization

## 1. Bối Cảnh

Auth-service sử dụng Argon2id (OWASP 2024 recommended) với cấu hình:
- Memory: 64MB, Iterations: 3, Parallelism: 1
- Benchmark: ~3.93 ops/s (254ms/op)

Yêu cầu: Đánh giá khả năng cải thiện throughput mà giữ nguyên security posture.

## 2. Use Cases

### UC-001: Tối Ưu Parameter Tuning

**Mô tả**: Giảm iterations từ 3→1 trong YAML config, giữ memory 64MB.

**Tại sao cần**: Memory hardness (64MB) là yếu tố chính chống GPU/ASIC attacks. Iterations chỉ là multiplicative factor — giảm từ 3→1 giữ nguyên memory barrier mà giảm 66% compute time.

**Basic Flow**:
1. Admin thay đổi `iterations: 1` trong `application-security.yml`
2. Deploy config change
3. New passwords encoded with t=1 (~85ms thay vì 254ms)
4. Old passwords (t=3) vẫn verify đúng (params embedded in hash)
5. `PasswordUpgradeService` tự động rehash khi user login

**Exception Flow**:
- E1: Security audit yêu cầu rollback → revert `iterations: 3` → zero-downtime

### UC-002: Monitor Migration Progress

**Mô tả**: Theo dõi % users đã được migrate sang t=1.

**Tại sao cần**: Cần biết khi nào tất cả users đã chuyển sang profile mới.

**Basic Flow**:
1. Watch Micrometer counter `password.migration.rehash.total`
2. Compare với total active users
3. Khi counter ≈ total users → migration complete

## 3. Business Rules

| Rule | Mô tả |
|------|--------|
| BR-001 | Password verify PHẢI chính xác 100% — không có false positive/negative |
| BR-002 | Migration PHẢI zero-downtime — không force password reset |
| BR-003 | Config thay đổi PHẢI backward compatible — old hashes still verify |
| BR-004 | Security parameters PHẢI >= OWASP minimum |

## 4. Traceability

| Requirement | Use Case | Component | Test |
|------------|----------|-----------|------|
| Improve verify throughput | UC-001 | `application-security.yml` | JMH benchmark |
| Zero-downtime migration | UC-001 | `PasswordUpgradeService` | Integration test |
| Monitor progress | UC-002 | Micrometer counter | Metric query |
| OWASP compliance | UC-001 | Parameter validation | Security review |
