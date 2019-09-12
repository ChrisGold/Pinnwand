import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import org.apache.logging.log4j.LogManager
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    val token = args.getOrElse(0) { "discord.token.txt" }.let { File(it).readText(Charsets.UTF_8) }
    val client = DiscordClientBuilder(token).build()
    val pinboards: Map<Snowflake, Pinboard> = emptyMap()    //TODO: Load from YAML

    client.eventDispatcher.on(ReactionAddEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        it.guildId.k?.let { guildId -> pinboards[guildId]?.let { pinboard -> pinboard(it) } }
    }

    client.eventDispatcher.on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        it.guildId.k?.let { guildId -> pinboards[guildId]?.let { pinboard -> pinboard(it) } }
    }
    client.login().block()
}

fun isPinEmoji(emoji: ReactionEmoji): Boolean {
    return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin }
}

val <T> Optional<T>.k get() = if (this.isPresent) this.get() else null

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")
val yaml = Yaml()