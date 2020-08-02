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

    val pinboardChannel =
        PinboardChannel(client, guildId, client.getChannelById(pinboardChannelId).block()!! as MessageChannel)

    fun isPinEmoji(emoji: ReactionEmoji): Boolean {
        return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin } || emoji.asCustomEmoji()
            .let { it.isPresent && it.get().name == pin }
    }

    fun countPins(it: Message): Int {
        return it.reactions.find { isPinEmoji(it.emoji) }?.count ?: 0
    }

    fun addReact(addEvent: ReactionAddEvent) = addEvent.message.subscribe { message ->
        logger.trace("Added react: $addEvent")
        //Ignore non-user posters
        val pins = countPins(message)
        if (pins >= threshold && message.author.isPresent) {
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
            getPinboardPost(message.id).subscribe { pinboardMessage ->
                bindData(message, pins, pinboardMessage)
            }
        }
    }

    fun removeReact(event: ReactionRemoveEvent?) {
        logger.trace("Removed react: $event")
    }

    fun deleteMessage(deletion: MessageDeleteEvent?) {
        logger.trace("Deleted message: $deletion")
    }

    fun getPinboardPost(messageId: Snowflake): Mono<Message> =
        db.findPinboardPost(messageId.asLong())?.let {
            client.getMessageById(pinboardChannelId, Snowflake.of(it.id.value))
        }
            ?: pinboardChannel.createEmptyMessage()

    private fun mentionUser(user: Snowflake?): String = "<@!${user?.asString()}>"

    private fun bindData(
        pinnedMessage: Message,
        pinCount: Int,
        pinboardPost: Message
    ): Mono<Message> {
        val channelId = pinnedMessage.channelId
        val messageId = pinnedMessage.id
        val content = pinnedMessage.content.k ?: "<empty>"
        val imageUrl = pinnedMessage.attachments.toList().getOrNull(0)?.url ?: pinnedMessage.embeds.getOrNull(0)?.url?.k
        val link = channelId?.let {
            "https://discordapp.com/channels/${guildId.asString()}/${channelId.asString()}/${messageId.asString()}"
        }
        val channel = "<#${channelId?.asString()}>"
        val mention = mentionUser(pinnedMessage.author.k!!.id)
        return pinboardPost.edit {
            it.setContent("A post from $mention was pinned.")
            it.setEmbed { embed ->
                embed.setDescription("[Link to Post]($link)")
                embed.addField("Content", content, false)
                embed.addField("Author", mention, true)
                embed.addField("Channel", channel, true)
                embed.setFooter("$pin $pinCount pushpins", null)
                imageUrl?.let { embed.setImage(it) }
            }
        }
    }

}