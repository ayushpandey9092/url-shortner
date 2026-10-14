# URL Shortener

A URL shortening service built with **FastAPI** and **SQLAlchemy**. It turns long links into short ones such as `http://localhost:8000/W9eTrsF`, redirects visitors to the original address, and records click analytics.

It comes with a small web page for creating links, interactive API docs, a test suite, Docker support and a GitHub Actions CI pipeline.

> The reasoning behind every design choice is written up in **[docs/DESIGN.md](docs/DESIGN.md)**.

---

## Features

| Feature | Details |
|---|---|
| **Shorten URLs** | 7-character Base62 codes, generated without collisions |
| **Fast redirects** | HTTP 302 by default (configurable to 301), served from an in-memory LRU cache |
| **Custom aliases** | Pick your own code, e.g. `/my-portfolio` |
| **Link expiry** | Optional expiry time. Expired links return **410 Gone** |
| **Click analytics** | Total clicks, last access, clicks per day for 7 days, top referrers |
| **Secure deletion** | A one-time delete token is returned at creation. Only its SHA-256 hash is stored |
| **Rate limiting** | Token bucket per client IP on link creation (default 30/min) |
| **URL validation** | Only http/https. Rejects malformed hosts, over-long URLs and loops to this service |
| **Web UI** | A single page at `/` for creating and copying links |
| **API docs** | Swagger UI at `/docs`, ReDoc at `/redoc` |
| **Health check** | `/health` reports app and database status |
| **Database choice** | SQLite out of the box, PostgreSQL by changing one setting |

---

## Tech stack

| Layer | Technology | Why |
|---|---|---|
| Language | **Python 3.11+** | Readable, widely used for backend services |
| Web framework | **FastAPI** | Fast, type-hinted, generates OpenAPI docs automatically |
| ASGI server | **Uvicorn** | Production-grade server for FastAPI |
| ORM | **SQLAlchemy 2.0** | Database-agnostic, so SQLite and PostgreSQL share one codebase |
| Validation | **Pydantic v2** and **pydantic-settings** | Request/response schemas and typed config from env vars |
| Database | **SQLite** (dev) / **PostgreSQL** (prod) | Zero setup locally, robust at scale |
| Cache | In-process **LRU + TTL** cache | Keeps hot redirects off the database. Swappable for Redis |
| Testing | **pytest** + FastAPI **TestClient** (httpx) | 51 tests, run on both SQLite and PostgreSQL |
| Linting | **Ruff** | Lint and format in one fast tool |
| Containers | **Docker**, **Docker Compose** | Same environment everywhere. Optional Postgres profile |
| CI | **GitHub Actions** | Lint, test on Python 3.11–3.13 and on PostgreSQL, build and smoke-test the image |
| Hosting | **Render** (Blueprint in `render.yaml`) | Deploys from GitHub on every push, free tier, managed Postgres |
| Frontend | Plain **HTML, CSS and JavaScript** | No build step. Supports light and dark mode |

---

## Project structure

```
url-shortener/
├── app/
│   ├── __init__.py          # Package version
│   ├── main.py              # App factory, wiring, lifespan (creates tables)
│   ├── config.py            # Settings from environment variables / .env
│   ├── database.py          # Engine, session factory, per-request session
│   ├── models.py            # ORM models: ShortURL, Click
│   ├── schemas.py           # Pydantic request/response models
│   ├── encoding.py          # Base62 + ID scrambling (short-code generation)
│   ├── services.py          # Business logic: create, resolve, stats, delete
│   ├── validators.py        # URL and alias validation, reserved words
│   ├── cache.py             # Thread-safe LRU cache with TTL
│   ├── rate_limit.py        # Token-bucket rate limiter
│   ├── routes/
│   │   ├── api.py           # /api/... JSON endpoints and /health
│   │   └── redirect.py      # GET /{code} redirect (registered last)
│   └── static/
│       └── index.html       # Web UI
├── tests/
│   ├── conftest.py          # Fixtures: isolated app + temp database per test
│   ├── test_api.py          # End-to-end API behaviour
│   ├── test_encoding.py     # Base62 and bijection proofs
│   ├── test_cache_and_limiter.py
│   └── test_config.py       # Settings: DB URL handling, Render URL fallback
├── docs/
│   └── DESIGN.md            # Design decisions in detail
├── .github/workflows/ci.yml # GitHub Actions pipeline
├── Dockerfile
├── docker-compose.yml
├── render.yaml              # One-click deployment to Render (app + Postgres)
├── requirements.txt         # Runtime dependencies
├── requirements-dev.txt     # + pytest, httpx, ruff
├── pyproject.toml           # Project metadata, pytest and ruff config
├── .env.example             # All settings with defaults
├── .gitignore
├── .dockerignore
└── LICENSE                  # MIT
```

