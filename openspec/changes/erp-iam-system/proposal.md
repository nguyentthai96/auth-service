# Proposal: erp-iam-system

## Executive Summary
Triển khai hệ thống Enterprise Identity & Access Management chia thành 3 modules độc lập (`auth-service`, `account-service`, `system-admin-service`) nhằm đảm bảo Single Responsibility và khả năng scale linh hoạt.

## Technical Approach
- Phân chia Domain: Tách Authentication (auth-service) ra khỏi Profile Management (account-service) và System Configuration (system-admin-service).
- Kế thừa Base-Core: Phát triển các Starter (`base-security-starter`, `base-data-starter`) để chia sẻ logic chung thay vì duplicate code.
- Event-Driven: Sử dụng Kafka để broadcast sự kiện như user_registered, password_changed, menu_updated.

## Expected Outcomes
- Một hệ thống ERP IAM mạnh mẽ hỗ trợ SSO, MFA, RBAC/PBAC.
- Hệ thống Menu động và API Partner scale tốt cho hàng trăm tenants.
- Tái sử dụng được 70% core code qua Plugin Architecture.
