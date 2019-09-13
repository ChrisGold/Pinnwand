import discord4j.core.DiscordClient
import discord4j.core.`object`.Embed
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
            val content = message.content.k
            val image = message.embeds.getOrNull(0)?.image?.k
            val channelId = message.channelId
            //Should the post be pinned at all?
            if (pinReaction != null && authorId != null && pinReaction.count >= pinThreshold) {
                //Has the post been pinned before?
                val pinboardPost = PinDB.findPinboardPost(message.id)
                when (pinboardPost) {
                    null -> {
                        //Post hasn't been pinned yet
                        println("Pinning $message")
                        val pinboardPost =
                            createPinPost(client, content, messageId, channelId, authorId, pinReaction.count, image)
                        PinDB.recordPinning(pinboardPost, authorId, messageId, pinReaction.count)
                    }
                    else -> {
                        //Post has been pinned already
                        updatePinPost(
                            client,
                            pinboardPost,
                            content,
                            messageId,
                            channelId,
                            authorId,
                            pinReaction.count,
                            image
                        )
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
            val channelId = message.channelId
            val author = message.author.k
            val image = message.embeds.getOrNull(0)?.image?.k
            if (pinReaction == null || pinReaction.count < pinThreshold) {
                if (pinboardPost != null) {
                    println("Unpinning $message")
                    PinDB.removePinning(pinboardPost)
                    deletePinPost(client, pinboardPost)
                }
            } else if (pinboardPost != null && pinReaction.count >= pinThreshold) {
                updatePinPost(
                    client,
                    pinboardPost,
                    content,
                    message.id,
                    channelId,
                    author?.id,
                    pinReaction.count,
                    image
                )
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

    fun createPinPost(
        client: DiscordClient,
        message: String?,
        messageId: Snowflake,
        channel: Snowflake?,
        author: Snowflake?,
        pinCount: Int?,
        image: Embed.Image?
    ): Snowflake {
        val pinboardChannel = client.getChannelById(pinboardChannelId).block() as MessageChannel
        val link = channel?.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channel.asString()}/${messageId.asString()}"
        }
        val channel = "<#${channel?.asString()}>"
        val mention = "<@!${author?.asString()}>"
        val message = pinboardChannel.createMessage {
            it.setContent("A post from $mention was pinned.")
            it.setEmbed { embed ->
                embed.setDescription("[Link to Post]($link)")
                embed.addField("Content", message, false)
                embed.addField("Author", mention, true)
                embed.addField("Channel", channel, true)
                embed.setFooter("$pin $pinCount pushpins", null)
                image?.url?.let { embed.setImage(it) }
            }
        }.block()

        return message.id
    }

    fun updatePinPost(
        client: DiscordClient,
        pinboardPost: Snowflake,
        message: String?,
        messageId: Snowflake,
        channel: Snowflake?,
        author: Snowflake?,
        pinCount: Int?,
        image: Embed.Image?
    ) {
        val link = channel?.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channel.asString()}/${messageId.asString()}"
        }
        val originalMessage = client.getMessageById(pinboardChannelId, pinboardPost).block()
        val mention = "<@!${author?.asString()}>"
        val channel = "<#${channel?.asString()}>"
        originalMessage.edit {
            it.setContent("A post from $mention was pinned.")
            it.setEmbed { embed ->
                embed.setDescription("[Link to Post]($link)")
                embed.addField("Content", message, false)
                embed.addField("Author", mention, true)
                embed.addField("Channel", channel, true)
                embed.setFooter("$pin $pinCount pushpins", null)
                image?.url?.let { embed.setImage(it) }
            }
        }.block()
    }

    fun deletePinPost(client: DiscordClient, pinboardPost: Snowflake) {
        client.getMessageById(pinboardChannelId, pinboardPost).block().delete().block()
    }
}