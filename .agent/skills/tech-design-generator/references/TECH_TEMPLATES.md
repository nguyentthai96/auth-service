# Tech Design Document Templates

> Reference file for `tech-design-generator` skill. Contains all 10 technical document templates.

## Table of Contents

1. [00-system-overview](#00-system-overview)
2. [01-roles-permissions](#01-roles-permissions)
3. [02-module-breakdown](#02-module-breakdown)
4. [03-domain-model](#03-domain-model)
5. [04-database-design](#04-database-design)
6. [05-api-design](#05-api-design)
7. [06-main-workflows](#06-main-workflows)
8. [07-state-machines](#07-state-machines)
9. [08-validation-rules](#08-validation-rules)
10. [09-error-handling](#09-error-handling)

---

## 00-system-overview

```markdown
---
type: tech-design
document: 00-system-overview
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
architecture: {chosen-pattern}
tech-stack: {backend}/{database}/{mobile}
---

# System Overview

## 1. Architecture Decision
- **Pattern**: {from architecture-debate.md}
- **Reasoning**: {summary of debate conclusion}
- **Trade-offs accepted**: {list}

## 2. C4 Diagrams

### Level 1: System Context
\```mermaid
graph TB
    U[/"👤 User"/] -->|uses| S["🏢 {System Name}"]
    S -->|sends| E["📧 Email Service"]
    S -->|reads/writes| DB[("🗄️ Database")]
    S -->|pushes| N["📱 Notification Service"]
\```

### Level 2: Container Diagram
\```mermaid
graph TB
    subgraph "System Boundary"
        WEB["🌐 Web App\n(React)"]
        MOB["📱 Mobile App\n(Flutter)"]
        API["⚙️ API Server\n(Spring Boot)"]
        DB[("🗄️ PostgreSQL")]
        CACHE[("⚡ Redis")]
        QUEUE["📬 Message Queue"]
    end
    WEB --> API
    MOB --> API
    API --> DB
    API --> CACHE
    API --> QUEUE
\```

### Level 3: Component Diagram (per container)
\```mermaid
graph TB
    subgraph "API Server"
        CTRL["Controllers"]
        SVC["Services"]
        REPO["Repositories"]
        SEC["Security Filter"]
    end
    CTRL --> SVC
    SVC --> REPO
    SEC --> CTRL
\```

## 3. Technology Stack
| Layer | Technology | Version | Justification |
|:---|:---|:---|:---|
| Backend | {tech} | {ver} | {from technology-analysis.md} |
| Database | {tech} | {ver} | {reason} |
| Cache | {tech} | {ver} | {reason} |
| Mobile | {tech} | {ver} | {reason} |
| Queue | {tech} | {ver} | {reason} |

## 4. Infrastructure Overview
- **Deployment**: {local/cloud/hybrid}
- **CI/CD**: {pipeline}
- **Monitoring**: {tools}

## 5. Cross-Cutting Concerns
- Authentication: {method}
- Authorization: → doc 01
- Logging: {structured JSON}
- Error handling: → doc 09
```

---

## 01-roles-permissions

```markdown
---
type: tech-design
document: 01-roles-permissions
project: {project-name}
---

# Roles & Permissions

## 1. Role Hierarchy
\```mermaid
graph TB
    ADMIN["🔑 Admin"] --> MANAGER["👔 Manager"]
    MANAGER --> USER["👤 User"]
    USER --> GUEST["🔓 Guest"]
\```

## 2. RBAC Matrix
| Resource | Action | Admin | Manager | User | Guest |
|:---|:---|:---:|:---:|:---:|:---:|
| /api/users | GET | ✅ | ✅ | 🔒 own | ❌ |
| /api/users | POST | ✅ | ✅ | ❌ | ❌ |
| /api/users | DELETE | ✅ | ❌ | ❌ | ❌ |

## 3. Permission Model
| Permission | Code | Description |
|:---|:---|:---|
| USER_READ | `user:read` | View user profiles |
| USER_WRITE | `user:write` | Create/update users |

## 4. Data-Level Security
- Row-level filtering: {which entities}
- Field-level masking: {sensitive fields}
```

---

## 02-module-breakdown

```markdown
---
type: tech-design
document: 02-module-breakdown
project: {project-name}
---

# Module Breakdown

## 1. Module Graph
\```mermaid
graph LR
    AUTH["🔐 Auth Module"] --> USER["👤 User Module"]
    USER --> NOTIFY["📬 Notification Module"]
    CORE["💼 Core Module"] --> USER
    CORE --> PAYMENT["💳 Payment Module"]
    PAYMENT --> NOTIFY
\```

## 2. Module Catalog
### Module: {name}
- **Package**: `com.{org}.{project}.{module}`
- **Responsibility**: {single responsibility}
- **Depends on**: {list of modules}
- **Exposes**: {public interfaces}
- **FR coverage**: FR-001, FR-003, FR-007

## 3. Dependency Matrix
| Module | Auth | User | Core | Payment | Notify |
|:---|:---:|:---:|:---:|:---:|:---:|
| Auth | — | → | | | |
| User | | — | | | → |
| Core | | → | — | → | |
| Payment | | | | — | → |

## 4. Module Size Estimation
| Module | Entities | APIs | Complexity |
|:---|:---:|:---:|:---|
| Auth | 2 | 5 | Medium |
```

---

## 03-domain-model

```markdown
---
type: tech-design
document: 03-domain-model
project: {project-name}
---

# Domain Model (DDD)

## 1. Bounded Contexts
\```mermaid
graph TB
    subgraph "Core Context"
        AGG1["📦 {Aggregate 1}"]
        AGG2["📦 {Aggregate 2}"]
    end
    subgraph "Supporting Context"
        AGG3["📦 {Aggregate 3}"]
    end
    AGG1 -.->|"Domain Event"| AGG3
\```

## 2. Aggregate Definitions

### Aggregate: {Name} (Root: {EntityName})
- **Invariants**: {business rules that must always hold}
- **Entities**:
  | Entity | Type | Key Attributes |
  |:---|:---|:---|
  | {Name} | Root | id, name, status |
  | {Child} | Entity | id, parentId, value |
- **Value Objects**:
  | VO | Fields | Validation |
  |:---|:---|:---|
  | Address | street, city, zip | zip: 5-6 digits |
  | Money | amount, currency | amount >= 0 |
- **Domain Events**:
  | Event | Trigger | Payload |
  |:---|:---|:---|
  | {Name}Created | create() | {id, timestamp} |

## 3. Context Mapping
| Upstream | Downstream | Pattern |
|:---|:---|:---|
| Core | Notification | Published Language (Events) |
| Auth | Core | Conformist |
```

---

## 04-database-design

```markdown
---
type: tech-design
document: 04-database-design
project: {project-name}
---

# Database Design

## 1. ERD
\```mermaid
erDiagram
    USER ||--o{ ROOM : manages
    ROOM ||--o{ CONTRACT : has
    CONTRACT ||--o{ PAYMENT : generates
    USER {
        uuid id PK
        varchar name
        varchar email UK
        timestamp created_at
    }
    ROOM {
        uuid id PK
        uuid user_id FK
        varchar name
        decimal price
    }
\```

## 2. Table Specifications

### Table: {table_name}
| Column | Type | Nullable | Default | Constraint | Description |
|:---|:---|:---:|:---|:---|:---|
| id | UUID | NO | gen_random_uuid() | PK | Primary key |
| created_at | TIMESTAMP | NO | now() | | Creation time (UTC) |

### Indexes
| Name | Columns | Type | Purpose |
|:---|:---|:---|:---|
| idx_{table}_{col} | col | B-Tree | WHERE filter |
| idx_{table}_{col1}_{col2} | col1, col2 | Composite | JOIN + filter |

## 3. Migration Strategy
- Tool: Flyway / Liquibase
- Naming: `V{NNN}__{description}.sql`
- Review: DDL changes require architect approval

## 4. Data Seeding
- Master data: {list}
- Test data: {approach}
```

---

## 05-api-design

```markdown
---
type: tech-design
document: 05-api-design
project: {project-name}
---

# API Design

## 1. API Standards
- Style: RESTful
- Versioning: URI path (`/api/v1/`)
- Auth: Bearer JWT
- Response: RFC 7807 ProblemDetail for errors

## 2. Endpoint Catalog

### {Module}: {Resource}

#### `POST /api/v1/{resources}`
- **FR**: FR-001
- **Auth**: `{permission}`
- **Request**:
  ```json
  {
    "name": "string (required, max 100)",
    "email": "string (required, email format)"
  }
  ```
- **Response 201**:
  ```json
  {
    "code": "00",
    "message": "success",
    "data": { "id": "uuid", "name": "string" }
  }
  ```
- **Error Responses**: → doc 09

## 3. Pagination Standard
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "size": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

## 4. API Summary Matrix
| Method | Path | Auth | FR | Module |
|:---|:---|:---|:---|:---|
| POST | /api/v1/users | Public | FR-001 | Auth |
| GET | /api/v1/rooms | user:read | FR-005 | Room |
```

---

## 06-main-workflows

```markdown
---
type: tech-design
document: 06-main-workflows
project: {project-name}
---

# Main Workflows (Sequence Diagrams)

## WF-01: {Workflow Name}

### Happy Path
\```mermaid
sequenceDiagram
    actor U as User
    participant C as Client
    participant A as API Server
    participant DB as Database
    participant Q as Queue

    U->>C: Action
    C->>A: POST /api/v1/resource
    A->>A: Validate input
    A->>DB: INSERT resource
    DB-->>A: OK
    A->>Q: Publish event
    A-->>C: 201 Created
    C-->>U: Success feedback
\```

### Error Path
\```mermaid
sequenceDiagram
    actor U as User
    participant C as Client
    participant A as API Server

    U->>C: Action
    C->>A: POST /api/v1/resource
    A->>A: Validate input
    A-->>C: 422 Validation Error
    C-->>U: Show error message
\```

## WF-02: ...
```

---

## 07-state-machines

```markdown
---
type: tech-design
document: 07-state-machines
project: {project-name}
---

# State Machines (Implementation)

## SM-01: {Entity} Lifecycle

### Implementation Diagram
\```mermaid
stateDiagram-v2
    [*] --> DRAFT: create()
    DRAFT --> PENDING: submit()
    note right of PENDING: Notify admin
    PENDING --> ACTIVE: approve(adminId)
    PENDING --> REJECTED: reject(reason)
    REJECTED --> DRAFT: revise()
    ACTIVE --> SUSPENDED: suspend(reason)
    SUSPENDED --> ACTIVE: resume()
    ACTIVE --> TERMINATED: terminate()
    TERMINATED --> [*]
\```

### Transition Table
| From | To | Method | Guard | Side Effects | Rollback |
|:---|:---|:---|:---|:---|:---|
| DRAFT | PENDING | submit() | allFieldsValid() | NotifyAdmin | Delete notification |

### Implementation Notes
- State stored in: `{table}.status` column
- Enum class: `{Entity}Status`
- Event published on transition: `{Entity}StatusChanged`
```

---

## 08-validation-rules

```markdown
---
type: tech-design
document: 08-validation-rules
project: {project-name}
---

# Validation Rules

## 1. Input Validation Catalog

### VR-001: {Rule Name}
- **Applies to**: API `POST /api/v1/{resource}`
- **Field**: `{fieldName}`
- **Type**: Required | Format | Range | Business
- **Rule**: {precise validation rule}
- **Error code**: `ERR_{MODULE}_{CODE}`
- **BR reference**: BR-001

## 2. Validation Summary Matrix
| API | Field | Type | Rule | Error Code |
|:---|:---|:---|:---|:---|
| POST /users | email | Format | RFC 5322 | ERR_AUTH_001 |
| POST /users | name | Required | Not blank, max 100 | ERR_AUTH_002 |

## 3. Cross-Field Validation
| Rule | Fields | Condition | Error |
|:---|:---|:---|:---|
| Date range | startDate, endDate | start < end | ERR_CORE_010 |

## 4. Implementation
- Layer: Controller (Bean Validation) + Service (business rules)
- Framework: Jakarta Validation annotations
- Custom validators for business rules
```

---

## 09-error-handling

```markdown
---
type: tech-design
document: 09-error-handling
project: {project-name}
---

# Error Handling

## 1. Error Code System
Format: `ERR_{MODULE}_{NNN}`

| Code | Module | HTTP | Message | Description |
|:---|:---|:---:|:---|:---|
| ERR_AUTH_001 | Auth | 401 | Invalid credentials | Wrong email/password |
| ERR_AUTH_002 | Auth | 403 | Insufficient permission | Missing required role |
| ERR_CORE_001 | Core | 404 | Resource not found | Entity doesn't exist |
| ERR_CORE_002 | Core | 409 | Conflict | Duplicate or state conflict |
| ERR_SYS_001 | System | 500 | Internal error | Unexpected server error |

## 2. Exception Hierarchy
\```mermaid
classDiagram
    RuntimeException <|-- BaseException
    BaseException <|-- BusinessException
    BaseException <|-- TechnicalException
    BusinessException <|-- NotFoundException
    BusinessException <|-- ValidationException
    BusinessException <|-- ConflictException
    TechnicalException <|-- ExternalServiceException
    TechnicalException <|-- DatabaseException
\```

## 3. Error Response Format (RFC 7807)
```json
{
  "type": "https://api.{domain}/errors/ERR_AUTH_001",
  "title": "Invalid credentials",
  "status": 401,
  "detail": "Email or password is incorrect",
  "instance": "/api/v1/auth/login",
  "errorCode": "ERR_AUTH_001",
  "timestamp": "2026-07-02T10:00:00Z"
}
```

## 4. Global Exception Handler
- `@ControllerAdvice` + `@ExceptionHandler`
- Map exception class → HTTP status + error code
- Log with correlation ID (MDC)
- Never expose stack traces to client
```
