import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    val token = args.getOrElse(0) { "discord.token.txt" }.let { File(it).readText(Charsets.UTF_8) }
    val client = DiscordClientBuilder(token).build()

    client.eventDispatcher.on(ReactionAddEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.flatMap {
        it.message
    }.subscribe {
        val pinReaction = it.reactions.firstOrNull { isPinEmoji(it.emoji) }
        if (pinReaction != null && pinReaction.count >= 5) {
            //TODO: Pin to board
            val message = it.content.k
            val author = it.author.k?.username
            logger.info("Pinned: $message by $author")
        }
    }

    client.eventDispatcher.on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.flatMap {
        it.message
    }.subscribe {
        val pinReaction = it.reactions.firstOrNull { isPinEmoji(it.emoji) }
        if (pinReaction == null || pinReaction.count < 5) {
            //TODO: Unpin from board
            val message = it.content.k
            val author = it.author.k?.username
            logger.info("Unpinned: $message by $author")
        }
    }

    client.login().block()
}

fun isPinEmoji(emoji: ReactionEmoji): Boolean {
    return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin }
}

val <T> Optional<T>.k get() = if (this.isPresent) this.get() else null

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")