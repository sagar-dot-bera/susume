---
description: Implement tenant registration, API key management, dashboard login, and JWT authentication. Covers both external API key auth and dashboard JWT auth flows end-to-end.
---

# Workflow: Authentication & Tenant Management

## Epics Covered
- E3: Tenant Registration & API Key Generation
- E4: Dashboard Authentication (JWT)
- E5: API Key Validation Middleware

## User Stories

### US-01: Tenant Registration
**As a** new client organization,
**I want to** register on the platform and receive an API key,
**So that** my application can start ingesting items and recording interactions.

**Acceptance Criteria:**
- `POST /api/v1/tenants/register` accepts `{ name, contactEmail }`
- System generates a UUID tenant ID
- System generates a cryptographically secure 32-char hex API key
- API key is returned in the response **exactly once** (never stored in plaintext)
- API key is stored as SHA-256 hash in the `tenants.api_key_hash` column
- Duplicate `contactEmail` returns HTTP 409
- Response: `{ tenantId, apiKey, name, createdAt }`

---

### US-02: API Key Validation Middleware
**As a** platform,
**I want to** validate the `X-API-KEY` header on every external request,
**So that** only authenticated tenants can access the API.

**Acceptance Criteria:**
- A Spring `OncePerRequestFilter` intercepts all `/api/v1/items`, `/api/v1/interactions`, `/api/v1/recommendations` routes
- Header `X-API-KEY` is SHA-256 hashed and compared against `tenants.api_key_hash`
- Comparison uses constant-time equals (no timing attack)
- Missing or invalid key returns HTTP 401 `{ code: "UNAUTHORIZED", message: "Invalid API key" }`
- On success, resolved `tenantId` is stored in `SecurityContextHolder` for downstream use
- API keys **never** appear in log output

---

### US-03: API Key Regeneration
**As a** tenant admin,
**I want to** regenerate my API key from the dashboard,
**So that** I can rotate credentials if they are compromised.

**Acceptance Criteria:**
- `POST /api/v1/dashboard/api-key/regenerate` (JWT required, ADMIN role)
- Generates a new API key atomically (old key invalidated in same transaction)
- New key returned in response body once
- Previous key immediately rejected on next request
- Masked key (last 8 chars visible) available via `GET /api/v1/dashboard/api-key`

---

### US-04: Dashboard Login
**As a** tenant admin,
**I want to** log in to the dashboard with email and password,
**So that** I can manage my tenant account.

**Acceptance Criteria:**
- `POST /api/v1/auth/login` accepts `{ email, password }`
- Looks up `dashboard_users` by `(tenant context, email)` — note: for login, tenant is resolved from email uniqueness across tenants
- Password verified against bcrypt hash (min cost factor 12)
- On success: returns signed JWT `{ token, expiresAt }`
- JWT payload contains: `userId`, `tenantId`, `role`, `iat`, `exp`
- Failed login returns HTTP 401 (no detail on which field was wrong)
- `last_login_at` updated on successful login

---

### US-05: JWT Validation Middleware
**As a** platform,
**I want to** validate JWT tokens on dashboard endpoints,
**So that** only authenticated users can access admin features.

**Acceptance Criteria:**
- A Spring `OncePerRequestFilter` intercepts all `/api/v1/dashboard/**` and `/api/v1/auth/refresh` routes
- Validates JWT signature, expiration, and structure
- Extracts `tenantId` and `role` from claims into `SecurityContextHolder`
- Expired/tampered/malformed tokens return HTTP 401
- ADMIN-only endpoints return HTTP 403 if role is VIEWER

---

## Implementation Tasks

### Backend (Spring Boot)

1. **Create `TenantService`**
   - `register(name, contactEmail)` → generates API key, hashes it, saves tenant, returns raw key once
   - `regenerateApiKey(tenantId)` → atomic update of `api_key_hash`, returns new raw key

2. **Create `ApiKeyFilter extends OncePerRequestFilter`**
   - Extract `X-API-KEY` header
   - SHA-256 hash it
   - Query `tenantRepository.findByApiKeyHash(hash)`
   - On match: set tenant context. On miss: return 401.

3. **Create `AuthService`**
   - `login(email, password)` → bcrypt verify → generate JWT
   - `refreshToken(token)` → validate, issue new token

4. **Create `JwtFilter extends OncePerRequestFilter`**
   - Extract `Authorization: Bearer {token}`
   - Validate with JJWT
   - Set `SecurityContext`

5. **Create `TenantController`** — `POST /register`

6. **Create `AuthController`** — `POST /login`, `POST /refresh`

7. **Create `DashboardApiKeyController`** — `GET /api-key`, `POST /api-key/regenerate`

---

## Security Checklist
- [ ] API keys never logged
- [ ] Constant-time comparison for API key validation
- [ ] JWT secret loaded from environment variable, not hardcoded
- [ ] bcrypt cost factor >= 12
- [ ] SHA-256 used for API key storage (never MD5/SHA-1)
- [ ] 401 errors give no detail about which credential was wrong
