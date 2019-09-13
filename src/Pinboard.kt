import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent

class Pinboard(val guild: String, val guildId: Snowflake, val pinboardChannelId: Snowflake, val pinThreshold: Int = 5) {
    operator fun invoke(client: DiscordClient, reactionAddEvent: ReactionAddEvent) =
        reactionAddEvent.message.subscribe { message ->
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            val messageId = message.id
            val author = message.author.k?.id
            val content = message.content.k
            if (pinReaction != null && author != null && pinReaction.count >= pinThreshold) {
                //TODO: Pin to board
                val pinboardPost = PinDB.findPinboardPost(message.id)
                when (pinboardPost) {
                    null -> {
                        println("Pinning $message")
                        val pinboardPost = createPinPost(client, content, message.author.k?.username, pinReaction.count)
                        PinDB.recordPinning(pinboardPost, author, messageId, pinReaction.count)
                    }
                    else -> {
                        updatePinPost(client, pinboardPost, content, message.author.k?.username, pinReaction.count)
                        PinDB.updatePinCount(pinboardPost, pinReaction.count)
                    }
                }
            }
        }

    operator fun invoke(client: DiscordClient, reactionRemoveEvent: ReactionRemoveEvent) =
        reactionRemoveEvent.message.subscribe { message ->
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            val messageId = message.id
            val author = message.author.k?.id
            val content = message.content.k
            val pinboardPost = PinDB.findPinboardPost(message.id)
            if (pinReaction == null || pinReaction.count < pinThreshold) {
                if (pinboardPost != null) {
                    println("Unpinning $message")
                    PinDB.removePinning(pinboardPost)
                    deletePinPost(client, pinboardPost)
                }
            } else if (pinboardPost != null && pinReaction.count >= pinThreshold) {
                updatePinPost(client, pinboardPost, content, message.author.k?.username, pinReaction.count)
                PinDB.updatePinCount(pinboardPost, pinReaction.count)
            }
        }

    fun createPinPost(client: DiscordClient, message: String?, author: String?, pinCount: Int?): Snowflake {
        val channel = client.getChannelById(pinboardChannelId).block() as MessageChannel
        val pinMessage = """
            Pinned comment:
            
            $message
            
            by $author
            ($pinCount pins)
        """.trimIndent()
        val message = channel.createMessage(pinMessage).block()
        return message.id
    }

    fun updatePinPost(
        client: DiscordClient,
        pinboardPost: Snowflake,
        message: String?,
        author: String?,
        pinCount: Int?
    ) {
        val originalMessage = client.getMessageById(pinboardChannelId, pinboardPost).block()
        val pinMessage = """
            Pinned comment:
            
            $message
            
            by $author
            ($pinCount pins)
        """.trimIndent()
        originalMessage.edit {
            it.setContent(pinMessage)
        }.block()
    }

    fun deletePinPost(client: DiscordClient, pinboardPost: Snowflake) {
        client.getMessageById(pinboardChannelId, pinboardPost).block().delete().block()
    }
}

