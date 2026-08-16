# Input Strategy Guide — hermes-pipeline

## Mục đích

Hướng dẫn agent truyền input phù hợp vào pipeline workflows.
Có **2 approaches** (ưu tiên Approach A):

### Approach A: `ingest` Command (RECOMMENDED — v1.2+)

```bash
# Tự động detect, enrich, lưu history, rồi chạy pipeline
./hermes-pipeline ingest "Event Sourcing pattern" --run

# Hoặc ingest trước, run sau
./hermes-pipeline ingest "https://example.com/article"
./hermes-pipeline ingest --use <id> --run
```

`ingest` sẽ auto-detect loại input (URL/file/text/idea), enrich qua LLM brainstorm,
lưu vào `.hermes/prompt_history/`, và inject `suggested_features` + `enriched_content`
vào pipeline config khi dùng `--run`.

### Approach B: Manual Step Config (Legacy)

Populate `.hermes/steps/<workflow>.yml` trước khi chạy `./hermes-pipeline start`.
Xem Section 3 để biết template cho mỗi workflow.

---

## 1. Workflow Selection Decision Tree

```
User request
│
├── Ý tưởng mới / mô tả tự do / idea prompt
│   └── ingest → wf_feature_research → wf_brainstorm_openspec → wf_openspec → wf_openspec_apply
│
├── Có URD/spec cụ thể (file .md / requirements)
│   └── wf_pre_openspec → wf_openspec → wf_openspec_apply
│
├── Đã có research output (openspec/research/<feature>/)
│   └── wf_brainstorm_openspec → wf_openspec → wf_openspec_apply
│       (hoặc skip brainstorm nếu research đã đủ rõ)
│
├── Đã có spec files (openspec/changes/<feature>/)
│   └── wf_openspec_apply
│
├── Quick input + run (URL/text/file)
│   └── ingest --run (auto-detect + enrich + start pipeline)
│
└── Không rõ → Hỏi user clarification
```

### Quick Decision Matrix

| Tình huống | Input có gì? | Workflow chain |
|------------|-------------|----------------|
| Ý tưởng mới, chưa research | Idea prompt / description | `ingest → feature_research → brainstorm → openspec → apply` |
| Ý tưởng + đã có research | Research folder | `brainstorm → openspec → apply` |
| URD / Requirements rõ ràng | URD file path | `pre_openspec → openspec → apply` |
| Spec đã xong, cần implement | Spec files | `openspec_apply` |
| Muốn research trước, chưa build | Idea prompt | `ingest → feature_research` (only) |
| Quick input + immediate run | URL/text/file | `ingest --run` |

---

## 2. Context Detection Checklist

Trước khi chạy pipeline, agent PHẢI thực hiện các bước sau:

### 2.1 Scan existing outputs

```bash
# Check existing research outputs
ls openspec/research/ 2>/dev/null

# Check existing change specs
ls openspec/changes/ 2>/dev/null

# Check pipeline progress
cat openspec/pipeline_progress.yml 2>/dev/null
```

### 2.2 Parse user message

Extract từ user request:
- **Feature name**: tên tính năng (e.g., "hermes-task-dashboard")
- **Idea/description**: mô tả ý tưởng ban đầu
- **Technology scope**: công nghệ liên quan (e.g., "React, Spring Boot")
- **Research goals**: mục tiêu research cụ thể
- **Existing artifacts**: file/folder user đã mention (@[path])

### 2.3 Map to input strategy

Dựa trên kết quả scan + parse:

| Detected | Input Strategy |
|----------|---------------|
| User mention idea/ý tưởng | → Populate `seed_prompt` trong step config |
| User mention @[folder] chứa research | → Set `research_dir` input |
| User mention URD/spec file | → Set `urd_source` input |
| Existing `openspec/research/<feature>` | → Inject research path vào `prior_context` |
| Existing `openspec/changes/<feature>` | → Inject spec path vào `prior_artifacts` |

---

## 3. Input Templates per Workflow

### 3.1 `wf_feature_research` — Research & Explore

**Khi nào**: User có ý tưởng ban đầu, cần research technologies/approaches.

**Input fields (`.hermes/steps/wf_feature_research.yml`)**:

```yaml
input:
  # REQUIRED: Mô tả ý tưởng/vấn đề cần research
  seed_prompt: |
    Research about implementing a task queue dashboard with real-time
    monitoring for Spring Boot microservices. Need to evaluate:
    - Queue technologies (Redis Streams vs RabbitMQ vs Kafka)
    - Dashboard frameworks (React Admin vs Grafana)
    - WebSocket vs SSE for real-time updates

  # REQUIRED: Feature name — dùng làm tên folder output
  feature_name: "hermes-task-dashboard"

  # OPTIONAL: Phạm vi công nghệ cần focus
  technology_scope: "Spring Boot, React, Redis, WebSocket"

  # OPTIONAL: Mục tiêu cụ thể
  research_goals: |
    - So sánh ≥3 queue solutions với pros/cons
    - Benchmark performance cho 10K messages/sec
    - Recommend tech stack phù hợp cho team 3-5 devs

  # OPTIONAL: Keywords cho web search
  search_keywords:
    - "task queue dashboard spring boot"
    - "real-time monitoring microservices"
    - "redis streams vs rabbitmq comparison 2025"
```

