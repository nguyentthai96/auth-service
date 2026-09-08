# Handoff Summary — Argon2id Password Encoder Migration

## Tóm tắt cho Downstream Workflows

### Feature
**BCrypt → Argon2id** password hashing migration với **Bean auto-configuration** pattern.

### Recommendation
**BUILD** — Sử dụng Spring Security `Argon2PasswordEncoder` + `DelegatingPasswordEncoder` (native support, zero external dependencies ngoài BouncyCastle đã có).

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Library | Spring Security Argon2PasswordEncoder | Native integration, pure Java, Spring team maintained |
| Migration Strategy | DelegatingPasswordEncoder + rehash-on-login | Zero downtime, no forced password reset |
| Config Pattern | Bean auto-config via `@ConditionalOnProperty` | DevOps flexibility, algorithm switch via YAML |
| OWASP Params | m=65536 (64MB), t=3, p=1 | OWASP 2024 recommended standard |
| Concurrency | Semaphore(20) limit | Prevent OOM from memory-hard hashing |

### Files to Create/Modify

| Action | File | Purpose |
|--------|------|---------|
| **NEW** | `shared/config/PasswordEncoderAutoConfiguration.kt` | Bean auto-config with DelegatingPasswordEncoder |
| **NEW** | `auth/application/PasswordUpgradeService.kt` | Rehash-on-login service |
| **NEW** | `V__add_password_hash_prefix.sql` | Add `{bcrypt}` prefix to existing hashes |
| **MODIFY** | `shared/config/SecurityProperties.kt` | Add `algorithm`, `argon2` properties |
| **MODIFY** | `shared/config/SecurityConfig.kt` | Remove passwordEncoder bean |
| **MODIFY** | `application-security.yml` | Add argon2 configuration |
| **MODIFY** | `build.gradle.kts` | Promote BouncyCastle to implementation |

### Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Memory pressure from Argon2id | HIGH — 64MB per hash | Semaphore concurrency limit (20) |
| Existing hashes without prefix | HIGH — DelegatingPasswordEncoder fails | Flyway migration adds `{bcrypt}` prefix |
| Password history cross-algorithm | MEDIUM | DelegatingPasswordEncoder.matches() handles both |

### Downstream Workflow Input

```
→ /wf_pre_openspec argon2id-password-encoder
  Input: openspec/research/argon2id-password-encoder/
  Use: business_analysis.md as URD source

→ /wf_openspec argon2id-password-encoder  
  Input: All research artifacts
  Use: technical_spec.md as implementation guide
```

---

## Research Artifacts Index

| File | Phase | Status |
|------|-------|--------|
| [`research_brief.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-password-encoder/research_brief.md) | Phase 1 | ✅ |
| [`comparison_analysis.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-password-encoder/comparison_analysis.md) | Phase 4 | ✅ |
| [`business_analysis.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-password-encoder/business_analysis.md) | Phase 5 | ✅ |
| [`technical_spec.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-password-encoder/technical_spec.md) | Phase 6 | ✅ |
| [`validation_report.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-password-encoder/validation_report.md) | Phase 7 | ✅ |
| [`handoff_summary.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-password-encoder/handoff_summary.md) | Phase 7 | ✅ |
