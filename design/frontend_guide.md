# AntiGravity Frontend Development Guidelines

## Tech Stack

Build the project using the following stack only.

- React 19
- Vite
- TypeScript
- Tailwind CSS v4
- React Router v7
- TanStack Query (when backend integration begins)
- React Hook Form
- Zod
- Lucide React Icons

Do not introduce additional UI libraries (Material UI, Ant Design, Chakra UI, Bootstrap, etc.) unless explicitly requested.

---

# Core Principles

- Follow **DESIGN.md** as the single source of truth.
- Build using **mock data only** until backend integration.
- Reuse components before creating new ones.
- Keep components generic and reusable.
- Keep the project modular and scalable.
- Write clean, readable, maintainable TypeScript.

---

# Project Architecture

Use feature-based architecture.

```
src/
│
├── assets/
├── components/
│   ├── ui/
│   ├── layout/
│   ├── navigation/
│   ├── forms/
│   ├── cards/
│   ├── feedback/
│   └── shared/
│
├── features/
│   ├── dashboard/
│   ├── authentication/
│   ├── recommendations/
│   ├── profile/
│   └── settings/
│
├── hooks/
├── lib/
├── mock/
├── routes/
├── services/
├── styles/
├── types/
├── utils/
├── App.tsx
└── main.tsx
```

Feature-specific components belong inside their feature.

Reusable components belong inside `components/ui`.

---

# React Guidelines

- Use React 19 features whenever appropriate.
- Use functional components only.
- Never use class components.
- Prefer composition over inheritance.
- Keep components focused on one responsibility.
- Extract repeated logic into custom hooks.
- Avoid prop drilling where possible.
- Keep state close to where it is used.
- Memoize only when necessary.

---

# TypeScript Guidelines

- Never use `any`.
- Prefer explicit interfaces.
- Use utility types when appropriate.
- Create shared types inside `/types`.
- Use strict typing everywhere.

---

# Styling Guidelines

Use Tailwind CSS for every component.

Do not write inline styles unless absolutely necessary.

Avoid CSS files unless they contain:

- Global styles
- Typography
- Utility classes
- Animations

Everything else should use Tailwind utilities.

---

# Official Color Palette

## Brand Colors

Primary

`#202549`

Primary Hover

`#2C3261`

Secondary

`#E63963`

Secondary Hover

`#D72C57`

Accent

`#F2C94C`

Accent Hover

`#D9B23F`

---

## Neutral Colors

Background

`#F5F3EC`

Surface

`#FFFFFF`

Surface Alt

`#E9E6DD`

Primary Text

`#2A2A2A`

Secondary Text

`#6B6B6B`

Muted Text

`#9A9A9A`

---

# Design Philosophy

Follow **The Editorial Ronin** design language.

The interface should feel

- Premium
- Editorial
- Manga inspired
- Minimal
- Spacious
- Dynamic

Never build a generic SaaS dashboard.

---

# Layout

Prefer editorial layouts instead of symmetric dashboards.

Examples

- 7 / 5 split
- 8 / 4 split
- Offset grids
- Hero sections
- Feature panels

Use generous whitespace.

---

# Borders

Never use

- 1px borders
- Gray divider lines
- Tailwind default borders

Always use

- 2px navy borders

---

# Shadows

Never use blurred shadows.

Always use hard offset shadows.

Example

```
4px 4px 0px #202549
```

---

# Border Radius

Buttons

4px

Cards

8px

Keep radius consistent.

---

# Typography

Headings

- Space Grotesk
- Anton

Body

- Inter

Labels

- Uppercase
- Bold
- +5% letter spacing

---

# Components

Before creating any component

1. Search existing components.
2. Reuse if possible.
3. Extend existing components.
4. Only create new components if necessary.

---

# Forms

Use

- React Hook Form
- Zod validation

Inputs

- White background
- Navy border

Focused

- Yellow background

---

# Tables

Prefer

- Cards
- Editorial layouts
- Lists

Avoid enterprise-style tables.

---

# Icons

Use only

- Lucide React

Avoid mixing icon libraries.

---

# Images

Avoid stock photos.

Prefer

- Illustrations
- Sketch graphics
- Editorial artwork

---

# Motion

Allowed

- Fade
- Scale
- Slide
- Button press

Avoid

- Bounce
- Flashy animations

Prefer skeleton loaders over spinners.

---

# Responsiveness

Design mobile first.

Support

- Mobile
- Tablet
- Laptop
- Desktop
- Large Desktop

No horizontal scrolling.

---

# State Handling

Every page must include

- Loading state
- Skeleton state
- Empty state
- Error state
- Success state

---

# Mock Data

Until backend integration

- Mock authentication
- Mock APIs
- Mock users
- Mock loading
- Mock errors

Never hardcode API URLs.

---

# Code Quality

- Use ESLint.
- Keep files small.
- Keep components under ~200 lines whenever reasonable.
- Move business logic into hooks or utilities.
- Avoid duplicated code.
- Prefer readable code over clever code.

---

# Performance

- Lazy-load routes.
- Code split by feature.
- Memoize expensive components only when necessary.
- Optimize images.
- Avoid unnecessary re-renders.

---

# Accessibility

Every interactive element must support

- Keyboard navigation
- Visible focus states
- Semantic HTML
- ARIA labels where needed

---

# Final Checklist

Every completed screen must

- Follow DESIGN.md exactly.
- Use the official AntiGravity color palette.
- Use React 19 + Vite + Tailwind CSS.
- Be fully responsive.
- Reuse existing components.
- Use mock data only.
- Include loading, error, success, and empty states.
- Avoid 1px borders and blurred shadows.
- Maintain consistent spacing and typography.
- Feel premium, handcrafted, and uniquely AntiGravity.
```