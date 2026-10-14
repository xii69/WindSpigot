package dev.xplaytime

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import dev.xplaytime.api.PlayTimeApi
import dev.xplaytime.api.PlayTimeApiProvider
import dev.xplaytime.command.PlayTimeCommand
import dev.xplaytime.config.MysqlConfig
import dev.xplaytime.config.MysqlConfigLoader
import dev.xplaytime.db.Database
import dev.xplaytime.db.PlayTimeRepository
import dev.xplaytime.listener.ConnectionListener
import dev.xplaytime.service.PlayTimeService
import dev.xplaytime.service.SessionTracker
import dev.xplaytime.task.ScheduledJobs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Plugin(
    id = "xplaytime",
    name = "xPlayTime",
    version = "1.0.0",
    description = "Low-overhead player playtime tracking for Velocity with MariaDB storage",
    authors = ["xPlayTime"],
)
class XPlayTimePlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path,
) : PlayTimeApiProvider {
    private val database = Database(logger)
    private val sessions = SessionTracker()
    private var executor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "xPlayTime-worker").apply { isDaemon = true }
    }
    private lateinit var service: PlayTimeService
    private var jobs: ScheduledJobs? = null
    private var config: MysqlConfig = MysqlConfig()

    override val api: PlayTimeApi
        get() = service

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        try {
            config = MysqlConfigLoader.loadOrCreate(dataDirectory)
            database.connect(config)
            service = PlayTimeService(
                repository = PlayTimeRepository(database.dataSource()),
                sessions = sessions,
                executor = executor,
                logger = logger,
            )
            jobs = ScheduledJobs(this, server.scheduler, service, logger).also { it.start(config) }

            server.eventManager.register(this, ConnectionListener(sessions, logger))
            registerCommands()

            logger.info("xPlayTime enabled (flush={}s, rollup={}m)",
                config.tracking.flushIntervalSeconds,
                config.rollup.intervalMinutes,
            )
        } catch (ex: Exception) {
            logger.error("Failed to enable xPlayTime — commands will not be registered", ex)
        }
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        try {
            jobs?.stop()
            if (::service.isInitialized) {
                service.flush(checkpointOnline = true, skipIfEmpty = false)
            }
        } catch (ex: Exception) {
            logger.error("Error during playtime flush on shutdown", ex)
        } finally {
            database.close()
            executor.shutdown()
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun registerCommands() {
        val meta = server.commandManager.metaBuilder("playtime")
            .plugin(this)
            .build()
        val command = PlayTimeCommand(server, service) { source ->
            reload(source)
        }.create()
        server.commandManager.register(meta, command)
    }

    private fun reload(source: com.velocitypowered.api.command.CommandSource) {
        try {
            config = MysqlConfigLoader.reload(dataDirectory)
            database.connect(config)
            service = PlayTimeService(
                repository = PlayTimeRepository(database.dataSource()),
                sessions = sessions,
                executor = executor,
                logger = logger,
            )
            jobs?.stop()
            jobs = ScheduledJobs(this, server.scheduler, service, logger).also { it.start(config) }
            source.sendMessage(Component.text("xPlayTime config reloaded.", NamedTextColor.GREEN))
            logger.info("xPlayTime reloaded")
        } catch (ex: Exception) {
            logger.error("Reload failed", ex)
            source.sendMessage(Component.text("Reload failed: ${ex.message}", NamedTextColor.RED))
        }
    }
}
