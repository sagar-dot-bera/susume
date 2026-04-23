# Susume - Multi-Tenant SaaS Recommendation Engine

A scalable, production-ready recommendation engine built as a multi-tenant SaaS platform. Leverages semantic embeddings and vector search to deliver intelligent, personalized recommendations for your users.

## Features

- **Multi-Tenant Architecture**: Complete tenant isolation with database-level scoping
- **Semantic Search**: Advanced text embedding using sentence transformers
- **Vector Database**: PostgreSQL with pgvector extension for efficient similarity search
- **RESTful API**: Comprehensive API for managing items, interactions, and recommendations
- **JWT Authentication**: Secure token-based authentication for API access
- **Dashboard**: HTML + Tailwind CSS web interface for administration and monitoring
- **Health Checks**: Built-in health endpoints and monitoring capabilities
- **Containerized**: Docker and Docker Compose for easy deployment

## Tech Stack

### Backend
- **Framework**: Spring Boot 3.2
- **Language**: Java 17
- **Build Tool**: Maven
- **Security**: Spring Security with JWT (JJWT)
- **ORM**: Spring Data JPA + Hibernate
- **Database**: PostgreSQL 15 with pgvector extension
- **Migrations**: Flyway
- **API Documentation**: Spring Boot Actuator

### Embedding Service
- **Framework**: FastAPI
- **Language**: Python 3
- **ML Model**: Sentence Transformers (all-MiniLM-L6-v2)
- **Server**: Uvicorn

### Frontend
- **Markup**: HTML5
- **Styling**: Tailwind CSS
- **Hosting**: Spring Boot static resources

### DevOps
- **Containerization**: Docker
- **Orchestration**: Docker Compose
- **Volumes**: Persistent storage for PostgreSQL and HuggingFace model cache

## Prerequisites

- **Docker** and **Docker Compose** (recommended for local development)
- **Java 17** (if running without Docker)
- **Python 3.8+** (if running embedding service standalone)
- **PostgreSQL 15** (if running without Docker)

## Getting Started

### Option 1: Using Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Susume
   ```

2. **Start the entire stack**
   ```bash
   cd infra
   docker-compose up -d
   ```

3. **Verify services are healthy**
   ```bash
   docker ps
   docker-compose logs -f
   ```

4. **Access the application**
   - API Server: http://localhost:8082
   - Embedding Service: http://localhost:8001
   - PostgreSQL: localhost:5433

### Option 2: Manual Setup

#### Database Setup
```bash
# Install PostgreSQL 15 with pgvector extension
# Create database and user
psql -U postgres -c "CREATE DATABASE recommendation_engine;"
psql -U postgres -c "CREATE USER sagar WITH PASSWORD 'Denji@0086';"
psql -U postgres -d recommendation_engine -c "GRANT ALL PRIVILEGES ON DATABASE recommendation_engine TO sagar;"
psql -U postgres -d recommendation_engine -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

#### Embedding Service
```bash
cd embedding-service
pip install -r requirements.txt
export MODEL_NAME=all-MiniLM-L6-v2
python main.py
```

#### API Service
```bash
cd api-service
export DATABASE_URL=jdbc:postgresql://localhost:5432/recommendation_engine
export DATABASE_USERNAME=sagar
export DATABASE_PASSWORD=Denji@0086
export EMBEDDING_SERVICE_URL=http://localhost:8001
export JWT_SECRET=your-secret-key-here
mvn clean install
mvn spring-boot:run
```

## Project Structure

```
Susume/
├── api-service/                 # Spring Boot backend application
│   ├── src/main/java/
│   │   └── com/susume/recommendation/
│   │       ├── client/          # External service clients
│   │       ├── config/          # Configuration classes
│   │       ├── controller/      # REST endpoints
│   │       ├── dto/             # Data transfer objects
│   │       ├── entity/          # JPA entities
│   │       ├── exception/       # Custom exceptions
│   │       ├── filter/          # Security filters
│   │       ├── repository/      # Data access layer
│   │       ├── service/         # Business logic
│   │       └── util/            # Utility classes
│   ├── src/main/resources/
│   │   ├── db/migration/        # Flyway migrations
│   │   ├── static/              # HTML/CSS/JS dashboard
│   │   └── application.properties
│   └── pom.xml
│
├── embedding-service/           # Python FastAPI embedding service
│   ├── main.py                  # FastAPI application
│   ├── requirements.txt         # Python dependencies
│   └── Dockerfile
│
├── infra/
│   └── docker-compose.yml       # Container orchestration
│
├── docs/                        # Documentation
├── docker-data/                 # Persistent volumes
│   ├── postgres/                # PostgreSQL data
│   └── huggingface/             # Model cache
│
└── README.md
```

