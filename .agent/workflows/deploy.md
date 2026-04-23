---
description: Build Docker images, validate environment config, run database migrations, and deploy all services. The dashboard is served as static files by Spring Boot — no separate frontend container needed.
---

# Workflow: Build & Deploy

## Epics Covered
- E17: Containerization
- E18: Environment Configuration
- E19: Database Migration
- E20: Service Health Validation

---

## Pre-Deploy Checklist
- [ ] All unit and integration tests passing (`./mvnw verify`)
- [ ] No secrets hardcoded in source:
  ```bash
  git grep -r "password\|secret\|api_key" --include="*.java" --include="*.py" --include="*.yml"
  ```
- [ ] All HTML files are in `api-service/src/main/resources/static/`
- [ ] Environment variables set in target environment (see list below)
- [ ] PostgreSQL instance accessible from API service

---

## Required Environment Variables

| Variable | Description | Example |
|---|---|---|
| `DATABASE_URL` | JDBC connection string | `jdbc:postgresql://db:5432/rec_engine` |
| `DATABASE_USERNAME` | DB user | `postgres` |
| `DATABASE_PASSWORD` | DB password | (use secrets manager) |
| `JWT_SECRET` | Min 256-bit secret | (use secrets manager) |
| `JWT_EXPIRY_HOURS` | Token lifetime | `24` |
| `EMBEDDING_SERVICE_URL` | Internal URL of embedding service | `http://embedding:8001` |
| `EMBEDDING_MODEL_NAME` | Transformer model name | `all-MiniLM-L6-v2` |
| `MAX_RECOMMENDATION_LIMIT` | Hard cap on result count | `50` |

---

## Build Steps

### 1. Copy Dashboard Files into Spring Boot Static Folder
All HTML, CSS references, and JS files must live under:
```
api-service/src/main/resources/static/
├── index.html
├── dashboard.html
├── items.html
├── stats.html
└── assets/
    └── app.js
```
Spring Boot serves this folder automatically at the root URL.
**No build step required** — Tailwind is loaded via CDN in each HTML file.

### 2. Build API Service JAR
```bash
cd api-service
./mvnw clean package -DskipTests
# Output: target/api-service-1.0.0.jar
# Static files are bundled inside the JAR automatically
```

### 3. Build Docker Images (2 custom images only)
```bash
# API Service (includes dashboard static files)
docker build -t kizuna-api:latest ./api-service

# Embedding Service
docker build -t kizuna-embedding:latest ./embedding-service

# postgres uses the official image — no custom build needed
```

### 4. Local Full-Stack Deploy (Docker Compose)
```bash
cd infra
cp .env.template .env
# Edit .env with your real values
docker-compose up -d
```

### 5. Verify Dashboard is Served
```bash
# Should return your login page HTML
curl http://localhost:8080/
curl http://localhost:8080/dashboard.html
```

---

## Database Migration

Flyway migrations run automatically on API service startup.
To run or inspect manually:

```bash
# Run migrations
./mvnw flyway:migrate \
  -Dflyway.url=$DATABASE_URL \
  -Dflyway.user=$DATABASE_USERNAME \
  -Dflyway.password=$DATABASE_PASSWORD

# Check migration status
./mvnw flyway:info
```

---

## Health Validation

After deployment, validate all services:

```bash
# 1. API Service health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 2. Embedding Service health
curl http://localhost:8001/health
# Expected: {"status":"ok","model":"all-MiniLM-L6-v2"}

# 3. Dashboard served correctly
curl -s http://localhost:8080/ | grep "<title>"
# Expected: contains your page title

# 4. End-to-end smoke test
curl -X POST http://localhost:8080/api/v1/tenants/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Smoke Test","contactEmail":"smoke@test.com"}'
# Expected: 200 with apiKey in response
```

---

## Rollback

```bash
# Roll back containers to previous image
docker-compose down
docker tag kizuna-api:previous kizuna-api:latest
docker-compose up -d

# Roll back DB migration (Flyway)
./mvnw flyway:undo
```

---

## Production Notes
- The dashboard is bundled inside the Spring Boot JAR/container — one less service to manage and monitor
- Tailwind CDN is used for simplicity; for production consider downloading the Tailwind CSS file and hosting it locally inside `static/assets/` to remove the CDN dependency
- All API calls from the dashboard JS use relative URLs (e.g. `/api/v1/...`) so they work regardless of the host domain
