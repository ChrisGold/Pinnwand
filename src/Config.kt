import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.util.Snowflake
import discord4j.gateway.retry.RetryOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import reactor.core.scheduler.Schedulers
import java.io.File
import java.time.Duration

/**
 * An instance of a processed configuration file
 */
data class Config(val client: DiscordClient, val pinboards: Map<Snowflake, Pinboard>) {
    companion object {
        private val yaml = Yaml(Constructor(YAMLConfig::class.java))
        fun read(file: File): Config {
            val configData = yaml.load<YAMLConfig>(file.reader())
            val tokenFile = configData.token
            val token = File(tokenFile).readText()
            val client = DiscordClientBuilder(token).run {
                retryOptions = RetryOptions(Duration.ofSeconds(10), Duration.ofMinutes(5), 8, Schedulers.elastic())
                build()
            }
            val pinboardConfigs = configData.guilds
            val pinboards = pinboardConfigs.map {
                val guild = it.guild
                val guildId = Snowflake.of(it.guildId)
                val pinboardChannelId = Snowflake.of(it.pinboardChannel)
                guildId to Pinboard(client, guild, guildId, pinboardChannelId, it.threshold)
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
    var threshold: Int = 5
)