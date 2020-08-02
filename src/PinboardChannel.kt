import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import reactor.core.publisher.Mono

class PinboardChannel(val client: DiscordClient, val guildId: Snowflake, val channel: MessageChannel) {
    fun createEmptyMessage(): Mono<Message> {
        return channel.createMessage("...")
    }
}