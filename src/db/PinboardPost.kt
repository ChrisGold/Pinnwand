package db

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

object PinboardPosts : LongIdTable("pinboard_post") {
    val guild = reference("guild", Guilds)
    val message = reference("message", PinnedMessages)
    val content = varchar("content", 1000)
    val imageUrl = varchar("image_url", 200).nullable()
}

class PinboardPost(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PinboardPost>(PinboardPosts)

    var guild by Guild referencedOn PinboardPosts.guild
    var message by PinnedMessage referencedOn PinboardPosts.message
    var content by PinboardPosts.content
    var imageUrl by PinboardPosts.imageUrl
}