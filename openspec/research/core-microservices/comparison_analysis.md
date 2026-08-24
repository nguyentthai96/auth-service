# Comparison & Gap Analysis

## 1. Product/Tool Comparison
| Approach | Phân bổ Database | Ưu điểm | Nhược điểm |
|----------|-----------------|---------|------------|
| **Monolithic Auth+Account** | 1 Database chung cho Auth và Account | Dễ join data, transaction ACID dễ dàng | Vi phạm nguyên tắc Microservices, khó scale riêng biệt |
| **Separated Services (Recommended)** | `auth_db`, `account_db`, `admin_db` riêng | Scalability cao, Loose coupling, Security tốt (Auth bị hack không lộ profile, hoặc ngược lại) | Distributed Transaction, Data sync phức tạp (Eventual Consistency) |
| **Outsourced IdP (Keycloak)** | Dùng Keycloak cho Auth, build Account/Admin | Giảm code Auth, chuẩn OIDC | Dữ liệu user phân tán giữa Keycloak DB và Account DB |

## 2. Recommendation
- Dựa trên context 3 module riêng rẽ đã tồn tại (`auth-service`, `account-service`, `system-admin-service`), khuyến nghị tiếp cận **Separated Services**.
- Tự build Auth Service sử dụng Spring Security OAuth2 (tạo Custom Authorization Server).
- Sử dụng Event-driven (Kafka/RabbitMQ) để sync data giữa các service khi có thay đổi quan trọng (vd: UserCreated event từ AccountService -> AuthService).
