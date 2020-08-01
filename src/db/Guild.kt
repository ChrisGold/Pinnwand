package db

import Pinboard
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

object Guilds : LongIdTable("guild") {
    val name = varchar("name", length = 50)
}

class Guild(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Guild>(Guilds) {
        fun fromPinboard(pinboard: Pinboard): Guild {
            val id = pinboard.guildId.asLong()
            val guildName = pinboard.guild
            val existing = Guild.findById(id)
            return if (existing != null) existing.apply { name = guildName } else {
                Guild.new(id) {
                    name = guildName
                }
            }
        }
    }

    var name by Guilds.name

    override fun toString(): String {
        return "Guild($id, '$name')"
    }
}