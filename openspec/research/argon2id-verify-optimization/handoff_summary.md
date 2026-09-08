---
type: research_handoff
feature: argon2id-verify-optimization
date: 2026-09-08
recommendation: build (parameter tuning — config change only)
research_dir: openspec/research/argon2id-verify-optimization/
status: complete
---

# Research Handoff: Argon2id Verify Performance Optimization

## Recommendation

**Giảm Argon2id iterations từ 3→1** (giữ memory 64MB) — cải thiện throughput **113%** (6.2→13.3 ops/s) chỉ với **1 dòng YAML config change**, vẫn đạt OWASP 2024 minimum, backward compatible, zero-downtime migration.

## Key Findings

| Finding | Detail |
|---------|--------|
| **Parameter tuning** | Giảm t=3→1: **13.33 ops/s** (vs 6.25 hiện tại) → **+113%** |
| **OWASP-A profile** | m=46MB, t=1: **24.05 ops/s** → +285% nhưng giảm memory hardness |
| **OWASP-low profile** | m=19MB, t=2: **33.70 ops/s** → +439% nhưng giảm đáng kể security |
| **Alternative libs** | Password4j (~5-15%), argon2-jvm (~10-30%) — effort cao, gain nhỏ hơn S1 |
| **Application level** | Login path đã optimized (1 verify/login). JWT verify ~19K ops/s (không bottleneck) |

## JMH Benchmark Results (Validated)

```
┌──────────────────────────────────────────────────────────────────┐
│  Argon2id Profile Comparison — Verify Throughput (ops/s)         │
│                                                                  │
│  bcrypt (str=12)        ██████████ 4.15                          │
│  argon2id (64MB, t=3)   ███████████████ 6.25     ← current      │
│  argon2id (64MB, t=1)   ██████████████████████████ 13.33  ← ✅   │
│  argon2id (46MB, t=1)   ████████████████████████████████████ 24.05│
│  argon2id (19MB, t=2)   ██████████████████████████████████████ 33.70│
│                                                                  │
│  ← Slower                                       Faster →        │
└──────────────────────────────────────────────────────────────────┘
```

## Implementation Summary

```diff
# application-security.yml — chỉ 1 dòng thay đổi
 password:
   argon2:
-    iterations: 3
+    iterations: 1
```

## Security Assessment

- ✅ Memory hardness giữ nguyên 64MB → GPU/ASIC resistance unchanged
- ✅ OWASP 2024 compliant (m ≥ 46MB, t ≥ 1)
- ✅ Rate limiting + CAPTCHA + account lock compensates
- ✅ Zero-downtime migration via PasswordUpgradeService
- ✅ Rollback: revert config → automatic re-hash

## Research Artifacts

| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| [opensource_findings.md](./opensource_findings.md) | BouncyCastle, argon2-jvm, Password4j evaluation |
| [web_research.md](./web_research.md) | 6 search iterations, OWASP profiles, industry practices |
| [comparison_analysis.md](./comparison_analysis.md) | 6 strategies compared, security trade-off matrix |
| [business_analysis.md](./business_analysis.md) | Use cases, business rules, traceability |
| [technical_spec.md](./technical_spec.md) | Implementation, migration, rollback plan |
| [validation_report.md](./validation_report.md) | Quality review — all checks PASS |

## Ready for

- `/wf_openspec_apply` — implement config change (1 line YAML)
- Benchmark validation — JMH results confirm 2x+ improvement
