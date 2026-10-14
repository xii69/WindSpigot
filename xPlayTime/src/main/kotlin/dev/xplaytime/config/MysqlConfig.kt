package dev.xplaytime.config

import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class PoolConfig(
    val maximumPoolSize: Int = 4,
    val minimumIdle: Int = 1,
    val connectionTimeoutMs: Long = 10_000,
    val idleTimeoutMs: Long = 600_000,
    val maxLifetimeMs: Long = 1_800_000,
)

data class TrackingConfig(
    val flushIntervalSeconds: Long = 60,
    val skipEmptyFlushes: Boolean = true,
    val onlineCheckpoint: Boolean = true,
)

data class RollupConfig(
    val intervalMinutes: Long = 30,
    val runOnStartup: Boolean = true,
    val startupDelaySeconds: Long = 15,
)

data class MysqlConfig(
    val host: String = "127.0.0.1",
    val port: Int = 3306,
    val database: String = "xplaytime",
    val username: String = "xplaytime",
    val password: String = "change-me",
    val jdbcParams: String = "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
    val pool: PoolConfig = PoolConfig(),
    val tracking: TrackingConfig = TrackingConfig(),
    val rollup: RollupConfig = RollupConfig(),
) {
    fun jdbcUrl(): String {
        val params = jdbcParams.trim().trimStart('?')
        val base = "jdbc:mariadb://$host:$port/$database"
        return if (params.isEmpty()) base else "$base?$params"
    }
}

object MysqlConfigLoader {
    private const val FILE_NAME = "mysql.yml"

    fun loadOrCreate(dataDirectory: Path): MysqlConfig {
        dataDirectory.createDirectories()
        val file = dataDirectory.resolve(FILE_NAME)
        if (!file.exists()) {
            copyDefault(file)
        }
        return parse(file)
    }

    fun reload(dataDirectory: Path): MysqlConfig {
        val file = dataDirectory.resolve(FILE_NAME)
        if (!file.exists()) {
            copyDefault(file)
        }
        return parse(file)
    }

    private fun copyDefault(target: Path) {
        val resource = MysqlConfigLoader::class.java.classLoader.getResourceAsStream(FILE_NAME)
            ?: error("Missing bundled $FILE_NAME")
        resource.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(file: Path): MysqlConfig {
        val yaml = Yaml()
        val root = file.inputStream().use { yaml.load<Map<String, Any?>>(it) } ?: emptyMap()
        val poolMap = root["pool"] as? Map<String, Any?> ?: emptyMap()
        val trackingMap = root["tracking"] as? Map<String, Any?> ?: emptyMap()
        val rollupMap = root["rollup"] as? Map<String, Any?> ?: emptyMap()

        return MysqlConfig(
            host = root.string("host", "127.0.0.1"),
            port = root.int("port", 3306),
            database = root.string("database", "xplaytime"),
            username = root.string("username", "xplaytime"),
            password = root.string("password", "change-me"),
            jdbcParams = root.string("jdbc-params", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"),
            pool = PoolConfig(
                maximumPoolSize = poolMap.int("maximum-pool-size", 4),
                minimumIdle = poolMap.int("minimum-idle", 1),
                connectionTimeoutMs = poolMap.long("connection-timeout-ms", 10_000),
                idleTimeoutMs = poolMap.long("idle-timeout-ms", 600_000),
                maxLifetimeMs = poolMap.long("max-lifetime-ms", 1_800_000),
            ),
            tracking = TrackingConfig(
                flushIntervalSeconds = trackingMap.long("flush-interval-seconds", 60),
                skipEmptyFlushes = trackingMap.bool("skip-empty-flushes", true),
                onlineCheckpoint = trackingMap.bool("online-checkpoint", true),
            ),
            rollup = RollupConfig(
                intervalMinutes = rollupMap.long("interval-minutes", 30),
                runOnStartup = rollupMap.bool("run-on-startup", true),
                startupDelaySeconds = rollupMap.long("startup-delay-seconds", 15),
            ),
        )
    }

    private fun Map<String, Any?>.string(key: String, default: String): String =
        this[key]?.toString()?.takeIf { it.isNotBlank() } ?: default

    private fun Map<String, Any?>.int(key: String, default: Int): Int =
        when (val v = this[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.long(key: String, default: Long): Long =
        when (val v = this[key]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean =
        when (val v = this[key]) {
            is Boolean -> v
            is String -> v.equals("true", true)
            else -> default
        }
}
