---
description: Initialize the full multi-tenant SaaS Recommendation Engine project. Sets up Spring Boot API, Python embedding service, PostgreSQL schema, and HTML+Tailwind dashboard served by Spring Boot. Run this first on a new repo.
---

# Workflow: Project Setup & Scaffolding

## Epics Covered
- E1: Project Initialization
- E2: Database Schema Bootstrap
- E3: Service Scaffold

---

## Phase 1 — Repository Structure

Create the following top-level directory layout:

```
recommendation-engine/
├── api-service/                         # Spring Boot backend
│   └── src/main/resources/
│       ├── static/                      # Dashboard — served by Spring Boot automatically
│       │   ├── index.html               # Login page
│       │   ├── dashboard.html           # Main dashboard
│       │   ├── items.html               # Item management
│       │   ├── stats.html               # Usage & stats
│       │   └── assets/
│       │       └── app.js               # Vanilla JS — all fetch() API calls
│       └── db/migration/                # Flyway SQL migration files
├── embedding-service/                   # Python FastAPI microservice
├── infra/                               # Docker Compose, env templates
└── docs/                                # Architecture docs, ADRs
```

**Steps:**

1. Scaffold `api-service/` as a Spring Boot 3.x project (Java 17+) with these dependencies:
   - `spring-boot-starter-web`
   - `spring-boot-starter-data-jpa`
   - `spring-boot-starter-security`
   - `spring-boot-starter-validation`
   - `jjwt` (JWT support)
   - `postgresql` driver
   - `flyway-core`
   - `lombok`
   - `spring-boot-starter-actuator`

2. Scaffold `embedding-service/` as a Python project with:
   - `fastapi`
   - `uvicorn`
   - `sentence-transformers`
   - `pydantic`

3. Place all dashboard HTML files under `api-service/src/main/resources/static/`.
   Spring Boot serves everything in `/static` at the root URL automatically.
   **No separate frontend server, no build step, no npm needed.**

4. Add Tailwind CSS via CDN in the `<head>` of every HTML file — no install required:
   ```html
   <script src="https://cdn.tailwindcss.com"></script>
   ```

5. Create `infra/docker-compose.yml` with 3 services only: `api`, `embedding`, `postgres`.
   No dashboard container needed — it is served directly by the Spring Boot `api` container.

6. Create `infra/.env.template` with all required environment variables (see Phase 3 below).

---

## Phase 2 — Database Schema

Create the PostgreSQL migration files using Flyway under `api-service/src/main/resources/db/migration/`.

### V1__create_tenants.sql
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE tenants (
  id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name           VARCHAR(255) NOT NULL,
  api_key_hash   VARCHAR(64)  NOT NULL UNIQUE,
  status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  contact_email  VARCHAR(255) NOT NULL,
  created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### V2__create_users.sql
```sql
CREATE TABLE dashboard_users (
  id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id      UUID         NOT NULL REFERENCES tenants(id),
  email          VARCHAR(255) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  role           VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
  created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
  last_login_at  TIMESTAMP,
  UNIQUE (tenant_id, email)
);
```

### V3__create_items.sql
```sql
CREATE TABLE items (
  id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id        UUID         NOT NULL REFERENCES tenants(id),
  external_item_id VARCHAR(255) NOT NULL,
  metadata         JSONB        NOT NULL,
  embedding        FLOAT[]      NOT NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  UNIQUE (tenant_id, external_item_id)
);
CREATE INDEX idx_items_tenant_status ON items(tenant_id, status);
```

### V4__create_interactions.sql
```sql
CREATE TABLE interactions (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id         UUID         NOT NULL REFERENCES tenants(id),
  external_user_id  VARCHAR(255) NOT NULL,
  external_item_id  VARCHAR(255) NOT NULL,
  interaction_type  VARCHAR(20)  NOT NULL,
  timestamp         TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_interactions_user ON interactions(tenant_id, external_user_id);
CREATE INDEX idx_interactions_item ON interactions(tenant_id, external_item_id);
CREATE INDEX idx_interactions_time ON interactions(tenant_id, timestamp);
```

---

## Phase 3 — Environment Config

Create `infra/.env.template`:
```
DATABASE_URL=jdbc:postgresql://localhost:5432/recommendation_engine
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=changeme

JWT_SECRET=replace_with_256bit_secret
JWT_EXPIRY_HOURS=24

EMBEDDING_SERVICE_URL=http://localhost:8001
EMBEDDING_MODEL_NAME=all-MiniLM-L6-v2

MAX_RECOMMENDATION_LIMIT=50
```

Also update `api-service/src/main/resources/application.properties`:
```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true

jwt.secret=${JWT_SECRET}
jwt.expiry-hours=${JWT_EXPIRY_HOURS}

embedding.service.url=${EMBEDDING_SERVICE_URL}
embedding.model.name=${EMBEDDING_MODEL_NAME}

recommendation.max-limit=${MAX_RECOMMENDATION_LIMIT:50}
```

---

## Phase 4 — Docker Compose (3 services only)

Create `infra/docker-compose.yml`:
```yaml
version: '3.8'
services:

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: recommendation_engine
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: changeme
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  embedding:
    build: ../embedding-service
    ports:
      - "8001:8001"
    environment:
      MODEL_NAME: all-MiniLM-L6-v2

  api:
    build: ../api-service
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/recommendation_engine
      DATABASE_USERNAME: postgres
      DATABASE_PASSWORD: changeme
      JWT_SECRET: replace_with_secret
      JWT_EXPIRY_HOURS: 24
      EMBEDDING_SERVICE_URL: http://embedding:8001
      EMBEDDING_MODEL_NAME: all-MiniLM-L6-v2
      MAX_RECOMMENDATION_LIMIT: 50
    depends_on:
      - postgres
      - embedding

volumes:
  postgres_data:
```

Note: The dashboard is served by the `api` container at `http://localhost:8080/`.
No separate dashboard service is needed.

---

## Acceptance Criteria
- [ ] `docker-compose up` starts all 3 services (api, embedding, postgres) without errors
- [ ] PostgreSQL schema applied via Flyway migration on API startup
- [ ] Spring Boot `GET /actuator/health` returns `{"status":"UP"}`
- [ ] Embedding service `GET /health` returns `{"status":"ok"}`
- [ ] `http://localhost:8080/` serves `index.html` (login page)
- [ ] `http://localhost:8080/dashboard.html` loads after login
