package db

import DBConfig
import LeaderboardEntry
import Pinboard
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import sf
import java.sql.Connection

class PinDB(val dbConfig: DBConfig) {
    val db = dbConfig.connect()

    init {
        //SQLite doesn't support all transaction modes
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            //Ensure that all tables are present
            SchemaUtils.createMissingTablesAndColumns(Guilds)
            SchemaUtils.createMissingTablesAndColumns(PinnedMessages)
            SchemaUtils.createMissingTablesAndColumns(PinboardPosts)
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

    fun removePinboardPost(post: Long) = transaction {
        PinboardPost.findById(post)?.delete()
    }

    fun findPinboardPost(originalMessage: Long) = transaction {
        PinboardPost.find {
            PinboardPosts.message eq originalMessage
        }.toList().getOrNull(0)
    }

    fun tally(guildId: Long) = transaction {
        val results = PinnedMessages
            .slice(PinnedMessages.author, PinnedMessages.pinCount.sum(), PinnedMessages.guild)
            .select {
                PinnedMessages.guild eq guildId
            }.execute(this)
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
}

private fun DBConfig.connect(): Database {
    return if (creds != null) {
        val creds = creds!!
        Database.connect(uri, driver, creds.user, creds.password)
    } else Database.connect(uri, driver)
}