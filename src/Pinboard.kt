import discord4j.core.DiscordClient
import discord4j.core.`object`.Embed
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import java.util.*

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
            PinPostData.from(message)?.let { pinPostData ->
                //Has the post been pinned before?
                val pinboardPost = PinDB.findPinboardPost(pinPostData.messageId)
                when (pinboardPost) {
                    null -> {
                        //Post hasn't been pinned yet
                        if (pinPostData.pinCount >= pinThreshold) {
                            pin(pinPostData)
                        }
                    }
                    else -> {
                        //Post has been pinned already
                        if (pinPostData.pinCount >= pinThreshold) {
                            update(pinPostData, pinboardPost)
                        }
                    }
                }
            }
        }

    /**
     * Register a [ReactionRemoveEvent] with this pinboard
     */
    operator fun invoke(reactionRemoveEvent: ReactionRemoveEvent) =
        reactionRemoveEvent.message.subscribe { message ->
            PinDB.findPinboardPost(message.id)?.let { pinboardPost ->
                PinPostData.from(message)?.let { pinPostData ->
                    if (pinPostData.pinCount < pinThreshold) {
                        unpin(pinPostData, pinboardPost)
                    } else if (pinPostData.pinCount >= pinThreshold) {
                        update(pinPostData, pinboardPost)
                    }
                } ?: unpin(null, pinboardPost)
            }
        }

    fun unpin(pinPostData: PinPostData?, pinboardPost: Snowflake) {
        logger.info("Unpinning $pinPostData")
        PinDB.removePinning(pinboardPost)
        deletePinPost(pinboardPost)
    }

    fun pin(pinPostData: PinPostData) {
        logger.info("Pinning $pinPostData")
        val newPinboardPost =
            createPinPost(pinPostData)
        PinDB.recordPinning(
            newPinboardPost,
            pinPostData.author ?: Snowflake.of(0),
            pinPostData.messageId,
            pinPostData.pinCount
        )
    }

    fun update(pinPostData: PinPostData, pinboardPost: Snowflake) {
        updatePinPost(
            pinboardPost,
            pinPostData
        )
        PinDB.updatePinCount(pinboardPost, pinPostData.pinCount)
    }

    operator fun invoke(messageDeleteEvent: MessageDeleteEvent) {
        val messageId = messageDeleteEvent.messageId
        PinDB.findPinboardPost(messageId)?.let {
            PinDB.removePinning(it)
            deletePinPost(it)
        }
    }

    operator fun invoke(messageCreateEvent: MessageCreateEvent) {
        val message = messageCreateEvent.message
        if (message.content.k?.startsWith("*leaderboard") ?: false) {
            message.channel.subscribe {
                displayLeaderboard(it)
            }
        }
    }

    fun createPinPost(
        pinPostData: PinPostData
    ): Snowflake = pinPostData.run {
        val pinboardChannel = client.getChannelById(pinboardChannelId).block() as MessageChannel
        val link = channel?.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channel.asString()}/${messageId.asString()}"
        }
        val channel = "<#${channel?.asString()}>"
        val mention = mentionUser(author)
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
        pinboardPost: Snowflake,
        pinPostData: PinPostData
    ) = pinPostData.run {
        val link = channel?.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channel.asString()}/${messageId.asString()}"
        }
        val originalMessage = client.getMessageById(pinboardChannelId, pinboardPost).block()
        val mention = mentionUser(author)
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

    fun deletePinPost(pinboardPost: Snowflake) {
        client.getMessageById(pinboardChannelId, pinboardPost).block().delete().block()
    }

    fun displayLeaderboard(channel: MessageChannel) {
        val leaderboard = PinDB.tallyLeaderboard()
        val (positions, users, pins) = display(leaderboard)
        channel.createMessage {
            it.setEmbed {
                it.setDescription("Pinnwand Leaderboard")
                it.addField("Rank", positions, true)
                it.addField("User", users, true)
                it.addField("Pins", pins, true)
            }
        }.block()
    }
}

data class PinPostData(
    val messageId: Snowflake,
    val message: String?,
    val channel: Snowflake?,
    val author: Snowflake?,
    val pinCount: Int,
    val image: Embed.Image?
) {
    companion object {
        fun from(message: Message): PinPostData? {
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            return if (pinReaction != null) {
                val image = message.embeds.getOrNull(0)?.image?.k
                PinPostData(
                    message.id,
                    message.content.k,
                    message.channelId,
                    message.author.k?.id,
                    pinReaction.count,
                    image
                )
            } else null
        }
    }
}

private fun display(list: List<LeaderboardEntry>): Triple<String, String, String> {
    val position = StringJoiner("\n")
    val user = StringJoiner("\n")
    val pins = StringJoiner("\n")

    if (list.isEmpty()) {
        return Triple("<empty>", "<empty>", "<empty>")
    }

    list.forEachIndexed { i, (author, pinCount) ->
        val pos = (i + 1).toString()
        position.add(pos)
        user.add(mentionUser(author))
        pins.add(pinCount.toString())
    }

    return Triple(position.toString(), user.toString(), pins.toString())
}

private fun mentionUser(user: Snowflake?): String = "<@!${user?.asString()}>"