---

## Getting started

### Option 1: Run locally with Python

Requires Python 3.11 or newer.

```bash
git clone https://github.com/<your-username>/url-shortener.git
cd url-shortener

python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate

pip install -r requirements-dev.txt
cp .env.example .env               # optional: adjust settings

uvicorn app.main:app --reload
```

Then open:
- **http://localhost:8000**: web UI
- **http://localhost:8000/docs**: interactive API docs

The SQLite database is created automatically at `data/urls.db`.

### Option 2: Run with Docker

```bash
docker compose up --build                      # SQLite on a named volume
docker compose --profile postgres up --build   # also starts PostgreSQL
```

To use PostgreSQL, switch the `DATABASE_URL` line in `docker-compose.yml` to the Postgres one.

---

## API reference

### Create a short link

`POST /api/shorten`

```bash
curl -X POST http://localhost:8000/api/shorten \
  -H "Content-Type: application/json" \
  -d '{
        "url": "https://github.com/fastapi/fastapi",
        "custom_alias": "fastapi-repo",
        "expires_at": "2027-01-01T00:00:00Z"
      }'
```

`custom_alias` and `expires_at` are optional.

**201 Created**
```json
{
  "short_code": "fastapi-repo",
  "short_url": "http://localhost:8000/fastapi-repo",
  "original_url": "https://github.com/fastapi/fastapi",
  "created_at": "2026-10-15T09:30:00.123456",
  "expires_at": "2027-01-01T00:00:00",
  "delete_token": "L3BIRMzk2Xh4N-rCPTQuWYnUTiyEnte3"
}
```

> Save the `delete_token`. It is shown only once and is the only way to delete the link.

Errors: `400` invalid URL, alias or expiry · `409` alias already taken · `429` rate limit exceeded (with a `Retry-After` header).

### Follow a short link

`GET /{code}` → `302 Found` with `Location: <original URL>`

`404` if the link doesn't exist or was deleted · `410` if it has expired.

### Get analytics

`GET /api/urls/{code}/stats`

```json
{
  "short_code": "W9eTrsF",
  "short_url": "http://localhost:8000/W9eTrsF",
  "original_url": "https://github.com/fastapi/fastapi",
  "created_at": "2026-10-15T09:30:00.123456",
  "expires_at": null,
  "is_expired": false,
  "total_clicks": 42,
  "last_accessed_at": "2026-10-15T11:02:13.000000",
  "clicks_last_7_days": [{ "date": "2026-10-15", "clicks": 30 }, "..."],
  "top_referrers": [{ "referrer": "https://news.ycombinator.com/", "clicks": 25 }]
}
```

### Delete a link

```bash
curl -X DELETE http://localhost:8000/api/urls/W9eTrsF \
  -H "X-Delete-Token: L3BIRMzk2Xh4N-rCPTQuWYnUTiyEnte3"
```

`204 No Content` on success · `403` missing or wrong token · `404` not found.

### Health check

`GET /health` → `{"status": "ok", "database": "ok", "version": "1.0.0"}`

All times are in **UTC**.

---

## Configuration

Settings are read from environment variables or a `.env` file. See [`.env.example`](.env.example).

| Variable | Default | Description |
|---|---|---|
| `BASE_URL` | `http://localhost:8000` | Public address used to build short links. On Render, `RENDER_EXTERNAL_URL` is used if this is unset |
| `DATABASE_URL` | `sqlite:///./data/urls.db` | SQLite or PostgreSQL. `postgres://` and `postgresql://` URLs from hosting providers work as-is |
| `CODE_LENGTH` | `7` | Length of generated codes (62⁷ ≈ 3.5 trillion codes) |
| `ID_SCRAMBLE_MULTIPLIER` | `2176477521981` | Scrambling constant. Odd, not divisible by 31. **Set once, never change** |
| `ID_SCRAMBLE_OFFSET` | `1094865271` | Scrambling constant. **Set once, never change** |
| `REDIRECT_STATUS_CODE` | `302` | `302`/`307` count every click. `301`/`308` let browsers cache |
| `CACHE_MAX_ITEMS` | `10000` | LRU cache size. `0` disables caching |
| `CACHE_TTL_SECONDS` | `3600` | How long a cached entry stays valid |
| `RATE_LIMIT_PER_MINUTE` | `30` | Link creations per client per minute. `0` disables |
| `TRUST_PROXY_HEADERS` | `false` | Use `X-Forwarded-For` for the client IP. Only behind a trusted proxy |
| `MAX_URL_LENGTH` | `2048` | Longest URL accepted |
| `MAX_EXPIRY_DAYS` | `365` | Furthest allowed expiry |

