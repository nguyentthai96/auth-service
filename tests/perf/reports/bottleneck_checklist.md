# Bottleneck Analysis Checklist — USE Method

> Systematic per-resource analysis: **U**tilization → **S**aturation → **E**rrors

---

## CPU

| Check | Prometheus Query | Threshold | Action if Exceeded |
|-------|-----------------|-----------|-------------------|
| Utilization | `process_cpu_usage{application="auth-service"}` | >80% sustained | Profile with async-profiler, check BCrypt strength |
| System Load | `system_load_average_1m{application="auth-service"}` | > num_cores | Scale horizontally or optimize hot paths |
| GC CPU Time | `rate(jvm_gc_pause_seconds_sum[1m])` | >5% of wall time | Tune GC, reduce allocation rate |

---

## Memory (JVM Heap)

| Check | Prometheus Query | Threshold | Action if Exceeded |
|-------|-----------------|-----------|-------------------|
| Heap Used | `jvm_memory_used_bytes{area="heap"}` | >80% of max | Increase -Xmx or find memory leaks |
| GC Frequency | `rate(jvm_gc_pause_seconds_count[1m])` | >10 pauses/min | Young gen too small, tune -Xmn |
| GC Pause Duration | `jvm_gc_pause_seconds_max` | >100ms | Switch to ZGC, reduce object allocation |
| Memory Leak | Heap trend over soak test | Monotonically increasing | Heap dump analysis, check for static collections |

---

## Database Pool (HikariCP)

| Check | Prometheus Query | Threshold | Action if Exceeded |
|-------|-----------------|-----------|-------------------|
| Active Connections | `hikaricp_connections_active` | >80% of pool max | Increase pool or optimize query time |
| Pending Threads | `hikaricp_connections_pending` | >0 for >30s | Pool exhaustion — increase pool or reduce query time |
| Connection Timeout | `hikaricp_connections_timeout_total` | Any increment | Pool too small or queries too slow |
| Leak Detection | App logs for `Connection leak detection` | Any occurrence | Find unclosed connections, check @Transactional boundaries |
| Connection Create Time | `hikaricp_connections_creation_seconds` | >200ms | Network latency or DB overloaded |

### Pool Sizing Formula
```
optimal_pool_size = (DB_cores × 2) + effective_spindle_count

| DB Server | Cores | SSD | Pool Size | Env Var |
|-----------|-------|-----|-----------|---------|
| Dev       | 4     | Yes | 10        | DB_POOL_MAX=10 |
| Prod      | 8     | Yes | 20        | DB_POOL_MAX=20 |

Constraint: num_instances × pool_size < PostgreSQL max_connections (default 100)
```

---

## Redis

| Check | Prometheus Query | Threshold | Action if Exceeded |
|-------|-----------------|-----------|-------------------|
| Command Rate | `rate(spring_data_repository_invocations_seconds_count[1m])` | Baseline + 50% | Cache stampede? Check TTL strategy |
| Latency | `spring_data_repository_invocations_seconds_max` | >10ms | Network issue or large values |
| Connection Pool | Redis `INFO clients` (via CLI) | connected_clients > 50 | Lettuce pool config |
| Memory | Redis `INFO memory` | used_memory > 80% maxmemory | Eviction policy, key TTL audit |
| Cache Hit Rate | App-level metrics (if instrumented) | <80% | TTL too short, cache key design |

---

## HTTP Server (Tomcat)

| Check | Prometheus Query | Threshold | Action if Exceeded |
|-------|-----------------|-----------|-------------------|
| Active Threads | `tomcat_threads_busy_threads` | >80% of max | Increase max-threads or use virtual threads |
| Thread Pool Config | `tomcat_threads_config_max_threads` | Default (200) | Tune for load profile |
| Queue Depth | `tomcat_threads_current_threads - tomcat_threads_busy_threads` | Growing trend | Requests queuing — scale or optimize |
| Error Rate | `rate(http_server_requests_seconds_count{status=~"5.."}[1m])` | >0.1% | Investigate 5xx causes |

---

## Virtual Threads

| Check | Method | Threshold | Action if Exceeded |
|-------|--------|-----------|-------------------|
| Pinning Events | `grep "onPinned" app.log` | Any occurrence | Replace `synchronized` with `ReentrantLock` |
| Carrier Thread Starvation | High latency + low CPU | Latency spikes with CPU < 50% | Pinning likely — check JVM flag output |

---

## Decision Flowchart

```
START: High P95 Latency observed
  │
  ├─ CPU > 80%? ──Yes──► Profile with async-profiler
  │                       → Check BCrypt strength
  │                       → Check serialization cost
  │
  ├─ DB Pool Pending > 0? ──Yes──► Query optimization
  │                                 → Increase pool size
  │                                 → Add caching layer
  │
  ├─ GC Pauses > 100ms? ──Yes──► Switch to ZGC
  │                               → Reduce allocation rate
  │                               → Increase heap
  │
  ├─ Redis Latency > 10ms? ──Yes──► Check network
  │                                  → Reduce value size
  │                                  → Pipeline commands
  │
  ├─ Thread Pool Full? ──Yes──► Enable virtual threads
  │                              → Increase max-threads
  │                              → Check for blocking calls
  │
  └─ None of the above ──► Check downstream services
                            → Check network latency
                            → Check DNS resolution
```
