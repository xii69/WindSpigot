package dev.xplaytime.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.xplaytime.config.MysqlConfig
import org.slf4j.Logger
import java.sql.Connection
import javax.sql.DataSource

class Database(
    private val logger: Logger,
) : AutoCloseable {
    @Volatile
    private var dataSource: HikariDataSource? = null

    fun connect(config: MysqlConfig) {
        close()
        // Explicit load so Shadow-relocated jars still register with DriverManager.
        Class.forName("org.mariadb.jdbc.Driver")
        val hikari = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl()
            username = config.username
            password = config.password
            driverClassName = "org.mariadb.jdbc.Driver"
            poolName = "xPlayTime"
            maximumPoolSize = config.pool.maximumPoolSize
            minimumIdle = config.pool.minimumIdle
            connectionTimeout = config.pool.connectionTimeoutMs
            idleTimeout = config.pool.idleTimeoutMs
            maxLifetime = config.pool.maxLifetimeMs
            isAutoCommit = true
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "64")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
        }
        dataSource = HikariDataSource(hikari)
        migrate()
        logger.info("Connected to MariaDB/MySQL at {}:{}", config.host, config.port)
    }

    fun dataSource(): DataSource =
        dataSource ?: error("Database is not connected")

    fun connection(): Connection = dataSource().connection

    private fun migrate() {
        connection().use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS xplaytime_players (
                        uuid BINARY(16) NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        total_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid),
                        KEY idx_xplaytime_players_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.trimIndent(),
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS xplaytime_daily (
                        uuid BINARY(16) NOT NULL,
                        day DATE NOT NULL,
                        play_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, day),
                        KEY idx_xplaytime_daily_day (day)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.trimIndent(),
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS xplaytime_website_rollup (
                        uuid BINARY(16) NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        daily_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        weekly_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        monthly_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        yearly_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        total_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid),
                        KEY idx_xplaytime_rollup_username (username),
                        KEY idx_xplaytime_rollup_total (total_ms)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.trimIndent(),
                )
            }
        }
    }

    override fun close() {
        dataSource?.close()
        dataSource = null
    }
}
