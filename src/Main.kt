import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    //Read config and build Discord Client
    val configFile = File((args.getOrNull(0) ?: "pinbot.config.yaml"))
    val (client, pinboards) = Config.read(configFile)

    //Listen to pin events
    client.eventDispatcher.on(ReactionAddEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        it.guildId.k?.let { guildId -> pinboards[guildId]?.let { pinboard -> pinboard(it) } }
    }

    //Listen to unpin events
    client.eventDispatcher.on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        it.guildId.k?.let { guildId -> pinboards[guildId]?.let { pinboard -> pinboard(it) } }
    }

    //Login
    client.login().block()
}

/**
 * A ReactionEmoji is a pin emoji if and only if it is the Unicode "📌"
 */
fun isPinEmoji(emoji: ReactionEmoji): Boolean {
    return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin }
}

/**
 * Helper method for transforming Java optionals into Kotlin nullables
 */
val <T> Optional<T>.k get() = if (this.isPresent) this.get() else null

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")