import db.PinDB
import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent

class Pinboard(
    val client: DiscordClient,
    val db: PinDB,
    val guild: String,
    val guildId: Snowflake,
    val pinboardChannelId: Snowflake,
    val pin: String,
    val threshold: Int
) {
    fun isPinEmoji(emoji: ReactionEmoji): Boolean {
        return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin } || emoji.asCustomEmoji()
            .let { it.isPresent && it.get().name == pin }
    }

    fun countPins(it: Message): Int {
        return it.reactions.find { isPinEmoji(it.emoji) }?.count ?: 0
    }

    fun addReact(addEvent: ReactionAddEvent) = addEvent.message.subscribe { message ->
        logger.trace("Added react: $addEvent")
        //Ignore non-user posters
        val pins = countPins(message)
        if (pins >= threshold && message.author.isPresent) {
            val author = message.author.get()
            message.attachments
            logger.info(
                "Pinning message: author = ${author.username}:\n" +
                        "\tAttached: ${message.attachments}\n" +
                        "\tEmbedded: ${message.embeds}\n" +
                        "\tContent:  ${message.content.k}"
            )
            //Register post in DB
            db.registerPinning(guildId.asLong(), message.id.asLong(), author.id.asLong(), pins)
            //TODO: Update pinboard message
        }
    }

    fun removeReact(event: ReactionRemoveEvent?) {
        logger.trace("Removed react: $event")
    }

    fun deleteMessage(deletion: MessageDeleteEvent?) {
        logger.trace("Deleted message: $deletion")
    }

}