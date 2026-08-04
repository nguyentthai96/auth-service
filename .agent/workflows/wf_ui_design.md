---
description: "Phase 2b: Generate 7 UI/UX design documents + AI-generated PNG mockups from BRD + Technical Design. Includes multi-agent design review."
---

# Workflow: UI/UX Design (`/wf_ui_design`)

## Overview

Tạo 7 tài liệu UI/UX Design + AI-generated mockups từ BRD + Technical Design. Bao gồm multi-agent design review bởi Designer, User Advocate, và Frontend Architect.

---

## Prerequisites

- `/wf_tech_design` đã hoàn tất
- Technical docs exist: `project_docs/{project-name}/technical/`

## Trigger

```
/wf_ui_design {project-name}
```

---

## Steps

### Step 1: Validate Prerequisites

1. Check tech docs exist
2. Read key inputs: system-overview, roles-permissions, api-design, main-workflows
3. Build context map: screens needed from workflows

### Step 2: Load Skills

Read:
- `.agent/skills/ui-ux-generator/SKILL.md`
- `.agent/skills/ui-ux-generator/references/UI_TEMPLATES.md`

### Step 3: Generate UI/UX Docs (Sequential — Phase 2 of skill)

| # | Document | Key Content |
|---|----------|-------------|
| 00 | quality-bar | Accessibility, performance budget, responsive |
| 01 | foundation | Design direction, principles, info architecture |
| 02 | framework-tech | UI framework selection rationale |
| 03 | design-system | Typography, spacing, grid |
| 04 | design-tokens | Colors (light+dark), shadows, animations |
| 05 | common-components | Button, Input, Card, Modal, Table, Toast |
| 06 | app-layout | Shell, sidebar, navigation, page templates |

### Step 4: 🎨 Mockup Generation (Phase 3 of skill)

For each key screen (derived from BRD 02 workflows):

1. List screens needed from user journeys
2. Create ASCII wireframe (inline in docs)
3. Convert to detailed image prompt
4. Call `generate_image` tool → save PNG
5. Embed in design docs

**Minimum screens**:
| # | Screen | Source Workflow |
|---|--------|---------------|
| 01 | Login / Register | WF: Authentication |
| 02 | Dashboard | WF: Main navigation |
| 03 | Primary List | WF: Entity management |
| 04 | Detail View | WF: Entity detail |
| 05 | Action Flow | WF: Core business flow |
| 06 | Settings | WF: User preferences |

### Step 5: 🔥 Multi-Agent Design Review (Phase 4 of skill)

| Agent | Focus |
|:---|:---|
| 🎨 **Designer** | Visual consistency, hierarchy, aesthetics |
| 👤 **User Advocate** | Usability, cognitive load, WCAG 2.1 AA |
| 🔒 **Constraint Guardian** | Responsive, performance budget, asset sizes |
| 🏗️ **Frontend Architect** | Component reusability, dark mode, token coverage |
| ⚖️ **Arbiter** | Synthesis, disposition |

**Output**: `ui-ux/ui-design-review.md`

### Step 6: Revise & Finalize

1. Fix design review issues
2. Regenerate affected mockups
3. Update `decision_log.md`

### Step 7: Auto-Commit

```bash
git add project_docs/{project-name}/ui-ux/
git commit -m "docs({project-name}): UI/UX design — 7 docs + {N} mockups

Design: {style}
Framework: {framework}
Mockups: {N} screens
Review: {APPROVED}"
```

### Step 8: Transition

```
═══════════════════════════════════════
UI/UX DESIGN COMPLETE
═══════════════════════════════════════
Documents:  7 UI/UX docs + review
Mockups:    {N} screens generated
Components: {M} defined
═══════════════════════════════════════
Ready: /wf_pre_openspec {project-name}
═══════════════════════════════════════
```

---

## Output Validation

- [ ] 7 UI/UX docs (00-06) exist
- [ ] `ui-design-review.md` generated
- [ ] `mockups/` folder has ≥ 6 PNGs
- [ ] Design tokens cover light + dark mode
- [ ] Component catalog complete
- [ ] `decision_log.md` updated
- [ ] Changes committed to git

---

## Next Step

```
/wf_pre_openspec {project-name}
```
