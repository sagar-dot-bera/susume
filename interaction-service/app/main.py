import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker, create_async_engine

from app.cache import NoOpRecommendationCache, RecommendationCache, RedisRecommendationCache
from app.config import Settings, get_settings
from app.errors import register_error_handlers
from app.routers.interactions import router as interactions_router
from app.routers.internal import router as internal_router


def create_app(
    settings: Settings | None = None,
    engine: AsyncEngine | None = None,
    cache: RecommendationCache | None = None,
) -> FastAPI:
    """Build the app; tests pass their own engine and cache instead of Postgres and Redis."""
    settings = settings or get_settings()
    logging.basicConfig(
        level=settings.log_level.upper(), format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
    )

    engine = engine or create_async_engine(settings.sqlalchemy_url, pool_pre_ping=True)
    if cache is None:
        cache = RedisRecommendationCache.from_url(settings.redis_url) if settings.redis_url else NoOpRecommendationCache()

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        await cache.close()
        await engine.dispose()

    app = FastAPI(
        title="Susume Interaction Service",
        description="Records and serves user-item interactions for Susume tenants (FastAPI + async SQLAlchemy).",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.state.settings = settings
    app.state.engine = engine
    app.state.session_factory = async_sessionmaker(engine, expire_on_commit=False)
    app.state.cache = cache

    register_error_handlers(app)
    app.include_router(interactions_router)
    app.include_router(internal_router)

    @app.get("/health", tags=["Health"])
    async def health() -> dict[str, str]:
        return {"status": "UP", "service": "interaction-service"}

    @app.get("/health/ready", tags=["Health"])
    async def readiness(request: Request) -> JSONResponse:
        try:
            async with request.app.state.engine.connect() as conn:
                await conn.execute(text("SELECT 1"))
        except Exception:
            return JSONResponse(status_code=503, content={"status": "DOWN", "database": "unreachable"})
        return JSONResponse(content={"status": "UP", "database": "reachable"})

    return app


app = create_app()
