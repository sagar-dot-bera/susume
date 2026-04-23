---
description: Build the HTML + Tailwind CSS dashboard for the recommendation engine. Covers all 4 pages — login, main dashboard, item management, and stats. All files go into Spring Boot's static folder and use vanilla JS fetch() to talk to the API.
---

# Workflow: Frontend Dashboard (HTML + Tailwind CSS)

## Epics Covered
- E21: Login Page
- E22: Main Dashboard
- E23: Item Management Page
- E24: Stats Page
- E25: Shared JS API Client

---

## File Structure

All files live inside Spring Boot's static folder — served automatically at the root URL:

```
api-service/src/main/resources/static/
├── index.html          → /              (login)
├── dashboard.html      → /dashboard.html
├── items.html          → /items.html
├── stats.html          → /stats.html
└── assets/
    └── app.js          → shared JS: auth helpers, fetch wrapper, nav
```

No build tools. No npm. Tailwind loaded via CDN on every page:
```html
<script src="https://cdn.tailwindcss.com"></script>
```

---

## Shared HTML Head (include in every page)

```html
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Kizuna — Recommendation Engine</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <script>
    tailwind.config = {
      theme: {
        extend: {
          colors: {
            brand: { DEFAULT: '#1F4E79', light: '#2E75B6', pale: '#D6E4F0' }
          }
        }
      }
    }
  </script>
  <script src="/assets/app.js" defer></script>
</head>
```

---

## Page 1 — index.html (Login)

**Route:** `http://localhost:8080/`

**UI Elements:**
- Centered card with logo/project name at top
- Email input field
- Password input field
- "Sign In" button
- Error message area (shown on failed login)

**JS behavior (in app.js):**
```javascript
async function login(email, password) {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  if (!res.ok) {
    showError('Invalid email or password');
    return;
  }
  const { token } = await res.json();
  localStorage.setItem('jwt', token);
  window.location.href = '/dashboard.html';
}
```

**Acceptance Criteria:**
- [ ] Successful login stores JWT in `localStorage` and redirects to `/dashboard.html`
- [ ] Failed login shows inline error message, does not redirect
- [ ] Page redirects to `/dashboard.html` automatically if JWT already exists in localStorage

---

## Page 2 — dashboard.html (Main Dashboard)

**Route:** `http://localhost:8080/dashboard.html`

**UI Elements:**
- Top navbar with project name + "Logout" button
- Sidebar nav: Dashboard, Items, Stats
- Summary cards (3 across):
  - Total Items (ACTIVE)
  - Total Interactions (last 30 days)
  - Recommendation Requests (last 30 days)
- API Key section:
  - Masked key display (e.g. `••••••••••••abcd1234`)
  - "Regenerate" button with confirmation dialog

**JS behavior:**
```javascript
// On page load
async function loadDashboard() {
  guardAuth(); // redirect to / if no JWT
  const stats = await apiFetch('/api/v1/dashboard/stats');
  document.getElementById('item-count').textContent = stats.itemCount;
  document.getElementById('interaction-count').textContent = stats.interactionCount;
  document.getElementById('rec-count').textContent = stats.recommendationCount;

  const keyData = await apiFetch('/api/v1/dashboard/api-key');
  document.getElementById('api-key-display').textContent = keyData.maskedKey;
}

async function regenerateKey() {
  if (!confirm('Regenerate API key? Your current key will stop working immediately.')) return;
  const res = await apiFetch('/api/v1/dashboard/api-key/regenerate', { method: 'POST' });
  document.getElementById('api-key-display').textContent = res.maskedKey;
  showSuccess('API key regenerated successfully');
}
```

**Acceptance Criteria:**
- [ ] Stats cards load from `GET /api/v1/dashboard/stats`
- [ ] API key shown masked; full key shown once after regeneration
- [ ] Logout clears `localStorage` and redirects to `/`
- [ ] Unauthenticated access redirects to `/`

---

## Page 3 — items.html (Item Management)

**Route:** `http://localhost:8080/items.html`

**UI Elements:**
- Top navbar + sidebar (same as dashboard)
- "Add Item" button → opens inline form
- Items table with columns: External ID, Metadata Preview, Status, Created, Actions
- Actions per row: "Delete" (soft-delete)
- Pagination controls (Previous / Next)
- Empty state message when no items exist

