---
name: feature-apply
description: Implement tasks from an OpenSpec feature change, extending the standard opsx-apply process with additional tracking documentation (todo-uncover, new-apis, delta-spec) and strict project standards enforcement.
license: VNPAY-DVNH
compatibility: Requires openspec CLI.
metadata:
  author: anhdt8
  version: "1.0"
---

Implement tasks from an OpenSpec change, with mandatory project standards compliance and extended tracking artifacts.

**Input**: Optionally specify a change name. If omitted, infer from conversation context or prompt.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` and let the user select

   Always announce: "Using change: <name>".

2. **Check status**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse `schemaName` and artifact structure.

3. **Get apply instructions**
   ```bash
   openspec instructions apply --change "<name>" --json
   ```
   Handle states:
   - `state: "blocked"`: show message, suggest completing artifacts first.
   - `state: "all_done"`: congratulate, suggest archive.
   - Otherwise: proceed.

4. **Read context files**

   Read all files listed in `contextFiles` from the apply instructions output.

5. **Load and Validate Project Standards (MANDATORY — before any code generation)**

   Read the following 4 core documents:

   | # | Document | Path |
   |---|----------|------|
   | 1 | Tech Stack & System Overview | `base_knowledge/structures/overview_system.md` |
   | 2 | Coding Conventions | `base_knowledge/standards/coding_standard.md` |
   | 3 | Logging Standard | `base_knowledge/standards/logging_standard.md` |
   | 4 | Error/Exception Handling | `base_knowledge/standards/error_handling_standard.md` |

   > **HALT CONDITION:** If ANY of the 4 documents above is missing, **STOP immediately**. Report which document(s) are missing and wait for the user to provide them. Do NOT generate any code.

   Then also read (no halt if missing):
   - All other files in `base_knowledge/standards/`
   - All files in `base_knowledge/common_rules/`

   Show progress: "Loaded N standard documents and M rule documents."

6. **Show current progress**

   Display schema, progress ("N/M tasks complete"), remaining tasks overview.

7. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on
   - Make the code changes required
   - **Strictly comply** with all standards and rules loaded in Step 5
   - Keep changes minimal and focused
   - Mark task complete: `- [ ]` → `- [x]`
   - Continue to next task

   While implementing, track: new APIs created, spec scopes impacted, uncovered TODOs.

   **Pause if:**
   - Task is unclear → ask for clarification
   - Implementation reveals a design issue → suggest updating artifacts
   - Code would violate a loaded standard → flag the conflict and ask
   - Error or blocker encountered → report and wait

8. **Generate Extended Tracking Artifacts**

   After completing tasks, generate/update in `openspec/changes/<name>/`:

   | Artifact | When to Create | Content |
   |----------|---------------|---------|
   | `todo-uncover.md` | Always | `TODO`, `FIXME`, uncovered edge cases discovered during implementation |
   | `new-apis.md` | If new endpoints were added | Path, HTTP Method, Request/Response payload, purpose |
   | `delta-spec.md` | If impact scope exists | Delta between original behavior and new implementation |

9. **On completion or pause, show status**

   Display:
   - Tasks completed this session
   - Overall progress: "N/M tasks complete"
   - Standards compliance confirmation
   - Extended artifacts created/updated
   - If all done: suggest archive

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting
- **NEVER generate code without first loading and validating all 4 core standard documents**
- **STRICTLY comply** with all rules in `base_knowledge/common_rules/` during code generation
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task checkbox immediately after completing each task
- Pause on errors, blockers, or unclear requirements — don't guess

**Fluid Workflow Integration**
- Can be invoked anytime: before all artifacts are done (if tasks exist), after partial implementation
- Allows artifact updates: if implementation reveals design issues, suggest updating artifacts
