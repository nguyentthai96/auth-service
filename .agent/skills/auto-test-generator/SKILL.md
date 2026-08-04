---
name: auto-test-generator
description: "Auto-generate unit tests, integration tests, and architecture tests from tech design + generated code. Produces JUnit 5 unit tests, Spring Boot integration tests, API contract tests, and ArchUnit architecture rules. Use after code-generator completes or alongside /wf_openspec_apply."
---

# Auto Test Generator

## Purpose

Automatically generate **comprehensive test suites** from Technical Design documents and generated source code — unit tests, integration tests, API contract tests, and architecture validation tests.

---

## When to Use

- After `code-generator` creates initial project code
- After `/wf_openspec_apply` implements a feature
- When test coverage < 80% (gap analysis mode)
- Before `/wf_integ_test` to create automated test classes

## When NOT to Use

- No source code exists → run code-generator first
- Manual exploratory testing → use `/wf_integ_test`

---

## Input

```
Tech Design docs:
├── 03-domain-model.md      → Entity unit tests
├── 04-database-design.md   → Repository tests
├── 05-api-design.md        → Controller + contract tests
├── 06-main-workflows.md    → Service integration tests
├── 07-state-machines.md    → State transition tests
├── 08-validation-rules.md  → Validation tests
├── 09-error-handling.md    → Exception tests

Source code (generated):
├── entity/*.java           → What to test
├── service/*.java          → Business logic tests
├── controller/*.java       → API tests
└── repository/*.java       → Data tests
```

---

## Output

```
src/test/java/com/{org}/{project}/
├── unit/
│   ├── {module}/
│   │   ├── entity/{Entity}Test.java           ← Entity invariants
│   │   ├── service/{Entity}ServiceTest.java   ← Business logic (mocked)
│   │   ├── enums/{Entity}StatusTest.java      ← State transitions
│   │   └── dto/{Dto}ValidationTest.java       ← Validation rules
│   └── common/
│       └── exception/ExceptionMappingTest.java
├── integration/
│   ├── {module}/
│   │   ├── {Entity}RepositoryIT.java          ← DB (Testcontainers)
│   │   ├── {Entity}ControllerIT.java          ← Full API flow
│   │   └── {Entity}WorkflowIT.java            ← Multi-step workflow
│   └── config/
│       └── TestContainerConfig.java
├── contract/
│   ├── {Module}ApiContractTest.java           ← Request/Response schemas
│   └── ErrorResponseContractTest.java
├── architecture/
│   └── ArchitectureRulesTest.java             ← ArchUnit rules
└── test-report.md                              ← Coverage summary
```

---

## Process (5 Phases)

### Phase 1: Analyze Source & Design

1. Scan generated source code → build class inventory
2. Read tech design docs → extract testable specifications
3. Map: each class → test categories needed

### Phase 2: Unit Tests

Per entity/service:

**Entity Tests** (from domain-model):
```java
@Test void shouldCreate{Entity}WithValidData()
@Test void shouldRejectInvalid{Field}()     // per validation rule
@Test void shouldEnforce{Invariant}()       // per aggregate invariant
```

**Service Tests** (from main-workflows):
```java
@ExtendWith(MockitoExtension.class)
class {Entity}ServiceTest {
    @Mock {Entity}Repository repository;
    @InjectMocks {Entity}ServiceImpl service;

    @Test void shouldCreate{Entity}Successfully()
    @Test void shouldThrowWhen{Entity}NotFound()
    @Test void shouldEnforce{BusinessRule}()  // per BR-NNN
}
```

**State Machine Tests** (from state-machines):
```java
@Test void shouldTransitionFrom{State1}To{State2}()
@Test void shouldRejectTransitionFrom{State1}To{InvalidState}()
@Test void shouldFireEventOn{Transition}()
// Test EVERY transition in the state diagram
```

**Validation Tests** (from validation-rules):
```java
@Test void shouldRejectBlank{Field}()
@Test void shouldRejectInvalidFormat{Field}()
@Test void shouldAcceptValid{Field}()
// One test per VR-NNN
```

