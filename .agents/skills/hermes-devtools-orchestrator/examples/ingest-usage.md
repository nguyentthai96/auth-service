# Ingest Command — Usage Examples

## Tổng quan

`hermes-pipeline ingest` là cách nhanh nhất để đưa input linh hoạt vào pipeline.
Command auto-detect loại input, enrich qua LLM brainstorm, lưu history, và
tùy chọn start pipeline ngay.

---

## 1. Ingest Text Prompt

Input ngắn — tên feature hoặc mô tả tính năng:

```bash
# Feature name (auto-detect: TEXT mode, < 30 words)
./hermes-pipeline ingest "Event Sourcing"

# Short description (auto-detect: TEXT mode)
./hermes-pipeline ingest "CQRS pattern for order management"
```

Output:
```
╔══════════════════════════════════════════════╗
║  INGESTION COMPLETE                         ║
╠══════════════════════════════════════════════╣
║  ID:       abc12345-def6-7890-...           ║
║  Mode:     text                             ║
║  Method:   passthrough                      ║
║  Topics:   event sourcing, CQRS             ║
╚══════════════════════════════════════════════╝
```

---

## 2. Ingest URL

Fetch nội dung từ URL, extract text, và enrich:

```bash
./hermes-pipeline ingest "https://martinfowler.com/eaaDev/EventSourcing.html"
```

Output:
```
  ℹ️ Detected mode: url
  📥 Fetching URL: https://martinfowler.com/...
  🧠 Enriching prompt via LLM brainstorm...
  💾 Saved to prompt history: abc12345...
```

---

## 3. Ingest File

Đọc nội dung file local (`.md`, `.txt`, `.yml`, v.v.):

```bash
# Via --file flag
./hermes-pipeline ingest --file docs/feature-idea.md

# Hoặc auto-detect file path
./hermes-pipeline ingest "docs/feature-idea.md"
```

---

## 4. Ingest Idea (Long Description)

Mô tả chi tiết > 30 words → auto-detect là IDEA mode:

```bash
./hermes-pipeline ingest "Tôi muốn xây dựng một hệ thống event sourcing \
cho domain order management. Hệ thống cần ghi nhận mọi thay đổi trạng thái \
của order thông qua event stream, hỗ trợ replay, temporal queries, và \
projection cho read models khác nhau."
```

---

## 5. Ingest + Run Pipeline

Ingest → enrich → tự động start pipeline:

```bash
# Ingest và chạy pipeline luôn
./hermes-pipeline ingest "Event Sourcing pattern" --run

# Ingest file, enrich, rồi chạy pipeline
./hermes-pipeline ingest --file docs/idea.md --run

# Skip enrichment (passthrough) + run
./hermes-pipeline ingest "CQRS" --no-enrich --run
```

Khi dùng `--run`:
- `suggested_features` từ enrichment → inject vào `config.features`
- `enriched_content` → inject vào `config.prompt_input_content`
- Layer 4 prompt → hiển thị section "User Input (Enriched)"

---

## 6. Prompt History

### List recent prompts

```bash
./hermes-pipeline ingest --history
```

Output:
```
═══════════════════════════════════════════════
  PROMPT INPUT HISTORY (3 records)
═══════════════════════════════════════════════

  1. abc12345  | text       | 2026-08-15 | "Event Sourcing"
  2. def67890  | url        | 2026-08-14 | "https://martinfowler.com/..."
  3. ghi11111  | idea       | 2026-08-13 | "Tôi muốn xây dựng..."
```

### Search

```bash
./hermes-pipeline ingest --search "event sourcing"
```

### Reuse previous prompt

```bash
# Xem prompt cũ, rồi reuse để chạy pipeline
./hermes-pipeline ingest --use abc12345 --run
```

---

## 7. Data Flow Diagram

```
User Input (text/URL/file/idea)
       │
       ▼
┌──────────────┐
│ InputDetector │  auto-detect: URL / FILE / TEXT / IDEA
└──────┬───────┘
       │
       ▼
┌────────────────┐
│ContentExtractor │  fetch URL (urllib), read file, or passthrough
└──────┬─────────┘
       │
       ▼
┌───────────────┐
│PromptEnricher │  LLM brainstorm → structured output (or passthrough)
└──────┬────────┘
       │
       ├──────► PromptHistoryManager → .hermes/prompt_history/records/{uuid}.yml
       │
       ▼ (if --run)
┌──────────────────┐
│ PipelineConfig    │  inject prompt_input_content + suggested_features
│ + Orchestrator    │  → _build_execution_context → enriched_prompt in ctx
│ + PromptComposer  │  → Layer 4: "📝 User Input (Enriched)"
└──────────────────┘
```

---

## 8. YAML Record Example

File `.hermes/prompt_history/records/{uuid}.yml`:

```yaml
prompt_input:
  id: "abc12345-def6-7890-1234-567890abcdef"
  mode: "text"
  raw_content: "Event Sourcing pattern for order management"
  source: "cli"
  created_at: "2026-08-15T13:00:00Z"
  metadata: {}

enriched_prompt:
  input_id: "abc12345-def6-7890-1234-567890abcdef"
  enriched_content: |
    ## Event Sourcing Pattern for Order Management

    ### Problem Domain
    Order management systems require full audit trail of state changes,
    support for replaying events, and the ability to project different
    read models from the same event stream.

    ### Key Technical Requirements
    - Event Store: immutable, append-only log
    - Event Replay: reconstruct state at any point in time
    - Temporal Queries: query state as of a specific timestamp
    - Read Model Projections: CQRS-style separate read/write models
    ...
  enrichment_method: "brainstorm"
  key_topics:
    - "event sourcing"
    - "CQRS"
    - "domain events"
    - "order management"
  suggested_features:
    - "event-store-implementation"
    - "order-event-sourcing"
  created_at: "2026-08-15T13:00:05Z"
```
