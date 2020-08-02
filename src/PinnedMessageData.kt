import discord4j.core.`object`.Embed
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.util.Snowflake

data class PinnedMessageData(
    val messageId: Snowflake,
    val message: String?,
    val channel: Snowflake?,
    val author: Snowflake?,
    val pinCount: Int,
    val image: Embed.Image?
) {
    companion object {
        fun from(message: Message): PinnedMessageData {
            val pinReaction = message.reactions.firstOrNull { isPinEmoji(it.emoji) }
            val image = message.embeds.getOrNull(0)?.image?.k
            return PinnedMessageData(
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