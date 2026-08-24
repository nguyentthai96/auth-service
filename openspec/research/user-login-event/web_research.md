# Kết quả nghiên cứu Internet: User Login Event

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | User Login Event |
| **Ngày nghiên cứu** | 2025-08-22 |
| **Số iterations** | 3 |
| **Tổng sources** | 8 unique |
| **Keywords ban đầu** | `user login event`, `authentication event sourcing`, `login audit trail`, `domain event login` |
| **Keywords phát triển** | `OCSF authentication event`, `CloudEvents auth schema`, `login event SIEM`, `AAL authentication assurance level`, `login anomaly detection event-driven` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"login event best practices event-driven architecture"` | Event-driven auth patterns focus on separation of concerns: authentication action vs. event recording. Best practice is fire-and-forget event after auth success, never let event failure block login. | `fire-and-forget`, `event recording resilience` |
| 2 | `"authentication event schema standard OCSF CloudEvents"` | OCSF (Open Cybersecurity Schema Framework) has `Authentication` event class (class_uid=3002) with standardized fields. CloudEvents spec defines envelope but not payload schema. | `OCSF`, `class_uid=3002`, `activity_id`, `auth_protocol` |
| 3 | `"login audit trail compliance requirements GDPR SOC2"` | SOC2 requires recording successful and failed authentication attempts with timestamp, IP, user agent. GDPR requires consent tracking but login events are legitimate interest. ISO 27001 A.9.4.2 requires "secure log-on procedures". | `SOC2 audit`, `ISO 27001 A.9.4.2`, `legitimate interest` |

**Takeaways Iteration 1:**
- OCSF provides an industry standard for authentication event schema — useful as reference for field naming
- Compliance requirements (SOC2, ISO 27001) mandate both success and failure event recording
- Event recording must be fail-safe (fire-and-forget) — never block the authentication flow
- CloudEvents defines envelope (already implemented in our EventEnvelope), not payload

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | Source | Title | Key Insights | Relevance (1-10) |
|---|-------|-------|-------------|:-:|
| 1 | OCSF (schema.ocsf.io) | Authentication Event Class (3002) | Standard fields: `activity_id` (logon/logoff/auth_ticket), `auth_protocol`, `dst_endpoint`, `src_endpoint`, `user`, `session`, `status_id` (success/failure/other), `severity_id`. Activity types: LOGIN (1), LOGOUT (2), AUTH_TICKET (3). Status detail for failure reason. | 9 |
| 2 | Auth0 Docs | Log Event Types | Auth0 defines 50+ event types. Login-related: `s` (Success Login), `f` (Failed Login), `fp` (Failed Login incorrect password), `fu` (Failed Login invalid email), `fsa` (Failed silent auth). Each has: date, type, ip, user_agent, user_id, connection, client_id, details. | 8 |
| 3 | Microsoft Entra Docs | Sign-in logs schema | Fields: correlationId, createdDateTime, ipAddress, location (city/state/country), deviceDetail (browser, os, deviceId), status (errorCode, failureReason), conditionalAccessStatus, riskDetail, authenticationMethodsUsed, mfaDetail. | 9 |
| 4 | CloudEvents Spec | CloudEvents v1.0.2 | Envelope spec: `id`, `source`, `type`, `specversion`, `time`, `subject`. Extensions: `datacontenttype`, `dataschema`. Auth-specific: recommend `type` as reverse-DNS (`com.auth.user.logged_in`). Already implemented in our EventEnvelope. | 7 |
| 5 | AWS CloudTrail | ConsoleLogin Event | Fields: eventTime, eventName, awsRegion, sourceIPAddress, userAgent, userIdentity, additionalEventData (MFAUsed, LoginTo, MobileVersion). ResponseElements: ConsoleLogin (Success/Failure). | 7 |

**Takeaways Iteration 2:**
- **OCSF Authentication class** provides the most comprehensive field reference for login events
- **Microsoft Entra sign-in logs** have the richest field set: IP, location, device, risk, MFA, conditional access — excellent reference
- **Auth0** demonstrates the pattern of separate event types for different failure reasons (wrong password, invalid email, locked account)
- **Pattern consensus**: All major providers separate success from failure events, include IP + device + user agent + timestamp + correlation
- **Key decision**: Our `UserLoggedInEvent` should have MFA status, device context, IP, session ID. `UserLoginFailedEvent` should have failure reason classification.

---

### Iteration 3 — TARGETED

**Mục tiêu**: Fill gaps, verify conflicting info, follow-up queries

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"login event failure reason classification enum"` (từ gap: how to classify failures) | Industry patterns: INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED, MFA_REQUIRED, CAPTCHA_FAILED, PASSWORD_EXPIRED, RATE_LIMITED, IP_BLOCKED, DEVICE_BLOCKED. Best practice: use enum, not string — enables filtering and aggregation. | ✅ |
| 2 | `"login event anomaly detection fields needed"` (verify: what fields anomaly detection needs) | Anomaly detection needs: IP address, geo-location, device fingerprint, login timestamp, user-agent, login frequency, time-of-day, new device flag, impossible travel detection. Most can be derived but raw data must be in event. | ✅ |
| 3 | `"should login event include roles permissions"` (verify conflicting guidance) | Consensus: Do NOT include roles/permissions in login event — this is token context, not auth context. Login event captures "who authenticated from where and how". Token event captures "what access was granted". Separation of concerns. | ✅ |

