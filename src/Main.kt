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
            val pinboardPost = PinDB.findPinboardPost(it.id)
            when (pinboardPost) {
                null -> {
                    println("Pinning $it")
                    //TODO: Pin post to the board
                    //TODO: Create PinDB entry
                }
                else -> {
                    //TODO: Update number of pins on the board
                    PinDB.updatePinCount(pinboardPost, pinReaction.count)
                }
            }
        }
    }

    client.eventDispatcher.on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.flatMap {
        it.message
    }.subscribe {
        val pinReaction = it.reactions.firstOrNull { isPinEmoji(it.emoji) }
        if (pinReaction == null || pinReaction.count < 5) {
            val pinboardPost = PinDB.findPinboardPost(it.id)
            if (pinboardPost != null) {
                println("Unpinning $it")
                //TODO: Delete pinboardPost from DB
                //TODO: Delete post from channel
            }
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