### 3.2 `wf_pre_openspec` — Analyze URD & Scan Codebase

**Khi nào**: Có URD/requirements rõ ràng, cần phân tích trước khi generate spec.

**Input fields (`.hermes/steps/wf_pre_openspec.yml`)**:

```yaml
input:
  # REQUIRED: Source tài liệu yêu cầu
  urd_source: "openspec/changes/task-queue-app/urd.md"
  source_type: "urd"           # "urd" | "research" | "requirements"

  # REQUIRED: Thông tin feature
  feature_name: "task-queue-app"
  project_type: "spring-boot-modular"  # hoặc "nextjs-serverless", etc.
  workspace_path: "."

  # OPTIONAL: Research output để làm giàu context
  prior_research: "openspec/research/hermes-task-dashboard/"

  # OPTIONAL: Source code paths cần scan
  scan_paths:
    - "src/main/java/com/example/taskqueue/"
    - "src/main/resources/application.yml"
```

### 3.3 `wf_brainstorm_openspec` — Brainstorm & Deep Thinking

**Khi nào**: Ý tưởng cần khám phá sâu, hoặc sau research cần evaluate approaches.

**Input fields (`.hermes/steps/wf_brainstorm_openspec.yml`)**:

```yaml
input:
  # Mode 1: Sau pre_openspec — đọc existing pre_openspec.md
  change_name: "task-queue-app"

  # Mode 2: Standalone idea — brainstorm từ đầu
  idea_description: |
    Xây dựng dashboard giám sát task queue real-time cho hệ thống
    microservices. Dashboard cần hiển thị queue depth, processing
    rate, error rate, và cho phép retry failed tasks.

  # Mode 3: Sau feature_research — đọc research artifacts
  research_dir: "openspec/research/hermes-task-dashboard/"

  # OPTIONAL: Constraints hoặc preferences
  constraints: |
    - Budget: team 3 devs, 4 sprints
    - Must integrate with existing Spring Boot services
    - Performance: handle 100K concurrent WebSocket connections
```

### 3.4 `wf_openspec` — Generate Specifications

**Khi nào**: Đã có pre_openspec hoặc brainstorm output, cần sinh spec files.

**Input fields (`.hermes/steps/wf_openspec.yml`)**:

```yaml
input:
  # Auto-detected from prior workflows — chỉ cần set nếu override
  artifact_context: "openspec/changes/task-queue-app/artifact_context.yml"
  change_name: "task-queue-app"

  # OPTIONAL: Override spec generation scope
  skip_phases: []  # e.g., ["Phase 3"] nếu không cần market research
```

### 3.5 `wf_openspec_apply` — Implement Code from Specs

**Khi nào**: Có spec files hoàn chỉnh, cần implement code.

**Input fields (`.hermes/steps/wf_openspec_apply.yml`)**:

```yaml
input:
  # Auto-detected — chỉ override nếu cần
  artifact_context: "openspec/changes/task-queue-app/artifact_context.yml"
  change_name: "task-queue-app"

  # OPTIONAL: Restrict implementation scope
  target_tasks: []  # Empty = all tasks from spec
  dry_run: false    # true = plan only, no code writes
```

---

## 4. Agent Analysis Flow

Khi user request đến, agent thực hiện theo thứ tự:

```
1. PARSE user message
   ├── Extract feature_name, idea/description
   ├── Identify @[mentioned_files_or_dirs]
   └── Detect intent (research? build? analyze?)

2. SCAN workspace
   ├── Check openspec/research/<feature> → research outputs
   ├── Check openspec/changes/<feature> → spec outputs
   ├── Check openspec/pipeline_progress.yml → existing progress
   └── Check .hermes/steps/ → existing step configs

3. DECIDE workflow chain
   ├── Match tình huống → Decision Tree (Section 1)
   └── Validate chain logic (research before spec, spec before apply)

4. POPULATE step configs
   ├── Write/update .hermes/steps/<workflow>.yml
   ├── Populate `input:` fields từ parsed context
   └── Set `headless_rules:` nếu cần custom behavior

5. CREATE/UPDATE pipeline config
   ├── Set features list
   ├── Set workflows chain
   └── Verify all paths exist

6. EXECUTE pipeline
   └── ./hermes-pipeline start --config <config>
```

---

## 5. Common Mistakes to Avoid

| ❌ Anti-pattern | ✅ Correct Approach |
|----------------|-------------------|
| Chạy pipeline với `input: {}` trống | Populate input từ user context trước khi chạy |
| Dùng `wf_feature_research` khi đã có research output | Detect existing `openspec/research/` và skip research |
| Không scan workspace trước khi quyết định workflow | LUÔN scan `openspec/` trước |
| Hardcode feature name trong step config | Parse từ user message hoặc pipeline config |
| Chạy `wf_openspec_apply` khi chưa có spec | Verify dependency chain trước |
| Bỏ qua `seed_prompt` cho research workflow | Research mù sẽ cho output kém — PHẢI có prompt |
