---
name: ui-ux-generator
description: "Generate 7 UI/UX design documents + AI-generated PNG mockups from BRD + Technical Design. Covers design principles, framework selection, design system, tokens, component catalog, layout patterns, and screen mockups. Use after /wf_tech_design completes."
---

# UI/UX Design Generator

## Purpose

Transform **BRD + Technical Design** into **7 UI/UX design documents** and **AI-generated screen mockups** that provide a complete design system ready for frontend implementation.

---

## When to Use

- After `/wf_tech_design` completes with APPROVED status
- As the core step in the `/wf_ui_design` workflow

## When NOT to Use

- Technical design not approved → run `/wf_tech_design` first
- Only need wireframes → create ASCII wireframes manually

---

## Input

Required files:
```
project_docs/{project-name}/
├── brds/                           ← Business requirements
├── technical/
│   ├── 00-system-overview.md       ← Architecture + tech stack
│   ├── 01-roles-permissions.md     ← RBAC (affects UI visibility)
│   ├── 05-api-design.md            ← API contracts (affects data display)
│   └── 06-main-workflows.md        ← Workflows (affects navigation flow)
└── decision_log.md
```

---

## Output

```
project_docs/{project-name}/ui-ux/
├── 00-production-ui-quality-bar.md     ← Quality standards
├── 01-ui-ux-foundation.md              ← Design principles + direction
├── 02-ui-framework-tech-stack.md       ← Framework selection rationale
├── 03-design-system-basics.md          ← Typography, spacing, grid, breakpoints
├── 04-design-tokens.md                 ← Color, shadow, radius, animation tokens
├── 05-common-ui-components.md          ← Reusable component catalog
├── 06-app-layout-components.md         ← Page layouts, navigation patterns
├── ui-design-review.md                 ← Multi-agent design review
└── mockups/                            ← AI-generated screen PNGs
    ├── 01-login-screen.png
    ├── 02-dashboard.png
    ├── 03-{screen-name}.png
    └── ...
```

For document templates, see [references/UI_TEMPLATES.md](references/UI_TEMPLATES.md).

---

## Process (5 Phases)

### Phase 1: Context Loading

1. Read BRD docs — extract user personas, workflows, features
2. Read tech docs — extract tech stack, API contracts, RBAC
3. Read `decision_log.md` — understand prior decisions
4. Derive platform targets from `technology-analysis.md`

### Phase 2: Generate UI/UX Docs (Sequential)

| # | Document | Key Content |
|---|----------|-------------|
| 00 | quality-bar | Visual quality standards, accessibility targets |
| 01 | foundation | Design direction, mood, principles, inspiration |
| 02 | framework-tech | Framework selection (React/Vue/Flutter) with rationale |
| 03 | design-system | Typography scale, spacing, grid, breakpoints |
| 04 | design-tokens | Color palette (light/dark), shadows, radii, animations |
| 05 | common-components | Button, Input, Card, Modal, Table, Toast catalog |
| 06 | app-layout | Shell layout, sidebar, top-bar, navigation, page templates |

### Phase 3: 🎨 Mockup Generation Pipeline

For each key screen identified from workflows (BRD 02):

1. **Create ASCII wireframe** inline in design docs
2. **Convert to detailed prompt** describing the UI:
   - Layout, components, colors, typography
   - Content placeholders with realistic data
   - Mood: modern, clean, professional
3. **Generate PNG** using `generate_image` tool
4. **Save** to `mockups/{NN}-{screen-name}.png`
5. **Embed** in design docs: `![Screen Name](mockups/{NN}-{screen-name}.png)`

**Naming convention**: `{2-digit-number}-{kebab-case-name}.png`

**Minimum screens** (derived from workflows):
- Login / Register
- Dashboard / Home
- Primary entity list (e.g., room list, user list)
- Primary entity detail
- Primary action flow (e.g., payment, booking)
- Settings / Profile

### Phase 4: 🔥 Multi-Agent Design Review

Invoke `multi-agent-brainstorming`:

| Agent | Focus |
|:---|:---|
| 🎨 **Designer** | Visual consistency, hierarchy, white space, modern aesthetics |
| 👤 **User Advocate** | Usability, cognitive load, accessibility (WCAG 2.1 AA) |
| 🔒 **Constraint Guardian** | Responsive coverage, performance budget, asset sizes |
| 🏗️ **Frontend Architect** | Component reusability, token coverage, dark mode support |
| ⚖️ **Arbiter** | Synthesis, disposition |

**Output**: `ui-ux/ui-design-review.md`

### Phase 5: Revise & Finalize

1. Fix issues from design review
2. Regenerate affected mockups if needed
3. Update `decision_log.md`
4. Verify component coverage for all screens

---

## Transition

```
═══════════════════════════════════════
UI/UX DESIGN COMPLETE
═══════════════════════════════════════
Project:        {project-name}
Documents:      7 UI/UX docs + review
Mockups:        {N} screens generated
Components:     {M} defined
Design Tokens:  {K} tokens
Platforms:      {web/mobile/both}
═══════════════════════════════════════
Ready: /wf_pre_openspec {project-name}
═══════════════════════════════════════
```

---

## Guardrails

- **DO** generate mockups for every key workflow screen
- **DO** define tokens for both light AND dark mode
- **DO** catalog components with props/variants/states
- **DO** test responsive breakpoints in design system
- **DO NOT** use placeholder images — use `generate_image` tool
- **DO NOT** skip accessibility (WCAG 2.1 AA minimum)
- **DO NOT** hardcode colors — always use design tokens

## Limitations
- Use this skill only when the task clearly matches the scope described above.
- Do not treat the output as a substitute for environment-specific validation, testing, or expert review.
- Stop and ask for clarification if required inputs, permissions, safety boundaries, or success criteria are missing.
