package dev.xplaytime.service

import dev.xplaytime.api.PlayTimeApi
import dev.xplaytime.api.PlayTimePeriod
import dev.xplaytime.db.PlayTimeRepository
import org.slf4j.Logger
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class PlayTimeService(
    private val repository: PlayTimeRepository,
    private val sessions: SessionTracker,
    private val executor: ExecutorService,
    private val logger: Logger,
) : PlayTimeApi {
    private val flushing = AtomicBoolean(false)

    override fun getPlayTime(uuid: UUID): CompletableFuture<Duration> =
        getPlayTime(uuid, PlayTimePeriod.TOTAL)

    override fun getPlayTime(uuid: UUID, period: PlayTimePeriod): CompletableFuture<Duration> =
        supplyAsync {
            Duration.ofMillis(resolveMs(uuid, period))
        }

    override fun getPlayTimeByName(username: String): CompletableFuture<Optional<Pair<UUID, Duration>>> =
        getPlayTimeByName(username, PlayTimePeriod.TOTAL)

    override fun getPlayTimeByName(
        username: String,
        period: PlayTimePeriod,
    ): CompletableFuture<Optional<Pair<UUID, Duration>>> =
        supplyAsync {
            val row = repository.findByUsernameIgnoreCase(username)
                ?: return@supplyAsync Optional.empty()
            val ms = resolveMs(row.uuid, period)
            Optional.of(row.uuid to Duration.ofMillis(ms))
        }

    override fun clearPlayTime(uuid: UUID): CompletableFuture<Boolean> =
        supplyAsync {
            sessions.clearMemory(uuid)
            repository.clearPlayer(uuid)
            true
        }

    override fun clearPlayTimeByName(username: String): CompletableFuture<Boolean> =
        supplyAsync {
            val row = repository.findByUsernameIgnoreCase(username) ?: return@supplyAsync false
            sessions.clearMemory(row.uuid)
            repository.clearPlayer(row.uuid)
            true
        }

    fun flush(checkpointOnline: Boolean, skipIfEmpty: Boolean): Int {
        if (!flushing.compareAndSet(false, true)) return 0
        try {
            if (checkpointOnline) {
                sessions.checkpointOnline()
            }
            if (skipIfEmpty && !sessions.hasDirty()) return 0
            val deltas = sessions.drainDirty()
            if (deltas.isEmpty()) return 0
            repository.flushDeltas(deltas)
            return deltas.size
        } catch (ex: Exception) {
            logger.error("Failed to flush playtime deltas", ex)
            // Re-queue lost deltas is hard after drain; prefer checkpoint cadence over perfect durability.
            return 0
        } finally {
            flushing.set(false)
        }
    }

    fun refreshRollup(): Int =
        try {
            // Push pending dirty first so the website sees fresh totals.
            flush(checkpointOnline = true, skipIfEmpty = true)
            repository.refreshWebsiteRollup()
        } catch (ex: Exception) {
            logger.error("Failed to refresh website rollup", ex)
            0
        }

    private fun resolveMs(uuid: UUID, period: PlayTimePeriod): Long {
        val live = sessions.liveAccruedMs(uuid) + sessions.pendingDirtyMs(uuid)
        return when (period) {
            PlayTimePeriod.TOTAL -> repository.getTotalMs(uuid) + live
            else -> {
                val today = LocalDate.now(ZoneOffset.UTC)
                val from = when (period) {
                    PlayTimePeriod.DAILY -> today
                    PlayTimePeriod.WEEKLY -> today.minusDays(6)
                    PlayTimePeriod.MONTHLY -> today.minusDays(29)
                    PlayTimePeriod.YEARLY -> today.minusDays(364)
                    PlayTimePeriod.TOTAL -> today
                }
                repository.getPeriodMs(uuid, from, today) + live
            }
        }
    }

    private fun <T> supplyAsync(block: () -> T): CompletableFuture<T> =
        CompletableFuture.supplyAsync(block, executor)
}
