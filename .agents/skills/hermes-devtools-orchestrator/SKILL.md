---
name: hermes-devtools-orchestrator
description: >
  Orchestrate the full lifecycle of hermes-pipeline binary: build from source,
  deploy to target projects, ingest flexible user input (URL/file/text/idea)
  with brainstorm enrichment, configure and execute dev workflows, evaluate
  results with quality scoring, and persist lessons learned. Use when building
  binary, deploying to new project, ingesting user prompt into pipeline,
  running dev pipeline, or analyzing pipeline execution quality.
category: workflow
risk: safe
source: workspace
tags: "[build, deploy, ingest, evaluate, improve, pipeline, binary, hermes]"
date_added: "2026-08-13"
date_updated: "2026-08-15"
---

# Hermes DevTools Orchestrator

## Purpose

Orchestrate the full lifecycle of the `hermes-pipeline` binary across multiple
target projects. This skill covers: build → deploy → **ingest** → configure →
execute → evaluate → learn. It ensures the binary is always built from latest
source, properly deployed, and that every execution produces measurable quality
metrics for continuous improvement.

## When to Use This Skill

- Building `hermes-pipeline` binary from source
- Deploying the binary to a new or existing target project
- **Ingesting user input** (URL, file, text, idea) into pipeline via `ingest` command
- Configuring pipeline for a specific project (`.hermes/` setup)
- **Preparing input** for pipeline workflows (idea/prompt → step config)
- **Analyzing context** before running pipeline (detect existing research/specs)
- **Searching/reusing prompt history** for past enriched inputs
- Running the pipeline and evaluating results
- Reviewing execution history and improvement trends
- Optimizing pipeline config based on past performance

## When NOT to Use

- Simple one-off script execution (no pipeline needed)
- Modifying hermes-pipeline source code (use standard dev workflow)
- Debugging individual pipeline modules (use systematic-debugging)

---

## Phase 1: Build Binary

Build the `hermes-pipeline` binary from current source using PyInstaller.

### Pre-flight Checks

```bash
# Verify venv exists
ls -la .venv/bin/python

# Verify source integrity — all required modules present
.venv/bin/python -c "
import importlib, sys
modules = [
    'src.cli.main', 'src.models', 'src.models.config',
    'src.models.prompt_input', 'src.pipeline.orchestrator',
    'src.pipeline.config_loader', 'src.progress_tracker',
    'src.prompt_composer', 'src.prompt_ingester',
    'src.prompt_history', 'src.tools.registry',
    'src.tools.executor', 'src.workflow_injector',
]
missing = []
for m in modules:
    try:
        importlib.import_module(m)
    except ImportError:
        missing.append(m)
if missing:
    print(f'MISSING: {missing}', file=sys.stderr)
    sys.exit(1)
print('All modules OK')
"
```

### Build Command

```bash
# From hermes_dev root
./build.sh --test
```

### Expected Outputs

| Output | Location | Verification |
|--------|----------|--------------|
| Binary | `dist/hermes-pipeline` | File exists, ~15-25MB |
| Smoke test | `--help` works | Exit code 0 |
| Status test | `status` command | Shows "No pipeline progress" |

### Build Metrics to Record

```yaml
build_metrics:
  timestamp: "ISO-8601"
  binary_size_mb: 0.0
  build_duration_seconds: 0
  python_version: ""
  tests_passed: true
  platform: "darwin-arm64"  # or linux-x86_64
```

---

## Phase 2: Deploy to Target Project

Copy the built binary to a target project workspace.

### Deploy Steps

1. **Verify binary exists**: `dist/hermes-pipeline`
2. **Verify target path**: Target project directory exists
3. **Copy binary**: `cp dist/hermes-pipeline <target-project>/`
4. **Set permissions**: `chmod +x <target-project>/hermes-pipeline`
5. **Verify deployment**: `<target-project>/hermes-pipeline --help`

### Init Target Configuration

If the target project does not have `.hermes/` directory:

```bash
cd <target-project>
./hermes-pipeline init
```

This creates the standard `.hermes/` structure:

```
<target-project>/
├── hermes-pipeline          # Binary
├── pipeline_config.yml      # Main config (or .hermes/pipelines/)
└── .hermes/
    ├── pipeline.yml         # Workflow dependencies
    ├── defaults.yml         # Default settings
    ├── steps/               # Per-workflow step configs
    ├── prompts/             # System prompts
    └── policies.yml         # Guardrail policies
```

---

## Phase 3: Configure Pipeline for Target

Tailor the pipeline configuration to the target project's needs.

### Workflow Selection Matrix

Select the correct workflow chain based on the user's situation:

