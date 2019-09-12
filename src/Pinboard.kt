import discord4j.core.DiscordClient
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent

class Pinboard(val guild: String, val guildId: Snowflake, val pinboardChannelId: Snowflake) {
    operator fun invoke(client: DiscordClient, reactionAddEvent: ReactionAddEvent) =
        reactionAddEvent.message.subscribe {
            val pinReaction = it.reactions.firstOrNull { isPinEmoji(it.emoji) }
            if (pinReaction != null && pinReaction.count >= 1) {
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

    operator fun invoke(client: DiscordClient, reactionRemoveEvent: ReactionRemoveEvent) =
        reactionRemoveEvent.message.subscribe {
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

    fun createPinPost(client: DiscordClient, message: String, author: String, pinCount: Int): Snowflake {
        TODO()
    }

    fun updatePinPost(client: DiscordClient, pinboardPost: Snowflake, message: String, author: String, pinCount: Int) {
        TODO()
    }

    fun deletePinPost(client: DiscordClient, pinboardPost: Snowflake) {
        TODO()
    }
}

