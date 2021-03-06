import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.MessageDeleteEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.event.domain.message.ReactionRemoveEvent
import discord4j.gateway.retry.RetryOptions
import org.apache.logging.log4j.LogManager
import reactor.core.scheduler.Schedulers
import java.io.File
import java.io.PrintStream
import java.time.Duration
import java.util.*

fun main(args: Array<String>) {
    initLogging()
    //Read config and build Discord Client
    val configFile = File((args.getOrNull(0) ?: "pinbot.config.yaml"))
    val (client, _, pinboards) = Config.read(configFile) {
        retryOptions = RetryOptions(Duration.ofSeconds(10), Duration.ofMinutes(30), 8, Schedulers.elastic())
    }
    fun onGuild(snowflake: Snowflake?, closure: Pinboard.() -> Unit) = pinboards[snowflake]?.let { it.closure() }

    //Listen to react events
    client.eventDispatcher.on(ReactionAddEvent::class.java).subscribe { event ->
        onGuild(event.guildId.k) { onAddReact(event) }
    }

    //Listen to remove-react events
    client.eventDispatcher.on(ReactionRemoveEvent::class.java).subscribe { event ->
        onGuild(event.guildId.k) { onRemoveReact(event) }
    }

    //Listen to deleted messages
    client.eventDispatcher.on(MessageDeleteEvent::class.java).subscribe { deletion ->
        //MessageDeleteEvent doesn't include the guild, so we send this event to every guild we have
        pinboards.values.forEach { it.onDeleteMessage(deletion) }
    }

    client.eventDispatcher.on(MessageCreateEvent::class.java).subscribe { creation ->
        val content = creation.message.content.k ?: return@subscribe
        if (content.startsWith("*leaderboard")) {
            val sections = content.trim().split(' ')
            val place = sections.getOrNull(1)?.toInt() ?: 0
            onGuild(creation.guildId.k) { showLeaderboard(creation.message.channelId, place) }
        } else if (content.startsWith("*rescan pinboard")) {
            onGuild(creation.guildId.k) { rescanPinboard() }
        } else if (content.startsWith("*top posts")) {
            onGuild(creation.guildId.k) { topPosts(creation.message.channelId, 10) }
        } else if (content.startsWith("*nostalgia")) {
            val arguments = content.trim().split(' ')
            val nostalgia = if (arguments.size >= 4) try {
                nostalgia(arguments[1], arguments[2], arguments[3])
            } catch (ex: Exception) {
                "Parsing Error!"
            } else "Wrong number of arguments!"
            onGuild(creation.guildId.k) { sendMessage(creation.message.channelId, nostalgia) }
        }
    }

    //Login
    client.login().block()
}

/**
 * A ReactionEmoji is a pin emoji if and only if it is the Unicode "📌"
 */
fun isPinEmoji(emoji: ReactionEmoji): Boolean {
    return emoji.asUnicodeEmoji().let { it.isPresent && it.get().raw == pin }
}

/**
 * Helper method for transforming Java optionals into Kotlin nullables
 */
val <T> Optional<T>.k get() = if (this.isPresent) this.get() else null
val <T> T?.o get() = Optional.ofNullable(this)

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")

fun initLogging() {
    fun errorProxy(realStream: PrintStream) = object : PrintStream(realStream) {
        override fun print(string: String) {
            super.print(string);
            val logger = LogManager.getLogger()
            logger.error(string)
        }
    }

    fun stdProxy(realStream: PrintStream) = object : PrintStream(realStream) {
        override fun print(string: String) {
            super.print(string);
            val logger = LogManager.getLogger()
            logger.info(string)
        }
    }
    System.setOut(stdProxy(System.out))
    System.setErr(errorProxy(System.err))
}