# Base Class Map

_Generated: 2026-09-08 | Services: auth-service (shared/config, auth/application)_

## Controller

- CqrsAuthController — `auth/adapter/in/web/CqrsAuthController.kt` (login, register, refresh, logout endpoints)
- AccountLifecycleController — `auth/adapter/in/web/AccountLifecycleController.kt` (change-password endpoint)

## Handler

- NOT DETECTED (Clean Architecture — commands/queries, not traditional handlers)

## Factory

- NOT DETECTED

## Service (Application Layer)

- TokenGenerator — `auth/application/command/TokenGenerator.kt` (passwordEncoder.encode/matches)
- PasswordPolicyService — `auth/application/PasswordPolicyService.kt` (passwordEncoder.encode/matches, password history)
- JwtService — `auth/application/JwtService.kt` (JWT signing/verification)

## Configuration

- SecurityConfig — `shared/config/SecurityConfig.kt` (PasswordEncoder @Bean definition)
- SecurityProperties — `shared/config/SecurityProperties.kt` (PasswordProperties data class)

## Client / Gateway

- NOT DETECTED (password hashing is internal — no external client)

## NOT DETECTED

- Handler, Factory, Client/Gateway
