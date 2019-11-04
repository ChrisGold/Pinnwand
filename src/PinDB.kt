import discord4j.core.`object`.util.Snowflake
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

/**
 * A singleton managing access to the database
 */
object PinDB {
    //Init the database with the JDBC driver for SQLite
    private val db = Database.connect("jdbc:sqlite:pins.db", "org.sqlite.JDBC")

    init {
        //SQLite doesn't support all transaction modes
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            //Ensure that all tables are present
            SchemaUtils.createMissingTablesAndColumns(PinboardPosts)
        }
    }

    /**
     * Record a new pinned post
     */
    fun recordPinning(
        guildId: Snowflake,
        pinboardPostId: Snowflake,
        author: Snowflake,
        pinnedPost: Snowflake,
        pinCount: Int
    ) =
        transaction(db) {
            PinboardPost.new {
                this.pinboardPost = EntityID(pinboardPostId.asLong(), PinboardPosts)
                this.guild = guildId.asLong()
                this.author = author.asLong()
                this.pinnedPost = pinnedPost.asLong()
                this.pinCount = pinCount
            }
        }

    /**
     * Remove a pinboard post
     */
    fun removePinning(pinboardPostId: Snowflake) = transaction(db) {
        PinboardPosts.deleteWhere {
            PinboardPosts.id eq pinboardPostId.asLong()
        }
    }

    /**
     * Update the pin count on a pinboard post
     */
    fun updatePinCount(pinnedPost: Snowflake, pinCount: Int) = transaction(db) {
        PinboardPosts.update({ PinboardPosts.pinnedPost eq pinnedPost.asLong() }) {
            it[PinboardPosts.pinCount] = pinCount
        }
    }

    /**
     * Find a specific post on the pinboard
     */
    fun findPinboardPost(pinnedPost: Snowflake): Snowflake? = transaction(db) {
        PinboardPost.find {
            PinboardPosts.pinnedPost eq pinnedPost.asLong()
        }.firstOrNull()?.pinboardPost?.let {
            Snowflake.of(it.value)
        }
    }

    fun tallyLeaderboard(guildId: Snowflake) = transaction {
        //TODO: Make this respect different guilds
        val results = PinboardPosts.slice(PinboardPosts.author, PinboardPosts.pinCount.sum())
            .select { PinboardPosts.guild eq guildId.asLong() }
            .groupBy(PinboardPosts.author)
            .execute(this)
        val list = ArrayList<LeaderboardEntry>()
        results?.let {
            while (it.next()) {
                //Remember: cursor fields are 1-indexed
                val author = it.getLong(1)
                val tally = it.getInt(2)
                list.add(LeaderboardEntry(Snowflake.of(author), tally))
            }
        }
        //Maybe do this as an SQL order-by
        list.sortByDescending { it.pinTotal }
        list
    }

    fun allPinnedPosts() = transaction {
        PinboardPosts.slice(PinboardPosts.pinnedPost).selectAll().map { Snowflake.of(it[PinboardPosts.pinnedPost]) }
    }

    fun truncateGuild(guildId: Snowflake) = transaction {
        PinboardPosts.deleteWhere {
            PinboardPosts.guild eq guildId.asLong()
        }
    }
}

object PinboardPosts : LongIdTable("pinboard_posts") {
    val guild = long("guild")
    val author = long("author")
    val pinnedPost = long("pinned_post")
    val pinCount = integer("pin_count")
}

class PinboardPost(pinboardPost: EntityID<Long>) : Entity<Long>(pinboardPost) {
    companion object : EntityClass<Long, PinboardPost>(PinboardPosts)

    var pinboardPost by PinboardPosts.id
    var guild by PinboardPosts.guild
    var author by PinboardPosts.author
    var pinnedPost by PinboardPosts.pinnedPost
    var pinCount by PinboardPosts.pinCount
}

data class LeaderboardEntry(val author: Snowflake, val pinTotal: Int)