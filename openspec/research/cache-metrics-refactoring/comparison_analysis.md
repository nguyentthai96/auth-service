# Deep Analysis & Trade-offs: Xử lý Bean Overriding cho Cache Metrics

Tài liệu này đi sâu vào phân tích sự khác biệt giữa 2 bean đang gây ra conflict, từ đó đánh giá chi tiết các phương án và giải thích tại sao Phương án 3 lại là giải pháp tối ưu nhất (chuẩn kiến trúc Spring Boot).

---

## 1. So Sánh 2 Bean: Tại sao có conflict và chúng làm gì?

### 1.1 `org.springframework.boot.cache.autoconfigure.metrics.CacheMetricsRegistrar` (Spring Boot Mặc định)
- **Nhiệm vụ:** Đây là "nhạc trưởng" (orchestrator) của Spring Boot Actuator trong việc giám sát (monitor) các Cache.
- **Cơ chế hoạt động:**
  - Nó lắng nghe các sự kiện tạo/khởi tạo CacheManager.
  - Nó sẽ quét toàn bộ danh sách các `CacheMeterBinderProvider` có trong ApplicationContext.
  - Khi một `Cache` được tạo ra, nó gọi các provider này. Nếu provider hỗ trợ loại Cache đó (ví dụ `CaffeineCache`, `RedisCache`), provider sẽ trả về một `MeterBinder` chuẩn để đăng ký metrics vào `MeterRegistry`.
- **Ưu điểm:** Hoạt động linh hoạt, tự động bắt được các cache được tạo lazily (chỉ tạo khi có người gọi tới).
- **Thiếu sót:** Chỉ hỗ trợ các cache chuẩn của Spring. Do chúng ta dùng `TwoLevelCache` (custom class), Registrar mặc định sẽ không biết cách bóc tách lớp L1 (Caffeine) ra để đo đạc metrics.

### 1.2 `com.ntt.basecore.autoconfigure.cache.CacheMetricsRegistrar` (Của `base-core`)
- **Nhiệm vụ:** Một Registrar tùy chỉnh được viết riêng để bind metrics cho `TwoLevelCache`.
- **Cơ chế hoạt động:**
  - Implement `SmartInitializingSingleton`, nghĩa là nó chờ tất cả các bean khởi tạo xong, sau đó lặp qua danh sách cache hiện có và gắp lớp L1 (Caffeine) ra để nhúng `CaffeineCacheMetrics`.
  - Khai báo thêm custom gauge `cache.hit.ratio`.
  - Chạy một Virtual Thread định kỳ 5 phút/lần để kiểm tra và in ra log cảnh báo nếu hit ratio < 80%.
- **Ưu điểm:** Đo đạc được metrics đặc thù của `TwoLevelCache`, có chức năng cảnh báo (monitor) tự động rất hữu ích.
- **Khuyết điểm:** Do dùng `SmartInitializingSingleton`, nó chỉ quét được các cache đã tạo sẵn lúc khởi động. Nếu một cache mới được tạo động (lazy load), nó sẽ bị bỏ sót. Hơn nữa, việc tự đặt tên trùng với Spring Boot gây ra `BeanDefinitionOverrideException`.

---

## 2. Trả lời Câu hỏi của User

### Q1: Tại sao đổi tên bean (Phương án 1) lại giải quyết được? 2 bean này khác nhau ra sao?
Như đã phân tích ở trên, 2 bean này có **nhiệm vụ tương tự nhau nhưng cách tiếp cận và đối tượng phục vụ khác nhau**:
- Bean của Spring Boot phục vụ các cache chuẩn và dùng cơ chế event-driven linh hoạt.
- Bean của `base-core` phục vụ riêng `TwoLevelCache` và dùng cơ chế quét 1 lần khi khởi động (SmartInitializingSingleton).

