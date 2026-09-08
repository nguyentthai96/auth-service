# Proposal: argon2id-verify-optimization

> **Type**: MAINTENANCE | **Flow**: Command | **Status**: Proposed

## Tóm tắt

Tối ưu hiệu năng Argon2id password verification bằng cách giảm `iterations` từ 3→1 trong `application-security.yml`, giữ nguyên memory hardness 64MB. Không thay đổi code, không thay đổi database.

## Vấn đề

Argon2id verify hiện tại đạt **6.25 ops/s** (~160ms/op) với config `m=65536, t=3, p=1`. Cấu hình này vượt xa OWASP 2024 minimum requirement. Throughput có thể cải thiện **+113%** bằng cách giảm iterations xuống minimum OWASP-compliant.

## Giải pháp đề xuất

Thay đổi 1 dòng YAML config:
```diff
# application-security.yml, line 67
-    iterations: 3                 # Time cost (OWASP 2024: 3)
+    iterations: 1                 # Time cost (OWASP 2024: ≥ 1, tuned from 3)
```

## Phạm vi

- **In scope**: Thay đổi `iterations` config, YAML comment update
- **Out of scope**: Code changes, database migration, Semaphore tuning, memory cost changes

## Metrics kỳ vọng

| Metric | Before | After | Improvement |
|--------|:---:|:---:|:---:|
| Verify throughput | 6.25 ops/s | 13.33 ops/s | +113% |
| Verify latency | ~160ms | ~75ms | −53% |
| History check (5x) | ~800ms | ~375ms | −53% |
| Login capacity (8 threads) | ~38/s | ~82/s | +116% |
| Memory per op | 64MB | 64MB | Unchanged |

## Risks

| # | Risk | Severity | Mitigation |
|---|------|:---:|---|
| 1 | Brute-force speed tăng 3x | 🟡 | Rate limiting + CAPTCHA + account lock (5 layers) |

## Dependencies

Không có dependency ngoài. Change hoàn toàn self-contained.

## Rollback Plan

Revert `iterations: 3` → automatic re-hash forward. Zero-downtime.
