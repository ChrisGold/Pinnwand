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
import java.time.Duration
import java.util.*

fun main(args: Array<String>) {
    //Read config and build Discord Client
    val configFile = File((args.getOrNull(0) ?: "pinbot.config.yaml"))
    val (client, pinboards) = Config.read(configFile) {
        retryOptions = RetryOptions(Duration.ofSeconds(10), Duration.ofMinutes(30), 8, Schedulers.elastic())
    }
    fun onGuild(snowflake: Snowflake?, closure: Pinboard.() -> Unit) = pinboards[snowflake]?.let { it.closure() }

    //Listen to pin events
    client.eventDispatcher.on(ReactionAddEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe { event ->
        onGuild(event.guildId.k) { this(event) }
    }

    //Listen to unpin events
    client.eventDispatcher.on(ReactionRemoveEvent::class.java).filter {
        isPinEmoji(it.emoji)
    }.subscribe { event ->
        onGuild(event.guildId.k) { this(event) }
    }

    //Listen to deleted messages
    client.eventDispatcher.on(MessageDeleteEvent::class.java).subscribe { deletion ->
        //MessageDeleteEvent doesn't include the guild, so we send this event to every guild we have
        pinboards.values.forEach { it(deletion) }
    }

    client.eventDispatcher.on(MessageCreateEvent::class.java).subscribe { event ->
        onGuild(event.guildId.k) { this(event) }
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

const val pin = "\uD83D\uDCCC"
val logger = LogManager.getLogger("eu.goldapp.Pinnwand")