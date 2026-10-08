package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 热度快照的 SQL 访问层。 */
@Repository
public class MemeTrendRepository {

    private static final String DELTA_CTE = """
            WITH daily_delta AS (
                SELECT meme_id, snapshot_date,
                       GREATEST(page_views - LAG(page_views, 1, page_views) OVER (PARTITION BY meme_id ORDER BY snapshot_date), 0) AS view_delta,
                       GREATEST(likes - LAG(likes, 1, likes) OVER (PARTITION BY meme_id ORDER BY snapshot_date), 0) AS like_delta,
                       GREATEST(comments - LAG(comments, 1, comments) OVER (PARTITION BY meme_id ORDER BY snapshot_date), 0) AS comment_delta
                FROM meme_daily_snapshot
                WHERE snapshot_date BETWEEN ? AND ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public MemeTrendRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int capture(LocalDate snapshotDate) {
        return jdbcTemplate.update("""
                INSERT INTO meme_daily_snapshot (meme_id, snapshot_date, page_views, likes, comments, created_at)
                SELECT id, ?, GREATEST(COALESCE(page_views, 0), 0), GREATEST(COALESCE(likes, 0), 0),
                       GREATEST(COALESCE(comments, 0), 0), CURRENT_TIMESTAMP
                FROM meme WHERE status = 1
                ON DUPLICATE KEY UPDATE page_views = VALUES(page_views), likes = VALUES(likes),
                                        comments = VALUES(comments), created_at = CURRENT_TIMESTAMP
                """, snapshotDate);
    }

    public List<TrendRow> topMemes(LocalDate fromDate, LocalDate throughDate, int limit) {
        String sql = DELTA_CTE + """
                , scores AS (
                    SELECT meme_id, SUM(view_delta) AS views, SUM(like_delta) AS likes,
                           SUM(comment_delta) AS comments,
                           SUM(view_delta + like_delta * 4 + comment_delta * 6) AS score
                    FROM daily_delta WHERE snapshot_date >= ? GROUP BY meme_id
                    HAVING SUM(view_delta + like_delta * 4 + comment_delta * 6) > 0
                )
                SELECT m.id, m.name, m.image, MAX(s.views) AS views, MAX(s.likes) AS likes,
                       MAX(s.comments) AS comments, MAX(s.score) AS score,
                       GROUP_CONCAT(DISTINCT t.name ORDER BY t.name SEPARATOR ',') AS tags
                FROM scores s JOIN meme m ON m.id = s.meme_id AND m.status = 1
                LEFT JOIN meme_tag_relation tr ON tr.meme_id = m.id
                LEFT JOIN meme_tag t ON t.id = tr.meme_tag_id
                GROUP BY m.id, m.name, m.image
                ORDER BY score DESC, m.release_time DESC, m.id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TrendRow(
                rs.getLong("id"), rs.getString("name"), rs.getString("image"),
                rs.getLong("views"), rs.getLong("likes"), rs.getLong("comments"),
                rs.getLong("score"), rs.getString("tags")),
                fromDate.minusDays(1), throughDate, fromDate, limit);
    }

    public List<DailyRow> dailyTotals(LocalDate fromDate, LocalDate throughDate) {
        String sql = DELTA_CTE + """
                SELECT snapshot_date, SUM(view_delta) AS views, SUM(like_delta) AS likes,
                       SUM(comment_delta) AS comments,
                       SUM(view_delta + like_delta * 4 + comment_delta * 6) AS score
                FROM daily_delta WHERE snapshot_date >= ?
                GROUP BY snapshot_date ORDER BY snapshot_date
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DailyRow(
                rs.getDate("snapshot_date").toLocalDate(), rs.getLong("views"),
                rs.getLong("likes"), rs.getLong("comments"), rs.getLong("score")),
                fromDate.minusDays(1), throughDate, fromDate);
    }

    /** 汇总近 7 天公开梗在各标签下的互动增长。 */
    public List<TagTrendRow> weeklyTagScores(LocalDate fromDate, LocalDate throughDate) {
        String sql = DELTA_CTE + """
                , scores AS (
                    SELECT meme_id,
                           SUM(view_delta + like_delta * 4 + comment_delta * 6) AS score
                    FROM daily_delta
                    WHERE snapshot_date >= ?
                    GROUP BY meme_id
                    HAVING SUM(view_delta + like_delta * 4 + comment_delta * 6) > 0
                )
                SELECT t.id AS tag_id, t.name AS tag_name,
                       SUM(s.score) AS interaction_score,
                       CAST(t.related_quantity AS UNSIGNED) AS related_quantity
                FROM scores s
                JOIN meme m ON m.id = s.meme_id AND m.status = 1
                JOIN meme_tag_relation tr ON tr.meme_id = m.id
                JOIN meme_tag t ON t.id = tr.meme_tag_id
                GROUP BY t.id, t.name, t.related_quantity
                ORDER BY interaction_score DESC, related_quantity DESC, tag_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TagTrendRow(
                rs.getInt("tag_id"), rs.getString("tag_name"),
                rs.getLong("interaction_score"), rs.getLong("related_quantity")),
                fromDate.minusDays(1), throughDate, fromDate);
    }

    /** 选择仍有关联公开梗的最热门标签，供趋势历史不足时回退。 */
    public Optional<TagTrendRow> fallbackPopularPublicTag() {
        List<TagTrendRow> rows = jdbcTemplate.query("""
                SELECT t.id AS tag_id, t.name AS tag_name,
                       0 AS interaction_score,
                       CAST(t.related_quantity AS UNSIGNED) AS related_quantity
                FROM meme_tag t
                JOIN meme_tag_relation tr ON tr.meme_tag_id = t.id
                JOIN meme m ON m.id = tr.meme_id AND m.status = 1
                GROUP BY t.id, t.name, t.related_quantity
                ORDER BY related_quantity DESC, tag_id ASC
                LIMIT 1
                """, (rs, rowNum) -> new TagTrendRow(
                rs.getInt("tag_id"), rs.getString("tag_name"),
                rs.getLong("interaction_score"), rs.getLong("related_quantity")));
        return rows.stream().findFirst();
    }

    public Optional<WeeklyTopicRow> findWeeklyTopic(int isoYear, int isoWeek) {
        List<WeeklyTopicRow> rows = jdbcTemplate.query("""
                SELECT tag_id, tag_name, interaction_score, is_fallback
                FROM meme_weekly_topic
                WHERE iso_week_year = ? AND iso_week_number = ?
                """, (rs, rowNum) -> new WeeklyTopicRow(
                rs.getInt("tag_id"), rs.getString("tag_name"),
                rs.getLong("interaction_score"), rs.getBoolean("is_fallback")),
                isoYear, isoWeek);
        return rows.stream().findFirst();
    }

    /** 主键冲突时保留首个写入值；调用方随后读取数据库中的最终主题。 */
    public void insertWeeklyTopicIfAbsent(int isoYear, int isoWeek, TagTrendRow topic, boolean fallback) {
        jdbcTemplate.update("""
                INSERT INTO meme_weekly_topic
                    (iso_week_year, iso_week_number, tag_id, tag_name, interaction_score, is_fallback)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE created_at = meme_weekly_topic.created_at
                """, isoYear, isoWeek, topic.tagId(), topic.tagName(), topic.score(), fallback);
    }

    public record TrendRow(Long memeId, String name, String image, long views, long likes,
                           long comments, long score, String tags) {
    }

    public record DailyRow(LocalDate date, long views, long likes, long comments, long score) {
    }

    public record TagTrendRow(Integer tagId, String tagName, long score, long relatedQuantity) {
    }

    public record WeeklyTopicRow(Integer tagId, String tagName, long interactionScore, boolean fallback) {
    }
}
