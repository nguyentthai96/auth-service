# Proposal: Argon2id Password Encoder Migration

> **Change**: argon2id-password-encoder
> **Type**: EXTEND
> **Status**: Proposed
> **Date**: 2026-09-08

## 1. Tổng quan

Chuyển đổi password hashing algorithm từ BCrypt (strength=12) sang Argon2id trong `auth-service`, tổ chức dưới dạng Spring Bean auto-configuration cho phép DevOps cấu hình linh hoạt qua YAML.

### Vấn đề hiện tại
- BCrypt chiếm **95.2% login latency** (~252ms, JMH benchmark)
- BCrypt chỉ cung cấp **CPU-hard** protection — dễ bị GPU/ASIC brute-force
- Không có khả năng switch algorithm mà không cần thay đổi code
- Không comply OWASP 2024 recommendation (Argon2id)

### Giải pháp đề xuất
- Sử dụng `DelegatingPasswordEncoder` + `PasswordEncoderAutoConfiguration`
- Argon2id default encoder (OWASP 2024: m=65536, t=3, p=1)
- Backward compatible với BCrypt hashes qua prefix-based routing
- Rehash-on-login transparent migration — zero downtime, no forced reset

## 2. Scope

### In Scope
- PasswordEncoderAutoConfiguration (NEW) — Bean auto-config
- PasswordUpgradeService (NEW) — Rehash-on-login
- SecurityProperties.PasswordProperties (MODIFY) — Add algorithm + argon2 params
- SecurityConfig (MODIFY) — Remove passwordEncoder bean
- LoginHandler (MODIFY) — Inject PasswordUpgradeService
- Flyway migration — Add `{bcrypt}` prefix to existing hashes
- build.gradle.kts — Promote BouncyCastle dependency

### Out of Scope
- Frontend changes (transparent backend change)
- API contract changes (no new endpoints)
- Force password reset for existing users
- Admin dashboard UI for algorithm configuration

## 3. Business Value

| # | Value | Priority |
|---|-------|----------|
| 1 | **Security upgrade**: GPU-resistant Argon2id (OWASP 2024) | P0 |
| 2 | **Operational flexibility**: Algorithm switch via YAML | P1 |
| 3 | **Future-proof**: DelegatingPasswordEncoder → easy algorithm migration | P1 |
| 4 | **Zero downtime**: Rehash-on-login, no forced reset | P0 |

## 4. Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Memory pressure (64MB/hash) | HIGH | MEDIUM | Semaphore(20) concurrency limit |
| Flyway migration ordering | HIGH | LOW | setDefaultPasswordEncoderForMatches safety net |
| Password history cross-algorithm | MEDIUM | LOW | DelegatingPasswordEncoder.matches() auto-handles |

## 5. Success Criteria

- [ ] Tất cả new passwords encoded bằng Argon2id
- [ ] Existing BCrypt hashes verify thành công
- [ ] Login latency p95 ≤ 500ms với Argon2id
- [ ] Rehash-on-login hoạt động transparent
- [ ] Algorithm switchable via YAML restart
- [ ] Zero downtime migration — no forced password reset
- [ ] JMH benchmark xác nhận Argon2id performance acceptable
