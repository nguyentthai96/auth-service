---
type: template
name: research_brief
version: "1.0"
language: vi
---

# Research Brief: {{FEATURE_NAME}}

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | {{FEATURE_NAME}} |
| **Ngày tạo** | {{DATE}} |
| **Input source** | {{INPUT_TYPE: name / file / url / idea}} |
| **Input content** | {{INPUT_CONTENT}} |
| **Người yêu cầu** | {{REQUESTER}} |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)
<!-- Tại sao cần tính năng này? Vấn đề gì đang cần giải quyết? -->

### 2.2 Mục tiêu (Objectives)
<!-- Tính năng này cần đạt được gì? -->
- [ ] Objective 1: ...
- [ ] Objective 2: ...

### 2.3 Phạm vi ban đầu (Initial Scope)
<!-- Phạm vi sơ bộ — sẽ refine sau khi research -->

| In Scope | Out of Scope |
|----------|-------------|
| ... | ... |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
<!-- Keywords chính để search -->
- `keyword_1`
- `keyword_2`

### 3.2 Secondary Keywords
<!-- Keywords phụ, mở rộng -->
- `secondary_1`

### 3.3 Domain-Specific Terms
<!-- Thuật ngữ chuyên ngành -->
- `term_1`: định nghĩa

### 3.4 Search Queries (pre-defined)
<!-- Các query search đã chuẩn bị sẵn -->

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"how to implement {{feature}} best practices"` | General | High |
| 2 | `"{{feature}} open source GitHub"` | Open Source | High |
| 3 | `"{{feature}} architecture patterns"` | Architecture | Medium |
| 4 | `"{{feature}} vs alternatives comparison"` | Comparison | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project
<!-- Kết quả scan project hiện tại — tính năng liên quan -->

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| ... | ... | High/Med/Low | ... |

### 4.2 Existing Code Patterns
<!-- Patterns đang dùng trong project có liên quan -->

### 4.3 Tech Stack Constraints
<!-- Ràng buộc từ tech stack hiện tại -->
- Language: ...
- Framework: ...
- Database: ...
- Build tool: ...

### 4.4 Integration Points
<!-- Các điểm tích hợp — module, API, table sẽ tương tác với feature mới -->

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| ... | API/Module/Table/Base Class | ... | ... |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
<!-- Danh sách câu hỏi cần answer qua research -->
- [ ] Q1: ...
- [ ] Q2: ...

### 5.2 Assumptions cần verify
<!-- Giả định cần kiểm chứng -->
- [ ] A1: ...

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | ... | ≥ N sources |
| Open source options | ... | ≥ 3 repos evaluated |
| Gap analysis | ... | All critical gaps identified |
| Business analysis | ... | All UCs documented |
| Technical spec | ... | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
