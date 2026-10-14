package dev.xplaytime.db

import java.sql.Date
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

data class PlayerPlayTimeRow(
    val uuid: UUID,
    val username: String,
    val totalMs: Long,
)

data class FlushDelta(
    val uuid: UUID,
    val username: String,
    val deltaMs: Long,
    val day: LocalDate,
)

class PlayTimeRepository(
    private val dataSource: DataSource,
) {
    fun findUuidByUsername(username: String): UUID? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT uuid FROM xplaytime_players WHERE username = ? LIMIT 1",
            ).use { ps ->
                ps.setString(1, username)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) UuidBinary.fromBytes(rs.getBytes("uuid")) else null
                }
            }
        }
    }

    /** Case-insensitive username lookup via LOWER — keep usernames lowercase on write. */
    fun findByUsernameIgnoreCase(username: String): PlayerPlayTimeRow? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT uuid, username, total_ms
                FROM xplaytime_players
                WHERE username = ?
                LIMIT 1
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, username.lowercase())
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return PlayerPlayTimeRow(
                        uuid = UuidBinary.fromBytes(rs.getBytes("uuid")),
                        username = rs.getString("username"),
                        totalMs = rs.getLong("total_ms"),
                    )
                }
            }
        }
    }

    fun getTotalMs(uuid: UUID): Long {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT total_ms FROM xplaytime_players WHERE uuid = ?",
            ).use { ps ->
                ps.setBytes(1, UuidBinary.toBytes(uuid))
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getLong("total_ms") else 0L
                }
            }
        }
    }

    fun getPeriodMs(uuid: UUID, fromInclusive: LocalDate, toInclusive: LocalDate): Long {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT COALESCE(SUM(play_ms), 0) AS total
                FROM xplaytime_daily
                WHERE uuid = ? AND day >= ? AND day <= ?
                """.trimIndent(),
            ).use { ps ->
                ps.setBytes(1, UuidBinary.toBytes(uuid))
                ps.setDate(2, Date.valueOf(fromInclusive))
                ps.setDate(3, Date.valueOf(toInclusive))
                ps.executeQuery().use { rs ->
                    rs.next()
                    return rs.getLong("total")
                }
            }
        }
    }

    /**
     * Batch upsert player totals + daily buckets in one connection / transaction.
     * Designed for rewriteBatchedStatements=true.
     */
    fun flushDeltas(deltas: Collection<FlushDelta>) {
        if (deltas.isEmpty()) return

        dataSource.connection.use { conn ->
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    INSERT INTO xplaytime_players (uuid, username, total_ms, last_seen)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        username = VALUES(username),
                        total_ms = total_ms + VALUES(total_ms),
                        last_seen = CURRENT_TIMESTAMP
                    """.trimIndent(),
                ).use { playerPs ->
                    conn.prepareStatement(
                        """
                        INSERT INTO xplaytime_daily (uuid, day, play_ms)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            play_ms = play_ms + VALUES(play_ms)
                        """.trimIndent(),
                    ).use { dailyPs ->
                        for (delta in deltas) {
                            if (delta.deltaMs <= 0L) continue
                            val bytes = UuidBinary.toBytes(delta.uuid)
                            playerPs.setBytes(1, bytes)
                            playerPs.setString(2, delta.username.lowercase())
                            playerPs.setLong(3, delta.deltaMs)
                            playerPs.addBatch()

                            dailyPs.setBytes(1, bytes)
                            dailyPs.setDate(2, Date.valueOf(delta.day))
                            dailyPs.setLong(3, delta.deltaMs)
                            dailyPs.addBatch()
                        }
                        playerPs.executeBatch()
                        dailyPs.executeBatch()
                    }
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            } finally {
                conn.autoCommit = previousAutoCommit
            }
        }
    }

    fun clearPlayer(uuid: UUID) {
        dataSource.connection.use { conn ->
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val bytes = UuidBinary.toBytes(uuid)
                conn.prepareStatement("DELETE FROM xplaytime_daily WHERE uuid = ?").use {
                    it.setBytes(1, bytes)
                    it.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM xplaytime_website_rollup WHERE uuid = ?").use {
                    it.setBytes(1, bytes)
                    it.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM xplaytime_players WHERE uuid = ?").use {
                    it.setBytes(1, bytes)
                    it.executeUpdate()
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            } finally {
                conn.autoCommit = previousAutoCommit
            }
        }
    }

    /**
     * Rebuild website rollup from daily + player totals in a single set-based statement.
     * Avoids per-player round trips.
     */
    fun refreshWebsiteRollup(): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO xplaytime_website_rollup (
                    uuid, username, daily_ms, weekly_ms, monthly_ms, yearly_ms, total_ms, updated_at
                )
                SELECT
                    p.uuid,
                    p.username,
                    COALESCE(SUM(CASE WHEN d.day = CURDATE() THEN d.play_ms ELSE 0 END), 0) AS daily_ms,
                    COALESCE(SUM(CASE WHEN d.day >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) THEN d.play_ms ELSE 0 END), 0) AS weekly_ms,
                    COALESCE(SUM(CASE WHEN d.day >= DATE_SUB(CURDATE(), INTERVAL 29 DAY) THEN d.play_ms ELSE 0 END), 0) AS monthly_ms,
                    COALESCE(SUM(CASE WHEN d.day >= DATE_SUB(CURDATE(), INTERVAL 364 DAY) THEN d.play_ms ELSE 0 END), 0) AS yearly_ms,
                    p.total_ms,
                    CURRENT_TIMESTAMP
                FROM xplaytime_players p
                LEFT JOIN xplaytime_daily d ON d.uuid = p.uuid
                    AND d.day >= DATE_SUB(CURDATE(), INTERVAL 364 DAY)
                GROUP BY p.uuid, p.username, p.total_ms
                ON DUPLICATE KEY UPDATE
                    username = VALUES(username),
                    daily_ms = VALUES(daily_ms),
                    weekly_ms = VALUES(weekly_ms),
                    monthly_ms = VALUES(monthly_ms),
                    yearly_ms = VALUES(yearly_ms),
                    total_ms = VALUES(total_ms),
                    updated_at = CURRENT_TIMESTAMP
                """.trimIndent(),
            ).use { ps ->
                return ps.executeUpdate()
            }
        }
    }
}
