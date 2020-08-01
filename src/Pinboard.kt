import discord4j.core.DiscordClient
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent

class Pinboard(
    client: DiscordClient,
    guild: String,
    guildId: Snowflake,
    pinboardChannelId: Snowflake,
    pin: String,
    threshold: Int
) {
    fun isPinEmoji(emoji: ReactionEmoji): Boolean {
        return emoji.asUnicodeEmoji().let { it.isPresent && it.get().toString() == pin } || emoji.asCustomEmoji()
            .let { it.isPresent && it.get().name == pin }
    }

    fun addReact(addEvent: ReactionAddEvent?) {
        println("Added react: $addEvent")
    }

    fun removeReact(event: ReactionRemoveEvent?) {
        println("Removed react: $event")
    }

    fun deleteMessage(deletion: MessageDeleteEvent?) {
        println("Deleted message: $deletion")
    }
}