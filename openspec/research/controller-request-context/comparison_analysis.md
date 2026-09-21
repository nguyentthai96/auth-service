# Comparison Analysis — Controller Standardization Approaches

## 1. Approaches Evaluated

| Approach | Description | Complexity |
|----------|------------|-----------|
| **A. Request-Scoped Bean + Base Controller** | `@RequestScope` bean + controller hierarchy | Medium |
| **B. HandlerMethodArgumentResolver** | Custom Spring argument resolver cho `RequestContext` | Medium |
| **C. ThreadLocal Static Util** | Static `RequestContextHolder` class kiểu legacy | Low |
| **D. AOP Aspect + Annotation** | `@WithRequestContext` annotation + aspect | High |

---

## 2. Comparison Matrix

| Criteria | Weight | A. Request-Scoped Bean | B. ArgumentResolver | C. ThreadLocal Static | D. AOP Aspect |
|----------|--------|----------------------|--------------------|--------------------|-------------|
| Clean Architecture compliance | 30% | ✅ 9/10 | ✅ 8/10 | ⚠️ 6/10 | ✅ 8/10 |
| Code reduction | 25% | ✅ 9/10 | ⚠️ 7/10 | ⚠️ 7/10 | ✅ 8/10 |
| Testability | 20% | ✅ 9/10 | ✅ 8/10 | ❌ 4/10 | ⚠️ 6/10 |
| Spring ecosystem alignment | 15% | ✅ 10/10 | ✅ 9/10 | ⚠️ 5/10 | ✅ 8/10 |
| Migration effort | 10% | ⚠️ 7/10 | ⚠️ 6/10 | ✅ 9/10 | ⚠️ 5/10 |
| **Weighted Score** | | **8.8** | **7.6** | **6.0** | **7.2** |

---

## 3. Detailed Analysis

### A. Request-Scoped Bean + Base Controller ⭐ RECOMMENDED

**Ưu điểm:**
- Spring IoC quản lý lifecycle → thread-safe, đúng scope
- Testable: Inject mock `RequestContext` dễ dàng
- Kotlin-friendly: Constructor injection, extension functions
- Base controller hierarchy giảm boilerplate triệt để
- Không cần static/global state

**Nhược điểm:**
- Thêm constructor parameter vào mỗi controller
- Cần CGLIB proxy cho `@RequestScope` bean
- Migration effort: phải sửa tất cả controller constructors

### B. HandlerMethodArgumentResolver

**Ưu điểm:**
- Không cần base class — mỗi method nhận `RequestContext` như parameter
- Declarative — thêm parameter vào method signature

**Nhược điểm:**
- Không giải quyết response helpers (vẫn cần base class cho `ok()`, `message()`)
- Mỗi method phải khai báo parameter → vẫn có repetition
- Không natural cho Kotlin (parameter object mỗi method)

### C. ThreadLocal Static Util

**Ưu điểm:**
- Đơn giản nhất — `RequestContextUtil.getCurrentUserId()`
- Migration dễ — chỉ đổi tên method call

**Nhược điểm:**
- Static method → khó test (cần PowerMock hoặc similar)
- Không Clean Architecture — global state
- Thread-unsafe nếu virtual threads hoặc async
- Anti-pattern trong modern Spring

### D. AOP Aspect

**Ưu điểm:**
- Cross-cutting concern → đúng bản chất AOP
- Không invasive — annotation-based

**Nhược điểm:**
- Black magic — khó debug
- Performance overhead (proxy, reflection)
- Over-engineering cho use case này
- Không giúp response helpers

---

## 4. Recommendation

> **Chọn Approach A: Request-Scoped Bean + Base Controller Hierarchy**

**Lý do:**
1. **Cao nhất về Clean Architecture**: DI-based, injectable, mockable
2. **Code reduction triệt để nhất**: Base controller + response helpers + extension functions
3. **Spring-native**: Dùng `@RequestScope`, `@Component`, constructor injection — 100% Spring idiom
4. **Testability tốt nhất**: Inject mock `RequestContext` trong unit test
5. **Kotlin-friendly**: Leverage data class, extension functions, null safety
6. **Consistent với codebase hiện tại**: Đã dùng constructor injection, CQRS command pattern

**Trade-offs chấp nhận:**
- Sửa constructor tất cả controllers (one-time effort, safe)
- CGLIB proxy overhead (~negligible)