| Situation | What user has | Workflow Chain |
|-----------|--------------|----------------|
| New idea, no research | Idea/prompt only | `ingest → feature_research → brainstorm → openspec → apply` |
| Idea + existing research | Research folder | `brainstorm → openspec → apply` |
| Clear URD/requirements | URD file | `pre_openspec → openspec → apply` |
| Specs done, need code | Spec files | `openspec_apply` only |
| Research only (no build) | Idea/prompt | `ingest → feature_research` only |
| Quick input + run | URL/text/file | `ingest --run` (auto-detect + enrich + start) |

**Decision logic**: Scan `openspec/research/` and `openspec/changes/` BEFORE selecting
workflows. If research output already exists for the feature, skip `wf_feature_research`.

### Configuration Checklist

- [ ] Set `sdk_mode` (google or claude)
- [ ] Set `model` appropriate for task complexity
- [ ] Define `features` list matching target project's development goals
- [ ] Select `workflows` chain using the matrix above
- [ ] Configure `settings` (max_retries, context_max_tokens)
- [ ] Set `workspace` path correctly
- [ ] Verify API key environment variable
- [ ] **Use `ingest` command OR populate step input** (see Phase 3.5)

### Config Validation

```bash
cd <target-project>
# Validate config before running
python3 -c "
import yaml
with open('pipeline_config.yml') as f:
    config = yaml.safe_load(f)
required = ['model', 'features', 'workflows']
missing = [k for k in required if k not in config]
if missing:
    print(f'Missing required keys: {missing}')
else:
    print(f'Config OK: {len(config[\"features\"])} features, {len(config[\"workflows\"])} workflows')
"
```

---

## Phase 3.5: Flexible Input Pipeline (NEW — v1.2)

**Two approaches to provide input** to the pipeline:

### Approach A: `ingest` Command (RECOMMENDED)

Use the built-in `ingest` CLI command that auto-detects input type,
enriches via LLM brainstorm, saves to history, and optionally starts pipeline.

```bash
cd <target-project>

# Ingest text prompt → auto-detect, enrich, save to history
./hermes-pipeline ingest "Event Sourcing pattern for order management"

# Ingest URL → fetch content, extract, enrich
./hermes-pipeline ingest "https://martinfowler.com/eaaDev/EventSourcing.html"

# Ingest file → read, enrich
./hermes-pipeline ingest --file docs/idea.md

# Ingest WITHOUT enrichment (passthrough)
./hermes-pipeline ingest "CQRS pattern" --no-enrich

# Ingest + immediately start pipeline
./hermes-pipeline ingest "Event Sourcing pattern" --run

# Reuse a previous prompt from history
./hermes-pipeline ingest --use <prompt-id> --run
```

**What `ingest` does internally:**

```
User input (text/URL/file/idea)
   ↓
1. InputDetector → auto-detect mode (URL/FILE/TEXT/IDEA)
   ↓
2. ContentExtractor → fetch URL, read file, or passthrough text
   ↓
3. PromptEnricher → LLM brainstorm enrichment (or passthrough)
   ↓
4. PromptHistoryManager → save raw + enriched to .hermes/prompt_history/
   ↓
5. [optional --run] → inject enriched_content + suggested_features into config → start pipeline
```

### Prompt History Management

All ingested prompts are persisted for audit trail and reuse:

```bash
# List recent prompt history
./hermes-pipeline ingest --history

# Search by keyword
./hermes-pipeline ingest --search "event sourcing"

# Reuse a previous enriched prompt
./hermes-pipeline ingest --use abc123-def456 --run
```

History storage layout:
```
.hermes/prompt_history/
├── index.yml              # Master index (sorted by created_at desc)
└── records/
    ├── {uuid-1}.yml       # Raw input + enriched output
    └── {uuid-2}.yml
```

### Pipeline Integration

When using `ingest --run`, the enriched prompt is automatically:
1. **Injected into `config.prompt_input_content`** → appears in Layer 4 context
2. **`suggested_features` merged into `config.features`** → pipeline knows what to process
3. **Available in `PromptComposer` Layer 4** as "User Input (Enriched)" section

---

### Approach B: Manual Step Config (Legacy)

Manually populate `.hermes/steps/<workflow>.yml` with input fields.
Still supported but `ingest` is preferred for new workflows.

> ⚠️ **Running pipeline with empty `input: {}` is an anti-pattern.**
> Research workflows run blind without a seed prompt.

#### Step 1: Parse User Request

Extract from the user's message:

