# Virtual Thread Pinning Detection Guide

## What is Pinning?

Virtual threads (Project Loom, JDK 21+) are lightweight threads that can be **pinned** to their carrier (platform) thread when they enter a `synchronized` block or native method. This prevents the carrier thread from being reused, effectively reducing concurrency.

## Enable Detection

```bash
# JVM flag — log ALL pinning events with full stack trace
-Djdk.tracePinnedThreads=full

# Lightweight — log only short form
-Djdk.tracePinnedThreads=short
```

## Sample Output

```
Thread[#40,ForkJoinPool-1-worker-3,5,CarrierThreads] 
    java.base/java.lang.VirtualThread$VThreadContinuation.onPinned(VirtualThread.java:183)
    java.base/jdk.internal.vm.Continuation.onPinned0(Continuation.java:393)
    java.base/java.lang.VirtualThread.park(VirtualThread.java:582)
    java.base/java.lang.System$2.parkVirtualThread(System.java:2643)
    java.base/jdk.internal.misc.VirtualThreads.park(VirtualThreads.java:54)
    java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:369)
    java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:754)
    java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:990)
    <your-code-stack-trace>
```

## How to Fix

### Replace `synchronized` with `ReentrantLock`

**Before (causes pinning):**
```kotlin
class SomeService {
    private val cache = mutableMapOf<String, Any>()
    
    @Synchronized
    fun getCached(key: String): Any? {
        return cache[key]
    }
}
```

**After (no pinning):**
```kotlin
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SomeService {
    private val cache = mutableMapOf<String, Any>()
    private val lock = ReentrantLock()
    
    fun getCached(key: String): Any? {
        return lock.withLock { cache[key] }
    }
}
```

## Common Offenders in Spring Ecosystem

| Library | Pinning Source | Status |
|---------|--------------|--------|
| HikariCP | `synchronized` in connection pool | Fixed in 5.1+ (uses `ReentrantLock`) |
| Logback | `synchronized` in appender | Fixed in 1.4.12+ |
| Jackson | `synchronized` in `ObjectMapper` | Safe — short critical sections |
| PostgreSQL JDBC | `synchronized` in connection | Partially fixed, monitor in logs |
| Tomcat | `synchronized` in connector | Fixed in 10.1.16+ |

## Monitoring During Load Tests

1. Start auth-service with `-Djdk.tracePinnedThreads=full`
2. Run K6 load test (e.g., `full_load.js`)
3. Grep logs for pinning events:
   ```bash
   grep -c "VirtualThread.*onPinned" app.log
   ```
4. If count > 0 during load → investigate stack trace → replace `synchronized`
