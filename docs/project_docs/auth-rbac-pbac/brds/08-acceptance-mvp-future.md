# BRD-08: Acceptance Criteria, MVP & Future Roadmap

## MVP Definition

### MVP Features (Phase 1)
| # | Feature | Priority | Effort |
|:--|:--------|:---:|:---:|
| 1 | User auth (login/register/refresh) | 🔴 | M |
| 2 | Domain CRUD + default setup | 🔴 | S |
| 3 | User management + domain membership | 🔴 | M |
| 4 | Group CRUD (domain-scoped) | 🔴 | M |
| 5 | Role management + hierarchy | 🔴 | M |
| 6 | Resource registration | 🔴 | S |
| 7 | Permission matrix (Role×Resource×Action) | 🔴 | L |
| 8 | Permission check API | 🔴 | L |
| 9 | PBAC policy engine (basic) | 🟡 | L |
| 10 | Read-only enforcement | 🔴 | S |
| 11 | JWT with embedded permissions | 🔴 | M |
| 12 | SQL migration scripts (Flyway) | 🔴 | M |
| 13 | Docker Compose dev setup | 🔴 | S |

**Estimated Total Effort**: ~4-6 weeks (1 developer)

### MVP Acceptance Tests

| Test | Description | Pass Criteria |
|:---|:---|:---|
| AT-01 | Register + Login + Get JWT | JWT contains roles and permissions |
| AT-02 | Create domain "booking" | Auto-creates ADMIN + VIEWER roles |
| AT-03 | Create group "Hotel-A Staff" | Group scoped to booking domain |
| AT-04 | Assign RECEPTIONIST role to group | Group members get receptionist perms |
| AT-05 | Add user to group | User gains group's permissions |
| AT-06 | Permission check: RECEPTIONIST reads bookings | ALLOW |
| AT-07 | Permission check: RECEPTIONIST deletes bookings | DENY |
| AT-08 | Permission check: VIEWER creates rooms | DENY (read-only) |
| AT-09 | Multi-domain: User has different roles in booking vs rental | Each domain independent |
| AT-10 | Policy: owner-only edit | User can edit own resource, denied for others |

## Future Roadmap

### Phase 2: Integration & Performance
- Redis caching for permission checks
- Spring Cloud Gateway integration (thunx-style)
- Query rewriting for data-level authorization
- WebSocket real-time permission updates
- API rate limiting per domain/user

### Phase 3: Advanced Features
- OAuth2 social login (Google, GitHub, Apple)
- SAML/LDAP enterprise integration
- Permission delegation (temporary grants)
- Time-based access policies (office hours only)
- IP-based restrictions
- MFA (TOTP, SMS)

### Phase 4: Enterprise
- Multi-tenancy with PostgreSQL RLS
- Audit logging with Kafka event stream
- Compliance dashboard (GDPR, SOC2)
- SDK libraries for Java, Kotlin, TypeScript
- Admin UI Dashboard (React)
- Terraform/Helm deployment templates

### Phase 5: Intelligence
- Anomaly detection (unusual permission usage)
- Permission recommendation engine
- Automated role mining (suggest roles from usage patterns)
- Permission impact analysis (what breaks if role changes)
