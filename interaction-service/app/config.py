from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import URL


class Settings(BaseSettings):
    """Service configuration, read from environment variables (or a local .env file)."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    postgres_host: str = "localhost"
    postgres_port: int = 5433
    postgres_db: str = "susume"
    postgres_user: str = "postgres"
    postgres_password: str = "postgres"

    # Full SQLAlchemy URL; overrides the POSTGRES_* settings when set (used by tests).
    database_url: str | None = None

    # Redis is optional: without it, recommendation-cache eviction is skipped.
    redis_url: str | None = None

    # Shared secret for service-to-service calls (/internal/*). Unset disables those routes.
    internal_api_token: str | None = None

    log_level: str = "INFO"

    @property
    def sqlalchemy_url(self) -> str | URL:
        if self.database_url:
            return self.database_url
        return URL.create(
            "postgresql+asyncpg",
            username=self.postgres_user,
            password=self.postgres_password,
            host=self.postgres_host,
            port=self.postgres_port,
            database=self.postgres_db,
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
