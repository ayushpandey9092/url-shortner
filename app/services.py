"""Business logic, kept separate from HTTP concerns so it is easy to test."""

import hashlib
import hmac
import secrets
from datetime import UTC, datetime, timedelta
from urllib.parse import urlsplit

from sqlalchemy import func, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from .cache import CachedURL, LRUCache
from .config import Settings
from .encoding import CodeGenerator
from .models import Click, ShortURL, utcnow
from .validators import ValidationError, normalize_and_validate_url, validate_alias


class AliasTakenError(Exception):
    pass


class NotFoundError(Exception):
    pass


class ExpiredError(Exception):
    pass


class ForbiddenError(Exception):
    pass


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _to_naive_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value
    return value.astimezone(UTC).replace(tzinfo=None)


class URLService:
    def __init__(self, settings: Settings, generator: CodeGenerator, cache: LRUCache) -> None:
        self.settings = settings
        self.generator = generator
        self.cache = cache
        self.own_host = urlsplit(settings.base_url).hostname

    def short_url_for(self, code: str) -> str:
        return f"{self.settings.base_url}/{code}"

    # ---- create -------------------------------------------------------------

    def create(
        self,
        db: Session,
        url: str,
        custom_alias: str | None = None,
        expires_at: datetime | None = None,
    ) -> tuple[ShortURL, str]:
        """Create a short link. Returns the row and the one-time delete token."""
        destination = normalize_and_validate_url(
            url, max_length=self.settings.max_url_length, own_host=self.own_host
        )
        alias = None
        if custom_alias:
            alias = validate_alias(custom_alias, looks_generated=self.generator.is_generated_shape)

        expiry = None
        if expires_at is not None:
            expiry = _to_naive_utc(expires_at)
            now = utcnow()
            if expiry <= now:
                raise ValidationError("Expiry time must be in the future.")
            if expiry > now + timedelta(days=self.settings.max_expiry_days):
                raise ValidationError(
                    f"Expiry cannot be more than {self.settings.max_expiry_days} days away."
                )

        token = secrets.token_urlsafe(24)
        row = ShortURL(
            original_url=destination,
            short_code=alias,
            is_custom=alias is not None,
            expires_at=expiry,
            delete_token_hash=_hash_token(token),
        )

        try:
            db.add(row)
            db.flush()  # assigns row.id inside the transaction
            if alias is None:
                row.short_code = self.generator.encode(row.id)
            db.commit()
        except IntegrityError as exc:
            db.rollback()
            if alias is None:
                # Generated codes are unique by construction. Reaching this means the
                # scramble constants were changed after data was written.
                raise RuntimeError(
                    "Generated short code collided with an existing one. Were "
                    "ID_SCRAMBLE_MULTIPLIER / ID_SCRAMBLE_OFFSET / CODE_LENGTH changed?"
                ) from exc
            raise AliasTakenError(f"The alias '{alias}' is already taken.") from exc

        db.refresh(row)
        return row, token

    # ---- resolve (the hot path) -----------------------------------------------

    def resolve(self, db: Session, code: str) -> CachedURL:
        """Find where a code points. Raises NotFoundError or ExpiredError."""
        cached = self.cache.get(code)
        if cached is None:
            row = db.scalar(
                select(ShortURL).where(ShortURL.short_code == code, ShortURL.is_active.is_(True))
            )
            if row is None:
                raise NotFoundError(code)
            cached = CachedURL(row.id, row.original_url, row.expires_at)
            self.cache.set(code, cached)

        if cached.expires_at is not None and cached.expires_at <= utcnow():
            raise ExpiredError(code)
        return cached

    @staticmethod
    def record_click(
        session_factory: sessionmaker[Session],
        url_id: int,
        referrer: str | None,
        user_agent: str | None,
    ) -> None:
        """Runs after the redirect response is sent, so it never slows the user down."""
        with session_factory() as db:
            now = utcnow()
            # Atomic increment: safe when many redirects for one link run at once.
            db.execute(
                update(ShortURL)
                .where(ShortURL.id == url_id)
                .values(click_count=ShortURL.click_count + 1, last_accessed_at=now)
            )
            db.add(
                Click(
                    short_url_id=url_id,
                    clicked_at=now,
                    referrer=(referrer or None) and referrer[:512],
                    user_agent=(user_agent or None) and user_agent[:512],
                )
            )
            db.commit()

    # ---- stats ----------------------------------------------------------------

    def get_row(self, db: Session, code: str) -> ShortURL:
        row = db.scalar(
            select(ShortURL).where(ShortURL.short_code == code, ShortURL.is_active.is_(True))
        )
        if row is None:
            raise NotFoundError(code)
        return row

    def stats(self, db: Session, code: str) -> dict:
        row = self.get_row(db, code)
        today = utcnow().date()
        since = datetime.combine(today - timedelta(days=6), datetime.min.time())

        day = func.date(Click.clicked_at)
        daily_rows = db.execute(
            select(day, func.count())
            .where(Click.short_url_id == row.id, Click.clicked_at >= since)
            .group_by(day)
        ).all()
        by_day = {str(d): n for d, n in daily_rows}
        daily = [
            {"date": d, "clicks": by_day.get(d.isoformat(), 0)}
            for d in (today - timedelta(days=offset) for offset in range(6, -1, -1))
        ]

        ref = func.coalesce(Click.referrer, "direct")
        referrer_rows = db.execute(
            select(ref, func.count().label("n"))
            .where(Click.short_url_id == row.id)
            .group_by(ref)
            .order_by(func.count().desc())
            .limit(5)
        ).all()

        return {
            "short_code": row.short_code,
            "short_url": self.short_url_for(row.short_code),
            "original_url": row.original_url,
            "created_at": row.created_at,
            "expires_at": row.expires_at,
            "is_expired": row.is_expired(),
            "total_clicks": row.click_count,
            "last_accessed_at": row.last_accessed_at,
            "clicks_last_7_days": daily,
            "top_referrers": [{"referrer": r, "clicks": n} for r, n in referrer_rows],
        }

    # ---- delete ---------------------------------------------------------------

    def delete(self, db: Session, code: str, token: str | None) -> None:
        row = self.get_row(db, code)
        if not token or not hmac.compare_digest(_hash_token(token), row.delete_token_hash):
            raise ForbiddenError(code)
        # Soft delete: the code stays reserved forever, so it can never be
        # reused to point somewhere else that old links did not intend.
        row.is_active = False
        row.deleted_at = utcnow()
        db.commit()
        self.cache.delete(code)