**Nếu đổi tên (Phương án 1):** Cả 2 bean đều sẽ tồn tại. Spring Boot sẽ chạy Registrar của nó (nhưng sẽ bỏ qua `TwoLevelCache` vì nó không hiểu). `base-core` sẽ chạy Registrar của mình để bind metrics cho `TwoLevelCache`. 
- **Lợi ích:** Giải quyết ngay lập tức lỗi crash mà không cần refactor code.
- **Hạn chế:** Code của chúng ta đang chạy "ngoài luồng" chuẩn của Spring Boot. Chức năng giám sát các cache tạo sau (lazy-init) của `base-core` sẽ vẫn bị thiếu.

### Q2: Quyết định dùng cả 2 hay chỉ dùng 1 trong 2 (Nếu dùng `@AutoConfigureBefore`)?
Nếu dùng `@AutoConfigureBefore` + `@ConditionalOnMissingBean`, chúng ta đang "đánh lừa" Spring Boot để **chỉ dùng 1 bean của `base-core`** và vứt bỏ bean của Spring Boot.
- **Hậu quả:** Rất nguy hiểm! Spring Boot Actuator có thể tự động inject bean `CacheMetricsRegistrar` (bản gốc) ở các chỗ khác trong framework. Nếu ta ép nó dùng bản custom của ta (khác Type), ứng dụng sẽ bị văng lỗi `BeanNotOfRequiredTypeException` hoặc `NoSuchBeanDefinitionException`. 
- **Kết luận:** KHÔNG THỂ dùng phương án 2 (thay thế type). Chỉ có thể cho phép tồn tại cả 2 (Phương án 1) hoặc đi theo Phương án 3.

---

## 3. "Thinking More": Tại sao Phương án 3 (CacheMeterBinderProvider) lại Tối ưu nhất?

Nếu chúng ta nhìn vào kiến trúc của Spring Boot, Spring "mời" các developer tích hợp custom cache của họ bằng cách implement `CacheMeterBinderProvider`, chứ không phải bằng cách tự viết lại toàn bộ vòng lặp duyệt cache.

### Tầm nhìn kiến trúc cho Phương án 3:

Chúng ta sẽ **tách bạch 2 trách nhiệm** đang bị nhồi nhét trong `CacheMetricsRegistrar` của `base-core`:

1. **Trách nhiệm Bind Metrics (Thu thập số liệu):**
   - Viết một class `TwoLevelCacheMeterBinderProvider implements CacheMeterBinderProvider<TwoLevelCache>`.
   - Spring Boot's Registrar (mặc định) sẽ tự động phát hiện provider này. Bất cứ khi nào một `TwoLevelCache` được tạo ra (dù là lúc khởi động hay lazy load), Spring Boot sẽ gọi provider của ta.
   - Bên trong provider, ta return một custom `MeterBinder` chứa logic `CaffeineCacheMetrics` và đăng ký custom `hit.ratio`.

2. **Trách nhiệm Monitor (Cảnh báo Hit Ratio < 80%):**
   - Tách logic Virtual Thread Scheduler ra thành một bean riêng, ví dụ: `CacheHitRatioAlertMonitor`.
   - Nó sẽ chạy độc lập, không dính dáng đến quá trình khởi tạo Metrics nữa.

### Lợi ích tuyệt đối của Phương án 3:
- **Chuẩn Spring Boot 100%**: Không còn bất kỳ conflict nào, không cần override bean, không cần rename bean.
- **Hỗ trợ Lazy-load Cache**: Metrics sẽ tự động được bind ngay cả khi một cache mới được tạo ra trong quá trình ứng dụng đang chạy (runtime), điều mà `SmartInitializingSingleton` cũ không làm được.
- **Clean Code (SRP)**: Tách biệt rõ ràng giữa "Đo lường" (Metrics Provider) và "Giám sát/Cảnh báo" (Alert Monitor).

## 4. Kết luận
Dù Phương án 1 (Rename Bean) giải quyết được lỗi ngay lập tức, nhưng nó chỉ là một workaround (lắp ráp tạm). **Phương án 3 mới là "Phương án Tối ưu" (Optimal Solution)** về mặt kiến trúc phần mềm, giúp module `base-core` tương thích hoàn toàn với hệ sinh thái của Spring Boot Actuator. 
