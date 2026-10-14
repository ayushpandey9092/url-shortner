"""Shared fixtures.

Tests use a fresh temporary SQLite file by default. To run the same suite
against PostgreSQL, point TEST_DATABASE_URL at an empty test database:

    TEST_DATABASE_URL=postgresql://user@localhost:5432/shortener_test pytest

Every table in that database is dropped before each test.
"""

import os

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.database import Base, build_engine
from app.main import create_app

TEST_DATABASE_URL = os.environ.get("TEST_DATABASE_URL")


def make_settings(tmp_path, **overrides) -> Settings:
    values = {
        "database_url": TEST_DATABASE_URL or f"sqlite:///{tmp_path / 'test.db'}",
        "base_url": "http://sho.rt",
        "rate_limit_per_minute": 0,
    }
    values.update(overrides)
    settings = Settings(_env_file=None, **values)
    if TEST_DATABASE_URL:
        engine = build_engine(settings.database_url)
        Base.metadata.drop_all(engine)
        engine.dispose()
    return settings


@pytest.fixture
def settings_factory(tmp_path):
    return lambda **overrides: make_settings(tmp_path, **overrides)


@pytest.fixture
def client(tmp_path):
    app = create_app(make_settings(tmp_path))
    with TestClient(app, base_url="http://sho.rt") as test_client:
        yield test_client
    app.state.engine.dispose()


@pytest.fixture
def shorten(client):
    def _shorten(url="https://example.com/some/long/path", **extra):
        return client.post("/api/shorten", json={"url": url, **extra})

    return _shorten
