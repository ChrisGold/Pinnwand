import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent

class Pinboard(
    val client: DiscordClient,
    val guild: String,
    val guildId: Snowflake,
    val pinboardChannelId: Snowflake,
    val pinThreshold: Int = 5
) {
    /**
     * Register a [ReactionAddEvent] with this pinboard
     */
    operator fun invoke(reactionAddEvent: ReactionAddEvent) =
        reactionAddEvent.message.subscribe { message ->
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            val messageId = message.id
            val author = message.author.k
            val authorId = author?.id
            val authorName = author?.username
            val content = message.content.k
            //Should the post be pinned at all?
            if (pinReaction != null && authorId != null && pinReaction.count >= pinThreshold) {
                //Has the post been pinned before?
                val pinboardPost = PinDB.findPinboardPost(message.id)
                when (pinboardPost) {
                    null -> {
                        //Post hasn't been pinned yet
                        println("Pinning $message")
                        val pinboardPost = createPinPost(client, content, authorName, pinReaction.count)
                        PinDB.recordPinning(pinboardPost, authorId, messageId, pinReaction.count)
                    }
                    else -> {
                        //Post has been pinned already
                        updatePinPost(client, pinboardPost, content, message.author.k?.username, pinReaction.count)
                        PinDB.updatePinCount(pinboardPost, pinReaction.count)
                    }
                }
            }
        }

    /**
     * Register a [ReactionRemoveEvent] with this pinboard
     */
    operator fun invoke(reactionRemoveEvent: ReactionRemoveEvent) =
        reactionRemoveEvent.message.subscribe { message ->
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            val content = message.content.k
            val pinboardPost = PinDB.findPinboardPost(message.id)
            val author = message.author.k
            val authorName = author?.username
            if (pinReaction == null || pinReaction.count < pinThreshold) {
                if (pinboardPost != null) {
                    println("Unpinning $message")
                    PinDB.removePinning(pinboardPost)
                    deletePinPost(client, pinboardPost)
                }
            } else if (pinboardPost != null && pinReaction.count >= pinThreshold) {
                updatePinPost(client, pinboardPost, content, authorName, pinReaction.count)
                PinDB.updatePinCount(pinboardPost, pinReaction.count)
            }
        }

    operator fun invoke(messageDeleteEvent: MessageDeleteEvent) {
        val messageId = messageDeleteEvent.messageId
        PinDB.findPinboardPost(messageId)?.let {
            PinDB.removePinning(it)
            deletePinPost(client, it)
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
        client: DiscordClient, pinboardPost: Snowflake, message: String?, author: String?, pinCount: Int?
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

