---
type: template
name: comparison_analysis
version: "1.0"
language: vi
description: Tổng hợp so sánh — comparison matrix, feature matrix, gap synthesis, recommendation
---

# Phân tích so sánh: {{FEATURE_NAME}}

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | {{FEATURE_NAME}} |
| **Ngày phân tích** | {{DATE}} |
| **Recommendation** | **{{Build from scratch / Adopt existing / Fork + customize / Hybrid approach}}** |
| **Rationale** | {{1-2 câu giải thích quyết định}} |
| **Confidence** | {{HIGH / MED / LOW}} — {{lý do}} |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | {{OS Project A}} | Open Source | {{approach}} | {{pros}} | {{cons}} | ✅ / ⚠️ / ❌ | {{X.X}} |
| 2 | {{OS Project B}} | Open Source | {{approach}} | {{pros}} | {{cons}} | ✅ / ⚠️ / ❌ | {{X.X}} |
| 3 | {{Product C}} | Commercial | {{approach}} | {{pros}} | {{cons}} | ✅ / ⚠️ / ❌ | {{X.X}} |
| 4 | Custom Build | In-house | {{approach}} | {{pros}} | {{cons}} | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | {{Solution_A}} | {{Solution_B}} | {{Solution_C}} | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| {{Core Feature 1}} | ✅ | ✅ | ⚠️ | ✅ | ⭐ Must |
| {{Core Feature 2}} | ✅ | ❌ | ✅ | ✅ | ⭐ Must |
| {{Nice Feature 3}} | ❌ | ✅ | ✅ | ❓ | Nice to have |
| {{Advanced Feature 4}} | ❌ | ❌ | ✅ | ❓ | Optional |
| {{Integration Feature}} | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| **Coverage** | **{{X/Y}}** | **{{X/Y}}** | **{{X/Y}}** | **{{X/Y}}** | |

<!-- Legend: ✅ Có đầy đủ | ⚠️ Có nhưng hạn chế | ❌ Không có | ❓ Cần build thêm -->

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | {{count}} | {{min%}} - {{max%}} |
| Nice to have | {{count}} | {{min%}} - {{max%}} |
| Optional | {{count}} | {{min%}} - {{max%}} |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | {{Solution_A}} | {{Solution_B}} | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| {{FR-01}} | {{UC-XXX}} | ✅ | ❌ | ✅ | Có — Solution B |
| {{FR-02}} | {{UC-XXX}} | ⚠️ | ✅ | ✅ | Partial — Solution A |
| {{NFR-01}} | Business rule | ❌ | ❌ | ✅ | Có — all external |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| {{aspect_1}} | {{current_state}} | {{target_state}} | {{gap_detail}} | {{HIGH/MED/LOW}} |
| {{aspect_2}} | {{current_state}} | {{target_state}} | {{gap_detail}} | {{HIGH/MED/LOW}} |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse {{Best Solution}} | Winner |
|--------|:---:|:---:|:---:|
| Time to market | {{estimate}} | {{estimate}} | {{option}} |
| Maintenance burden | {{assess}} | {{assess}} | {{option}} |
| Feature coverage | {{coverage}} | {{coverage}} | {{option}} |
| Integration effort | {{effort}} | {{effort}} | {{option}} |
| Long-term flexibility | {{assess}} | {{assess}} | {{option}} |
| Risk | {{risk_level}} | {{risk_level}} | {{option}} |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | {{Solution_A}} | {{Solution_B}} | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | {{score}} | {{score}} | {{score}} |
| Integration ease | 25% | {{score}} | {{score}} | {{score}} |
| Maintenance | 20% | {{score}} | {{score}} | {{score}} |
| Community/Support | 15% | {{score}} | {{score}} | {{score}} |
| Learning curve | 10% | {{score}} | {{score}} | {{score}} |
| **Tổng điểm (weighted)** | | **{{total}}** | **{{total}}** | **{{total}}** |

### Reasoning

**Recommended approach**: {{Build from scratch / Adopt / Fork + customize / Hybrid}}

**Lý do**:
1. {{reasoning_1 — evidence-based}}
2. {{reasoning_2 — evidence-based}}
3. {{reasoning_3 — evidence-based}}

**Trade-offs chấp nhận**:
- {{trade-off 1}} — chấp nhận vì {{reason}}
- {{trade-off 2}} — chấp nhận vì {{reason}}

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| {{risk_1}} | {{HIGH/MED/LOW}} | {{HIGH/MED/LOW}} | {{mitigation}} |
| {{risk_2}} | {{HIGH/MED/LOW}} | {{HIGH/MED/LOW}} | {{mitigation}} |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| {{Solution_A}} | {{estimate}} | {{LOW/MED/HIGH}} | {{LOW/MED/HIGH}} |
| Custom Build | {{estimate}} | {{LOW/MED/HIGH}} | {{LOW/MED/HIGH}} |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
