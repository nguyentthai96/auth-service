# Error Pattern

> Feature: erp-iam-system
> Generated: 2026-08-05

## Exception Handling

- Pattern: `@ControllerAdvice` + RFC 7807 ProblemDetail format
- Base Classes: `BaseException` (or similar standard from base-core)

## Common Exceptions

- `AuthenticationException`
- `AccessDeniedException`
- `ResourceNotFoundException`
- `ValidationException`
