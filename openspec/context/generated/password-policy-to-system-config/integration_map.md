# Integration Map

_Generated: 2026-10-02_

## Redis Cache Integration

- Client: `StringRedisTemplate` — auto-configured bean qua `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Protocol: Redis Protocol (RESP) qua Lettuce driver
- Operation: `opsForHash()`, `opsForValue()`, `expire()`
- Keys:
  - `system:config:AUTH_LOGIN`: Redis Hash chứa key-value chính sách mật khẩu.
  - TTL: Không set hoặc set 24h kèm refresh khi nhận event.

## Kafka Message Broker Integration

- Client: `KafkaTemplate<String, String>` / `KafkaTemplate<String, Any>` — Spring Kafka bean
- Protocol: Kafka binary protocol
- Topic: `system.config.changed`
- Producer: `system-admin-service` (khi update cấu hình trong `DomainConfigService`)
- Consumer: `auth-service` (lắng nghe `@KafkaListener` để làm mới L1 cache)

## PostgreSQL Database Integration

- Client: Spring Data JPA / Hibernate / HikariCP
- Schema:
  - `auth_db`: Drop table `password_policies` via Flyway migration `V26__drop_password_policies_table.sql`. Keep `password_history`.
  - `system_admin_db`: Bảng `system_configs` chứa các bản ghi thuộc group `AUTH_LOGIN`.

## NOT DETECTED

- SOAP / XML Banking Host integrations (N/A — hệ thống microservices nội bộ Bigbang)
- External REST Gateway (tích hợp trực tiếp qua Redis/Kafka)
