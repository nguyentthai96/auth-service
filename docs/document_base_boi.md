# Kiến Trúc Base-Core & Plugin System cho ERP

Mục tiêu: Phân tích và tổ chức lại các thành phần dùng chung (Core) và các thành phần tùy chọn (Optional) theo mô hình "Trình cắm Plugin" (Pluggable Architecture) nhằm tăng cường khả năng tái sử dụng, dễ mở rộng và cô lập lỗi cho toàn bộ hệ thống ERP (auth-service, account-service, system-admin-service, v.v.).

## 1. Nguyên Tắc Thiết Kế (Design Principles)

- **Core (Kernel):** Chứa những thành phần **bắt buộc** phải có mà mọi service đều dùng (nhỏ gọn, không có business logic).
- **Plugins (Starters):** Các tính năng **tùy chọn**, chỉ khi nào service khai báo dependency thì tính năng đó mới được "cắm" vào (Pluggable). Sử dụng cơ chế `AutoConfiguration` của Spring Boot.
- **SPI (Service Provider Interface):** Cung cấp các Interface/Pointcut để các plugin có thể móc (hook) vào logic của Core mà không làm Core bị phụ thuộc ngược.

---

## 2. Tổ Chức Thành Phần Dùng Chung (Bắt Buộc - Core Kernel)

Đây là xương sống của hệ thống, mọi microservice đều phải phụ thuộc vào.

### A. `common-utils` (Thư viện tiện ích thuần túy)
- **Đặc điểm:** Không phụ thuộc Spring Boot, chỉ là các extension functions và utility thuần Kotlin/Java.
- **Thành phần:**
  - `StringUtils`, `DateUtils`, `CollectionUtils`
  - `Snowflake` / `NanoId` Generator (Tạo ID)
  - `HexUtil`, `CryptoUtils` (Mã hóa cơ bản)
  - Các hàm extension cho Kotlin (`StringExtensions.kt`)

### B. `base-model` (Data Contracts)
- **Đặc điểm:** Chỉ chứa Class/Interface, không có logic thực thi.
- **Thành phần:**
  - `BaseEntity`, `SnowflakePersistentAuditableEntity` (Base JPA Entities)
  - `ApiResponse<T>`, `PageResponse<T>` (Chuẩn Response trả về)
  - `ErrorCodeBase` (Interface cho Exception)

### C. `base-core` (The Kernel)
- **Đặc điểm:** Cấu hình lõi của Spring Boot.
- **Thành phần:**
  - **Global Exception Handling:** `@ControllerAdvice`, `GlobalExceptionHandler`.
  - **Base Controller & Service:** `AbstractCrudService`, `BaseController`.
  - **AOP Logging:** Tự động log Request/Response.

---

## 3. Tổ Chức Thành Phần Tùy Chọn (Trình Cắm - Plugins / Starters)

Các thành phần này được tổ chức theo chuẩn **Spring Boot Starter**. Microservice nào cần tính năng nào thì "cắm" starter đó vào `build.gradle.kts`.

### A. Data & Persistence Plugins
1. **`base-data-starter`**: 
   - *Khi nào cắm:* Khi service cần dùng DB PostgreSQL + JPA.
   - *Cung cấp:* Cấu hình HikariCP, JPA Auditing (tự động điền `createdBy`, `updatedAt`), Flyway migration.
2. **`base-redis-starter`**: 
   - *Khi nào cắm:* Khi service cần Cache hoặc Redis.
   - *Cung cấp:* `RedisTemplate`, Cấu hình `@EnableCaching`, Distributed Lock.

### B. Security & Identity Plugins
3. **`base-security-starter`**: 
   - *Khi nào cắm:* Khi service cần bảo vệ API (Auth, Account, Admin, v.v.).
   - *Cung cấp:* JWT Validation Filter, RBAC/PBAC Evaluator bean, Security Context config.
   - *Tính năng cắm thêm (Pluggable):* Có thể disable cấu hình Keycloak bằng property `app.security.keycloak.enabled=false`.
4. **`base-mfa-plugin` (New)**: 
   - *Chuyên biệt cho auth-service:* Chứa logic verify OTP, TOTP, CAPTCHA.

