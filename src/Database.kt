import discord4j.core.`object`.util.Snowflake
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

object PinDB {
    val db = Database.connect("jdbc:sqlite:pins.db", "org.sqlite.JDBC")
    init {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(PinboardPosts)
        }
    }

    fun hasBeenPinned(pinnedPost: Snowflake): Boolean {
        val id = pinnedPost.asLong()
        return !PinboardPost.find {
            PinboardPosts.pinnedPost eq id
        }.empty()
    }

    fun recordPinning(pinboardPostId: Snowflake, author: Snowflake, pinnedPost: Snowflake, pinCount: Int) =
        transaction(db) {
            PinboardPost.new {
                this.pinboardPost = EntityID(pinboardPostId.asLong(), PinboardPosts)
                this.author = author.asLong()
                this.pinnedPost = pinnedPost.asLong()
                this.pinCount = pinCount
            }
        }

    fun removePinning(pinboardPostId: Snowflake) = transaction(db) {
        PinboardPosts.deleteWhere {
            PinboardPosts.id eq pinboardPostId.asLong()
        }
    }

    fun updatePinCount(pinnedPost: Snowflake, pinCount: Int) = transaction(db) {
        PinboardPost.find {
            PinboardPosts.pinnedPost eq pinnedPost.asLong()
        }.firstOrNull()?.let {
            it.pinCount = pinCount
        }
    }

    fun findPinboardPost(pinnedPost: Snowflake): Snowflake? = transaction(db) {
        PinboardPost.find {
            PinboardPosts.pinnedPost eq pinnedPost.asLong()
        }.firstOrNull()?.pinboardPost?.let {
            Snowflake.of(it.value)
        }
    }
}

object PinboardPosts : LongIdTable("pinboard_posts") {
    val author = long("author")
    val pinnedPost = long("pinned_post")
    val pinCount = integer("pin_count")
}

class PinboardPost(pinboardPost: EntityID<Long>) : Entity<Long>(pinboardPost) {
    companion object : EntityClass<Long, PinboardPost>(PinboardPosts)

    var pinboardPost by PinboardPosts.id
    var author by PinboardPosts.author
    var pinnedPost by PinboardPosts.pinnedPost
    var pinCount by PinboardPosts.pinCount
}