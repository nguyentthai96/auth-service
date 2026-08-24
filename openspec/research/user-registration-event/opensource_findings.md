# Kết quả tìm kiếm Open Source: User Registration Event

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Registration Event |
| **Ngày tìm kiếm** | 2025-08-22 |
| **Số dự án tìm thấy** | 7 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Kotlin/Java, Spring Boot 4.x, PostgreSQL, Kafka, CQRS/Event Sourcing |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"user registration event sourcing open source GitHub"` | 5 relevant repos | Axon, Eventuate, Keycloak, Spring Modulith, custom projects |
| 2 | `"CQRS event sourcing Spring Boot Kotlin user aggregate registration"` | 3 relevant repos | Focused on Kotlin/Spring implementations |
| 3 | `"domain event CloudEvents user registered schema"` | 2 relevant specs | CloudEvents spec, OCSF schema |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Keycloak | https://github.com/keycloak/keycloak | ~24,000 | Active (daily) | Apache-2.0 | ✅ Có |
| 2 | Axon Framework | https://github.com/AxonFramework/AxonFramework | ~5,100 | Active (weekly) | Apache-2.0 | ✅ Có |
| 3 | Eventuate Tram | https://github.com/eventuate-tram/eventuate-tram-core | ~600 | Monthly | Apache-2.0 | ✅ Có |
| 4 | Spring Modulith | https://github.com/spring-projects/spring-modulith | ~850 | Active (weekly) | Apache-2.0 | ✅ Có |
| 5 | Debezium | https://github.com/debezium/debezium | ~10,500 | Active (daily) | Apache-2.0 | ❌ Không (CDC tool, not domain event framework) |
| 6 | AxonIQ Server | https://github.com/AxonIQ/axon-server-se | ~200 | Monthly | AxonIQ Open Source License | ❌ Không (server component, not event model) |
| 7 | CloudEvents SDK Java | https://github.com/cloudevents/sdk-java | ~400 | Quarterly | Apache-2.0 | ❌ Không (SDK only, not domain event pattern) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured event model |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 9 | 20% | 1.80 | Full EventType enum (REGISTER, REGISTER_ERROR, LOGIN, LOGIN_ERROR, etc.), Event class with details map, EventListenerSPI for extensibility |
| Applicability | 5 | 15% | 0.75 | Java-based, but different architecture (monolithic IAM vs microservice). Event model is reference-worthy but not directly portable |
| Activity | 10 | 15% | 1.50 | Daily commits, massive community, Red Hat backed |
| Documentation | 9 | 15% | 1.35 | Extensive documentation including event listener customization guides |
| Code quality | 9 | 15% | 1.35 | Well-tested, mature codebase with established review process |
| Community | 10 | 10% | 1.00 | ~24,000 stars, large ecosystem |
| Popularity | 10 | 10% | 1.00 | Industry standard IAM platform |
| **Tổng điểm** | | | **8.75/10** | |

#### Axon Framework

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Full CQRS/ES framework: @Aggregate, @EventHandler, @CommandHandler, EventStore, event upcasting, snapshot support |
| Applicability | 4 | 15% | 0.60 | Java/Kotlin compatible but requires full framework buy-in. Auth-service uses custom CQRS — adopting Axon would be major refactor |
| Activity | 8 | 15% | 1.20 | Weekly commits, active development |
| Documentation | 9 | 15% | 1.35 | Excellent reference docs, Axon Academy, blog posts |
| Code quality | 9 | 15% | 1.35 | Well-tested, modular architecture |
| Community | 8 | 10% | 0.80 | ~5,100 stars, active Discuss forum |
| Popularity | 8 | 10% | 0.80 | De facto Java CQRS/ES framework |
| **Tổng điểm** | | | **8.10/10** | |

#### Eventuate Tram

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 7 | 20% | 1.40 | Transactional outbox pattern, domain event publishing, event sourcing aggregate support |
| Applicability | 6 | 15% | 0.90 | Java/Spring Boot compatible, transactional outbox is directly relevant. However, auth-service already has custom outbox implementation |
| Activity | 5 | 15% | 0.75 | Monthly commits, slower pace |
| Documentation | 6 | 15% | 0.90 | Good docs but less comprehensive than Axon/Keycloak |
| Code quality | 7 | 15% | 1.05 | Decent test coverage, modular |
| Community | 5 | 10% | 0.50 | ~600 stars, smaller community |
| Popularity | 6 | 10% | 0.60 | Known in microservices patterns community (Chris Richardson) |
| **Tổng điểm** | | | **6.10/10** | |

#### Spring Modulith

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 6 | 20% | 1.20 | ApplicationEventPublisher integration, event externalization to Kafka/AMQP, event publication log (incomplete_events table) |
| Applicability | 7 | 15% | 1.05 | Spring Boot native, easy to integrate. However, auth-service already has custom event infrastructure that's more feature-rich |
| Activity | 9 | 15% | 1.35 | Weekly commits, Spring team maintained |
| Documentation | 7 | 15% | 1.05 | Good Spring reference docs, growing examples |
| Code quality | 9 | 15% | 1.35 | Spring team quality standards |
| Community | 6 | 10% | 0.60 | ~850 stars, growing |
| Popularity | 7 | 10% | 0.70 | Rising in Spring ecosystem |
| **Tổng điểm** | | | **7.30/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Keycloak | 9 | 5 | 10 | 9 | 9 | 10 | 10 | **8.75** |
| 2 | Axon Framework | 10 | 4 | 8 | 9 | 9 | 8 | 8 | **8.10** |
| 3 | Spring Modulith | 6 | 7 | 9 | 7 | 9 | 6 | 7 | **7.30** |
| 4 | Eventuate Tram | 7 | 6 | 5 | 6 | 7 | 5 | 6 | **6.10** |

---

## 4. Gap Analysis chi tiết

### Keycloak — Gap Analysis

**Overall Score**: 8.75 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| User Registration Event | ✅ | | EventType.REGISTER with full details map (userId, username, email, realmId, clientId, ipAddress) | Flat details map instead of typed event class |
| Registration Failure Event | ✅ | | EventType.REGISTER_ERROR with error details | Error details are string-based, not enum-classified |
| Event Listener SPI | ✅ | | Extensible EventListenerProviderFactory for custom event handling | Complex SPI — over-engineered for simple use cases |
| Event Store | | ❌ | - | Events are transient (listener-only, not persisted to event store by default) |
| Transactional Outbox | | ❌ | - | No outbox pattern — events fired synchronously via SPI |
| CloudEvents Envelope | | ❌ | - | Custom event format, not CloudEvents-compatible |
| Kafka Integration | ⚠️ | | Available via event listener SPI plugin | Requires custom listener implementation |
| Schema Versioning | | ❌ | - | No built-in schema versioning |

**Verdict**: Tham khảo pattern — excellent event type taxonomy and failure event model
**Recommendation**: Tham khảo pattern
**Reasoning**: Keycloak's EventType enum (REGISTER, REGISTER_ERROR) and Event details map provide a strong reference for event type naming and registration failure classification. However, its event architecture is synchronous listener-based, not event-sourced, so the persistence and outbox patterns are not applicable.

### Axon Framework — Gap Analysis

**Overall Score**: 8.10 / 10
**URL**: https://github.com/AxonFramework/AxonFramework

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| User Registration Event | ✅ | | @Aggregate + @EventSourcingHandler pattern, typed domain events | Requires full Axon adoption |
| Registration Failure Event | ✅ | | Command validation exceptions can be modeled as events | No built-in failure event class |
| Event Store | ✅ | | Built-in event store with snapshot support, upcasting | Proprietary format |
| Event Upcasting | ✅ | | Schema evolution via EventUpcaster chain | Complex API for simple cases |
| Transactional Outbox | ✅ | | AxonIQ Dead Letter Queue + subscription tracking | Requires Axon Server |
| CloudEvents Envelope | | ❌ | - | Custom serialization format |
| Correlation ID | ✅ | | MetaData map with correlationIdentifier | Built into framework |
| Schema Versioning | ✅ | | @Revision annotation on event classes | Excellent pattern |

**Verdict**: Tham khảo pattern — excellent event upcasting and @Revision pattern for schema evolution
**Recommendation**: Tham khảo pattern
**Reasoning**: Axon's @Revision annotation and EventUpcaster chain provide the best reference for schema evolution strategy. The aggregate pattern is also a strong reference, but full Axon adoption is not feasible for this project (custom CQRS already in place).

### Eventuate Tram — Gap Analysis

**Overall Score**: 6.10 / 10
**URL**: https://github.com/eventuate-tram/eventuate-tram-core

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| User Registration Event | ✅ | | DomainEvent interface, typed events | Less prescriptive about event design |
| Event Store | ⚠️ | | Event aggregates with snapshot | Less mature than Axon |
| Transactional Outbox | ✅ | | Core strength — MESSAGE table in same DB, CDC or polling relay | Auth-service already has this pattern |
| Kafka Integration | ✅ | | Built-in Kafka producer | Simple integration |
| Schema Versioning | | ❌ | - | No built-in schema versioning |
| CloudEvents Envelope | | ❌ | - | Custom message format |
| Idempotency | ✅ | | MessageID-based deduplication | Good pattern reference |

**Verdict**: Tham khảo pattern — transactional outbox implementation reference
**Recommendation**: Tham khảo pattern
**Reasoning**: Eventuate Tram's transactional outbox pattern (MESSAGE table + CDC/polling relay) closely matches auth-service's existing OutboxPoller implementation. The message deduplication via MessageID is a valuable pattern reference for idempotent event processing.

### Spring Modulith — Gap Analysis

**Overall Score**: 7.30 / 10
**URL**: https://github.com/spring-projects/spring-modulith

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| User Registration Event | ✅ | | @ApplicationModuleListener, typed events | Spring application events only |
| Event Externalization | ✅ | | Automatic externalization to Kafka/AMQP/SQS | Less control over serialization |
| Event Publication Log | ✅ | | incomplete_events tracking table | Similar to outbox but lighter |
| Transactional Outbox | ⚠️ | | Via event publication log | Not a true outbox — uses Spring TX event listener |
| Schema Versioning | | ❌ | - | No built-in versioning |
| CloudEvents Envelope | | ❌ | - | Spring event format |
| Idempotency | | ❌ | - | Not built-in |

**Verdict**: Tham khảo pattern — event externalization and publication log patterns
**Recommendation**: Tham khảo pattern
**Reasoning**: Spring Modulith's event publication log (tracking incomplete events) is a lighter alternative to the full outbox pattern. The automatic externalization to Kafka is elegant but provides less control than the custom outbox approach already in auth-service.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Keycloak | 8.75/10 | Tham khảo pattern | Event type taxonomy (REGISTER, REGISTER_ERROR), failure event model |
| 🥈 2 | Axon Framework | 8.10/10 | Tham khảo pattern | Schema evolution (@Revision, EventUpcaster), aggregate pattern |
| 🥉 3 | Spring Modulith | 7.30/10 | Tham khảo pattern | Event externalization, publication log pattern |
| 4 | Eventuate Tram | 6.10/10 | Tham khảo pattern | Transactional outbox reference, message deduplication |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| Build from scratch (enhance existing) | Auth-service already has production-grade event sourcing infrastructure (EventService, EventEnvelope, OutboxPoller). All 4 open source projects offer patterns for reference but none are directly integrable without major refactoring. The recommendation is to enhance the existing UserRegisteredEvent following patterns observed in Keycloak (failure events), Axon (schema versioning), and established internal patterns (LoginEventRecorder fire-and-forget). | Codebase analysis + scoring matrix |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-08-22
> **Next step**: Comparison Analysis (comparison_analysis.md)
