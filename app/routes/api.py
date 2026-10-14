"""JSON API: create, inspect and delete short links."""

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response, status
from sqlalchemy import text
from sqlalchemy.orm import Session

from .. import __version__
from ..database import get_db
from ..rate_limit import enforce_rate_limit
from ..schemas import ErrorResponse, HealthResponse, ShortenRequest, ShortenResponse, StatsResponse
from ..services import AliasTakenError, ForbiddenError, NotFoundError, URLService
from ..validators import ValidationError

router = APIRouter(prefix="/api", tags=["links"])


def get_service(request: Request) -> URLService:
    return request.app.state.url_service


@router.post(
    "/shorten",
    response_model=ShortenResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(enforce_rate_limit)],
    responses={
        400: {"model": ErrorResponse, "description": "Invalid URL, alias or expiry"},
        409: {"model": ErrorResponse, "description": "Custom alias already taken"},
        429: {"model": ErrorResponse, "description": "Rate limit exceeded"},
    },
)
def shorten(
    payload: ShortenRequest,
    db: Session = Depends(get_db),
    service: URLService = Depends(get_service),
) -> ShortenResponse:
    """Create a short link for a long URL."""
    try:
        row, token = service.create(db, payload.url, payload.custom_alias, payload.expires_at)
    except ValidationError as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, str(exc)) from exc
    except AliasTakenError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc)) from exc

    return ShortenResponse(
        short_code=row.short_code,
        short_url=service.short_url_for(row.short_code),
        original_url=row.original_url,
        created_at=row.created_at,
        expires_at=row.expires_at,
        delete_token=token,
    )


@router.get(
    "/urls/{code}/stats",
    response_model=StatsResponse,
    responses={404: {"model": ErrorResponse}},
)
def get_stats(
    code: str,
    db: Session = Depends(get_db),
    service: URLService = Depends(get_service),
) -> StatsResponse:
    """Click analytics for a short link."""
    try:
        return StatsResponse(**service.stats(db, code))
    except NotFoundError as exc:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Short link not found.") from exc


@router.delete(
    "/urls/{code}",
    status_code=status.HTTP_204_NO_CONTENT,
    responses={403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def delete_url(
    code: str,
    x_delete_token: str | None = Header(default=None),
    db: Session = Depends(get_db),
    service: URLService = Depends(get_service),
) -> Response:
    """Deactivate a short link. Requires the delete token returned at creation."""
    try:
        service.delete(db, code, x_delete_token)
    except NotFoundError as exc:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Short link not found.") from exc
    except ForbiddenError as exc:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Missing or invalid delete token.") from exc
    return Response(status_code=status.HTTP_204_NO_CONTENT)


health_router = APIRouter(tags=["ops"])


@health_router.get("/health", response_model=HealthResponse)
def health(db: Session = Depends(get_db)) -> HealthResponse:
    """Liveness and database connectivity check, for load balancers and Docker."""
    try:
        db.execute(text("SELECT 1"))
        db_status = "ok"
    except Exception:  # pragma: no cover - only when the DB is down
        db_status = "unavailable"
    return HealthResponse(
        status="ok" if db_status == "ok" else "degraded", database=db_status, version=__version__
    )