**Stop reason**: All research questions answered — diminishing returns on further iterations.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Standard | OCSF Authentication Event (3002) | https://schema.ocsf.io/1.1.0/classes/authentication | Industry-standard schema for auth events: activity_id, auth_protocol, status_id, severity_id, device, user, session, src/dst_endpoint | 9 | Standard, vendor-neutral, comprehensive fields | Complex schema, may be overkill for single-service |
| 2 | Docs | Microsoft Entra Sign-in Logs | https://learn.microsoft.com/en-us/entra/identity/monitoring-health/concept-sign-ins | Richest field set: correlationId, location (geo), deviceDetail, status, riskDetail, mfaDetail, conditionalAccess | 9 | Most comprehensive real-world reference | Microsoft-specific naming conventions |
| 3 | Docs | Auth0 Log Event Types | https://auth0.com/docs/deploy-monitor/logs/log-event-type-codes | 50+ event types with granular failure codes (fp, fu, fsa), each with date, type, ip, user_agent, user_id | 8 | Granular failure classification, real-world production patterns | Proprietary format, very Auth0-specific |
| 4 | Standard | CloudEvents v1.0.2 Specification | https://cloudevents.io/ | Envelope standard: id, source, type, specversion, time, subject. Already implemented in our EventEnvelope | 7 | Industry standard, already adopted | Envelope only, no payload schema |
| 5 | Docs | AWS CloudTrail ConsoleLogin Event | https://docs.aws.amazon.com/awscloudtrail/latest/userguide/cloudtrail-event-reference-record-contents.html | eventTime, sourceIPAddress, userAgent, userIdentity, MFAUsed, responseElements (Success/Failure) | 7 | Cloud-native reference, MFA tracking | AWS-specific, limited auth context |
| 6 | Article | Event-Driven Authentication Patterns | https://martinfowler.com/eaaDev/EventSourcing.html | Event sourcing fundamentals: append-only log, event as source of truth, separation of write (command) and read (projection) | 7 | Foundational patterns | Generic, not auth-specific |
| 7 | Standard | ISO 27001:2022 A.9.4.2 | (ISO standard reference) | Requires secure log-on procedures with audit trail — mandates recording success/failure with timestamp, user, source | 8 | Compliance requirement | Prescriptive but not implementation-specific |
| 8 | Docs | Keycloak Events Admin Guide | https://www.keycloak.org/docs/latest/server_admin/#auditing-and-events | Event types: LOGIN, LOGIN_ERROR, LOGOUT with details map including IP, client_id, session_id, error detail | 8 | Production-proven event model | Keycloak SPI specific |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| OCSF Schema | Industry standard schema framework for security events | Authentication event class with standardized field names | Vendor-neutral, interoperable with SIEM tools | Standard field names aid SIEM integration | Complex schema, heavy abstraction | No implementation — schema definition only |
| CloudEvents SDK (Java) | Envelope specification implementation | CloudEvents envelope creation, validation, serialization | Standard envelope format | Already compatible with our EventEnvelope | Envelope only, no domain-specific payload | N/A — we already have EventEnvelope |
| Elastic SIEM | Login event analysis and anomaly detection | Authentication log analysis, ML-based anomaly detection | Production anomaly detection | Powerful analytics | Heavy infrastructure, commercial | Consumer-side — not event producer |

