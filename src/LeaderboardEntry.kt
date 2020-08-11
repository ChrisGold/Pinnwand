import discord4j.core.`object`.util.Snowflake

data class LeaderboardEntry(val author: Snowflake, val totalPins: Int)

data class TopPostEntry(val message: Snowflake, val author: Snowflake, val totalPins: Int)