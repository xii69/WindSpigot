package dev.xplaytime.api

import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public API other Velocity plugins can depend on.
 *
 * Acquire an instance from the Velocity plugin manager:
 * ```
 * val api = server.pluginManager
 *     .getPlugin("xplaytime")
 *     .flatMap { it.instance }
 *     .filter { it is PlayTimeApiProvider }
 *     .map { (it as PlayTimeApiProvider).api }
 *     .orElse(null)
 * ```
 *
 * Or cast the plugin instance if you shade/compile against this module:
 * ```
 * val plugin = server.pluginManager.getPlugin("xplaytime").flatMap { it.instance }.orElse(null)
 * val api = (plugin as? PlayTimeApiProvider)?.api
 * ```
 *
 * All methods are non-blocking and complete on the plugin async executor.
 * Online-session accrual is included in returned durations.
 */
interface PlayTimeApi {
    /** Overall playtime for [uuid], including the active session if online. */
    fun getPlayTime(uuid: UUID): CompletableFuture<Duration>

    /** Playtime for [uuid] within [period]. */
    fun getPlayTime(uuid: UUID, period: PlayTimePeriod): CompletableFuture<Duration>

    /** Overall playtime looked up by last-known username (case-insensitive). */
    fun getPlayTimeByName(username: String): CompletableFuture<Optional<Pair<UUID, Duration>>>

    /** Period playtime looked up by username. */
    fun getPlayTimeByName(username: String, period: PlayTimePeriod): CompletableFuture<Optional<Pair<UUID, Duration>>>

    /** Wipe stored playtime for [uuid] (daily rows + totals + rollup). */
    fun clearPlayTime(uuid: UUID): CompletableFuture<Boolean>

    /** Wipe stored playtime for a username if known. */
    fun clearPlayTimeByName(username: String): CompletableFuture<Boolean>
}

/** Marker implemented by the plugin main class so dependents can resolve [PlayTimeApi]. */
interface PlayTimeApiProvider {
    val api: PlayTimeApi
}
