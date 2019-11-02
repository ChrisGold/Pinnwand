import discord4j.core.`object`.entity.Message
import java.time.format.DateTimeFormatter
import java.util.*

private val zoneId = TimeZone.getDefault().toZoneId()
private val format = DateTimeFormatter.ISO_LOCAL_DATE

fun Message.getDatestamp(): String {
    val datetime = this.timestamp.atZone(zoneId)
    return datetime.format(format)
}