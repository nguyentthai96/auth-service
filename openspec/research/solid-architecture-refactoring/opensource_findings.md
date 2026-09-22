# Open Source Findings: SOLID Architecture Patterns

## Evaluated Projects

### 1. Axon Framework (AxonIQ)

**URL**: https://github.com/AxonFramework/AxonFramework
**Stars**: ~5.1K | **Activity**: Weekly commits | **License**: Apache 2.0

**Scoring Matrix**:
| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 9/10 | 20% | 1.80 |
| Applicability (Kotlin + Spring Boot) | 8/10 | 15% | 1.20 |
| Activity | 9/10 | 15% | 1.35 |
| Documentation | 9/10 | 15% | 1.35 |
| Code quality | 9/10 | 15% | 1.35 |
| Community | 8/10 | 10% | 0.80 |
| Popularity | 8/10 | 10% | 0.80 |
| **Overall** | | | **8.65** |

**Gap Analysis**:
| Aspect | Status | Details |
|--------|:------:|---------|
| CommandBus/CommandGateway | ✅ | Full implementation với pipeline decorators |
| QueryBus/QueryGateway | ✅ | Type-safe query dispatch |
| Event Sourcing | ✅ | Full CQRS + ES support |
| Spring Boot integration | ✅ | Auto-configuration, starters |
| Kotlin support | ✅ | Extensions available |
| Lightweight option | ❌ | Framework nặng, overkill cho use case này |
| Custom pipeline steps | ✅ | MessageHandlerInterceptor chain |

**Verdict**: **Tham khảo pattern** — Axon quá nặng cho auth-service, nhưng CommandBus + Pipeline pattern rất đáng học hỏi. Tự implement lightweight CommandBus theo pattern của Axon.

---

### 2. MediatR (jbogard) — .NET nhưng pattern tham khảo

**URL**: https://github.com/jbogard/MediatR
**Stars**: ~11.5K | **Activity**: Monthly | **License**: Apache 2.0

**Scoring Matrix**:
| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 8/10 | 20% | 1.60 |
| Applicability (wrong stack) | 3/10 | 15% | 0.45 |
| Activity | 7/10 | 15% | 1.05 |
| Documentation | 8/10 | 15% | 1.20 |
| Code quality | 9/10 | 15% | 1.35 |
| Community | 9/10 | 10% | 0.90 |
| Popularity | 9/10 | 10% | 0.90 |
| **Overall** | | | **7.45** |

**Gap Analysis**:
| Aspect | Status | Details |
|--------|:------:|---------|
| Mediator Pattern | ✅ | Canonical implementation |
| Pipeline behaviors | ✅ | IPipelineBehavior — cross-cutting concerns |
| Request/Response | ✅ | IRequest<TResponse> type-safe |
| Spring Boot | ❌ | .NET only |
| Kotlin | ❌ | C# only |

**Verdict**: **Tham khảo pattern** — Interface design (`IRequest<T>`, `IRequestHandler<TRequest, TResponse>`, `IPipelineBehavior`) rất phù hợp để port sang Kotlin.

---

### 3. Open Policy Agent (OPA) / Rego

**URL**: https://github.com/open-policy-agent/opa
**Stars**: ~10K+ | **Activity**: Daily | **License**: Apache 2.0

**Scoring Matrix**:
| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 10/10 | 20% | 2.00 |
| Applicability | 4/10 | 15% | 0.60 |
| Activity | 10/10 | 15% | 1.50 |
| Documentation | 9/10 | 15% | 1.35 |
| Code quality | 9/10 | 15% | 1.35 |
| Community | 10/10 | 10% | 1.00 |
| Popularity | 10/10 | 10% | 1.00 |
| **Overall** | | | **8.80** |

**Gap Analysis**:
| Aspect | Status | Details |
|--------|:------:|---------|
| Policy language (Rego) | ✅ | Powerful, declarative |
| Condition operators | ✅ | Unlimited extensibility |
| Performance | ✅ | Compiled policies, sub-ms |
| Spring Boot SDK | ⚠️ | Community-maintained |
| Embedded mode | ✅ | Can run as sidecar or library |
| Learning curve | ❌ | Rego DSL requires training |
| JSONB integration | ❌ | Separate policy storage |

