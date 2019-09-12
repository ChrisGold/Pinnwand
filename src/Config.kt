import discord4j.core.`object`.util.Snowflake
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File

data class Config(val token: String, val pinboards: Map<Snowflake, Pinboard>) {
    companion object {
        val yaml = Yaml(Constructor(YAMLConfig::class.java))
        fun read(file: File): Config {
            val configData = yaml.load<YAMLConfig>(file.reader())
            val tokenFile = configData.token
            val token = File(tokenFile).readText()
            val pinboardConfigs = configData.guilds
            val pinboards = pinboardConfigs.map {
                val guild = it.guild
                val guildId = Snowflake.of(it.guildId)
                val pinboardChannelId = Snowflake.of(it.pinboardChannel)
                guildId to Pinboard(guild, guildId, pinboardChannelId)
            }.toMap()
            return Config(token, pinboards)
        }
    }
}

data class YAMLConfig(var token: String = "", var guilds: List<YAMLPinboard> = emptyList())

data class YAMLPinboard(var guild: String = "", var guildId: Long = 0, var pinboardChannel: Long = 0)