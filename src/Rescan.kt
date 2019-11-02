import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import reactor.core.publisher.Flux
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors

class Rescan(val pinboard: Pinboard) {
    private val client = pinboard.client

    operator fun invoke() {
        val pinnedPosts = getPinnedPosts().collect(Collectors.toList())
        pinnedPosts.subscribe {
            it.forEach { println(PinPostData.from(it)) }
        }
    }

    fun getPinnedPosts(): Flux<Message> {
        val oneYearAgo = Instant.now().minus(1, ChronoUnit.MONTHS)
        val channels = getChannels()
        val chunks = channels.flatMap { channel ->
            channel.getMessagesAfter(Snowflake.of(oneYearAgo)).filter {
                pinboard.isPinnable(it)
            }
        }.groupBy(Message::getDatestamp)
        return chunks.flatMap {
            logger.info("Reading messages of ${it.key()}.")
            it
        }
    }

    private fun getChannels(): Flux<MessageChannel> {
        val guild = client.getGuildById(pinboard.guildId)
        val channels = guild.flatMapMany { it.channels }
        return channels.filter {
            it is MessageChannel
        }.map {
            it as MessageChannel
        }
    }
}