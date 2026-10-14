"""The redirect endpoint: GET /{code}. Registered last so it never shadows other routes."""

from fastapi import APIRouter, BackgroundTasks, Depends, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from ..database import get_db
from ..services import ExpiredError, NotFoundError, URLService

router = APIRouter(tags=["redirect"])

_ERROR_PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>{title}</title>
<style>body{{font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;
margin:0;background:#f6f5f2;color:#1d1d1b}}main{{text-align:center;padding:24px}}
h1{{font-size:56px;margin:0}}a{{color:#3b5bdb}}</style></head>
<body><main><h1>{code}</h1><p>{message}</p>
<p><a href="/">Shorten a link</a></p></main></body></html>"""


def _error(code: int, title: str, message: str) -> HTMLResponse:
    html = _ERROR_PAGE.format(code=code, title=title, message=message)
    return HTMLResponse(html, status_code=code)


@router.get("/{code}", include_in_schema=True, summary="Redirect to the original URL")
def follow(
    code: str,
    request: Request,
    background: BackgroundTasks,
    db: Session = Depends(get_db),
):
    service: URLService = request.app.state.url_service
    try:
        target = service.resolve(db, code)
    except NotFoundError:
        return _error(404, "Link not found", "This short link does not exist or was deleted.")
    except ExpiredError:
        return _error(410, "Link expired", "This short link has expired.")

    background.add_task(
        URLService.record_click,
        request.app.state.session_factory,
        target.url_id,
        request.headers.get("referer"),
        request.headers.get("user-agent"),
    )

    status_code = request.app.state.settings.redirect_status_code
    response = RedirectResponse(target.original_url, status_code=status_code)
    if status_code in (302, 307):
        # Make sure browsers and proxies come back to us, so every click is counted.
        response.headers["Cache-Control"] = "private, no-store"
    return response
