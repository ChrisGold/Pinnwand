import db.PinDB
import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.util.Snowflake
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File

/**
 * An instance of a processed configuration file
 */
data class Config(val client: DiscordClient, val db: PinDB, val pinboards: Map<Snowflake, Pinboard>) {
    companion object {
        private val yaml = Yaml(Constructor(YAMLConfig::class.java))
        fun read(file: File, discordClientConfig: DiscordClientBuilder.() -> Unit): Config {
            val configData = yaml.load<YAMLConfig>(file.reader())
            val tokenFile = configData.token
            val token = File(tokenFile).readText()
            val client = DiscordClientBuilder(token).run {
                discordClientConfig()
                build()
            }
            val pinboardConfigs = configData.guilds
            val pinDB = PinDB(configData.database)
            val pinboards = pinboardConfigs.mapNotNull {
                val guild = it.guild
                val guildId = Snowflake.of(it.guildId)
                val pinboardChannelId = Snowflake.of(it.pinboardChannel)
                val pin = it.pin
                val pinboard = try {
                    Pinboard(client, pinDB, guild, guildId, pinboardChannelId, pin, it.threshold)
                } catch (ex: Exception) {
                    println("Could not initialize $guild")
                    ex.printStackTrace()
                    null
                }
                pinboard?.let { guildId to pinboard }
            }.toMap()
            pinDB.registerGuilds(pinboards.values)
            return Config(client, pinDB, pinboards)
        }
    }
}

data class YAMLConfig(
    var token: String = "",
    var database: DBConfig = DBConfig(),
    var guilds: List<YAMLPinboard> = emptyList()
)

data class YAMLPinboard(
    var guild: String = "",
    var guildId: Long = 0,
    var pinboardChannel: Long = 0,
    var pin: String = "",
    var threshold: Int = 5
)

data class DBConfig(var uri: String = "", var driver: String = "", var creds: DBCredentials? = null)

data class DBCredentials(var user: String = "", var password: String = "")