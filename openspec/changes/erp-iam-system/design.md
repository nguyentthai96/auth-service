# Design: erp-iam-system

## 1. Architecture Overview
- **Pattern**: Hexagonal Architecture / Clean Architecture within Spring Boot Modulith
- **Authorization Flow**: Decentralized. JWT (RS256) is kept lean (only identity + role IDs).
- **API Gateway**: Handles rate limiting independently by reading Bucket4j configuration populated by `system-admin-service` via Redis.
- **Inter-service Communication**: Event-driven via Kafka for eventual consistency (e.g., auto-provisioning), REST for synchronous operations.

## 2. Component Design

### 2.1 auth-service
- **MfaService**: Handles generation and verification of OTP/TOTP.
- **SsoAdapter**: Handles OAuth2 flow with Keycloak. Upon auto-provision, publishes `iam.user.sso_provisioned` Kafka event.
- **JwtService**: Upgraded to use RS256 for asymmetric signing. Issues lean JWTs without bulky permission payloads.

### 2.2 account-service
- **ProfileService**: Consumes `iam.user.sso_provisioned` to create user profiles automatically.
- **DeviceSessionService**: Tracks active tokens and devices. Integrates with Redis to store active session IDs.

### 2.3 system-admin-service
- **MenuPermissionService**: Computes accessible menus based on intersection of Role Permissions and Menu Permissions. Caches results in Redis.
- **ApiKeyService**: Generates secure keys (format `ntt_pk_...`), stores hashes, and populates Bucket4j rate limit configs into Redis. Publishes Redis Pub/Sub events for immediate revocation.
- **WorkflowEngine**: Evaluates JSONB conditions to determine the next approval step dynamically.

## 3. Database Design
- Separate schemas or logical separation per service module.
- Audit Log stored immutably.
- Tree structures (Menu, Department) use Parent-ID strategy with `level` caching.

## 4. Security Design
- JWT validated at Gateway using Public Key exposed by `auth-service`.
- Internal services communicate via trusted JWT propagation or gRPC with internal tokens.
