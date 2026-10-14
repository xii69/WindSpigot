package dev.xplaytime.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory session accrual with dirty deltas ready for batched DB flush.
 * Hot path (join/leave/tick) never touches JDBC.
 */
class SessionTracker {
    private data class Session(
        val username: String,
        val startedAtMs: Long,
        @Volatile var lastCheckpointMs: Long,
    )

    private data class DirtyKey(val uuid: UUID, val day: LocalDate)

    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val dirty = ConcurrentHashMap<DirtyKey, AtomicLong>()
    private val dirtyUsernames = ConcurrentHashMap<UUID, String>()

    fun startSession(uuid: UUID, username: String, nowMs: Long = System.currentTimeMillis()) {
        sessions[uuid] = Session(username = username, startedAtMs = nowMs, lastCheckpointMs = nowMs)
        dirtyUsernames[uuid] = username.lowercase()
    }

    fun endSession(uuid: UUID, nowMs: Long = System.currentTimeMillis()): Long {
        val session = sessions.remove(uuid) ?: return 0L
        val delta = (nowMs - session.lastCheckpointMs).coerceAtLeast(0L)
        if (delta > 0L) {
            accrue(uuid, session.username, delta, nowMs)
        }
        return delta
    }

    /** Checkpoint all online sessions into the dirty map (for periodic flush). */
    fun checkpointOnline(nowMs: Long = System.currentTimeMillis()): Int {
        var count = 0
        for ((uuid, session) in sessions) {
            val delta = (nowMs - session.lastCheckpointMs).coerceAtLeast(0L)
            if (delta <= 0L) continue
            session.lastCheckpointMs = nowMs
            accrue(uuid, session.username, delta, nowMs)
            count++
        }
        return count
    }

    fun liveAccruedMs(uuid: UUID, nowMs: Long = System.currentTimeMillis()): Long {
        val session = sessions[uuid] ?: return 0L
        return (nowMs - session.lastCheckpointMs).coerceAtLeast(0L)
    }

    fun pendingDirtyMs(uuid: UUID): Long {
        var total = 0L
        for ((key, value) in dirty) {
            if (key.uuid == uuid) total += value.get()
        }
        return total
    }

    fun hasDirty(): Boolean = dirty.isNotEmpty()

    fun onlineCount(): Int = sessions.size

    fun drainDirty(): List<dev.xplaytime.db.FlushDelta> {
        if (dirty.isEmpty()) return emptyList()
        val snapshot = ArrayList<dev.xplaytime.db.FlushDelta>(dirty.size)
        val keys = dirty.keys.toList()
        for (key in keys) {
            val amount = dirty.remove(key)?.get() ?: continue
            if (amount <= 0L) continue
            val username = dirtyUsernames[key.uuid] ?: "unknown"
            snapshot.add(
                dev.xplaytime.db.FlushDelta(
                    uuid = key.uuid,
                    username = username,
                    deltaMs = amount,
                    day = key.day,
                ),
            )
        }
        return snapshot
    }

    fun clearMemory(uuid: UUID) {
        sessions.remove(uuid)
        dirty.keys.filter { it.uuid == uuid }.forEach { dirty.remove(it) }
        dirtyUsernames.remove(uuid)
    }

    fun resetSessionClock(uuid: UUID, nowMs: Long = System.currentTimeMillis()) {
        sessions.computeIfPresent(uuid) { _, session ->
            session.copy(startedAtMs = nowMs, lastCheckpointMs = nowMs)
        }
    }

    private fun accrue(uuid: UUID, username: String, deltaMs: Long, nowMs: Long) {
        dirtyUsernames[uuid] = username.lowercase()
        val day = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        dirty.computeIfAbsent(DirtyKey(uuid, day)) { AtomicLong(0L) }.addAndGet(deltaMs)
    }
}
