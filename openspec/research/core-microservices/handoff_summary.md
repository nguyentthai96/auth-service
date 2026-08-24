---
type: research_handoff
feature: core-microservices-features
date: 2026-08-24
recommendation: build
research_dir: openspec/research/core-microservices/
status: complete
---

# Research Handoff: Core Microservices Features (Auth, Account, Admin)

## Recommendation
Triển khai giải pháp **Custom Microservices (Build)** thay vì phụ thuộc hoàn toàn vào một External IdP (như Keycloak) để dễ dàng kiểm soát luồng nghiệp vụ đặc thù (eKYC, quản lý thiết bị). Tách biệt cơ sở dữ liệu để đảm bảo kiến trúc loosely coupled.

## Key Findings
- **Data Segregation**: Việc tách `credentials` (auth-service), `profile` (account-service) và `RBAC` (system-admin-service) là best practice, nhưng cần xử lý Eventual Consistency khi một user bị khóa (Block).
- **Authentication**: JWT (JSON Web Token) ký bằng RSA (Asymmetric encryption) để các service khác có thể tự verify mà không cần call lại auth-service.

## Use Cases Identified
- **UC-AUTH**: Login, Refresh Token, Đổi mật khẩu.
- **UC-ACC**: Quản lý Profile, Định danh KYC, Quản lý thiết bị đăng nhập.
- **UC-ADM**: Quản lý phân quyền RBAC, Audit Log hệ thống, Khóa/Mở khóa User.

## Ready for
- `/wf_brainstorm_openspec core-microservices --from-research` — để đào sâu về giải pháp messaging giữa 3 service.
- `/wf_pre_openspec openspec/research/core-microservices/business_analysis.md` — để tiến hành formal URD analysis cho phase code tiếp theo.

## Research Artifacts
| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system |
| [opensource_findings.md](./opensource_findings.md) | Open source evaluation |
| [web_research.md](./web_research.md) | Internet research |
| [comparison_analysis.md](./comparison_analysis.md) | Comparison + gap analysis |
| [business_analysis.md](./business_analysis.md) | Business analysis (use cases) |
| [technical_spec.md](./technical_spec.md) | Technical specification |
| [validation_report.md](./validation_report.md) | Quality review results |
