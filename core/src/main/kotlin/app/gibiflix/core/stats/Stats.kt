package app.gibiflix.core.stats

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Session(
    val comicId: String,
    val startedAt: Long,
    val endedAt: Long,
    val pages: Int,
    val panels: Int,
    val finishedComic: Boolean,
) {
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0)
}

data class MonthCount(val month: YearMonth, val count: Int)

data class StatsSummary(
    val weekMs: Long,
    val monthMs: Long,
    val totalMs: Long,
    val streakDays: Int,
    val finishedPerMonth: List<MonthCount>,
    val perComicMs: Map<String, Long>,
    val sessions: Int,
)

object StatsCalculator {
    const val MIN_SESSION_MS = 30_000L

    /**
     * Sessões curtas (< 30 s) não contam. Semana = últimos 7 dias; mês = mês corrente.
     * Sequência = dias seguidos com leitura até hoje (ou até ontem, se hoje ainda não leu).
     */
    fun compute(all: List<Session>, now: Long, zone: ZoneId, months: Int = 6): StatsSummary {
        val sessions = all.filter { it.durationMs >= MIN_SESSION_MS }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val weekStart = today.minusDays(6)
        val month = YearMonth.from(today)
        val weekMs = sessions.filter { !day(it.startedAt).isBefore(weekStart) }.sumOf { it.durationMs }
        val monthMs = sessions.filter { YearMonth.from(day(it.startedAt)) == month }.sumOf { it.durationMs }
        val days = sessions.map { day(it.startedAt) }.toSet()
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) { streak++; cursor = cursor.minusDays(1) }
        val finished = sessions.filter { it.finishedComic }
            .groupBy { YearMonth.from(day(it.endedAt)) }
            .mapValues { (_, v) -> v.map { it.comicId }.toSet().size }
        val perMonth = (months - 1 downTo 0).map { k -> month.minusMonths(k.toLong()) }.map { MonthCount(it, finished[it] ?: 0) }
        return StatsSummary(
            weekMs = weekMs,
            monthMs = monthMs,
            totalMs = sessions.sumOf { it.durationMs },
            streakDays = streak,
            finishedPerMonth = perMonth,
            perComicMs = sessions.groupBy { it.comicId }.mapValues { (_, v) -> v.sumOf { it.durationMs } },
            sessions = sessions.size,
        )
    }

    fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}
