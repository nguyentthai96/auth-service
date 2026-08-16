# Evaluation Report — Sample

> This is a sample evaluation report generated after a hermes-pipeline execution.
> Use this format when creating new evaluation reports.

---

## Summary

| Metric | Value |
|--------|-------|
| **Project** | taskqueue-app |
| **Date** | 2026-08-13 |
| **Overall Score** | **85/100 (Good)** |
| **Features** | 3/3 completed |
| **Duration** | ~18 minutes |
| **Errors** | 1 retry (non-critical) |

---

## Dimension Breakdown

| Dimension | Weight | Score | Notes |
|-----------|--------|-------|-------|
| Completeness | 30% | 100 | All 3 features fully completed |
| Output Quality | 25% | 80 | 16/20 output files valid and well-structured |
| Error Rate | 20% | 90 | 1 retry on wf_openspec for feature #2 |
| Efficiency | 15% | 70 | Slightly over baseline (6 min/feature avg) |
| Consistency | 10% | 85 | Feature #2 took longer due to retry |

---

## Feature Status

| Feature | Status | Duration | Score | Notes |
|---------|--------|----------|-------|-------|
| task-queue-core | ✅ Completed | 5m 30s | 92 | Clean execution, no retries |
| task-scheduler | ✅ Completed | 7m 15s | 78 | 1 retry on wf_openspec |
| task-monitoring | ✅ Completed | 5m 45s | 85 | Clean execution |

---

## Errors & Issues

### Issue #1: Retry on wf_openspec (task-scheduler)

- **Workflow**: `wf_openspec`
- **Feature**: `task-scheduler`
- **Error**: Context window exceeded during spec generation
- **Resolution**: Automatic retry with condensed context succeeded
- **Impact**: Low — added ~2 minutes to execution
- **Recommendation**: Consider increasing `context_max_tokens` to 12000

---

## Trend (Last 3 Runs)

| Date | Score | Rating | Features |
|------|-------|--------|----------|
| 2026-08-01 | 72 | Fair | 2/3 |
| 2026-08-07 | 79 | Good | 3/3 |
| **2026-08-13** | **85** | **Good** | **3/3** |

**Trend**: 📈 Improving (+6.5 points/week)

---

## Recommendations

1. **Increase `context_max_tokens`** to 12000 for this project — complex
   features consistently need more context window
2. **Consider enabling `reflection`** — the project is stable enough to
   benefit from self-improvement feedback loop
3. **Add `wf_brainstorm_openspec`** for the next complex feature batch —
   brainstorming improves spec quality for novel features
4. **Monitor efficiency** — average duration is trending up, may need
   model or workflow optimization

---

## Config Snapshot

```yaml
model: gemini-2.5-pro
sdk_mode: google
features: [task-queue-core, task-scheduler, task-monitoring]
workflows: [wf_pre_openspec, wf_openspec, wf_openspec_apply]
settings:
  max_retries: 3
  context_max_tokens: 8000
  memory_max_items: 20
```

---

## Lessons Extracted

1. **[config]** `context_max_tokens=8000` insufficient for scheduler-type
   features → increase to 12000
2. **[pattern]** Pipeline handles 3-feature batches well within 20 minutes
   → current batch size is optimal
3. **[workflow]** `wf_pre_openspec + wf_openspec + wf_openspec_apply` chain
   produces reliable results for this project type
