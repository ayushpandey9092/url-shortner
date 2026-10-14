"""Application settings, loaded from environment variables or a `.env` file."""

from functools import lru_cache

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_ignore_empty=True,  # a blank variable means "use the default"
        extra="ignore",
        populate_by_name=True,
    )

    app_name: str = "URL Shortener"

    # Public base URL used to build short links, e.g. https://sho.rt
    # On Render, RENDER_EXTERNAL_URL is used when BASE_URL is not set.
    base_url: str = Field(
        default="http://localhost:8000",
        validation_alias=AliasChoices("BASE_URL", "RENDER_EXTERNAL_URL"),
    )

    # Any SQLAlchemy URL. SQLite for local use, PostgreSQL for production.
    # Hosting providers' "postgres://" and "postgresql://" URLs are accepted
    # and switched to the psycopg 3 driver automatically.
    database_url: str = "sqlite:///./data/urls.db"

    # Short-code generation. Set these ONCE per deployment and never change them:
    # new IDs could then map onto codes that already exist. The multiplier must be
    # odd and not divisible by 31. Pick your own values so your codes differ from
    # every other copy of this project.
    code_length: int = Field(default=7, ge=4, le=10)
    id_scramble_multiplier: int = 2_176_477_521_981
    id_scramble_offset: int = 1_094_865_271

    # 302 keeps analytics accurate; 301 lets browsers cache the redirect.
    redirect_status_code: int = 302

    # In-memory LRU cache for hot short codes.
    cache_max_items: int = Field(default=10_000, ge=0)
    cache_ttl_seconds: int = Field(default=3_600, ge=1)

    # Per-client rate limit on link creation. 0 disables it.
    rate_limit_per_minute: int = Field(default=30, ge=0)
    # Read the client IP from X-Forwarded-For. Enable only behind a trusted proxy.
    trust_proxy_headers: bool = False

    max_url_length: int = Field(default=2048, ge=32)
    max_expiry_days: int = Field(default=365, ge=1)

    @field_validator("redirect_status_code")
    @classmethod
    def _valid_redirect(cls, value: int) -> int:
        if value not in (301, 302, 307, 308):
            raise ValueError("redirect_status_code must be one of 301, 302, 307, 308")
        return value

    @field_validator("database_url")
    @classmethod
    def _postgres_driver(cls, value: str) -> str:
        for prefix in ("postgres://", "postgresql://"):
            if value.startswith(prefix):
                return "postgresql+psycopg://" + value[len(prefix) :]
        return value

    @field_validator("base_url")
    @classmethod
    def _strip_slash(cls, value: str) -> str:
        return value.rstrip("/")


@lru_cache
def get_settings() -> Settings:
    return Settings()