**JS behavior:**
```javascript
// Load paginated items
async function loadItems(cursor = null) {
  const url = cursor ? `/api/v1/items?cursor=${cursor}&limit=20` : '/api/v1/items?limit=20';
  const res = await apiFetch(url);
  renderTable(res.data);
  document.getElementById('next-btn').dataset.cursor = res.nextCursor || '';
  document.getElementById('next-btn').disabled = !res.nextCursor;
}

// Create item
async function createItem(externalItemId, metadata) {
  await apiFetch('/api/v1/items', {
    method: 'POST',
    body: JSON.stringify({ externalItemId, metadata })
  });
  loadItems();
}

// Soft-delete item
async function deleteItem(externalItemId) {
  if (!confirm(`Delete item "${externalItemId}"?`)) return;
  await apiFetch(`/api/v1/items/${externalItemId}`, { method: 'DELETE' });
  loadItems();
}
```

**Acceptance Criteria:**
- [ ] Items table loads from `GET /api/v1/items`
- [ ] "Add Item" form POSTs to `POST /api/v1/items` and refreshes the table
- [ ] "Delete" soft-deletes the item and removes it from the table
- [ ] Pagination works with next/prev cursor
- [ ] Metadata shown as truncated JSON preview (max 60 chars)

---

## Page 4 — stats.html (Usage Stats)

**Route:** `http://localhost:8080/stats.html`

**UI Elements:**
- Top navbar + sidebar
- Interaction breakdown table: counts per interaction type (VIEW, CLICK, LIKE, PURCHASE)
- Top 5 trending items list (from `GET /api/v1/recommendations/trending?limit=5`)
- Total interaction count badge

**JS behavior:**
```javascript
async function loadStats() {
  guardAuth();
  // Interaction history for a summary view
  const stats = await apiFetch('/api/v1/dashboard/stats');
  renderStatsSummary(stats);

  // Trending items
  const trending = await apiFetch('/api/v1/recommendations/trending?limit=5');
  renderTrendingList(trending.recommendations);
}
```

**Acceptance Criteria:**
- [ ] Stats load on page entry
- [ ] Trending items list shows top 5 items with metadata preview
- [ ] Page gracefully handles empty state (no interactions yet)

---

## Shared app.js — API Client & Auth Helpers

```javascript
// ── Auth helpers ──────────────────────────────────────────
function getToken() {
  return localStorage.getItem('jwt');
}

function guardAuth() {
  if (!getToken()) window.location.href = '/';
}

function logout() {
  localStorage.removeItem('jwt');
  window.location.href = '/';
}

// ── Fetch wrapper (adds JWT header automatically) ─────────
async function apiFetch(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getToken()}`,
      ...(options.headers || {})
    },
    body: options.body ? options.body : undefined
  });

  if (res.status === 401) {
    logout(); // Token expired — send back to login
    return;
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Request failed: ${res.status}`);
  }

  return res.json();
}

// ── UI helpers ────────────────────────────────────────────
function showError(msg) {
  const el = document.getElementById('error-msg');
  if (el) { el.textContent = msg; el.classList.remove('hidden'); }
}

function showSuccess(msg) {
  const el = document.getElementById('success-msg');
  if (el) { el.textContent = msg; el.classList.remove('hidden');
    setTimeout(() => el.classList.add('hidden'), 3000); }
}

// ── Shared nav active state ───────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  const path = window.location.pathname;
  document.querySelectorAll('[data-nav]').forEach(link => {
    if (link.dataset.nav === path) link.classList.add('bg-brand-pale', 'font-semibold');
  });
});
```

---

## Tailwind Utility Classes to Use

| Element | Classes |
|---|---|
| Page background | `bg-gray-50 min-h-screen` |
| Card / panel | `bg-white rounded-xl shadow p-6` |
| Primary button | `bg-brand text-white px-4 py-2 rounded-lg hover:bg-brand-light` |
| Danger button | `bg-red-500 text-white px-3 py-1 rounded hover:bg-red-600` |
| Input field | `border border-gray-300 rounded-lg px-3 py-2 w-full focus:outline-none focus:ring-2 focus:ring-brand-light` |
| Table header | `bg-brand text-white text-sm font-semibold px-4 py-3` |
| Table row | `border-b hover:bg-gray-50 px-4 py-3 text-sm` |
| Sidebar link | `flex items-center gap-2 px-4 py-2 rounded-lg text-gray-700 hover:bg-brand-pale` |
| Badge | `inline-block px-2 py-0.5 text-xs rounded-full` |
| Success badge | `bg-green-100 text-green-700` |
| Inactive badge | `bg-gray-100 text-gray-500` |

---

## Acceptance Criteria — Full Dashboard
- [ ] All 4 pages load without console errors
- [ ] JWT stored in localStorage on login; cleared on logout
- [ ] All API calls use relative URLs (`/api/v1/...`) — works on any host
- [ ] Unauthenticated access to any page redirects to `/`
- [ ] 401 response from any API call triggers logout and redirect to `/`
- [ ] Tailwind CDN loads correctly; no broken styles
- [ ] Static files served correctly from `http://localhost:8080/`
