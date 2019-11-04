import discord4j.core.DiscordClient
import discord4j.core.`object`.Embed
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import discord4j.rest.http.client.ClientException
import reactor.core.publisher.Mono
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
            if (isPinnable(message)) {
                PinPostData.from(message).let { pinPostData ->
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
        }

    /**
     * Register a [ReactionRemoveEvent] with this pinboard
     */
    operator fun invoke(reactionRemoveEvent: ReactionRemoveEvent) =
        reactionRemoveEvent.message.subscribe { message ->
            PinDB.findPinboardPost(message.id)?.let { pinboardPost ->
                if (isPinnable(message)) {
                    PinPostData.from(message).let { pinPostData ->
                        if (pinPostData.pinCount < pinThreshold) {
                            unpin(pinPostData, pinboardPost)
                        } else if (pinPostData.pinCount >= pinThreshold) {
                            update(pinPostData, pinboardPost)
                        }
                    }
                } else unpin(null, pinboardPost)
            }
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
        val content = message.content.k ?: return
        if (content.startsWith("*leaderboard")) {
            message.channel.subscribe {
                displayLeaderboard(it)
            }
        } else if (content.startsWith("*rescan")) {
            val rescan = Rescan(this)
            rescan()
        }
    }

    private fun unpin(pinPostData: PinPostData?, pinboardPost: Snowflake) {
        logger.info("Unpinning $pinPostData")
        PinDB.removePinning(pinboardPost)
        deletePinPost(pinboardPost)
    }

    private fun pin(pinPostData: PinPostData) {
        logger.info("Pinning $pinPostData")
        createPinPost(pinPostData).subscribe {
            PinDB.recordPinning(
                guildId,
                it.id,
                pinPostData.author ?: Snowflake.of(0),
                pinPostData.messageId,
                pinPostData.pinCount
            )
        }

    }

    private fun update(pinPostData: PinPostData, pinboardPost: Snowflake) {
        updatePinPost(
            pinboardPost,
            pinPostData
        ).subscribe {
            PinDB.updatePinCount(pinboardPost, pinPostData.pinCount)
        }
    }

    private fun createPinPost(
        pinPostData: PinPostData
    ): Mono<Message> = pinPostData.run {
        client.getChannelById(pinboardChannelId).map { it as MessageChannel }.flatMap { pinboardChannel ->
            val link = channel?.let {
                "https://discordapp.com/channels/${guildId.asString()}/${channel.asString()}/${messageId.asString()}"
            }
            val channel = "<#${channel?.asString()}>"
            val mention = mentionUser(author)
            pinboardChannel.createMessage {
                it.setContent("A post from $mention was pinned.")
                it.setEmbed { embed ->
                    embed.setDescription("[Link to Post]($link)")
                    embed.addField("Content", message, false)
                    embed.addField("Author", mention, true)
                    embed.addField("Channel", channel, true)
                    embed.setFooter("$pin $pinCount pushpins", null)
                    image?.url?.let { embed.setImage(it) }
                }
            }
        }
    }

    private fun updatePinPost(
        pinboardPost: Snowflake,
        pinPostData: PinPostData
    ): Mono<Message> = pinPostData.run {
        val link = channel?.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channel.asString()}/${messageId.asString()}"
        }
        client.getMessageById(pinboardChannelId, pinboardPost).doOnError {
            if (it is ClientException && it.status.code() == 404) {
                //The post has been deleted
                createPinPost(pinPostData).subscribe()
            } else throw  it
        }.flatMap { msg ->
            val mention = mentionUser(author)
            val channel = "<#${channel?.asString()}>"
            msg.edit { edit ->
                edit.setContent("A post from $mention was pinned.")
                edit.setEmbed { embed ->
                    embed.setDescription("[Link to Post]($link)")
                    embed.addField("Content", message ?: "null", false)
                    embed.addField("Author", mention, true)
                    embed.addField("Channel", channel, true)
                    embed.setFooter("$pin $pinCount pushpins", null)
                    image?.url?.let { embed.setImage(it) }
                }
            }
        }
    }

    private fun deletePinPost(pinboardPost: Snowflake) {
        client.getMessageById(pinboardChannelId, pinboardPost).flatMap {
            it.delete()
        }.doOnError {
            if (it is ClientException && it.status.code() == 404) {
                //The post has been deleted
                PinDB.removePinning(pinboardPost)
            } else throw  it
        }.subscribe()
    }

    private fun displayLeaderboard(channel: MessageChannel) {
        val leaderboard = PinDB.tallyLeaderboard(guildId)
        val (positions, users, pins) = display(leaderboard)
        channel.createMessage {
            it.setEmbed {
                it.setDescription("Pinnwand Leaderboard")
                it.addField("Rank", positions, true)
                it.addField("User", users, true)
                it.addField("Pins", pins, true)
            }
        }.subscribe()
    }

    fun isPinnable(message: Message): Boolean {
        return message.reactions.find { isPinEmoji(it.emoji) && it.count >= pinThreshold } != null
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
        fun from(message: Message): PinPostData {
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            val image = message.embeds.getOrNull(0)?.image?.k
            return PinPostData(
                message.id,
                message.content.k,
                message.channelId,
                message.author.k?.id,
                pinReaction?.count ?: 0,
                image
            )
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

private fun extractLink(embedDescription: String): String? {
    val start = embedDescription.indexOf('(')
    val end = embedDescription.indexOf(')', start)
    if (start < 0 || end < 0) return null
    return embedDescription.substring(start + 1, end)
}

private fun extractMessage(link: String): Snowflake = Snowflake.of(link.substringAfterLast('/'))

private fun extractInfo(embed: Embed): Pair<Snowflake, Int>? {
    val pinnedPostId = embed.description.k?.let { extractLink(it) }?.let { extractMessage(it) }
    val pinCount = embed.footer.k?.text?.split(' ')?.getOrNull(1)?.toInt()
    return if (pinnedPostId == null || pinCount == null) null
    else pinnedPostId to pinCount
}