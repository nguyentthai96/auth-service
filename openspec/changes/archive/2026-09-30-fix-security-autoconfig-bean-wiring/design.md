# Design: fix-security-autoconfig-bean-wiring

## Architecture Decision

### Problem Statement

Spring Boot auto-configuration nested `@Configuration` classes have 3 interrelated issues:

```
┌── @AutoConfiguration ─────────────────────────────┐
│  SecurityAutoConfiguration                        │
│                                                   │
│  ┌─ @Configuration (nested) ───────────────────┐  │
│  │  @ConditionalOnBean(EMF) ← evaluate sai ❌  │  │
│  │  @EnableJpaRepositories  ← override scan ❌  │  │
│  │  @EntityScan             ← override scan ❌  │  │
│  │  DynamicAuthorizationJpaConfiguration       │  │
│  │    └─ @Bean DynamicAuthorizationManager     │  │
│  └─────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

### Decision: Consumer-Controlled Scanning (Approach C3)

**Rationale:**
- Starter libraries KHÔNG nên khai báo `@EntityScan`/`@EnableJpaRepositories` vì cả hai đều **override** (không additive) consumer's auto-scan
- `@ConditionalOnBean` trên nested `@Configuration` là unreliable — Spring Boot docs khuyến nghị chỉ dùng trên `@AutoConfiguration` classes
- Consumer tự control scan scope — extensible và không couple

### After Fix

```
┌── @AutoConfiguration ─────────────────────────────┐
│  SecurityAutoConfiguration                        │
│  afterName = [HibernateJpaAutoConfiguration]      │
│                                                   │
│  ┌─ @Configuration (nested) ───────────────────┐  │
│  │  @ConditionalOnClass(JPA classes) ✅        │  │
│  │  (no @EntityScan)                           │  │
│  │  (no @EnableJpaRepositories)                │  │
│  │  (no @ConditionalOnBean)                    │  │
│  │  DynamicAuthorizationJpaConfiguration       │  │
│  │    └─ @Bean DynamicAuthorizationManager     │  │
│  └─────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘

┌── Consumer (auth-service) ────────────────────────┐
│  @SpringBootApplication                           │
│  @EntityScan(authservice + basecore.entity)    ✅ │
│  @EnableJpaRepositories(authservice + basecore) ✅│
│  AuthServiceApplication                           │
└───────────────────────────────────────────────────┘
```

## Component Mapping

| Component | File | Change Type |
|-----------|------|-------------|
| `SecurityAutoConfiguration` | `base-security-starter/.../SecurityAutoConfiguration.kt` | [MODIFY] |
| `AuthServiceApplication` | `auth-service/.../AuthServiceApplication.kt` | [MODIFY] |

## Data Flow

Không thay đổi — `DynamicAuthorizationManager` vẫn:
1. Inject `EndpointSecurityRuleRepository`
2. Load security rules từ DB
3. Cache qua Caffeine
4. Evaluate access decisions trong `SecurityConfig` filter chain

## Error Handling

Không thay đổi error handling.

## Testing Strategy

- **Verification**: `./gradlew clean bootRun` — service starts successfully
- **Regression**: Tất cả existing security rules vẫn hoạt động
- **Debug**: `--debug` flag cho Spring Boot condition evaluation report
