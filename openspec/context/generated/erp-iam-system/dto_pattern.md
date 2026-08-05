# DTO Pattern

> Feature: erp-iam-system
> Generated: 2026-08-05

## Request/Response DTOs

- Pattern: `<Entity><Action>Request` / `<Entity><Action>Response` (e.g., `LoginRequest`, `TokenResponse`)
- Package convention: Typically inside `adapter.in.web.dto` or `application.dto`

## Validation

- Annotations: Jakarta Validation API (`@Valid`, `@NotNull`, `@NotBlank`)

## Mapping

- Method: MapStruct or Extension Functions (TBD)
