---
type: template
name: opensource_findings
version: "1.0"
language: vi
description: Kết quả tìm kiếm và đánh giá open source — scoring matrix + gap analysis chi tiết
---

# Kết quả tìm kiếm Open Source: {{FEATURE_NAME}}

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | {{FEATURE_NAME}} |
| **Ngày tìm kiếm** | {{DATE}} |
| **Số dự án tìm thấy** | {{TOTAL_FOUND}} |
| **Số dự án đánh giá chi tiết** | {{TOTAL_EVALUATED}} |
| **Tech stack mục tiêu** | {{TARGET_TECH_STACK}} |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"{{FEATURE_NAME}} open source GitHub repository"` | {{count}} kết quả | ... |
| 2 | `"{{FEATURE_NAME}} library framework {{TECH_STACK}}"` | {{count}} kết quả | ... |
| 3 | {{follow-up query}} | {{count}} kết quả | ... |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | {{project_1}} | {{url}} | {{stars}} | {{date}} | {{license}} | ✅ Có |
| 2 | {{project_2}} | {{url}} | {{stars}} | {{date}} | {{license}} | ✅ Có |
| 3 | {{project_3}} | {{url}} | {{stars}} | {{date}} | {{license}} | ❌ Không (lý do) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

<!-- Lặp cho mỗi project đánh giá -->

#### {{PROJECT_1_NAME}}

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | {{score}} | 20% | {{weighted}} | {{evidence}} |
| Applicability | {{score}} | 15% | {{weighted}} | {{evidence}} |
| Activity | {{score}} | 15% | {{weighted}} | {{evidence}} |
| Documentation | {{score}} | 15% | {{weighted}} | {{evidence}} |
| Code quality | {{score}} | 15% | {{weighted}} | {{evidence}} |
| Community | {{score}} | 10% | {{weighted}} | {{evidence}} |
| Popularity | {{score}} | 10% | {{weighted}} | {{evidence}} |
| **Tổng điểm** | | | **{{TOTAL}}/10** | |

<!-- Lặp lại cho project tiếp theo -->

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | {{project_1}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | **{{total}}** |
| 2 | {{project_2}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | **{{total}}** |
| 3 | {{project_3}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | {{x}} | **{{total}}** |

---

## 4. Gap Analysis chi tiết

<!-- Lặp cho mỗi project top 3-5 -->

### {{PROJECT_1_NAME}} — Gap Analysis

**Overall Score**: {{X.X}} / 10
**URL**: {{REPO_URL}}

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| {{Core Feature A}} | ✅ | | {{strength}} | - |
| {{Core Feature B}} | | ❌ | - | {{weakness}} |
| Scalability | ⚠️ | | {{partial_strength}} | {{limitation}} |
| Security | ✅ | | {{strength}} | - |
| Documentation | ✅ | | {{strength}} | {{weakness}} |
| Integration ({{TECH_STACK}}) | | ❌ | - | {{gap_detail}} |
| Test coverage | ✅ | | {{strength}} | {{weakness}} |

**Verdict**: {{Có thể dùng trực tiếp / Cần fork+customize / Tham khảo pattern / Không phù hợp}}
**Recommendation**: {{Dùng trực tiếp / Fork + customize / Tham khảo pattern / Bỏ qua}}
**Reasoning**: {{1-2 câu giải thích lý do}}

<!-- Lặp lại cho project tiếp theo -->

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | {{project}} | {{score}}/10 | {{verdict}} | {{use_case}} |
| 🥈 2 | {{project}} | {{score}}/10 | {{verdict}} | {{use_case}} |
| 🥉 3 | {{project}} | {{score}}/10 | {{verdict}} | {{use_case}} |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| {{Dùng / Fork / Tham khảo / Build from scratch}} | {{reasoning}} | {{source URLs}} |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm {{DATE}}
> **Next step**: Comparison Analysis (comparison_analysis.md)
