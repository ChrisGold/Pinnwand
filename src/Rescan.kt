import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors

class Rescan(val pinboard: Pinboard) {
    private val client = pinboard.client

    operator fun invoke() {
        pinboard.disable()
        logger.info("Getting all posts on the pinboard")
        val pinboardPosts = getPostsOnPinboard().blockingCollect()
        logger.info("Found ${pinboardPosts.size} posts on pinboard channel")
        logger.info("Getting all posts that have been pinned by users")
        val pinnedPosts = getPinnedPosts().blockingCollect()
        logger.info("Found ${pinnedPosts.size} pinned posts")

        val pinboardPostsByMessage =
            pinboardPosts.mapNotNull { (pinboardPost, pinnedPostData) -> pinnedPostData?.let { it.messageId to pinboardPost } }
                .toMap()
        val pinboardPostIds = pinboardPostsByMessage.map { it.key.asLong() }.toSet()
        val pinnedPostIds = pinnedPosts.map { it.messageId.asLong() }.toSet()

        val unposted = pinboardPostIds - pinnedPostIds
        logger.info("${unposted.size} pinned post have not been posted.")

        val falselyPosted = pinnedPostIds - pinboardPostIds
        logger.info("${falselyPosted.size} posts on the pinboard have not been posted.")

        PinDB.truncateGuild(pinboard.guildId)

        //Ensure all existing posts are pinned
        //Go through all pinnedPosts:
        pinnedPosts.forEach { ppd ->
            val pinboardPost = pinboardPostsByMessage[ppd.messageId]
            val updatedPost = if (pinboardPost != null) {
                //If pinnedPost is on pinboard, update
                pinboard.updatePinPost(pinboardPost.id, ppd)
            } else {
                //If pinnedPost is not on pinboard, pin
                pinboard.createPinPost(ppd)
            }
            updatedPost.subscribe {
                //Record pinning
                PinDB.recordPinning(pinboard.guildId, it.id, ppd.author ?: Snowflake.of(0), ppd.messageId, ppd.pinCount)
            }
        }

        //Go through all pinboardPosts
        pinboardPosts.forEach { (pinboardPost, pinnedMessage) ->
            //If pinboardPost is not pinned, delete
            if (pinnedMessage == null) {
                pinboard.deletePinPost(pinboardPost.id)
            }
        }
        pinboard.enable()
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
            }
                .map { it to it.extractLink().o }
                .filter { (pinboardMessage, link) -> link.isPresent }
                .map { (pinboardMessage, link) -> pinboardMessage to link.get() }
                .map { (pinboardMessage, link) -> pinboardMessage to decomposeLink(link) }
                .flatMap { (pinboardMessage, link) ->
                    try {
                        client.getMessageById(link.channelId, link.messageId)
                            .map { pinboardMessage to it }
                    } catch (ex: Exception) {
                        Mono.just(pinboardMessage to null)
                    }
                }
                .map { (pinboardMessage, link) -> pinboardMessage to link?.let { PinPostData.from(link) } }
        }

    private fun <T> Flux<T>.blockingCollect() = collect(Collectors.toList()).block()
}