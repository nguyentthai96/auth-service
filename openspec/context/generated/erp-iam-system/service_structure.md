# Service Structure

> Feature: erp-iam-system
> Generated: 2026-08-05

## Package Structure

- `com.ntt.authservice.auth`: Authentication module
  - `adapter.in.web`: REST controllers (`AuthController.kt`)
  - `application`: Business logic (`AuthService.kt`, `JwtService.kt`)
- `com.ntt.authservice.pbac`: Policy-Based Access Control module
- `com.ntt.authservice.rbac`: Role-Based Access Control module

## Naming Conventions

- Controller: `*Controller.kt` (e.g., `AuthController.kt`)
- Service: `*Service.kt` (e.g., `AuthService.kt`, `JwtService.kt`)
