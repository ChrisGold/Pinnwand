import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
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
        logger.info("Getting a list of all channels")
        val channels = getChannels()
        val oneYearAgo = Instant.now().minus(30, ChronoUnit.DAYS)
        val chunks = channels.flatMap { channel ->
            logger.info("Checking channel: ${channel.name}")
            channel.getMessagesAfter(Snowflake.of(oneYearAgo)).filter {
                pinboard.isPinnable(it)
            }
        }.groupBy(Message::getDatestamp)
        return chunks.flatMap {
            logger.info("Reading messages of ${it.key()}.")
            it
        }
    }

    private val readAccess = setOf(Permission.READ_MESSAGE_HISTORY, Permission.VIEW_CHANNEL)
    private fun getChannels(): Flux<TextChannel> {
        return client.getGuildById(pinboard.guildId)
            .flatMapMany { it.channels }
            .ofType(TextChannel::class.java)
            .filterWhen {
                it.getEffectivePermissions(client.selfId.get()).map {
                    it.containsAll(readAccess)
                }
            }
    }
}