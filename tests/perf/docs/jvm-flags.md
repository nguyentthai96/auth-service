# JVM Performance Flags for auth-service

## Performance Testing Profile

```bash
export PERF_JVM_OPTS="\
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms512m \
  -Xmx1024m \
  -XX:+AlwaysPreTouch \
  -Djdk.tracePinnedThreads=full \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=./heapdumps"

# Run with perf flags
java $PERF_JVM_OPTS -jar auth-service.jar
```

## Flag Explanation

| Flag | Purpose | Impact |
|------|---------|--------|
| `-XX:+UseZGC` | Z Garbage Collector — low-latency GC | GC pauses <10ms typically |
| `-XX:+ZGenerational` | Generational ZGC (JDK 21+) — better throughput | Reduces CPU overhead vs non-generational |
| `-Xms512m -Xmx1024m` | Fixed heap size — eliminates resizing pauses | Predictable memory footprint |
| `-XX:+AlwaysPreTouch` | Pre-touch heap pages on startup | Eliminates page faults during load |
| `-Djdk.tracePinnedThreads=full` | Log virtual thread pinning events | See `virtual-thread-pinning.md` |
| `-XX:+HeapDumpOnOutOfMemoryError` | Auto heap dump on OOM | Post-mortem analysis |

## Docker / Gradle Usage

### Docker
```dockerfile
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx1024m -XX:+AlwaysPreTouch"
ENTRYPOINT ["java", "$JAVA_OPTS", "-jar", "app.jar"]
```

### Gradle (bootRun)
```bash
./gradlew bootRun --args="--spring.profiles.active=dev" \
  -Dorg.gradle.jvmargs="$PERF_JVM_OPTS"
```

## HikariCP Pool Sizing

Formula: `connections = (DB_cores × 2) + effective_spindle_count`

| Profile | DB Specs | Pool Size | Env Var |
|---------|----------|-----------|---------|
| Development | 4-core, 8GB, SSD | 10 | `DB_POOL_MAX=10` |
| Production | 8-core, 32GB, SSD | 20 | `DB_POOL_MAX=20` |

> SSD → `effective_spindle_count = 1` (no rotational penalty)
> Constraint: `num_instances × pool_size < PostgreSQL max_connections` (default 100)
