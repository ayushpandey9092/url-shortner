package com.example.url_shortner.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the short_urls and clicks tables.
 */
@Repository
public class ShortUrlRepository {

    // The columns we read for one link. Every query that loads a link starts with this.
    private static final String SELECT_LINK =
            "SELECT id, original_url, short_code, delete_token_hash, is_active, click_count, "
                    + "expires_at, created_at FROM short_urls ";

    private final JdbcTemplate jdbc;

    public ShortUrlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a link that never expires and returns its generated ID. */
    public long save(String originalUrl, String deleteTokenHash) {
        return save(originalUrl, deleteTokenHash, null);
    }

    /**
     * Inserts a new link and returns its generated ID.
     *
     * <p>@Transactional makes the INSERT and the last_insert_rowid() query run on the same
     * connection. SQLite reports the last ID per connection, so without this, a pooled
     * connection could return another request's ID.
     */
    @Transactional
    public long save(String originalUrl, String deleteTokenHash, String expiresAt) {
        jdbc.update(
                "INSERT INTO short_urls (original_url, delete_token_hash, expires_at) "
                        + "VALUES (?, ?, ?)",
                originalUrl, deleteTokenHash, expiresAt);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    /** Writes the short code into an existing row. */
    public void updateShortCode(long id, String shortCode) {
        jdbc.update("UPDATE short_urls SET short_code = ? WHERE id = ?", shortCode, id);
    }

    /** Returns the link with this short code, or empty if there isn't one. */
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        return findOne(SELECT_LINK + "WHERE short_code = ?", shortCode);
    }

    /** Returns the link with this ID, or empty if there isn't one. */
    public Optional<ShortUrl> findById(long id) {
        return findOne(SELECT_LINK + "WHERE id = ?", id);
    }

    /** Marks a link as deleted. The row stays, so the history is kept. */
    public void deactivate(long id) {
        jdbc.update(
                "UPDATE short_urls SET is_active = 0, deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                id);
    }

    /**
     * Records one click: adds a row to clicks and adds 1 to the link's total.
     * @Transactional makes both changes succeed or fail together.
     */
    @Transactional
    public void recordClick(long shortUrlId, String referrer, String userAgent) {
        jdbc.update(
                "INSERT INTO clicks (short_url_id, referrer, user_agent) VALUES (?, ?, ?)",
                shortUrlId, referrer, userAgent);
        jdbc.update(
                "UPDATE short_urls SET click_count = click_count + 1 WHERE id = ?",
                shortUrlId);
    }

    /** The time of the most recent click, or null if nobody has clicked yet. */
    public String lastClickTime(long shortUrlId) {
        return jdbc.queryForObject(
                "SELECT MAX(clicked_at) FROM clicks WHERE short_url_id = ?",
                String.class, shortUrlId);
    }

    /** Click counts per day for the last 7 days. Each row has "day" and "clicks". */
    public List<Map<String, Object>> clicksPerDayLastWeek(long shortUrlId) {
        return jdbc.queryForList(
                "SELECT date(clicked_at) AS day, COUNT(*) AS clicks FROM clicks "
                        + "WHERE short_url_id = ? AND clicked_at >= datetime('now', '-7 days') "
                        + "GROUP BY day ORDER BY day",
                shortUrlId);
    }

    /** The 5 referrers with the most clicks. Each row has "referrer" and "clicks". */
    public List<Map<String, Object>> topReferrers(long shortUrlId) {
        return jdbc.queryForList(
                "SELECT COALESCE(referrer, 'direct') AS referrer, COUNT(*) AS clicks "
                        + "FROM clicks WHERE short_url_id = ? "
                        + "GROUP BY COALESCE(referrer, 'direct') ORDER BY clicks DESC LIMIT 5",
                shortUrlId);
    }

    // Runs a query that should return at most one link.
    private Optional<ShortUrl> findOne(String sql, Object argument) {
        List<ShortUrl> rows = jdbc.query(sql, new ShortUrlRowMapper(), argument);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * Turns one row of the short_urls table into a ShortUrl.
     * JdbcTemplate calls mapRow once for each row the query returns.
     */
    private static class ShortUrlRowMapper implements RowMapper<ShortUrl> {

        @Override
        public ShortUrl mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ShortUrl(
                    rs.getLong("id"),
                    rs.getString("original_url"),
                    rs.getString("short_code"),
                    rs.getString("delete_token_hash"),
                    rs.getBoolean("is_active"),
                    rs.getLong("click_count"),
                    rs.getString("expires_at"),
                    rs.getString("created_at"));
        }
    }
}
