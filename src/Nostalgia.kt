import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

fun nostalgia(a: String, b: String, c: String): String {
    val start = Entry.of(a)
    val middle = Entry.of(b)
    val end = Entry.of(c)

    val startDate = if (start is Entry.QuestionMark) {
        val m = (middle as Entry.Time).date
        val e = (end as Entry.Time).date
        m - Period.between(m, e)
    } else (start as Entry.Time).date

    val middleDate = if (middle is Entry.QuestionMark) {
        val s = (start as Entry.Time).date
        val e = (end as Entry.Time).date
        e.minusDays((ChronoUnit.DAYS.between(s, e) / 2).toLong())
    } else (middle as Entry.Time).date

    val endDate = if (end is Entry.QuestionMark) {
        val s = (start as Entry.Time).date
        val m = (middle as Entry.Time).date
        m + Period.between(s, m)
    } else (end as Entry.Time).date

    val days = ChronoUnit.DAYS.between(startDate, middleDate)

    return ("$days days have passed between $startDate and $middleDate\n$days days have passed between $middleDate and $endDate")
}

sealed class Entry {
    data class Time(val date: LocalDate) : Entry()
    object QuestionMark : Entry()
    companion object {
        fun of(string: String): Entry {
            val trimmed = string.trim()
            if (trimmed == "?") return QuestionMark
            else if (trimmed == "today") return Time(LocalDate.now())
            else return Time(LocalDate.parse(string))
        }
    }
}