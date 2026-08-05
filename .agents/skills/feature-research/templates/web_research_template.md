---
type: template
name: web_research
version: "1.0"
language: vi
description: Kết quả tìm kiếm internet — Perplexity-style iterative search + product evaluation
---

# Kết quả nghiên cứu Internet: {{FEATURE_NAME}}

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | {{FEATURE_NAME}} |
| **Ngày nghiên cứu** | {{DATE}} |
| **Số iterations** | {{TOTAL_ITERATIONS}} |
| **Tổng sources** | {{TOTAL_SOURCES}} unique |
| **Keywords ban đầu** | {{INITIAL_KEYWORDS}} |
| **Keywords phát triển** | {{EVOLVED_KEYWORDS}} |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"how to implement {{FEATURE_NAME}} best practices"` | {{summary}} | {{keywords}} |
| 2 | `"{{FEATURE_NAME}} architecture design patterns"` | {{summary}} | {{keywords}} |
| 3 | `"{{FEATURE_NAME}} vs alternatives comparison"` | {{summary}} | {{keywords}} |

**Takeaways Iteration 1:**
- {{key insight 1}}
- {{key insight 2}}
- {{gaps to explore in next iteration}}

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | {{url}} | {{title}} | {{insights}} | {{score}} |
| 2 | {{url}} | {{title}} | {{insights}} | {{score}} |
| 3 | {{url}} | {{title}} | {{insights}} | {{score}} |

**Takeaways Iteration 2:**
- {{approach 1: description + trade-offs}}
- {{approach 2: description + trade-offs}}
- {{conflicting info: {{topic}} — source A says X, source B says Y}}

---

### Iteration 3+ — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | {{query}} (từ gap trong Iter 2) | {{result}} | ✅ / ❌ |
| 2 | {{query}} (verify conflict) | {{result}} | ✅ / ❌ |

**Stop reason**: {{diminishing returns / all questions answered / max iterations reached}}

---

## 3. Bài viết/Nguồn quan trọng

<!-- Danh sách tổng hợp tất cả sources đáng chú ý -->

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Article | {{title}} | {{url}} | {{insights}} | {{1-10}} | {{pros}} | {{cons}} |
| 2 | Video | {{title}} | {{url}} | {{insights}} | {{1-10}} | {{pros}} | {{cons}} |
| 3 | Docs | {{title}} | {{url}} | {{insights}} | {{1-10}} | {{pros}} | {{cons}} |
| 4 | Blog | {{title}} | {{url}} | {{insights}} | {{1-10}} | {{pros}} | {{cons}} |

---

## 4. Đánh giá sản phẩm/Công cụ

<!-- Products/tools tìm được, KHÔNG phải open source repos (đã ở opensource_findings) -->

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| {{product_1}} | {{approach}} | {{features}} | {{benefits}} | {{pros}} | {{cons}} | {{missing}} |
| {{product_2}} | {{approach}} | {{features}} | {{benefits}} | {{pros}} | {{cons}} | {{missing}} |

### So sánh tính năng chi tiết

| Feature | {{Product_1}} | {{Product_2}} | {{Custom Build}} | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| {{Feature A}} | ✅ | ❌ | ⚠️ | ⭐ Must |
| {{Feature B}} | ✅ | ✅ | ✅ | Nice to have |
| {{Feature C}} | ❌ | ✅ | ❓ | Optional |

---

## 5. Patterns & Approaches

<!-- Các approaches/patterns phát hiện qua research, với trade-offs -->

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | {{approach_name}} | {{description}} | {{pros}} | {{cons}} | {{when_to_use}} | {{url}} |
| 2 | {{approach_name}} | {{description}} | {{pros}} | {{cons}} | {{when_to_use}} | {{url}} |

---

## 6. Câu hỏi chưa trả lời

<!-- Gaps còn lại sau khi research — cần input từ stakeholder hoặc further research -->

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | {{question}} | ✅ | {{reason}} | {{impact}} |
| 2 | {{question}} | ❌ | {{reason}} | {{impact}} |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm {{DATE}}
> **Next step**: Comparison Analysis (comparison_analysis.md)
