import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.EventDispatcher
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import org.apache.logging.log4j.LogManager
import java.io.File

fun main(args: Array<String>) {
    val token = args.getOrElse(0) { "discord.token.txt" }.let { File(it).readText(Charsets.UTF_8) }
    val client = DiscordClientBuilder(token).build()
    client.eventDispatcher.onPin {
        val guild = it.guild.block()?.name
        val pinner = it.user.block()?.username
        val message = it.message.block()
        val author = message?.author?.map { it.username }?.orElse("UNKNOWN_AUTHOR")
        val content = message?.content?.orElse("NO_MESSAGE")
        logger.info("$guild: $pinner pinned $content by $author")
    }
    client.login().block()
}

fun EventDispatcher.onPin(onReactionAdded: (ReactionAddEvent) -> Unit) {
    on(ReactionAddEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        onReactionAdded(it)
    }
}

fun EventDispatcher.onUnPin(onReactionRemoved: (ReactionRemoveEvent) -> Unit) {
    on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe {
        onReactionRemoved(it)
    }
}

fun isPinEmoji(emoji: ReactionEmoji): Boolean {
    return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin }
}

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")