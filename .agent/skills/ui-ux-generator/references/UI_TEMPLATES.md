# UI/UX Document Templates

> Reference file for `ui-ux-generator` skill.

## Table of Contents

1. [00-production-ui-quality-bar](#00-production-ui-quality-bar)
2. [01-ui-ux-foundation](#01-ui-ux-foundation)
3. [02-ui-framework-tech-stack](#02-ui-framework-tech-stack)
4. [03-design-system-basics](#03-design-system-basics)
5. [04-design-tokens](#04-design-tokens)
6. [05-common-ui-components](#05-common-ui-components)
7. [06-app-layout-components](#06-app-layout-components)

---

## 00-production-ui-quality-bar

```markdown
---
type: ui-ux
document: 00-production-ui-quality-bar
project: {project-name}
---

# Production UI Quality Bar

## Visual Standards
- **Typography**: System font stack + 1 custom font max
- **Spacing**: 4px base grid (4, 8, 12, 16, 24, 32, 48, 64)
- **Colors**: Max 5 brand colors + semantic colors
- **Animations**: < 300ms for UI transitions, 60fps target
- **Icons**: Single icon library, consistent stroke width

## Accessibility (WCAG 2.1 AA)
- Color contrast: ≥ 4.5:1 (normal text), ≥ 3:1 (large text)
- Focus indicators: Visible on all interactive elements
- Screen reader: All images have alt text
- Keyboard: All actions reachable via keyboard
- Touch targets: ≥ 44x44px on mobile

## Performance Budget
- First Contentful Paint: < 1.5s
- Largest Contentful Paint: < 2.5s
- Total bundle size: < 200KB (gzipped)
- Image format: WebP with JPEG fallback

## Responsive Breakpoints
| Name | Min-Width | Target |
|:---|:---|:---|
| Mobile | 0px | Phone portrait |
| Tablet | 768px | Tablet portrait |
| Desktop | 1024px | Laptop |
| Wide | 1440px | Desktop monitor |
```

---

## 01-ui-ux-foundation

```markdown
---
type: ui-ux
document: 01-ui-ux-foundation
project: {project-name}
---

# UI/UX Foundation

## 1. Design Direction
- **Style**: {modern/minimal/corporate/playful}
- **Mood**: {keywords: clean, trustworthy, efficient}
- **Inspiration**: {reference products/websites}

## 2. Design Principles
1. **Clarity**: Every element serves a purpose
2. **Consistency**: Same pattern for same action everywhere
3. **Feedback**: Every user action gets immediate response
4. **Forgiveness**: Allow undo, confirm destructive actions
5. **Efficiency**: Minimize steps for common tasks

## 3. Information Architecture
\```mermaid
graph TB
    HOME["🏠 Dashboard"] --> ROOMS["🏢 Rooms"]
    HOME --> TENANTS["👤 Tenants"]
    HOME --> PAYMENTS["💳 Payments"]
    HOME --> SETTINGS["⚙️ Settings"]
    ROOMS --> ROOM_DETAIL["Room Detail"]
    TENANTS --> TENANT_DETAIL["Tenant Detail"]
\```

## 4. Navigation Pattern
- **Primary**: Bottom tab (mobile) / Sidebar (desktop)
- **Secondary**: Breadcrumbs for nested screens
- **Contextual**: Action menus, FAB buttons
```

---

## 02-ui-framework-tech-stack

```markdown
---
type: ui-ux
document: 02-ui-framework-tech-stack
project: {project-name}
---

# UI Framework & Tech Stack

## 1. Framework Decision
| Option | Pros | Cons | Verdict |
|:---|:---|:---|:---:|
| {Framework A} | {pros} | {cons} | ✅ Chosen |
| {Framework B} | {pros} | {cons} | ❌ |

**Rationale**: {from technology-analysis.md debate}

## 2. UI Library Stack
| Category | Library | Version | Purpose |
|:---|:---|:---|:---|
| UI Framework | {name} | {ver} | Core rendering |
| State Mgmt | {name} | {ver} | State management |
| Routing | {name} | {ver} | Navigation |
| Forms | {name} | {ver} | Form handling |
| HTTP | {name} | {ver} | API calls |
| Charts | {name} | {ver} | Data visualization |

## 3. Build & Tooling
- Bundler: {Vite/Webpack}
- Linting: ESLint + Prettier
- Testing: {Jest/Vitest} + {Testing Library}
```

---

## 03-design-system-basics

```markdown
---
type: ui-ux
document: 03-design-system-basics
project: {project-name}
---

# Design System Basics

## 1. Typography Scale
| Name | Size | Weight | Line Height | Use |
|:---|:---|:---|:---|:---|
| Display | 32px | 700 | 1.2 | Page titles |
| H1 | 24px | 700 | 1.3 | Section headers |
| H2 | 20px | 600 | 1.3 | Subsections |
| Body | 16px | 400 | 1.5 | Content |
| Small | 14px | 400 | 1.4 | Labels, captions |
| Tiny | 12px | 400 | 1.4 | Helper text |

## 2. Spacing Scale (4px base)
| Token | Value | Use |
|:---|:---|:---|
| space-1 | 4px | Tight padding |
| space-2 | 8px | Internal padding |
| space-3 | 12px | Small gap |
| space-4 | 16px | Standard gap |
| space-6 | 24px | Section gap |
| space-8 | 32px | Large gap |
| space-12 | 48px | Section separation |
| space-16 | 64px | Page margin |

## 3. Grid System
- Mobile: 4 columns, 16px gutter
- Tablet: 8 columns, 24px gutter
- Desktop: 12 columns, 24px gutter
- Max content width: 1200px

## 4. Border Radius
| Token | Value | Use |
|:---|:---|:---|
| radius-sm | 4px | Small elements |
| radius-md | 8px | Cards, inputs |
| radius-lg | 12px | Modals |
| radius-full | 9999px | Chips, avatars |
```

---

## 04-design-tokens

```markdown
---
type: ui-ux
document: 04-design-tokens
project: {project-name}
---

# Design Tokens

## 1. Color Palette

### Brand Colors
| Token | Light | Dark | Use |
|:---|:---|:---|:---|
| primary | #2563EB | #3B82F6 | CTAs, links |
| primary-hover | #1D4ED8 | #60A5FA | Hover states |
| secondary | #64748B | #94A3B8 | Secondary actions |

### Semantic Colors
| Token | Light | Dark | Use |
|:---|:---|:---|:---|
| success | #16A34A | #22C55E | Positive |
| warning | #D97706 | #F59E0B | Caution |
| error | #DC2626 | #EF4444 | Errors |
| info | #2563EB | #3B82F6 | Information |

### Surface Colors
| Token | Light | Dark | Use |
|:---|:---|:---|:---|
| bg-primary | #FFFFFF | #0F172A | Main background |
| bg-secondary | #F8FAFC | #1E293B | Card background |
| bg-tertiary | #F1F5F9 | #334155 | Hover, selected |
| text-primary | #0F172A | #F8FAFC | Body text |
| text-secondary | #64748B | #94A3B8 | Captions |
| border | #E2E8F0 | #334155 | Borders |

## 2. Shadow System
| Token | Light | Dark |
|:---|:---|:---|
| shadow-sm | 0 1px 2px rgba(0,0,0,0.05) | 0 1px 2px rgba(0,0,0,0.3) |
| shadow-md | 0 4px 6px rgba(0,0,0,0.1) | 0 4px 6px rgba(0,0,0,0.4) |
| shadow-lg | 0 10px 15px rgba(0,0,0,0.1) | 0 10px 15px rgba(0,0,0,0.4) |

## 3. Animation Tokens
| Token | Value | Use |
|:---|:---|:---|
| duration-fast | 150ms | Hover, focus |
| duration-normal | 250ms | Transitions |
| duration-slow | 350ms | Page transitions |
| easing-default | cubic-bezier(0.4, 0, 0.2, 1) | Standard |
```

---

## 05-common-ui-components

```markdown
---
type: ui-ux
document: 05-common-ui-components
project: {project-name}
---

# Common UI Components

## Component Catalog

### Button
- **Variants**: Primary, Secondary, Outline, Ghost, Danger
- **Sizes**: Small (32px), Medium (40px), Large (48px)
- **States**: Default, Hover, Active, Disabled, Loading
- **Props**: `variant`, `size`, `disabled`, `loading`, `icon`, `onClick`

### Input
- **Types**: Text, Email, Password, Number, Search, Textarea
- **States**: Default, Focus, Error, Disabled
- **Props**: `type`, `label`, `placeholder`, `error`, `helperText`, `required`

### Card
- **Variants**: Default, Elevated, Outlined, Interactive
- **Props**: `variant`, `padding`, `onClick`

### Modal / Dialog
- **Variants**: Default, Alert, Confirm, Fullscreen (mobile)
- **Props**: `open`, `title`, `onClose`, `size`, `actions`

### Table
- **Features**: Sort, Filter, Pagination, Row selection
- **Props**: `columns`, `data`, `sortable`, `selectable`, `pagination`

### Toast / Notification
- **Variants**: Success, Error, Warning, Info
- **Position**: Top-right (desktop), Bottom (mobile)
- **Props**: `variant`, `message`, `duration`, `action`

### Empty State
- **Props**: `icon`, `title`, `description`, `action`

### Loading
- **Variants**: Spinner, Skeleton, Progress bar
```

---

## 06-app-layout-components

```markdown
---
type: ui-ux
document: 06-app-layout-components
project: {project-name}
---

# App Layout & Navigation

## 1. Shell Layout
\```
┌────────────────────────────────────────┐
│  Top Bar (Logo + Search + User Menu)   │
├──────┬─────────────────────────────────┤
│      │                                 │
│ Side │        Content Area             │
│ bar  │                                 │
│      │                                 │
│      │                                 │
├──────┴─────────────────────────────────┤
│  (Mobile only: Bottom Tab Bar)         │
└────────────────────────────────────────┘
\```

## 2. Navigation Components

### Sidebar (Desktop)
- Collapsed: 64px (icons only)
- Expanded: 240px (icons + labels)
- Sections: grouped by domain

### Bottom Tab Bar (Mobile)
- Max 5 tabs
- Active indicator: filled icon + label
- Inactive: outlined icon only

### Breadcrumbs
- Max 3 levels deep
- Truncate middle levels on mobile

## 3. Page Templates

### List Page
\```
┌─────────────────────────┐
│ Page Title    [+ Add]   │
├─────────────────────────┤
│ Search [____] [Filter▼] │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ Item Card           │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ Item Card           │ │
│ └─────────────────────┘ │
├─────────────────────────┤
│ ◀ 1 2 3 ... 10 ▶       │
└─────────────────────────┘
\```

### Detail Page
\```
┌─────────────────────────┐
│ ← Back   Title   [Edit] │
├─────────────────────────┤
│ ┌──────┐ Name: ...      │
│ │ Img  │ Status: Active │
│ └──────┘ Created: ...   │
├─────────────────────────┤
│ [Tab1] [Tab2] [Tab3]    │
├─────────────────────────┤
│ Tab content area        │
└─────────────────────────┘
\```

### Form Page
\```
┌─────────────────────────┐
│ ← Cancel   Title  [Save]│
├─────────────────────────┤
│ Label                    │
│ [________________]       │
│ Label                    │
│ [________________]       │
│ Label                    │
│ [________________]       │
├─────────────────────────┤
│ [Cancel]      [Submit]   │
└─────────────────────────┘
\```
```
