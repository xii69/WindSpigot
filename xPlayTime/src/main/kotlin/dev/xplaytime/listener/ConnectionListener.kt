package dev.xplaytime.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import dev.xplaytime.service.SessionTracker
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Starts accrual as early as [LoginEvent] (proxy auth success) so playtime still
 * counts when no backend server is available. Later events are idempotent.
 */
class ConnectionListener(
    private val sessions: SessionTracker,
    private val logger: Logger,
) {
    private val started = ConcurrentHashMap.newKeySet<UUID>()

    @Subscribe
    fun onLogin(event: LoginEvent) {
        val player = event.player
        startIfNeeded(player.uniqueId, player.username, "LoginEvent")
    }

    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        startIfNeeded(player.uniqueId, player.username, "PostLoginEvent")
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val player = event.player
        startIfNeeded(player.uniqueId, player.username, "ServerConnectedEvent")
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId
        started.remove(uuid)
        val delta = sessions.endSession(uuid)
        if (delta > 0L) {
            logger.debug("Session end for {}: +{}ms pending flush", event.player.username, delta)
        }
    }

    private fun startIfNeeded(uuid: UUID, username: String, source: String) {
        if (started.add(uuid)) {
            sessions.startSession(uuid, username)
            logger.debug("Session start for {} via {}", username, source)
        }
    }
}
