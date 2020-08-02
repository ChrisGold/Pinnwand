package db

import DBConfig
import Pinboard
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
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
}

private fun DBConfig.connect(): Database {
    return if (creds != null) {
        val creds = creds!!
        Database.connect(uri, driver, creds.user, creds.password)
    } else Database.connect(uri, driver)
}