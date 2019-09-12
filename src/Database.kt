import discord4j.core.`object`.util.Snowflake
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

object PinDB {
    init {
        Database.connect("jdbc:sqlite:my.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    }

    fun hasBeenPinned(pinnedPost: Snowflake): Boolean {
        val id = pinnedPost.asLong()
        return !PinboardPost.find {
            PinboardPosts.pinnedPost eq id
        }.empty()
    }

    fun recordPinning(pinboardPostId: Snowflake, author: Snowflake, pinnedPost: Snowflake, pinCount: Int) {
        TODO()
    }

    fun removePinning(pinboardPostId: Snowflake) {
        TODO()
    }

    fun updatePinCount(pinboardPostId: Snowflake, pinCount: Int) {
        TODO()
    }

    fun findPinboardPost(pinnedPost: Snowflake): Snowflake? {
        TODO()
    }

}

object PinboardPosts : LongIdTable() {
    val author = long("author")
    val pinnedPost = long("pinned_post")
    val pinCount = long("pin_count")
}

class PinboardPost(pinboardPost: EntityID<Long>) : Entity<Long>(pinboardPost) {
    companion object : EntityClass<Long, PinboardPost>(PinboardPosts)

    val pinboardPost by PinboardPosts.id
    val author by PinboardPosts.author
    val pinnedPost by PinboardPosts.pinnedPost
    val pinCount by PinboardPosts.pinCount
}