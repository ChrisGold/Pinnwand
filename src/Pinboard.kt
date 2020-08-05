import db.PinDB
import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import reactor.core.publisher.Mono

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
                getPinboardPost(message).subscribe { pinboardMessage ->
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

    private fun removeMessage(messageId: Snowflake) {
        //Check if message has been pinned before
        val pinboardPost = db.findPinboardPost(messageId.asLong())
        if (pinboardPost != null) {
            val pinboardPostId = pinboardPost.id.value
            db.removePinning(messageId.asLong())
            db.removePinboardPost(pinboardPostId)
            pinboardChannel.deletePost(pinboardPostId.sf)
        }
    }

    private fun getPinboardPost(message: Message): Mono<Message> {
        return db.findPinboardPost(message.id.asLong())?.let {
            client.getMessageById(pinboardChannelId, Snowflake.of(it.id.value))
        }
            ?: pinboardChannel.createEmptyMessage().doOnSuccess {
                db.savePinboardPost(guildId.asLong(), it.id.asLong(), message.id.asLong(), message.content.k.orEmpty())
            }
    }
}