| Extract | Example |
|---------|---------|
| **Feature name** | "hermes-task-dashboard" |
| **Idea/description** | "Build a dashboard for task queue monitoring" |
| **Technology scope** | "Spring Boot, React, Redis" |
| **Mentioned files** | `@[openspec/research/hermes-task-dashboard]` |
| **Intent** | research / design / implement |

#### Step 2: Scan Workspace Context

```bash
# Check existing research outputs
ls openspec/research/ 2>/dev/null

# Check existing change specs
ls openspec/changes/ 2>/dev/null

# Check pipeline progress
cat openspec/pipeline_progress.yml 2>/dev/null
```

**Auto-detect prior work**: If `openspec/research/<feature>/` exists, the feature
has already been researched — skip `wf_feature_research` and pass research path as input
to downstream workflows.

#### Step 3: Populate Step Config

Write/update `.hermes/steps/<workflow>.yml` with extracted context:

**For `wf_feature_research`** — the seed prompt is REQUIRED:
```yaml
input:
  seed_prompt: "<user's idea/description transformed into research prompt>"
  feature_name: "<extracted feature name>"
  technology_scope: "<relevant technologies>"
  research_goals: "<specific questions to answer>"
```

**For `wf_brainstorm_openspec`** — link to prior research:
```yaml
input:
  research_dir: "openspec/research/<feature>/"
  idea_description: "<enriched description from research findings>"
  change_name: "<feature name>"
```

**For `wf_pre_openspec`** — link to URD:
```yaml
input:
  urd_source: "<path to URD or requirements file>"
  feature_name: "<feature name>"
  prior_research: "openspec/research/<feature>/"  # if available
```

See [references/input-strategy-guide.md](references/input-strategy-guide.md) for
complete input templates and decision tree.

#### Step 4: Validate Input Before Run

```bash
# Verify step config has input populated
cat .hermes/steps/wf_feature_research.yml | grep -A 5 "input:"
# Should NOT show "input: {}" — must have actual content
```

---

## Phase 4: Execute Pipeline

Run the pipeline on the target project.

### Execution Commands

```bash
cd <target-project>

# Start pipeline
./hermes-pipeline start --config pipeline_config.yml

# Monitor progress
./hermes-pipeline status

# Reset if needed
./hermes-pipeline reset --feature <feature-name>
```

### During Execution — Monitor

Track these signals during execution:
- **Progress file**: `openspec/pipeline_progress.yml`
- **Logs**: Console output or `logs/` directory
- **Output artifacts**: `openspec/` directory

---

## Phase 5: Evaluate Results

After pipeline execution completes, evaluate the quality of outputs.

### Quality Scoring Rubric (0-100)

| Dimension | Weight | Criteria |
|-----------|--------|----------|
| Completeness | 30% | All features processed, all workflows completed |
| Output Quality | 25% | Generated files are valid, well-structured |
| Error Rate | 20% | Number of retries, failures, error recoveries |
| Efficiency | 15% | Token usage, execution time per feature |
| Consistency | 10% | Results match expected patterns |

### Evaluation Process

1. **Parse progress file** → extract completion status per feature
2. **Check output artifacts** → verify required files exist
3. **Count errors/retries** → from pipeline progress and logs
4. **Calculate scores** → weighted sum of dimension scores
5. **Generate report** → structured markdown evaluation

### Score Interpretation

| Score | Rating | Action |
|-------|--------|--------|
| 90-100 | Excellent | Record as baseline, no action needed |
| 70-89 | Good | Minor config tuning recommended |
| 50-69 | Fair | Review config and workflow selection |
| 0-49 | Poor | Root cause analysis needed |

See [references/evaluation-framework.md](references/evaluation-framework.md) for detailed scoring methodology.

---

## Phase 6: Learn and Improve

Record execution results and extract lessons for future optimization.

### What to Record

```yaml
execution_record:
  id: "<project>-<timestamp>"
  project: "<target-project-name>"
  timestamp: "ISO-8601"
  config_snapshot:
    model: ""
    sdk_mode: ""
    features_count: 0
    workflows: []
  results:
    overall_score: 0
    features_completed: 0
    features_total: 0
    total_duration_seconds: 0
    error_count: 0
  lessons: []
```

### Lesson Extraction Rules

After each execution, analyze patterns:

1. **Repeated failures** → Record as anti-pattern with suggested fix
2. **Successful patterns** → Record as proven approach
3. **Config insights** → Record optimal settings per project type
4. **Performance trends** → Compare with previous runs on same project

### Memory Integration

Use `agent-memory` patterns to store/retrieve:

