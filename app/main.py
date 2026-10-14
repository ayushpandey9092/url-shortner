"""Application factory and ASGI entry point.

Run locally with:  uvicorn app.main:app --reload
"""

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse

from . import __version__
from .cache import LRUCache
from .config import Settings, get_settings
from .database import Base, build_engine, build_session_factory
from .encoding import CodeGenerator
from .rate_limit import TokenBucketLimiter
from .routes import api, redirect
from .services import URLService

STATIC_DIR = Path(__file__).parent / "static"

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("url_shortener")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()

    engine = build_engine(settings.database_url)
    generator = CodeGenerator(
        settings.code_length, settings.id_scramble_multiplier, settings.id_scramble_offset
    )
    cache = LRUCache(settings.cache_max_items, settings.cache_ttl_seconds)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # Creates tables on first start. A larger deployment would use Alembic migrations.
        Base.metadata.create_all(engine)
        logger.info(
            "Started %s v%s with database %s",
            settings.app_name,
            __version__,
            engine.url.render_as_string(hide_password=True),
        )
        yield
        engine.dispose()

    app = FastAPI(
        title=settings.app_name,
        version=__version__,
        description="Shorten long URLs, redirect visitors, and track clicks.",
        lifespan=lifespan,
    )

    app.state.settings = settings
    app.state.engine = engine
    app.state.session_factory = build_session_factory(engine)
    app.state.cache = cache
    app.state.rate_limiter = TokenBucketLimiter(settings.rate_limit_per_minute)
    app.state.url_service = URLService(settings, generator, cache)

    @app.get("/", include_in_schema=False)
    def index() -> FileResponse:
        return FileResponse(STATIC_DIR / "index.html")

    app.include_router(api.router)
    app.include_router(api.health_router)
    app.include_router(redirect.router)  # must stay last: it matches any single path segment
    return app


app = create_app()
