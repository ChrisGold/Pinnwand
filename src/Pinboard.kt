import db.PinDB
import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Channel
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import discord4j.rest.http.client.ClientException
import reactor.core.publisher.Mono
import java.net.URI
import kotlin.math.min

class Pinboard(
    val client: DiscordClient,
    val db: PinDB,
    val guild: String,
    val guildId: Snowflake,
    val pinboardChannelId: Snowflake,
    val pin: String,
    val threshold: Int
) {

    private val pinboardChannel =
        PinboardChannel(client, guildId, client.getChannelById(pinboardChannelId).block()!! as MessageChannel)

    private fun isPinEmoji(emoji: ReactionEmoji): Boolean {
        return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin } || emoji.asCustomEmoji()
            .let { it.isPresent && it.get().name == pin }
    }

    private fun countPins(it: Message): Int {
        return it.reactions.find { isPinEmoji(it.emoji) }?.count ?: 0
    }

    fun onAddReact(addEvent: ReactionAddEvent) = addEvent.message.subscribe { message ->
        logger.trace("Added react: $addEvent")
        updateBasedOnMessage(message)
    }

    fun onRemoveReact(removeEvent: ReactionRemoveEvent) = removeEvent.message.subscribe { message ->
        logger.trace("Removed react: $removeEvent")
        updateBasedOnMessage(message)
    }

    fun updateBasedOnMessage(message: Message) {
        val pins = countPins(message)
        //Ignore non-user posters
        if (message.author.isPresent) {
            //Message should be pinned
            if (pins >= threshold) {
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
                getPinboardPost(message, author.id).subscribe { pinboardMessage ->
                    pinboardChannel.bindData(message, pins, pinboardMessage).subscribe()
                }
            }
            //Message should not be pinned
            else {
                removeMessage(message.id)
            }
        }
    }

    fun onDeleteMessage(deletionEvent: MessageDeleteEvent) {
        logger.trace("Deleted message: $deletionEvent")
        removeMessage(deletionEvent.messageId)
    }

    fun showLeaderboard(channelId: Snowflake, startingPlace: Int) =
        client.getChannelById(channelId).subscribe { channel ->
            if (channel.type == Channel.Type.GUILD_TEXT) {
                channel as TextChannel
                val leaderboard = db.tally(guildId.asLong())
                val content = formatLeaderboard(leaderboard, 20, startingPlace)
                logger.info("Leaderboard: \n$content")
                channel.createMessage { mcs ->
                    mcs.setEmbed {
                        it.setDescription("Pinnwand Leaderboard")
                        it.addField("#", content.substring(0, min(content.length, 1000)), true)
                    }
                }.subscribe()
            }
        }

    private fun removeMessage(messageId: Snowflake) {
        //Check if message has been pinned before
        val pinboardPost = db.findPinboardPostByOriginalMessage(messageId.asLong())
        if (pinboardPost != null) {
            val pinboardPostId = pinboardPost.id.value
            db.removePinning(messageId.asLong())
            db.removePinboardPost(pinboardPostId)
            pinboardChannel.deletePost(pinboardPostId.sf)
        }
    }

    private fun getPinboardPost(message: Message, authorId: Snowflake? = null): Mono<Message> {
        return db.findPinboardPostByOriginalMessage(message.id.asLong())?.let {
            client.getMessageById(pinboardChannelId, Snowflake.of(it.id.value))
        }
            ?: pinboardChannel.createNewMessage(authorId).doOnSuccess {
                db.savePinboardPost(guildId.asLong(), it.id.asLong(), message.id.asLong(), message.content.k.orEmpty())
            }
    }

    fun rescanPinboard() {
        val pinboardPostMessages = pinboardChannel.allPinboardPostMessages()
        pinboardPostMessages.filter {
            db.findPinboardPostById(it.id.asLong()) == null
        }.subscribe { pinboardPostMessage ->
            val link = extractOriginalMessage(pinboardPostMessage)
            link?.let {
                client.getMessageById(link.channelId, link.messageId).doOnError {
                    if ((it as ClientException?)?.status?.code() == 404) {
                        logger.info("Old message deleted: $pinboardPostMessage")
                        pinboardPostMessage.delete()
                    }
                }.subscribe { message ->
                    val author = message.author.k
                    logger.info(
                        "Found old message: author = ${author?.username}:\n" +
                                "\tTimestamp: ${message.timestamp.toString()}\n" +
                                "\tAttached: ${message.attachments}\n" +
                                "\tEmbedded: ${message.embeds}\n" +
                                "\tContent:  ${message.content.k}"
                    )
                    relinkPost(pinboardPostMessage.id, message.channelId, message.id)
                }
            }
        }
    }

    fun topPosts(channelId: Snowflake, postCount: Int) = client.getChannelById(channelId).subscribe { channel ->
        if (channel.type == Channel.Type.GUILD_TEXT) {
            channel as TextChannel
            val leaderboard = db.topPosts(guildId.asLong(), 10)
            val content = formatTopPosts(leaderboard)
            logger.info("Top Posts: \n$content")
            channel.createMessage { mcs ->
                mcs.setEmbed {
                    it.setDescription("Pinnwand Top Posts")
                    it.addField("#", content.substring(0, min(content.length, 1000)), true)
                }
            }.subscribe()
        }
    }

    private fun relinkPost(postId: Snowflake, messageChannelId: Snowflake, messageId: Snowflake) {
        client.getMessageById(messageChannelId, messageId).subscribe { message ->
            val author = message.author.k ?: return@subscribe
            val pins = countPins(message)
            db.registerPinning(guildId.asLong(), message.id.asLong(), author.id.asLong(), pins)
            db.savePinboardPost(guildId.asLong(), postId.asLong(), message.id.asLong(), message.content.k.orEmpty())
            updateBasedOnMessage(message)
        }
    }

    data class MessageLink(val guildId: Snowflake, val channelId: Snowflake, val messageId: Snowflake)

    private fun extractOriginalMessage(pinboardPostMessage: Message): MessageLink? {
        try {
            val embed = pinboardPostMessage.embeds.getOrNull(0) ?: return null
            val description = embed.description.k ?: return null
            val linkTitle = "[Link to Post]("
            val linkContentIndex = description.indexOf(linkTitle) + linkTitle.length
            val linkContentEnd = description.indexOf(')', linkContentIndex)
            val linkContent = description.substring(linkContentIndex, linkContentEnd)
            val path = URI(linkContent).path
            val components = path.split('/')
            return MessageLink(Snowflake.of(components[2]), Snowflake.of(components[3]), Snowflake.of(components[4]))
        } catch (ex: Exception) {
            return null
        }
    }
}