---
name: deployment-pipeline
description: "Deploy generated application to local (Docker Compose), staging, or production environments. Handles Docker build, database migration, health checks, and rollback. Use after code generation + tests pass."
---

# Deployment Pipeline

## Purpose

Deploy generated application from code → running service. Supports **local Docker Compose** (dev), **staging**, and **production** environments with automated health checks and rollback capability.

---

## When to Use

- After `code-generator` + `auto-test-generator` complete with passing tests
- After `/wf_openspec_apply` + `/wf_integ_test` complete
- When user requests "deploy" or "run" the generated app

## When NOT to Use

- Code doesn't compile → fix first
- Tests failing → fix first
- No Docker installed → provide manual instructions

---

## Deployment Targets

| Target | Method | Use For |
|:---|:---|:---|
| 🏠 Local | Docker Compose | Development, demo, testing |
| 🏗️ Staging | Docker + cloud VM | Pre-production validation |
| 🚀 Production | K8s / Cloud Run | Live deployment |

---

## Process

### Phase 1: Pre-Deploy Checks

```bash
# 1. Verify code compiles
./gradlew build -x test  # or mvn package -DskipTests

# 2. Verify tests pass
./gradlew test

# 3. Check Docker is available
docker --version
docker compose version

# 4. Check port availability
lsof -i :8080  # backend
lsof -i :5432  # postgres
lsof -i :6379  # redis
```

If any check fails → STOP with clear error message.

### Phase 2: Generate Docker Files

**Dockerfile** (backend):
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/{project}-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml**:
```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/{project}
      - SPRING_REDIS_HOST=redis
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 5

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: {project}
      POSTGRES_USER: {project}
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U {project}"]
      interval: 10s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

volumes:
  pgdata:
```

### Phase 3: Build & Deploy (Local)

```bash
# Build application
./gradlew build -x test

# Build and start containers
docker compose up -d --build

# Wait for health checks
echo "Waiting for services..."
sleep 10

# Verify health
curl -s http://localhost:8080/actuator/health | jq .
```

### Phase 4: Post-Deploy Validation

```bash
# 1. Health check
curl -s http://localhost:8080/actuator/health

# 2. Smoke test — hit a basic endpoint
curl -s http://localhost:8080/api/v1/health

# 3. Database migration check
curl -s http://localhost:8080/actuator/flyway

# 4. List running containers
docker compose ps
```

### Phase 5: Reporting

Generate `deployment-report.md`:

```markdown
# Deployment Report — {project-name}

| Field | Value |
|:---|:---|
| Date | YYYY-MM-DD HH:MM |
| Target | Local (Docker Compose) |
| Backend | http://localhost:8080 |
| Database | PostgreSQL 16 @ localhost:5432 |
| Cache | Redis 7 @ localhost:6379 |
| Status | ✅ Running |

## Services
| Service | Status | Port | Health |
|:---|:---:|:---:|:---:|
| app | ✅ Up | 8080 | healthy |
| db | ✅ Up | 5432 | healthy |
| redis | ✅ Up | 6379 | healthy |

## Access
- API: http://localhost:8080/api/v1/
- Actuator: http://localhost:8080/actuator/
- Swagger: http://localhost:8080/swagger-ui.html

## Useful Commands
- Logs: `docker compose logs -f app`
- Stop: `docker compose down`
- Reset: `docker compose down -v && docker compose up -d --build`
```

---

## Rollback

```bash
# Stop current deployment
docker compose down

# Revert to previous commit
git revert HEAD --no-edit

# Rebuild and redeploy
./gradlew build -x test
docker compose up -d --build
```

---

## Guardrails

- **DO** run pre-deploy checks before any deployment
- **DO** wait for health checks before declaring success
- **DO** generate deployment report
- **DO** include rollback instructions
- **DO NOT** deploy with failing tests
- **DO NOT** use production credentials in docker-compose (use .env)
- **DO NOT** skip database migration verification
- **DO NOT** expose debug ports in staging/production

## Limitations
- Local deployment only requires Docker. Cloud deployments need additional configuration.
- Stop and ask for clarification if infrastructure requirements are unclear.