## API Endpoints

### Authentication
- `POST /api/auth/login` - Authenticate and receive JWT token
- `POST /api/auth/validate` - Validate JWT token

### Items Management
- `POST /api/items` - Create a new item
- `GET /api/items` - List all items (tenant-scoped)
- `GET /api/items/{id}` - Get item details
- `PUT /api/items/{id}` - Update item
- `DELETE /api/items/{id}` - Delete item

### Recommendations
- `POST /api/recommendations` - Generate recommendations
- `GET /api/recommendations/{id}` - Get recommendation details

### Interactions
- `POST /api/interactions` - Record user interaction
- `GET /api/interactions` - List interactions (tenant-scoped)

### Health & Status
- `GET /actuator/health` - Application health check
- `GET /actuator/metrics` - Application metrics

## Configuration

### Environment Variables

#### API Service
```env
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/recommendation_engine
DATABASE_USERNAME=sagar
DATABASE_PASSWORD=your-password

# JWT
JWT_SECRET=your-very-secret-key-minimum-32-characters
JWT_EXPIRY_HOURS=24

# Embedding Service
EMBEDDING_SERVICE_URL=http://localhost:8001
EMBEDDING_MODEL_NAME=all-MiniLM-L6-v2

# API Limits
MAX_RECOMMENDATION_LIMIT=50
```

#### Embedding Service
```env
MODEL_NAME=all-MiniLM-L6-v2
```

> **Security**: Never commit secrets to version control. Use environment variables or a secrets manager in production.

## Testing

### Run All Tests
```bash
cd api-service
mvn test
```

### Run Specific Test Suite
```bash
mvn test -Dtest=RecommendationServiceTest
```

### Test Coverage
```bash
mvn test jacoco:report
```

Test results are available in `target/surefire-reports/`

## Database Schema

The recommendation engine uses the following key entities:

- **Tenants**: Isolated customer environments
- **Items**: Products/content to be recommended
- **Users**: System users within a tenant
- **Interactions**: User-item interactions (views, clicks, purchases)
- **Embeddings**: Vector representations of items
- **Recommendations**: Generated recommendations with scoring

All tables include `tenant_id` for multi-tenant isolation.

## Docker Compose Services

The `docker-compose.yml` orchestrates three services:

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `postgres` | pgvector/pgvector:pg15 | 5433 | Vector database |
| `embedding` | api-service/embedding-service | 8001 | Text embedding API |
| `api` | api-service/api-service | 8082 | Main REST API |

Health checks ensure services are ready before dependent services start.

## Security Considerations

- **JWT Authentication**: All API endpoints require valid JWT tokens
- **Tenant Isolation**: Database queries are scoped by `tenant_id`
- **Password Hashing**: User passwords are hashed using bcrypt
- **CORS**: Configure CORS headers for frontend access
- **SQL Injection**: Protected through parameterized queries
- **Secrets Management**: Store sensitive data in environment variables, never in code

## Performance Tuning

- **Vector Search**: Leverage pgvector indexes for fast similarity search
- **Connection Pooling**: HikariCP for efficient database connections
- **Caching**: Spring Cache abstraction for reducing database queries
- **Async Processing**: Consider async endpoints for long-running operations

## Contributing

1. Create a feature branch: `git checkout -b feature/your-feature`
2. Commit changes: `git commit -am 'Add your feature'`
3. Push to branch: `git push origin feature/your-feature`
4. Submit a pull request

See `.agent/workflows/` for automated development workflows and best practices.

## Workflows

The project includes automated development workflows in `.agent/workflows/`:
- `recommendation-engine.md` - Core recommendation engine implementation

Follow these workflows when implementing new features to maintain consistency and best practices.

## Troubleshooting

### PostgreSQL Connection Issues
- Verify container is running: `docker ps | grep postgres`
- Check credentials match in `docker-compose.yml`
- Ensure port 5433 is not already in use

### Embedding Service Not Responding
- Check logs: `docker logs recommendation_embedding`
- Verify HuggingFace cache volume is writable
- First startup may take 5+ minutes to download the model

### API Service Fails to Start
- Verify database is healthy: `docker logs recommendation_postgres`
- Check environment variables are set correctly
- Review logs: `docker logs recommendation_api`

## Support

For issues, questions, or suggestions:
1. Check existing issues on GitHub
2. Review logs: `docker-compose logs -f <service-name>`
3. Consult the workflow documentation in `.agent/workflows/`

## License

[Your License Here]

## Acknowledgments

Built with:
- Spring Boot and Spring Data JPA
- Sentence Transformers and HuggingFace
- PostgreSQL and pgvector
- FastAPI and Uvicorn
- Docker and Docker Compose

---

**Happy recommending!**
