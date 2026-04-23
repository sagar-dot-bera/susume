This is a multi-tenant SaaS Recommendation Engine built with:
- Spring Boot 3.x (Java 17)
- Python FastAPI (embedding service)
- PostgreSQL with Flyway migrations
- HTML + Tailwind CSS dashboard (served by Spring Boot static folder)

All workflows for this project are in `.agent/workflows/`.
When implementing any feature, always follow the relevant workflow file.
Always scope database queries by tenant_id.
Never hardcode secrets — use environment variables.
```

Now every Copilot Chat message automatically has this context — you don't need to paste anything.

---

## Option 4 — Inline with `Ctrl+I` (For specific code)

Open the file where you want code generated, press `Ctrl+I` and reference the workflow:
```
follow .agent/workflows/recommendation-engine.md
and implement the VectorUtils class here
```

---