### C. Integration & Communication Plugins
5. **`base-messaging-starter`**: 
   - *Khi nào cắm:* Khi service cần Kafka/RabbitMQ.
   - *Cung cấp:* KafkaProducer, KafkaConsumer configs, Event Schema (Avro/JSON).
6. **`base-file-starter`**: 
   - *Khi nào cắm:* Khi service cần upload/download file (MinIO, S3).
   - *Cung cấp:* `StorageService` interface và implementation MinIO.

### D. Resilience & Observability Plugins
7. **`base-resilience-starter`**: 
   - *Cung cấp:* Rate Limiting (Bucket4j), Circuit Breaker (Resilience4j).
   - *Cách cắm:* Cấu hình thông qua Annotation `@RateLimit` trên các API cần thiết.
8. **`base-observability-starter`**: 
   - *Cung cấp:* Prometheus metrics, Micrometer tracing, OpenTelemetry (Log aggregation).

---

## 4. Cơ Chế "Trình Cắm" (Plugin Mechanism) hoạt động thế nào?

Để các Starter thực sự hoạt động như một "Plugin", ta áp dụng 3 kỹ thuật sau:

### 4.1. Điều kiện kích hoạt (Conditional Loading)
Sử dụng các Annotation của Spring Boot để Plugin chỉ hoạt động khi đáp ứng đủ điều kiện (Property bật, Class tồn tại).
```kotlin
@Configuration
@ConditionalOnProperty(prefix = "app.ratelimit", name = ["enabled"], havingValue = "true")
@ConditionalOnClass(Bucket4j::class)
class RateLimitAutoConfiguration {
    @Bean
    fun rateLimitInterceptor(): RateLimitInterceptor = RateLimitInterceptor()
}
```

### 4.2. Java SPI (Service Provider Interface) cho Event & Hooks
Hệ thống Core định nghĩa các Interface, các Plugin sẽ implements và tự đăng ký. Ví dụ `AuditLogPlugin`:
```kotlin
// Trong base-core
interface SystemActionHook {
    fun afterActionExecuted(actionContext: ActionContext)
}

// Trong base-audit-starter (Plugin)
@Component
class AuditLoggingHook : SystemActionHook {
    override fun afterActionExecuted(context: ActionContext) {
        // Log action to Elasticsearch asynchronously
    }
}
```
Spring Boot Core sẽ inject `List<SystemActionHook>` và gọi đồng loạt.

### 4.3. Spring Modulith (Application Modules)
Ở tầng Microservice (vd: `system-admin-service`), nếu có các tính năng lớn (như Workflow, Menu, API Partner), thay vì tách ra Microservice riêng, ta dùng **Spring Modulith** để chia chúng thành các "Internal Plugins" (Module nội bộ).
- Khai báo file `package-info.java` để giới hạn truy cập.
- Giao tiếp giữa `MenuModule` và `WorkflowModule` thông qua Spring Application Events thay vì gọi Service trực tiếp.

---

## 5. Kế Hoạch Triển Khai Thực Tế

### Bước 1: Refactor Kernel (Base Core)
- Dọn dẹp `common-utils` và `base-core` loại bỏ mọi thư viện thừa.
- Tách `BaseEntity` và `ApiResponse` ra `base-model` để các service khác có thể import mà không cần kéo toàn bộ Spring Boot web.

### Bước 2: Chuẩn hóa Auto-Configuration (Starters)
- Đảm bảo tất cả starters trong thư mục `starters/` đều có tệp `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Thêm `@ConditionalOnProperty` cho mọi tính năng (ví dụ: tắt Kafka nếu không dùng).

### Bước 3: Áp dụng vào 3 Modules ERP
- **auth-service:** 
  - *Dependencies:* base-core, base-data-starter, base-security-starter, base-redis-starter (lưu token).
- **account-service:** 
  - *Dependencies:* base-core, base-data-starter, base-security-starter, base-messaging-starter (nhận event login).
- **system-admin-service:** 
  - *Dependencies:* base-core, base-data-starter, base-security-starter, base-resilience-starter (Rate limit API Partner).

## Mời đánh giá
Kế hoạch này đã bóc tách rõ Core và Plugin. Nếu bạn đồng ý với hướng kiến trúc này, chúng ta sẽ tiến hành cấu trúc lại các folder và code trong `base-core` cũng như `common-utils` theo chuẩn AutoConfiguration của Spring.
