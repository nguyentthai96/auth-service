---
name: code-generator
description: "Generate production-ready source code from Technical Design + UI/UX documents. Maps tech docs to project scaffolding, entity classes, repositories, services, controllers, DTOs, and frontend components. Integrates with OpenSpec apply for incremental code generation. Use after /wf_tech_design and /wf_ui_design complete."
---

# Code Generator

## Purpose

Transform **Technical Design + UI/UX documents** into **production-ready source code** — project scaffolding, backend layers (entity → repo → service → controller), frontend components, and database migrations.

---

## When to Use

- After `/wf_tech_design` and `/wf_ui_design` complete
- As the bridge between design phase and OpenSpec apply
- When generating a new project from scratch

## When NOT to Use

- Modifying existing features → use `/wf_openspec_apply` directly
- Bug fixes → use debugging workflow
- No tech design exists → run `/wf_tech_design` first

---

## Input

```
project_docs/{project-name}/
├── technical/
│   ├── 00-system-overview.md    → Project scaffolding
│   ├── 01-roles-permissions.md  → Security config
│   ├── 02-module-breakdown.md   → Package structure
│   ├── 03-domain-model.md       → Entity classes
│   ├── 04-database-design.md    → Migrations, DDL
│   ├── 05-api-design.md         → Controllers, DTOs
│   ├── 06-main-workflows.md     → Service methods
│   ├── 07-state-machines.md     → State enums, transitions
│   ├── 08-validation-rules.md   → Validation annotations
│   └── 09-error-handling.md     → Exception classes
├── ui-ux/
│   ├── 03-design-system-basics.md → CSS/theme
│   ├── 04-design-tokens.md        → Token files
│   ├── 05-common-ui-components.md → Component code
│   └── 06-app-layout-components.md → Layout code
└── decision_log.md
```

---

## Output: Generated Project Structure

### Backend (Spring Boot example)

```
{project-name}/
├── build.gradle / pom.xml              ← from system-overview
├── src/main/java/com/{org}/{project}/
│   ├── config/
│   │   ├── SecurityConfig.java         ← from roles-permissions
│   │   ├── CacheConfig.java            ← from system-overview
│   │   └── WebConfig.java
│   ├── common/
│   │   ├── exception/
│   │   │   ├── BaseException.java      ← from error-handling
│   │   │   ├── BusinessException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── dto/
│   │   │   └── BaseResponse.java
│   │   └── validation/
│   │       └── CustomValidators.java   ← from validation-rules
│   ├── {module}/                        ← per module from module-breakdown
│   │   ├── entity/
│   │   │   └── {Entity}.java           ← from domain-model
│   │   ├── repository/
│   │   │   └── {Entity}Repository.java
│   │   ├── service/
│   │   │   ├── {Entity}Service.java    ← from main-workflows
│   │   │   └── impl/
│   │   │       └── {Entity}ServiceImpl.java
│   │   ├── controller/
│   │   │   └── {Entity}Controller.java ← from api-design
│   │   ├── dto/
│   │   │   ├── {Entity}CreateRequest.java
│   │   │   └── {Entity}Response.java   ← from api-design
│   │   └── enums/
│   │       └── {Entity}Status.java     ← from state-machines
│   └── ...
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/
│       └── V001__{description}.sql     ← from database-design
└── src/test/java/
    └── ... (see auto-test-generator)
```

### Frontend (React example)

```
{project-name}-web/
├── package.json
├── src/
│   ├── styles/
│   │   ├── tokens.css                  ← from design-tokens
│   │   ├── globals.css                 ← from design-system
│   │   └── components.css
│   ├── components/
│   │   ├── ui/                         ← from common-ui-components
│   │   │   ├── Button.jsx
│   │   │   ├── Input.jsx
│   │   │   ├── Card.jsx
│   │   │   └── ...
│   │   └── layout/                     ← from app-layout-components
│   │       ├── Shell.jsx
│   │       ├── Sidebar.jsx
│   │       └── TopBar.jsx
│   ├── pages/                          ← from main-workflows
│   │   ├── LoginPage.jsx
│   │   ├── DashboardPage.jsx
│   │   └── ...
│   ├── services/                       ← from api-design
│   │   └── api.js
│   └── App.jsx
└── ...
```

---

## Process (5 Phases)

### Phase 1: Analyze & Plan

1. Read all tech + UI/UX docs
2. Map docs → code files (table above)
3. Determine generation order (dependencies first):
   ```
   common/exception → entity → repository → service → dto → controller
   tokens.css → components → layout → pages
   ```
4. Load cost profile: `agent-config/profiles/wf_code_gen.yaml`

### Phase 2: Backend Scaffolding

1. **Project init**: `build.gradle` / `pom.xml` with dependencies from system-overview
2. **Config classes**: Security, Cache, Web config
3. **Common layer**: Exception hierarchy, BaseResponse, validators
4. **Database migrations**: DDL from database-design doc

### Phase 3: Backend Business Logic

Per module (from module-breakdown):
1. **Entities**: Map DDD aggregates → JPA entities
2. **Repositories**: Interface per entity + custom queries
3. **Services**: Implement workflows from sequence diagrams
4. **State machines**: Enum + transition validation methods
5. **DTOs**: Request/Response per API endpoint
6. **Controllers**: REST endpoints with validation + auth

### Phase 4: Frontend Code

1. **Design tokens → CSS**: Generate `tokens.css` from design-tokens doc
2. **Components**: Map component catalog → React/Vue components
3. **Layouts**: Shell, navigation, page templates
4. **Pages**: One page per key workflow screen
5. **API service**: HTTP client matching api-design endpoints

### Phase 5: Integration & Commit

1. Verify: all entities referenced in API have controllers
2. Verify: all migrations match entity definitions
3. Verify: all API endpoints have corresponding frontend calls
4. Generate `README.md` with setup instructions
5. Auto-commit:
   ```bash
   git add {project-name}/
   git commit -m "feat({project-name}): initial code generation from tech design

   Backend: {N} modules, {M} entities, {K} endpoints
   Frontend: {L} components, {P} pages
   Migrations: {Q} DDL scripts"
   ```

---

## Tech Stack Mapping

| Design Doc Says | Backend Code | Frontend Code |
|:---|:---|:---|
| Spring Boot | Gradle + Spring Boot 3 | — |
| PostgreSQL | Flyway migrations | — |
| Redis | Spring Data Redis | — |
| React | — | Vite + React |
| Flutter | — | Flutter project |
| REST API | @RestController | fetch/axios client |
| JWT Auth | Spring Security + JWT | Auth context + interceptor |

---

## Guardrails

- **DO** generate code that compiles (verify with build)
- **DO** follow project's naming conventions from tech design
- **DO** include TODO comments for complex business logic
- **DO** reference FR/BR IDs in code comments
- **DO NOT** generate test code here → use `auto-test-generator`
- **DO NOT** deviate from tech design decisions
- **DO NOT** add dependencies not in system-overview
- **DO NOT** hardcode config values → use `application.yml`

## Limitations
- Generated code is a starting point — requires human review for complex business logic.
- Stop and ask for clarification if tech design has ambiguities.
