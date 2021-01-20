import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class PinboardChannel(val client: DiscordClient, val guildId: Snowflake, val channel: MessageChannel) {
    fun createEmptyMessage(): Mono<Message> {
        return channel.createMessage("...")
    }

    fun createNewMessage(userId: Snowflake? = null): Mono<Message> {
        return when (userId) {
            null -> createEmptyMessage()
            else -> {
                val mention = mentionUser(userId)
                channel.createMessage("A post from $mention was pinned.")
            }
        }
    }

    fun deletePost(postId: Snowflake) {
        channel.getMessageById(postId).subscribe { pinboardMessage ->
            logger.trace("Deleting $pinboardMessage")
            pinboardMessage!!.delete().block()
        }
    }

    fun bindData(
        pinnedMessage: Message,
        pinCount: Int,
        pinboardPost: Message
    ): Mono<Message> {
        val channelId = pinnedMessage.channelId
        val messageId = pinnedMessage.id
        val content = pinnedMessage.content.k ?: ""
        val readableContent =
            if (content.length > 999) content.substring(0..999) + Typography.ellipsis
            else content
        val imageUrl = pinnedMessage.attachments.toList().getOrNull(0)?.url ?: pinnedMessage.embeds.getOrNull(0)?.url?.k
        val link = channelId.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channelId.asString()}/${messageId.asString()}"
        }
        val channel = "<#${channelId.asString()}>"
        val mention = mentionUser(pinnedMessage.author.k!!.id)
        return pinboardPost.edit {
            it.setContent("A post from $mention was pinned.")
            it.setEmbed { embed ->
                embed.setDescription("[Link to Post]($link)")
                embed.addField("Content", readableContent, false)
                embed.addField("Author", mention, true)
                embed.addField("Channel", channel, true)
                embed.setFooter("$pin $pinCount pushpins", null)
                imageUrl?.let { embed.setImage(it) }
            }
        }
    }

    fun allPinboardPostMessages(): Flux<Message> {
        val selfId = client.selfId.k ?: return Flux.empty()
        val lastMessage = channel.lastMessageId.k ?: return Flux.empty()
        return channel.getMessagesBefore(lastMessage).filter {
            it.author.k?.id == selfId
        }
    }
}