package dev.xplaytime.task

import com.velocitypowered.api.scheduler.ScheduledTask
import com.velocitypowered.api.scheduler.Scheduler
import dev.xplaytime.config.MysqlConfig
import dev.xplaytime.service.PlayTimeService
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

class ScheduledJobs(
    private val plugin: Any,
    private val scheduler: Scheduler,
    private val service: PlayTimeService,
    private val logger: Logger,
) {
    private val tasks = mutableListOf<ScheduledTask>()

    fun start(config: MysqlConfig) {
        stop()

        val flushSeconds = config.tracking.flushIntervalSeconds.coerceAtLeast(5)
        val flushTask = Runnable {
            val flushed = service.flush(
                checkpointOnline = config.tracking.onlineCheckpoint,
                skipIfEmpty = config.tracking.skipEmptyFlushes,
            )
            if (flushed > 0) {
                logger.debug("Flushed {} playtime delta row(s)", flushed)
            }
        }
        tasks += scheduler
            .buildTask(plugin, flushTask)
            .repeat(flushSeconds, TimeUnit.SECONDS)
            .schedule()

        val rollupMinutes = config.rollup.intervalMinutes.coerceAtLeast(1)
        val initialDelay = if (config.rollup.runOnStartup) {
            config.rollup.startupDelaySeconds.coerceAtLeast(1)
        } else {
            TimeUnit.MINUTES.toSeconds(rollupMinutes)
        }
        val rollupTask = Runnable {
            val rows = service.refreshRollup()
            logger.info("Website rollup refreshed ({} player row(s))", rows)
        }
        tasks += scheduler
            .buildTask(plugin, rollupTask)
            .delay(initialDelay, TimeUnit.SECONDS)
            .repeat(rollupMinutes, TimeUnit.MINUTES)
            .schedule()
    }

    fun stop() {
        tasks.forEach { it.cancel() }
        tasks.clear()
    }
}
