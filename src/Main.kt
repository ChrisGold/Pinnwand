import discord4j.core.DiscordClientBuilder
import java.io.File

fun main(args: Array<String>) {
    val token = args.getOrElse(0) { "discord.token.txt" }.let { File(it).readText(Charsets.UTF_8) }
    val client = DiscordClientBuilder(token).build()
    client.login().block()
}