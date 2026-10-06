# Impact Analysis: filter-architecture-consolidation

> Generated: 2026-10-05 | Type: MAINTENANCE | Confidence: 1.00/1.0

## 1. Core Files (Directly Modified)

| # | File | Module | Action | FR | LOC Impact |
|---|------|--------|--------|-----|------------|
| 1 | `BaseRequestContextFilter.kt` | base-web-starter | [MODIFY] | FR-001 | +3 lines |
| 2 | `RequestContextProperties.kt` | base-web-starter | [MODIFY] | FR-001 | +1 field |
| 3 | `BaseControllerAdvice.kt` | base-core/src | [MODIFY] | FR-002 | Comment only |
| 4 | `AbstractSessionValidationFilter.kt` | base-security-starter | [MODIFY] | FR-011 | ~10 lines change |
| 5 | `ResourceJwtAuthFilter.kt` | account-service | [DELETE] | FR-003 | -86 lines |
| 6 | `SecurityConfig.kt` | account-service | [MODIFY] | FR-003 | ~5 lines remove |
| 7 | `application.yml` | account-service | [MODIFY] | FR-005 | +5 lines |
| 8 | `ResourceJwtAuthFilter.kt` | system-admin-service | [DELETE] | FR-004 | -86 lines |
| 9 | `SecurityConfig.kt` | system-admin-service | [MODIFY] | FR-004 | ~5 lines remove |
| 10 | `application.yml` | system-admin-service | [MODIFY] | FR-005 | +5 lines |
| 11 | `AuthSessionValidationFilter.kt` | auth-service | [ADD] | FR-006,007,008 | ~120 lines new |
| 12 | `ContentLanguageFilter.kt` | auth-service | [DELETE] | FR-009 | -48 lines |
| 13 | `SecurityConfig.kt` | auth-service | [MODIFY] | FR-010 | ~10 lines change |

## 2. Call Tree (Upstream Impact)

### BaseRequestContextFilter (FR-001)
```
BaseRequestContextFilter.doFilterInternal()     ← MODIFY (add Content-Language set)
  └── ALL requests to ALL services              ← AUTO via base-web-starter
  └── No callers affected (filter chain entry)  ← 🟢 Low impact
```

### AbstractSessionValidationFilter (FR-011)
```
AbstractSessionValidationFilter.sendUnauthorized()  ← MODIFY (JSON → ProblemDetail)
  ├── called from doFilterInternal() line 76       ← same class
  ├── called from doFilterInternal() line 90       ← same class
  ├── called from doFilterInternal() line 97       ← same class
  └── called from doFilterInternal() line 103      ← same class
  └── DefaultSessionValidationFilter (extends)     ← inherits unchanged
  └── AuthSessionValidationFilter (extends, NEW)   ← inherits unchanged
  └── Impact: 🟢 Low (internal method, same class)
```

### ResourceJwtAuthFilter DELETE (FR-003, FR-004)
```
ResourceJwtAuthFilter                              ← DELETE
  └── SecurityConfig.securityFilterChain()          ← MODIFY (remove addFilterBefore)
      └── addFilterBefore(jwtAuthFilter, ...)       ← REMOVE reference
      └── DefaultSessionValidationFilter            ← AUTO REPLACE (servlet registration)
      └── Impact: 🟡 Medium (auth mechanism change)
```

### JwtAuthFilter → AuthSessionValidationFilter (FR-006)
```
JwtAuthFilter                                      ← DELETE (236 LOC)
  └── SecurityConfig.securityFilterChain()          ← MODIFY (remove addFilterBefore)
  └── AuthSessionValidationFilter (NEW)             ← REPLACE at servlet level
      ├── TokenBlacklistCacheService.isBlacklisted() ← REUSE (no change)
      ├── JwtTokenExtractor.extractAndValidate()     ← REUSE (no change)
      ├── FingerprintService.validate()              ← REUSE (no change)
      └── MeterRegistry.counter()                   ← REUSE (no change)
      └── Impact: 🟡 Medium (refactor God object)
```

## 3. Blast Radius

| Depth | Symbols | Risk |
|-------|---------|------|
| d=1 (direct) | 13 files across 6 modules | 🟡 Medium |
| d=2 (indirect) | SecurityFilterChain configs (3 services) | 🟡 Medium |
| d=3 (transitive) | All authenticated endpoints (via filter chain) | 🟡 Medium |

**Overall Risk: 🟡 MEDIUM** — Large scope nhưng mỗi phase independent, changes mechanical (delete duplicate, add to base).

## 4. Reuse Map

| Symbol | Source | Decision | Reason |
|--------|--------|----------|--------|
| `DefaultSessionValidationFilter` | base-security-starter | **REUSE** (100%) | Exact replacement cho ResourceJwtAuthFilter |
| `AbstractSessionValidationFilter` | base-security-starter | **EXTEND** (80%) | auth-service kế thừa + override 4 methods |
| `ObservableFilterWrapper` | base-web-starter | **REUSE** (100%) | Wrap AuthSessionValidationFilter |
| `JwtTokenExtractor` | base-security-starter | **REUSE** (100%) | Token extraction logic |
| `TokenBlacklistCacheService` | auth-service | **REUSE** (100%) | Blacklist check delegation |
| `FingerprintService` | auth-service | **REUSE** (100%) | Device fingerprint validation |
| `OrderConstants` | base-core/src | **REUSE** (100%) | SESSION_VALIDATION = -1700 |
| `SecurityProperties` | base-security-starter | **REUSE** (100%) | publicPaths config |
| `RequestContextProperties` | base-web-starter | **MODIFY** | Add contentLanguageEnabled field |

## 5. Context Snapshot

### Base-Core State (before change)
- `BaseRequestContextFilter`: MDC + RequestContext only (NO Content-Language)
- `AbstractSessionValidationFilter.sendUnauthorized()`: JSON string format `{"error":"Unauthorized","message":"..."}`
- `DefaultSessionValidationFilter`: Registered via FilterRegistrationBean (order -1700), `@ConditionalOnProperty` + `@ConditionalOnMissingBean`

### Service State (before change)
- account-service: `ResourceJwtAuthFilter` (86 LOC) registered via `addFilterBefore` in SecurityConfig
- system-admin-service: `ResourceJwtAuthFilter` (86 LOC) registered via `addFilterBefore` in SecurityConfig
- auth-service: `JwtAuthFilter` (236 LOC) registered via `addFilterBefore` in SecurityConfig + `ContentLanguageFilter` (48 LOC)

### Archive Reference
- `2026-09-30-fix-session-filter-autoconfig-conflict`: Added `@ConditionalOnProperty(session-validation.enabled)` + `matchIfMissing=true`. auth-service sets `enabled=false`.
