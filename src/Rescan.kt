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
        logger.info("Getting all posts on the pinboard")
        val pinboardPosts = getPostsOnPinboard().blockingCollect()
        logger.info("Found ${pinboardPosts.size} posts on pinboard channel")
        logger.info("Getting all posts that have been pinned by users")
        val pinnedPosts = getPinnedPosts().blockingCollect()
        logger.info("Found ${pinnedPosts.size} pinned posts")

        val pinboardPostIds = pinboardPosts.map { it.messageId.asLong() }.toSet()
        val pinnedPostIds = pinnedPosts.map { it.messageId.asLong() }.toSet()

        val unposted = pinboardPostIds - pinnedPostIds
        logger.info("${unposted.size} pinned post have not been posted.")

        val falselyPosted = pinnedPostIds - pinboardPostIds
        logger.info("${falselyPosted.size} posts on the pinboard have not been posted.")
    }

    private val oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS)
    fun getPinnedPosts(): Flux<PinPostData> {
        logger.info("Getting a list of all channels")
        val channels = getChannels()
        val chunks = channels.flatMap { channel ->
            logger.info("Checking channel: ${channel.name}")
            channel.getMessagesAfter(Snowflake.of(oneMonthAgo)).filter {
                pinboard.isPinnable(it)
            }
        }.groupBy(Message::getDatestamp)
        return chunks.flatMap {
            logger.info("Reading messages of ${it.key()}.")
            it
        }.map { PinPostData.from(it) }
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

    private fun getPostsOnPinboard() =
        client.getChannelById(pinboard.pinboardChannelId).flatMapMany {
            it as TextChannel
            it.getMessagesAfter(Snowflake.of(oneMonthAgo)).filter {
                it.author.k?.id == client.selfId.k
            }.map { it.extractLink() }.filter { it != null }.map { it as String }
                .map { decomposeLink(it) }.flatMap { client.getMessageById(it.channelId, it.messageId) }
                .map { PinPostData.from(it) }
        }

    private fun <T> Flux<T>.blockingCollect() = collect(Collectors.toList()).block()
}