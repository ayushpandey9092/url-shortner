from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from app.main import create_app
from app.models import utcnow


def future(**delta) -> str:
    return (datetime.now(UTC) + timedelta(**delta)).isoformat()


# ---- shortening --------------------------------------------------------------


def test_shorten_returns_short_link(shorten):
    res = shorten("https://example.com/a/long/path?x=1")
    assert res.status_code == 201
    body = res.json()
    assert len(body["short_code"]) == 7
    assert body["short_url"] == f"http://sho.rt/{body['short_code']}"
    assert body["original_url"] == "https://example.com/a/long/path?x=1"
    assert body["delete_token"]
    assert body["expires_at"] is None


def test_same_url_twice_gives_two_distinct_codes(shorten):
    a = shorten("https://example.com").json()["short_code"]
    b = shorten("https://example.com").json()["short_code"]
    assert a != b


def test_scheme_is_added_when_missing(shorten):
    assert shorten("example.com/page").json()["original_url"] == "https://example.com/page"


def test_rejects_invalid_urls(shorten):
    for bad in [
        "",
        "   ",
        "ftp://example.com/file",
        "javascript:alert(1)",
        "https://",
        "http://localhost/admin",
        "https://exa mple.com",
        "https://bad..host.com",
        "https://example.com:99999/",
    ]:
        res = shorten(bad)
        assert res.status_code == 400, bad


def test_non_http_schemes_get_a_clear_message(shorten):
    for bad in ["javascript:alert(1)", "mailto:someone@example.com", "data:text/html,hi"]:
        assert "Only http and https" in shorten(bad).json()["detail"], bad


def test_host_with_port_and_no_scheme_is_accepted(shorten):
    res = shorten("example.com:8080/page")
    assert res.status_code == 201
    assert res.json()["original_url"] == "https://example.com:8080/page"


def test_rejects_url_that_is_too_long(shorten):
    res = shorten("https://example.com/" + "a" * 3000)
    assert res.status_code == 400


def test_rejects_own_domain_to_prevent_loops(shorten):
    res = shorten("http://sho.rt/abc1234")
    assert res.status_code == 400
    assert "own domain" in res.json()["detail"]


# ---- custom aliases ------------------------------------------------------------


def test_custom_alias(shorten, client):
    res = shorten("https://example.com/portfolio", custom_alias="my-portfolio")
    assert res.status_code == 201
    assert res.json()["short_code"] == "my-portfolio"
    assert client.get("/my-portfolio", follow_redirects=False).headers["location"] == (
        "https://example.com/portfolio"
    )


def test_duplicate_alias_conflicts(shorten):
    assert shorten(custom_alias="taken_one").status_code == 201
    res = shorten(custom_alias="taken_one")
    assert res.status_code == 409


def test_reserved_and_malformed_aliases(shorten):
    for alias in ["api", "docs", "health", "ab", "has space", "x" * 31, "emoji😀"]:
        assert shorten(custom_alias=alias).status_code == 400, alias


def test_alias_cannot_look_like_a_generated_code(shorten):
    assert shorten(custom_alias="abcdefg").status_code == 400  # 7 alphanumerics
    assert shorten(custom_alias="abc-efg").status_code == 201  # has a dash: fine


# ---- redirects -----------------------------------------------------------------


def test_redirect_uses_302_and_no_store(shorten, client):
    code = shorten("https://example.com/target").json()["short_code"]
    res = client.get(f"/{code}", follow_redirects=False)
    assert res.status_code == 302
    assert res.headers["location"] == "https://example.com/target"
    assert "no-store" in res.headers["cache-control"]


def test_unknown_code_is_404(client):
    res = client.get("/doesNotExist", follow_redirects=False)
    assert res.status_code == 404
    assert "text/html" in res.headers["content-type"]


def test_301_is_configurable(settings_factory):
    app = create_app(settings_factory(redirect_status_code=301))
    with TestClient(app) as c:
        code = c.post("/api/shorten", json={"url": "https://example.com"}).json()["short_code"]
        res = c.get(f"/{code}", follow_redirects=False)
        assert res.status_code == 301
        assert "cache-control" not in res.headers


# ---- expiry --------------------------------------------------------------------


