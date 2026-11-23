CREATE TABLE IF NOT EXISTS short_urls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    original_url TEXT NOT NULL,
    short_code TEXT UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    delete_token_hash TEXT NOT NULL,
    is_active BOOLEAN  NOT NULL DEFAULT 1,
    deleted_at TIMESTAMP,
    click_count INTEGER NOT NULL DEFAULT 0,
    expires_at TEXT
);

CREATE TABLE IF NOT EXISTS clicks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    short_url_id INTEGER NOT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    referrer TEXT,
    user_agent TEXT,
    FOREIGN KEY (short_url_id) REFERENCES short_urls(id)
);

CREATE INDEX IF NOT EXISTS idx_clicks_link_time ON clicks (short_url_id, clicked_at);
