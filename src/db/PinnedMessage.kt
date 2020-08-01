package db

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

object PinnedMessages : LongIdTable("pinned_message") {
    val guild = reference("guild", Guilds)
    val author = long("author")
    val pinCount = integer("pin_count")
}

class PinnedMessage(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PinnedMessage>(PinnedMessages)

    var guild by Guild referencedOn PinnedMessages.guild
    var author by PinnedMessages.author
    var pinCount by PinnedMessages.pinCount
}