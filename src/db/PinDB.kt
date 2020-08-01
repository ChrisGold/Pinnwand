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
        }
    }

    fun registerGuilds(guilds: Collection<Pinboard>) = transaction {
        guilds.forEach {
            val guild = Guild.fromPinboard(it)
            println("Registering guild: $guild")
        }
    }
}

private fun DBConfig.connect(): Database {
    return if (creds != null) {
        val creds = creds!!
        Database.connect(uri, driver, creds.user, creds.password)
    } else Database.connect(uri, driver)
}