> Choose your own `ID_SCRAMBLE_*` values before the first deployment, so your codes differ from every other copy of this project.
>
> Blank variables are ignored, so an empty value falls back to the default.

---

## Running tests

```bash
pytest          # 51 tests, about 2 seconds
ruff check .    # lint
ruff format .   # format
```

Each test gets its own app instance and temporary SQLite database, so tests never share state.

To run the same suite against PostgreSQL, point `TEST_DATABASE_URL` at an **empty test database** (all its tables are dropped before each test):

```bash
TEST_DATABASE_URL=postgresql://user:pass@localhost:5432/shortener_test pytest
```

---

## Publishing to GitHub

```bash
cd url-shortener
git init
git add .
git commit -m "Initial commit: URL shortener service"
git branch -M main
git remote add origin https://github.com/<your-username>/url-shortener.git
git push -u origin main
```

The CI workflow runs automatically on every push and pull request. Remember to replace `<YOUR NAME>` in `LICENSE`.

---

## Deploying to Render

[Render](https://render.com) builds the Docker image straight from your GitHub repository and redeploys on every push. The included [`render.yaml`](render.yaml) Blueprint creates the web service **and** a PostgreSQL database in one go.

### Steps

1. **Push the project to GitHub** (see above).
2. **Sign up at [render.com](https://render.com)** with your GitHub account.
3. In the dashboard, click **New → Blueprint** and select your `url-shortener` repository.
4. Render reads `render.yaml` and shows what it will create: a web service and a database named `url-shortener-db`. It asks for `ID_SCRAMBLE_MULTIPLIER` and `ID_SCRAMBLE_OFFSET`: enter your own values (multiplier odd and not divisible by 31), or leave them blank to use the defaults.
5. Click **Apply**. The first build takes a few minutes.
6. Open `https://<your-service>.onrender.com`. Short links use that address automatically.

Check it worked: `https://<your-service>.onrender.com/health` should return `{"status":"ok","database":"ok",...}`.

### Free-tier limits to know about

| Limit | What it means for you |
|---|---|
| The service **sleeps after 15 minutes** without traffic | The next visit takes about a minute to wake it. Fine for a portfolio project |
| The **free database expires after 30 days** | After a 14-day grace period it is deleted with all links. Upgrade it, or use Neon (below) |
| The disk is wiped on restart | That's why SQLite isn't used on Render. Postgres keeps the data |
| 750 free instance hours and 5 GB outbound bandwidth per month | Plenty for a personal project |

### Keeping your data free and permanent: use Neon for the database

[Neon](https://neon.com) has a free Postgres plan with no expiry and no card required.

1. Create a project at neon.com and copy its **connection string** (`postgresql://...?sslmode=require`).
2. In `render.yaml`, delete the whole `databases:` block, and replace the `DATABASE_URL` entry with:
   ```yaml
      - key: DATABASE_URL
        sync: false
   ```
3. Push, create the Blueprint, and paste the Neon connection string when Render asks for `DATABASE_URL`.

The app accepts the string exactly as Neon gives it. Neon's database also sleeps after a few minutes idle, which adds a short delay to the first request.

### Custom domain (optional)

Add your domain under the service's **Settings → Custom Domains** in Render, then set `BASE_URL=https://your-domain` in **Environment**, so short links use it.

### Other hosts

The app is a standard Docker container that listens on `$PORT` (default 8000) and reads all settings from environment variables, so it runs on Railway, Fly.io, Google Cloud Run, or any VPS (`docker compose up -d`). Give it a `DATABASE_URL` for Postgres, set `BASE_URL`, and set `TRUST_PROXY_HEADERS=true` when it sits behind a proxy or load balancer.

---

## Roadmap

- User accounts and API keys, so people can list and manage their links
- Redis cache and rate limiter for running several app instances
- Alembic database migrations
- QR code generation for each link
- Malicious-URL screening (e.g. Google Safe Browsing)
- Analytics dashboard with country and device breakdowns

See [docs/DESIGN.md](docs/DESIGN.md#13-scaling-path) for how the design scales.

## License

[MIT](LICENSE)