**Verdict**: **Tham khảo pattern** — OPA quá lớn cho PBAC module hiện tại, nhưng operator extensibility pattern (predicate evaluation) rất đáng học hỏi.

---

### 4. Keycloak Authentication SPI

**URL**: https://github.com/keycloak/keycloak
**Stars**: ~25K+ | **Activity**: Daily | **License**: Apache 2.0

**Scoring Matrix**:
| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 10/10 | 20% | 2.00 |
| Applicability | 5/10 | 15% | 0.75 |
| Activity | 10/10 | 15% | 1.50 |
| Documentation | 8/10 | 15% | 1.20 |
| Code quality | 8/10 | 15% | 1.20 |
| Community | 10/10 | 10% | 1.00 |
| Popularity | 10/10 | 10% | 1.00 |
| **Overall** | | | **8.65** |

**Gap Analysis**:
| Aspect | Status | Details |
|--------|:------:|---------|
| Authentication Flow SPI | ✅ | `Authenticator` + `AuthenticationFlowContext` — Pipeline pattern |
| MFA Provider SPI | ✅ | `CredentialProvider` + `CredentialValidator` — Strategy Pattern |
| Required Actions | ✅ | Post-authentication pipeline steps |
| Extensible via SPI | ✅ | Plugin-based, implement interface |
| Spring Boot | ❌ | Standalone server, not library |

**Verdict**: **Tham khảo pattern quan trọng** — Keycloak Authentication Flow SPI là reference implementation tốt nhất cho Authentication Pipeline pattern. `Authenticator` interface + `AuthenticationFlowContext` chính là Chain of Responsibility.

---

### 5. Spring Cloud Function + Spring Modulith

**URL**: https://github.com/spring-projects/spring-modulith
**Stars**: ~1K+ | **Activity**: Weekly | **License**: Apache 2.0

**Scoring Matrix**:
| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 6/10 | 20% | 1.20 |
| Applicability | 9/10 | 15% | 1.35 |
| Activity | 8/10 | 15% | 1.20 |
| Documentation | 7/10 | 15% | 1.05 |
| Code quality | 9/10 | 15% | 1.35 |
| Community | 6/10 | 10% | 0.60 |
| Popularity | 6/10 | 10% | 0.60 |
| **Overall** | | | **7.35** |

**Gap Analysis**:
| Aspect | Status | Details |
|--------|:------:|---------|
| Module boundaries | ✅ | `@ApplicationModule` enforced boundaries |
| Event-driven | ✅ | `ApplicationModuleListener` |
| Clean Architecture fit | ⚠️ | Module-level, not layer-level |
| ArchUnit validation | ✅ | Architecture tests out of box |

**Verdict**: **Nice-to-have** — Hữu ích cho enforce module boundaries (auth ↔ rbac ↔ pbac) nhưng không giải quyết trực tiếp các SOLID violations.

---

## Summary & Recommendation

| Rank | Project | Score | Decision |
|------|---------|-------|----------|
| 1 | **OPA** | 8.80 | Tham khảo operator pattern |
| 2 | **Keycloak SPI** | 8.65 | Tham khảo Auth Pipeline + MFA Provider |
| 3 | **Axon Framework** | 8.65 | Tham khảo CommandBus pattern |
| 4 | **MediatR** | 7.45 | Tham khảo interface design |
| 5 | **Spring Modulith** | 7.35 | Nice-to-have module enforcement |

> **Recommendation**: **BUILD** — Tự implement lightweight patterns lấy cảm hứng từ các project trên:
> - CommandBus design từ Axon/MediatR
> - Authentication Pipeline từ Keycloak SPI
> - Operator extensibility từ OPA
> - MFA Provider Strategy từ Keycloak CredentialProvider SPI
