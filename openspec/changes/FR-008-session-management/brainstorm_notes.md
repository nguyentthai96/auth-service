# Brainstorm Notes: FR-008 Session Management

> Deep thinking notes cho Session Management — phân tích các hướng thiết kế, trade-offs, và quyết định kiến trúc.

## 1. Problem Decomposition

### 1.1 Core Challenges
- **C1**: Track session state across distributed instances → Redis as SSoT
- **C2**: Enforce concurrent limits without race conditions → Atomic Redis ops
- **C3**: Auto-expire without polling overhead → Redis TTL + scheduled cleanup
- **C4**: Admin monitoring without performance impact → Separate read path

### 1.2 Design Options Explored

#### Option A: Spring Session + Redis (Standard)
- **Pro**: Battle-tested, auto session clustering
- **Con**: Tied to HttpSession, doesn't fit JWT flow. Over-complex config.
- **Verdict**: ❌ Rejected — too much impedance mismatch with existing JWT-based auth

#### Option B: Custom Session Store (Redis Hash + PostgreSQL)
- **Pro**: Full control, fits JWT flow, hybrid storage (hot/cold)
- **Con**: More code to write, need to handle cleanup ourselves
- **Verdict**: ✅ Selected — best fit for existing architecture

#### Option C: Database-only Session Store
- **Pro**: Simple, single source
- **Con**: Latency on every request (session validation), no TTL
- **Verdict**: ❌ Rejected — performance unacceptable for per-request validation

## Selected Direction

### Architecture: Hybrid Redis + PostgreSQL
```
Redis (HOT) ← per-request validation, TTL, concurrent counting
PostgreSQL (COLD) ← session history, audit, analytics
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Session ID format | UUID v4 | Cryptographic randomness, no collision |
| Storage strategy | Redis primary + DB write-behind | Sub-5ms validation |
| Concurrent limit enforcement | Redis Lua script | Atomic check-and-create |
| Eviction policy | FIFO (oldest first) | Predictable, user-friendly |
| Cleanup mechanism | Scheduled (5 min) + Redis TTL | Belt and suspenders |
| Admin API pagination | Cursor-based for Redis, offset for DB | Different data stores |

### Data Flow Architecture
```
LOGIN → SessionService.createSession()
  ├─ Redis: EVAL lua_check_and_create.lua → atomic
  ├─ Redis: SADD user sessions set
  ├─ PostgreSQL: INSERT (async via @Async or event)
  └─ JWT: embed sessionId in claims

REQUEST → SessionValidationFilter
  ├─ Redis: HGETALL session:{uid}:{sid}
  ├─ Redis: EXPIRE refresh TTL
  └─ If missing → 401

CLEANUP → SessionCleanupScheduler
  ├─ Redis: SETNX distributed lock
  ├─ DB: SELECT where last_activity < threshold
  ├─ Redis: DEL expired sessions
  └─ DB: UPDATE status=EXPIRED
```

## 3. Trade-offs Analysis

### 3.1 Consistency vs Performance
- **Choice**: Eventual consistency between Redis and PostgreSQL
- **Trade-off**: Redis is truth for "is session active?", DB may lag 5-15s
- **Mitigation**: Session creation synchronous to both; expiry async to DB

### 3.2 Security vs Usability
- **Choice**: Default 30 min idle timeout, configurable per domain
- **Trade-off**: Short timeout = more secure but annoying UX
- **Mitigation**: "Remember me" option extends to 7 days; activity tracking refreshes TTL

### 3.3 Admin Visibility vs Privacy
- **Choice**: Admin can see all sessions but NOT session content
- **Trade-off**: Full visibility helps security but may concern privacy
- **Mitigation**: Only metadata (device, IP, timestamps) visible; audit all admin views

## 4. Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|------------|------------|
| Redis cluster failover → brief session loss | High | Low | PostgreSQL fallback mode |
| Lua script performance at scale | Medium | Low | Simple script, sub-1ms |
| Scheduler thundering herd | Low | Medium | Distributed lock, jitter |
| Token-session desync after revoke | Medium | Low | Short-lived access tokens (15 min) |

## 5. Implementation Sequence

1. Entity + Repository (LoginSessionEntity, LoginSessionRepository)
2. Redis Session Store (RedisSessionStore)
3. SessionService (core logic)
4. SessionValidationFilter (per-request check)
5. SessionCleanupScheduler (background cleanup)
6. AdminSessionController (admin APIs)
7. Integration tests

## 6. Open Questions Resolved

| Question | Decision |
|----------|----------|
| Should we use Spring Session? | No — too coupled to HttpSession |
| Redis data structure: Hash vs String? | Hash — structured access to fields |
| Eviction: oldest first or notify user? | Oldest first (FIFO) — simpler UX |
| Cleanup frequency? | 5 min — balance between freshness and load |
| Admin can revoke own session? | No — prevent accidental lockout |