```python
# Store execution record
memory_write({
    key: "hermes-exec-<project>-<date>",
    type: "execution_record",
    content: "<yaml-formatted-record>",
    tags: ["hermes", "pipeline", "<project-name>", "<score-rating>"]
})

# Retrieve past records for same project
memory_search({
    query: "hermes pipeline <project-name>",
    type: "execution_record"
})

# Find proven patterns
memory_search({
    query: "hermes successful pattern",
    type: "lesson"
})
```

### Continuous Improvement Loop

```
Execute → Evaluate → Record → Compare with baseline → Adjust config → Re-execute
```

Key improvement actions:
- **Model tuning**: Switch model if quality/cost ratio is suboptimal
- **Workflow optimization**: Skip workflows that consistently add no value
- **Feature batching**: Optimize feature list based on dependencies
- **Config refinement**: Adjust retries, tokens, timeouts based on history

---

## Quick Reference

### Common Commands

| Action | Command |
|--------|---------|
| Build binary | `./build.sh --test` |
| Deploy to target | `cp dist/hermes-pipeline <target>/` |
| Init config | `cd <target> && ./hermes-pipeline init` |
| **Ingest text input** | `./hermes-pipeline ingest "Event Sourcing"` |
| **Ingest URL** | `./hermes-pipeline ingest "https://..."` |
| **Ingest file** | `./hermes-pipeline ingest --file idea.md` |
| **Ingest + run** | `./hermes-pipeline ingest "..." --run` |
| **View history** | `./hermes-pipeline ingest --history` |
| **Search history** | `./hermes-pipeline ingest --search "keyword"` |
| **Reuse prompt** | `./hermes-pipeline ingest --use <id> --run` |
| Start pipeline | `./hermes-pipeline start --config pipeline_config.yml` |
| Check status | `./hermes-pipeline status` |
| Reset feature | `./hermes-pipeline reset --feature <name>` |

### Integration Points

| Skill | Integration |
|-------|------------|
| `agent-memory` | Persistent execution history and lessons |
| `improve-agent` | Performance analysis and optimization |
| `multi-agent-optimize` | Parallel execution optimization |
| `design-orchestration` | Risk assessment before major changes |
| `skill-orchestrator` | Combine with other skills for complex tasks |

### File Structure

```
hermes_dev/                              # Source project
├── build.sh                             # Build script
├── build.spec                           # PyInstaller spec
├── src/                                 # Source modules
│   ├── cli/main.py                      # CLI entry + ingest command
│   ├── models/
│   │   ├── config.py                    # PipelineConfig + prompt_input fields
│   │   └── prompt_input.py              # InputMode, PromptInput, EnrichedPrompt
│   ├── pipeline/
│   │   ├── orchestrator.py              # Enriched context injection
│   │   └── config_loader.py             # YAML config parser
│   ├── prompt_ingester.py               # 3-stage: detect → extract → enrich
│   ├── prompt_history.py                # YAML-based history manager
│   └── prompt_composer.py               # Layer 4 enriched prompt rendering
└── dist/hermes-pipeline                 # Built binary

<target-project>/                        # Target project
├── hermes-pipeline                      # Deployed binary
├── pipeline_config.yml                  # Pipeline config
├── .hermes/                             # Config directory
│   ├── pipeline.yml
│   ├── defaults.yml
│   ├── steps/                           # Per-workflow step configs
│   └── prompt_history/                  # Ingested prompt records (NEW)
│       ├── index.yml                    # Master index
│       └── records/{uuid}.yml           # Individual records
└── openspec/                            # Output artifacts
    └── pipeline_progress.yml            # Progress tracking
```

## References

- Build strategies: [references/build-strategies.md](references/build-strategies.md)
- Evaluation framework: [references/evaluation-framework.md](references/evaluation-framework.md)
- Memory schema: [references/memory-schema.md](references/memory-schema.md)
- **Input strategy guide**: [references/input-strategy-guide.md](references/input-strategy-guide.md)
- Pipeline config template: [examples/pipeline-config-template.yml](examples/pipeline-config-template.yml)
- Evaluation report sample: [examples/evaluation-report-sample.md](examples/evaluation-report-sample.md)
- **Feature research step config**: [examples/step-config-feature-research.yml](examples/step-config-feature-research.yml)
- **Research-to-spec chain config**: [examples/step-config-research-to-spec.yml](examples/step-config-research-to-spec.yml)
- **Ingest usage examples**: [examples/ingest-usage.md](examples/ingest-usage.md)

## Limitations

- Use this skill only when the task clearly matches the scope described above.
- Binary is platform-specific — build on the same OS/arch as the target.
- Do not treat evaluation scores as absolute — they are relative to baseline.
- Stop and ask for clarification if target project structure is unclear.
