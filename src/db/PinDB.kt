package db

import DBConfig
import LeaderboardEntry
import Pinboard
import TopPostEntry
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import sf
import java.sql.Connection

class PinDB(dbConfig: DBConfig) {
    val db = dbConfig.connect()

    init {
        //SQLite doesn't support all transaction modes
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            //Ensure that all tables are present
            SchemaUtils.createMissingTablesAndColumns(Guilds, PinnedMessages, PinboardPosts)
        }
    }

    fun registerGuilds(guilds: Collection<Pinboard>) = transaction {
        guilds.forEach {
            val guild = Guild.fromPinboard(it)
            println("Registering guild: $guild")
        }
    }

    fun registerPinning(guild: Long, message: Long, author: Long, pinCount: Int) = transaction {
        val existing = PinnedMessage.findById(message)
        if (existing == null) {
            PinnedMessage.new(message) {
                this.author = author
                this.guild = Guild.findById(guild)!!
                this.pinCount = pinCount
            }
        } else {
            existing.pinCount = pinCount
        }
    }

    fun removePinning(message: Long) = transaction {
        PinnedMessage.findById(message)?.delete()
    }

    fun savePinboardPost(guild: Long, post: Long, message: Long, content: String, imageUrl: String? = null) =
        transaction {
            val existing = PinboardPost.findById(post)
            existing?.apply {
                this.guild = Guild.findById(guild)!!
                this.message = PinnedMessage.findById(message)!!
                this.content = content
                this.imageUrl = imageUrl
            }
                ?: PinboardPost.new(post) {
                    this.guild = Guild.findById(guild)!!
                    this.message = PinnedMessage.findById(message)!!
                    this.content = content
                    this.imageUrl = imageUrl
                }
        }

    fun findPinboardPostById(post: Long) = transaction {
        PinboardPost.findById(post)
    }

    fun removePinboardPost(post: Long) = transaction {
        PinboardPost.findById(post)?.delete()
    }

    fun findPinboardPostByOriginalMessage(originalMessage: Long) = transaction {
        PinboardPost.find {
            PinboardPosts.message eq originalMessage
        }.toList().getOrNull(0)
    }

    fun tally(guildId: Long) = transaction {
        val results = PinnedMessages
            .slice(PinnedMessages.author, PinnedMessages.pinCount.sum(), PinnedMessages.guild)
            .select {
                PinnedMessages.guild eq guildId
            }.groupBy(PinnedMessages.author)
            .execute(this)
        val list = ArrayList<LeaderboardEntry>()
        results?.let {
            while (it.next()) {
                //Remember: cursor fields are 1-indexed
                val author = it.getLong(1)
                val tally = it.getInt(2)
                list.add(LeaderboardEntry(author.sf, tally))
            }
        }
        //Maybe do this as an SQL order-by
        list.sortByDescending { it.totalPins }
        list
    }

    fun topPosts(guildId: Long, postCount: Int) = transaction {
        val results = PinnedMessages.join(
            PinboardPosts,
            JoinType.LEFT,
            additionalConstraint = { PinnedMessages.id eq PinboardPosts.message })
            .slice(
                PinnedMessages.id,
                PinnedMessages.author,
                PinnedMessages.pinCount,
                PinboardPosts.content,
                PinnedMessages.guild
            )
            .select {
                PinnedMessages.guild eq guildId
            }
            .orderBy(PinnedMessages.pinCount, SortOrder.DESC)
            .limit(postCount)
            .execute(this)
        val topPosts = ArrayList<TopPostEntry>(postCount)

        results?.let {
            while (it.next()) {
                //Remember: cursor fields are 1-indexed
                //val id = it.getLong(1)
                val author = it.getLong(2)
                val pinCount = it.getInt(3)
                val content = it.getString(4)
                topPosts.add(TopPostEntry(content, author.sf, pinCount))
            }
        }
        topPosts
    }
}

private fun DBConfig.connect(): Database {
    return if (creds != null) {
        val creds = creds!!
        Database.connect(uri, driver, creds.user, creds.password)
    } else Database.connect(uri, driver)
}