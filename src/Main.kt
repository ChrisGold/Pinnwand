import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    val configFile = File((args.getOrNull(0) ?: "pinbot.config.yaml"))
    val (token, pinboards) = Config.read(configFile)
    val client = DiscordClientBuilder(token).build()

    client.guilds.subscribe {
        val name = it.name
        val id = it.id
        it.channels.subscribe {
            val channelName = it.name
            val channelId = it.id
            println("Guild $name ($id): Channel $channelName ($channelId)")
        }
    }

    client.eventDispatcher.on(ReactionAddEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        it.guildId.k?.let { guildId -> pinboards[guildId]?.let { pinboard -> pinboard(client, it) } }
    }

    client.eventDispatcher.on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        it.guildId.k?.let { guildId -> pinboards[guildId]?.let { pinboard -> pinboard(client, it) } }
    }
    client.login().block()
}

fun isPinEmoji(emoji: ReactionEmoji): Boolean {
    return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin }
}

val <T> Optional<T>.k get() = if (this.isPresent) this.get() else null

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")