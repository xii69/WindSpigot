package dev.xplaytime.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import dev.xplaytime.api.PlayTimePeriod
import dev.xplaytime.service.PlayTimeService
import dev.xplaytime.util.DurationFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.concurrent.CompletableFuture

class PlayTimeCommand(
    private val server: ProxyServer,
    private val service: PlayTimeService,
    private val reloadAction: (CommandSource) -> Unit,
) {
    fun create(): BrigadierCommand {
        val root = LiteralArgumentBuilder.literal<CommandSource>("playtime")
            .requires { it.hasPermission("xplaytime.use") }
            .executes { ctx -> selfTotal(ctx) }
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("get")
                    .requires { it.hasPermission("xplaytime.get") }
                    .then(
                        RequiredArgumentBuilder.argument<CommandSource, String>("username", StringArgumentType.word())
                            .suggests { _, builder -> suggestPlayers(builder) }
                            .executes { ctx -> getOther(ctx) },
                    ),
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("clear")
                    .requires { it.hasPermission("xplaytime.clear") }
                    .then(
                        RequiredArgumentBuilder.argument<CommandSource, String>("username", StringArgumentType.word())
                            .suggests { _, builder -> suggestPlayers(builder) }
                            .executes { ctx -> clear(ctx) },
                    ),
            )
            .then(periodLiteral("daily", PlayTimePeriod.DAILY))
            .then(periodLiteral("weekly", PlayTimePeriod.WEEKLY))
            .then(periodLiteral("monthly", PlayTimePeriod.MONTHLY))
            .then(periodLiteral("yearly", PlayTimePeriod.YEARLY))
            .then(periodLiteral("total", PlayTimePeriod.TOTAL))
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("reload")
                    .requires { it.hasPermission("xplaytime.admin") }
                    .executes { ctx ->
                        reloadAction(ctx.source)
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("rollup")
                    .requires { it.hasPermission("xplaytime.admin") }
                    .executes { ctx ->
                        ctx.source.sendMessage(Component.text("Refreshing website rollup…", NamedTextColor.YELLOW))
                        CompletableFuture.runAsync {
                            val rows = service.refreshRollup()
                            ctx.source.sendMessage(
                                Component.text("Rollup complete ($rows rows).", NamedTextColor.GREEN),
                            )
                        }
                        Command.SINGLE_SUCCESS
                    },
            )

        return BrigadierCommand(root)
    }

    private fun periodLiteral(name: String, period: PlayTimePeriod) =
        LiteralArgumentBuilder.literal<CommandSource>(name)
            .requires { it.hasPermission("xplaytime.use") }
            .executes { ctx -> selfPeriod(ctx, period) }

    private fun selfTotal(ctx: CommandContext<CommandSource>): Int {
        val player = ctx.source as? Player
        if (player == null) {
            ctx.source.sendMessage(Component.text("Players only. Use /playtime get <username>.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }
        service.getPlayTime(player.uniqueId).thenAccept { duration ->
            player.sendMessage(
                Component.text("Your playtime: ", NamedTextColor.GOLD)
                    .append(Component.text(DurationFormat.pretty(duration), NamedTextColor.YELLOW)),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun selfPeriod(ctx: CommandContext<CommandSource>, period: PlayTimePeriod): Int {
        val player = ctx.source as? Player
        if (player == null) {
            ctx.source.sendMessage(Component.text("Players only.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }
        service.getPlayTime(player.uniqueId, period).thenAccept { duration ->
            player.sendMessage(
                Component.text("Your ${period.name.lowercase()} playtime: ", NamedTextColor.GOLD)
                    .append(Component.text(DurationFormat.pretty(duration), NamedTextColor.YELLOW)),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun getOther(ctx: CommandContext<CommandSource>): Int {
        val username = StringArgumentType.getString(ctx, "username")
        service.getPlayTimeByName(username).thenAccept { optional ->
            if (optional.isEmpty) {
                ctx.source.sendMessage(Component.text("No playtime found for '$username'.", NamedTextColor.RED))
                return@thenAccept
            }
            val (uuid, duration) = optional.get()
            ctx.source.sendMessage(
                Component.text("Playtime for $username ($uuid): ", NamedTextColor.GOLD)
                    .append(Component.text(DurationFormat.pretty(duration), NamedTextColor.YELLOW)),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun clear(ctx: CommandContext<CommandSource>): Int {
        val username = StringArgumentType.getString(ctx, "username")
        service.clearPlayTimeByName(username).thenAccept { cleared ->
            if (cleared) {
                ctx.source.sendMessage(Component.text("Cleared playtime for '$username'.", NamedTextColor.GREEN))
            } else {
                ctx.source.sendMessage(Component.text("No playtime found for '$username'.", NamedTextColor.RED))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun suggestPlayers(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        server.allPlayers.forEach { player ->
            if (player.username.lowercase().startsWith(remaining)) {
                builder.suggest(player.username)
            }
        }
        return builder.buildFuture()
    }
}