### Phase 3: Integration Tests

**Repository Tests** (with Testcontainers):
```java
@DataJpaTest
@Testcontainers
class {Entity}RepositoryIT {
    @Container static PostgreSQLContainer<?> pg = ...;

    @Test void shouldPersistAndRetrieve{Entity}()
    @Test void shouldFindBy{CustomQuery}()
    @Test void shouldEnforceUniqueConstraint()
}
```

**Controller Tests** (full API flow):
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class {Entity}ControllerIT {
    @Autowired TestRestTemplate restTemplate;

    @Test void shouldCreateViaApi()         // POST → 201
    @Test void shouldReturn404ForMissing()  // GET → 404
    @Test void shouldReturn422ForInvalid()  // POST → 422
    @Test void shouldEnforceAuth()          // No token → 401
}
```

**Workflow Tests** (multi-step from sequence diagrams):
```java
@Test void shouldCompleteFullWorkflow() {
    // Step 1: Create entity
    // Step 2: Update status
    // Step 3: Verify final state
    // Matches sequence diagram from 06-main-workflows
}
```

### Phase 4: Architecture Tests (ArchUnit)

```java
@AnalyzeClasses(packages = "com.{org}.{project}")
class ArchitectureRulesTest {

    @ArchTest ArchRule controllersShouldNotAccessRepositories =
        noClasses().that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..repository..");

    @ArchTest ArchRule servicesShouldNotAccessControllers =
        noClasses().that().resideInAPackage("..service..")
            .should().accessClassesThat().resideInAPackage("..controller..");

    @ArchTest ArchRule entitiesShouldNotDependOnServices =
        noClasses().that().resideInAPackage("..entity..")
            .should().accessClassesThat().resideInAPackage("..service..");

    @ArchTest ArchRule layeredArchitecture = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Controller").definedBy("..controller..")
        .layer("Service").definedBy("..service..")
        .layer("Repository").definedBy("..repository..")
        .whereLayer("Controller").mayOnlyAccessLayers("Service")
        .whereLayer("Service").mayOnlyAccessLayers("Repository");
}
```

### Phase 5: Report & Commit

1. Run all generated tests: `./gradlew test`
2. Generate coverage report
3. Create `test-report.md`:

```markdown
## Auto-Generated Test Report

| Category | Tests | Pass | Fail | Coverage |
|:---|:---:|:---:|:---:|:---:|
| Unit | N | N | 0 | 85% |
| Integration | M | M | 0 | 70% |
| Contract | K | K | 0 | 100% |
| Architecture | L | L | 0 | N/A |
| **Total** | **T** | **T** | **0** | **80%+** |
```

4. Auto-commit:
```bash
git add src/test/
git commit -m "test({project-name}): auto-generated test suite

Unit: {N} tests (entity, service, state, validation)
Integration: {M} tests (repository, controller, workflow)
Contract: {K} tests (API schema validation)
Architecture: {L} rules (layer dependencies)
Coverage: {X}%"
```

---

## Test Naming Convention

```
should{ExpectedBehavior}When{Condition}
```

Examples:
- `shouldCreateRoomSuccessfully`
- `shouldThrowNotFoundWhenRoomDoesNotExist`
- `shouldRejectBlankRoomName`
- `shouldTransitionFromDraftToPending`

---

## Guardrails

- **DO** test every state transition in state machine
- **DO** test every validation rule (VR-NNN)
- **DO** test every API endpoint (happy + error paths)
- **DO** use `@MockitoBean` (not deprecated `@MockBean`)
- **DO** use Testcontainers for DB tests (not H2)
- **DO NOT** test framework code (Spring itself)
- **DO NOT** use `@Autowired` field injection in tests
- **DO NOT** skip error path testing
- Target: **> 80% coverage**

## Limitations
- Generated tests are a starting point — complex business logic tests may need manual refinement.
- Stop and ask for clarification if tech design has ambiguities affecting test expectations.
