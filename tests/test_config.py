import pytest
from pydantic import ValidationError

from app.config import Settings


@pytest.fixture(autouse=True)
def clean_env(monkeypatch):
    for name in ("BASE_URL", "RENDER_EXTERNAL_URL", "DATABASE_URL", "REDIRECT_STATUS_CODE"):
        monkeypatch.delenv(name, raising=False)


def settings(**values) -> Settings:
    return Settings(_env_file=None, **values)


@pytest.mark.parametrize(
    ("given", "expected"),
    [
        ("postgres://u:p@db:5432/app", "postgresql+psycopg://u:p@db:5432/app"),
        (
            "postgresql://u:p@db/app?sslmode=require",
            "postgresql+psycopg://u:p@db/app?sslmode=require",
        ),
        ("postgresql+psycopg://u@db/app", "postgresql+psycopg://u@db/app"),
        ("sqlite:///./data/urls.db", "sqlite:///./data/urls.db"),
    ],
)
def test_database_url_uses_psycopg3(given, expected):
    assert settings(database_url=given).database_url == expected


def test_base_url_defaults_to_localhost():
    assert settings().base_url == "http://localhost:8000"


def test_base_url_falls_back_to_render_external_url(monkeypatch):
    monkeypatch.setenv("RENDER_EXTERNAL_URL", "https://my-app.onrender.com/")
    assert settings().base_url == "https://my-app.onrender.com"


def test_explicit_base_url_wins_over_render(monkeypatch):
    monkeypatch.setenv("RENDER_EXTERNAL_URL", "https://my-app.onrender.com")
    monkeypatch.setenv("BASE_URL", "https://sho.rt")
    assert settings().base_url == "https://sho.rt"


def test_invalid_redirect_status_is_rejected():
    with pytest.raises(ValidationError):
        settings(redirect_status_code=200)


def test_blank_variables_fall_back_to_defaults(monkeypatch):
    monkeypatch.setenv("ID_SCRAMBLE_MULTIPLIER", "")
    monkeypatch.setenv("ID_SCRAMBLE_OFFSET", "")
    s = settings()
    assert s.id_scramble_multiplier == 2_176_477_521_981
    assert s.id_scramble_offset == 1_094_865_271
