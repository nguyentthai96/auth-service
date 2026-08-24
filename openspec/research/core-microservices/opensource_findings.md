# Open Source Findings

## 1. Keycloak (Reference for Auth Service)
- URL: https://github.com/keycloak/keycloak
- Focus: Identity and Access Management

### Scoring
- Feature completeness: 10/10
- Applicability: 8/10
- Activity: 9/10
- Documentation: 9/10
- Code quality: 8/10
- Community: 10/10
- Popularity: 10/10

### Gap Analysis
| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| OIDC/SAML | ✅ | | Standard compliant | Heavyweight, overkill cho startup |
| User Federation | ✅ | | Hỗ trợ LDAP/AD tốt | Khó config custom flows phức tạp |

**Verdict**: Có thể dùng như Standalone Server hoặc tham khảo pattern cho Auth Service tự build.
**Recommendation**: Tham khảo luồng OAuth2/OIDC của Keycloak để thiết kế Custom Auth Service.

## 2. ZITADEL (Reference for Multi-tenant IAM)
- URL: https://github.com/zitadel/zitadel
- Focus: Cloud-native IAM for B2B SaaS

### Gap Analysis
| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Multi-tenancy | ✅ | | Native B2B support | Build bằng Go (khác tech stack Java) |
| Audit Trail | ✅ | | Event-sourcing core | Learning curve cao |

**Verdict**: Tham khảo cách thiết kế Audit Trail cho system-admin-service.

## 3. Refine (Reference for Admin Panel)
- URL: https://github.com/refinedev/refine
- Focus: React-based headless admin framework
**Verdict**: Công cụ Frontend hữu ích, giúp định hình các API CRUD mà system-admin-service cần cung cấp.