def test_expiry_in_the_past_is_rejected(shorten):
    res = shorten(expires_at=future(minutes=-5))
    assert res.status_code == 400


def test_expiry_too_far_ahead_is_rejected(shorten):
    res = shorten(expires_at=future(days=400))
    assert res.status_code == 400


def test_expired_link_returns_410(shorten, client):
    code = shorten(expires_at=future(hours=1)).json()["short_code"]
    assert client.get(f"/{code}", follow_redirects=False).status_code == 302

    # Fast-forward: move the stored expiry into the past and clear the cache.
    from app.models import ShortURL

    with client.app.state.session_factory() as db:
        row = db.query(ShortURL).filter_by(short_code=code).one()
        row.expires_at = utcnow() - timedelta(seconds=1)
        db.commit()
    client.app.state.cache.clear()

    assert client.get(f"/{code}", follow_redirects=False).status_code == 410


def test_cached_entry_still_honours_expiry(shorten, client):
    code = shorten(expires_at=future(hours=1)).json()["short_code"]
    client.get(f"/{code}", follow_redirects=False)  # now cached

    from app.cache import CachedURL

    cached = client.app.state.cache.get(code)
    client.app.state.cache.set(
        code, CachedURL(cached.url_id, cached.original_url, utcnow() - timedelta(seconds=1))
    )
    assert client.get(f"/{code}", follow_redirects=False).status_code == 410


# ---- analytics -----------------------------------------------------------------


def test_clicks_are_counted(shorten, client):
    code = shorten("https://example.com").json()["short_code"]
    for _ in range(3):
        client.get(f"/{code}", follow_redirects=False, headers={"Referer": "https://news.site/"})
    client.get(f"/{code}", follow_redirects=False)

    stats = client.get(f"/api/urls/{code}/stats").json()
    assert stats["total_clicks"] == 4
    assert stats["last_accessed_at"] is not None
    assert len(stats["clicks_last_7_days"]) == 7
    assert stats["clicks_last_7_days"][-1]["clicks"] == 4
    refs = {r["referrer"]: r["clicks"] for r in stats["top_referrers"]}
    assert refs == {"https://news.site/": 3, "direct": 1}


def test_stats_for_unknown_code_is_404(client):
    assert client.get("/api/urls/nope123/stats").status_code == 404


# ---- deletion ------------------------------------------------------------------


def test_delete_requires_valid_token(shorten, client):
    body = shorten().json()
    code = body["short_code"]

    assert client.delete(f"/api/urls/{code}").status_code == 403
    wrong = client.delete(f"/api/urls/{code}", headers={"X-Delete-Token": "wrong"})
    assert wrong.status_code == 403

    client.get(f"/{code}", follow_redirects=False)  # warm the cache
    res = client.delete(f"/api/urls/{code}", headers={"X-Delete-Token": body["delete_token"]})
    assert res.status_code == 204

    assert client.get(f"/{code}", follow_redirects=False).status_code == 404
    assert client.get(f"/api/urls/{code}/stats").status_code == 404


def test_deleted_alias_is_not_reusable(shorten, client):
    body = shorten(custom_alias="gone-link").json()
    client.delete("/api/urls/gone-link", headers={"X-Delete-Token": body["delete_token"]})
    assert shorten(custom_alias="gone-link").status_code == 409


# ---- rate limiting & ops ---------------------------------------------------------


def test_rate_limit(settings_factory):
    app = create_app(settings_factory(rate_limit_per_minute=3))
    with TestClient(app) as c:
        codes = [
            c.post("/api/shorten", json={"url": "https://example.com"}).status_code
            for _ in range(4)
        ]
        assert codes == [201, 201, 201, 429]
        res = c.post("/api/shorten", json={"url": "https://example.com"})
        assert int(res.headers["retry-after"]) >= 1
        # Only link creation is limited; other endpoints still respond.
        assert c.get("/health").status_code == 200


def test_health(client):
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["database"] == "ok"


def test_home_page_and_docs(client):
    assert "URL Shortener" in client.get("/").text
    assert client.get("/docs").status_code == 200
    assert "/api/shorten" in client.get("/openapi.json").json()["paths"]
