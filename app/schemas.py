"""Request and response models for the API."""

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field


class ShortenRequest(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "url": "https://example.com/some/very/long/path?with=params",
                "custom_alias": "my-link",
                "expires_at": "2027-01-01T00:00:00Z",
            }
        }
    )

    url: str = Field(..., description="The long URL to shorten (http or https).")
    custom_alias: str | None = Field(
        default=None,
        description="Optional custom code: 3-30 characters, letters, digits, '-' or '_'.",
    )
    expires_at: datetime | None = Field(
        default=None, description="Optional expiry time (ISO 8601). Naive times are read as UTC."
    )


class ShortenResponse(BaseModel):
    short_code: str
    short_url: str
    original_url: str
    created_at: datetime
    expires_at: datetime | None
    delete_token: str = Field(
        ..., description="Shown once. Send it in the X-Delete-Token header to delete the link."
    )


class DailyClicks(BaseModel):
    date: date
    clicks: int


class ReferrerCount(BaseModel):
    referrer: str
    clicks: int


class StatsResponse(BaseModel):
    short_code: str
    short_url: str
    original_url: str
    created_at: datetime
    expires_at: datetime | None
    is_expired: bool
    total_clicks: int
    last_accessed_at: datetime | None
    clicks_last_7_days: list[DailyClicks]
    top_referrers: list[ReferrerCount]


class HealthResponse(BaseModel):
    status: str
    database: str
    version: str


class ErrorResponse(BaseModel):
    detail: str
