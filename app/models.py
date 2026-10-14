"""ORM models."""

from datetime import UTC, datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


def utcnow() -> datetime:
    """Naive UTC timestamp. All times are stored as UTC without tzinfo."""
    return datetime.now(UTC).replace(tzinfo=None)


class ShortURL(Base):
    __tablename__ = "short_urls"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    # Nullable only for the instant between INSERT and setting the generated
    # code inside the same transaction. Unique indexes allow multiple NULLs.
    short_code: Mapped[str | None] = mapped_column(String(32), unique=True, index=True)
    original_url: Mapped[str] = mapped_column(Text, nullable=False)
    is_custom: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    # SHA-256 of the one-time management token returned at creation.
    delete_token_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    click_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_accessed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    clicks: Mapped[list["Click"]] = relationship(
        back_populates="short_url", cascade="all, delete-orphan", passive_deletes=True
    )

    def is_expired(self, now: datetime | None = None) -> bool:
        return self.expires_at is not None and self.expires_at <= (now or utcnow())


class Click(Base):
    __tablename__ = "clicks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    short_url_id: Mapped[int] = mapped_column(
        ForeignKey("short_urls.id", ondelete="CASCADE"), nullable=False
    )
    clicked_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)
    referrer: Mapped[str | None] = mapped_column(String(512), nullable=True)
    user_agent: Mapped[str | None] = mapped_column(String(512), nullable=True)

    short_url: Mapped[ShortURL] = relationship(back_populates="clicks")

    __table_args__ = (Index("ix_clicks_url_time", "short_url_id", "clicked_at"),)
