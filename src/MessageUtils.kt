import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.util.Snowflake
import java.time.format.DateTimeFormatter
import java.util.*

private val zoneId = TimeZone.getDefault().toZoneId()
private val format = DateTimeFormatter.ISO_LOCAL_DATE

fun Message.getDatestamp(): String {
    val datetime = this.timestamp.atZone(zoneId)
    return datetime.format(format)
}

fun Message.extractLink(): String? {
    val embed = this.embeds.getOrNull(0) ?: return null
    val description = embed.description.k ?: return null
    return description.extractMarkdownLink()
}

fun String.extractMarkdownLink(): String? {
    val from = this.indexOf('(')
    val to = this.indexOf(')', from)
    return substring(from + 1, to)
}

data class MessageData(val guildId: Snowflake, val channelId: Snowflake, val messageId: Snowflake) {
    override fun toString(): String {
        return "https://discordapp.com/channels/${guildId.asString()}/${channelId.asString()}/${messageId.asString()}"
    }
}

fun decomposeLink(link: String): MessageData {
    val prefix = "https://discordapp.com/channels/"
    val content = link.substring(prefix.length)
    val (guild, channel, message) = content.split('/').map { Snowflake.of(it) }
    return MessageData(guild, channel, message)
}