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

fun formatLeaderboard(list: List<LeaderboardEntry>, limit: Int, offset: Int): String {
    val content = StringBuilder()

    if (list.isEmpty()) {
        return "<empty>"
    }

    list.forEachIndexed { i, (author, pinCount) ->
        if (i >= offset && i < offset + limit) {
            val pos = (i + 1).toString()
            content.append(pos)
            content.append(": ")
            content.append(mentionUser(author))
            content.append(" (")
            content.append(pinCount)
            content.append(")\n")
        }
    }

    return content.toString()
}

fun formatTopPosts(list: List<TopPostEntry>): String = list.asSequence().withIndex().joinToString("\n") { (id, entry) ->
    val (message, author, totalPins) = entry
    "${id + 1}: ${mentionUser(author)} $totalPins Pins\n${message}"
}

fun mentionUser(user: Snowflake?): String = "<@!${user?.asString()}>"

val Long.sf: Snowflake
    get() = Snowflake.of(this)