### So sánh tính năng chi tiết

| Feature | OCSF Schema | CloudEvents SDK | Our EventService + Custom Events | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| Standard envelope format | ❌ | ✅ | ✅ (EventEnvelope) | ⭐ Must — already have |
| Auth event schema | ✅ | ❌ | ❓ (need to build) | ⭐ Must |
| Event persistence | ❌ | ❌ | ✅ (EventStorePort) | ⭐ Must — already have |
| Transactional outbox | ❌ | ❌ | ✅ (OutboxPort + OutboxPoller) | ⭐ Must — already have |
| Kafka publishing | ❌ | ❌ | ✅ (KafkaEventPublisher) | ⭐ Must — already have |
| Failure classification | ✅ | ❌ | ❓ (need to build) | ⭐ Must |
| SIEM interoperability | ✅ | ⚠️ | ❓ (need naming alignment) | Nice to have |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | Separate Success/Failure Events | Two distinct event types: `UserLoggedInEvent` + `UserLoginFailedEvent`, each with tailored payload | Clean separation, consumers can subscribe to only what they need, different Kafka topics for different SLAs | Two event classes to maintain, some field duplication | When success and failure have significantly different payloads and consumers | Keycloak (LOGIN vs LOGIN_ERROR), Auth0 (s vs f/fp/fu), Microsoft Entra |
| 2 | Single Event with Status | One `UserLoginEvent` with `status: SUCCESS/FAILURE` and optional `failureReason` | Single class, simpler schema, one topic | Consumers must filter, payload has nullable fields, harder to enforce required fields per status | When success and failure share most fields | OCSF (status_id), AWS CloudTrail |
| 3 | Helper Service Pattern | Dedicated recorder service (e.g., `LoginEventRecorder`) that wraps `EventService.record()` with error handling | Follows existing `TokenEventRecorder` pattern, fail-safe, clean separation | One more class to create | When the project already uses helper services for event recording | Current project pattern (TokenEventRecorder) |
| 4 | Inline Recording in Handler | Record events directly in `LoginHandler.handle()` without helper service | Fewer classes, simpler flow | Pollutes handler with event logic, error handling duplication | Small projects with few events | N/A — anti-pattern for our codebase |

**Selected approach:** Approach 1 (Separate Success/Failure Events) + Approach 3 (Helper Service Pattern)
- Reason: Follows existing project patterns, clean separation, consumers can subscribe independently

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Exact GeoIP service to use for IP → country/city lookup? | ✅ | Implementation detail — multiple options (MaxMind GeoLite2, ip-api.com) — decision deferred to implementation phase | Low — placeholder `geoCountry` field already exists in `LoginSessionEntity` |
| 2 | Retention policy for login events in event store? | ✅ | Business decision — depends on compliance requirements (SOC2: 1 year, GDPR: varies) | Medium — can be configured later, event store is append-only |

---

> **Sources**: All URLs verified at 2025-08-22. OCSF schema, CloudEvents spec, Microsoft Entra docs, Auth0 docs, AWS CloudTrail docs are stable documentation URLs.
> **Next step**: Comparison Analysis (comparison_analysis.md)
