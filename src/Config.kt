import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.util.Snowflake
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File

/**
 * An instance of a processed configuration file
 */
data class Config(val client: DiscordClient, val pinboards: Map<Snowflake, Pinboard>) {
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
            val pinboards = pinboardConfigs.map {
                val guild = it.guild
                val guildId = Snowflake.of(it.guildId)
                val pinboardChannelId = Snowflake.of(it.pinboardChannel)
                val pin = it.pin
                guildId to Pinboard(client, guild, guildId, pinboardChannelId, pin, it.threshold)
            }.toMap()
            return Config(client, pinboards)
        }
    }
}

data class YAMLConfig(var token: String = "", var guilds: List<YAMLPinboard> = emptyList())

data class YAMLPinboard(
    var guild: String = "",
    var guildId: Long = 0,
    var pinboardChannel: Long = 0,
    var pin: String = "",
    var threshold: Int = 5
)

data class DBConfig(val uri: String, val driver: String, val creds: DBCredentials? = null)

data class DBCredentials(val user: String, val password: String)