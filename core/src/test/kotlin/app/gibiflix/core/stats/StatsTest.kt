package app.gibiflix.core.stats

import app.gibiflix.core.backup.BackupData
import app.gibiflix.core.backup.BackupMerge
import app.gibiflix.core.backup.CollectionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(d: Int, h: Int = 12, month: Int = 10) = LocalDateTime.of(2026, month, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun s(day: Int, minutes: Int, comic: String = "a", finished: Boolean = false, month: Int = 10) =
        Session(comic, at(day, month = month), at(day, month = month) + minutes * 60_000L, 5, 10, finished)

    @Test fun shortSessionsDoNotCount() {
        val r = StatsCalculator.compute(listOf(Session("a", 0, 20_000, 1, 1, false)), at(27), zone)
        assertEquals(0, r.sessions); assertEquals(0L, r.totalMs)
    }

    @Test fun weekMonthTotalsAndStreak() {
        val list = listOf(s(27, 10), s(26, 20), s(25, 5), s(23, 30), s(10, 60), s(30, 15, month = 9))
        val r = StatsCalculator.compute(list, at(27, 20), zone)
        assertEquals((10 + 20 + 5 + 30) * 60_000L, r.weekMs)
        assertEquals((10 + 20 + 5 + 30 + 60) * 60_000L, r.monthMs)
        assertEquals(3, r.streakDays)
    }

    @Test fun streakCountsFromYesterdayWhenTodayEmpty() {
        val r = StatsCalculator.compute(listOf(s(26, 10), s(25, 10)), at(27, 9), zone)
        assertEquals(2, r.streakDays)
    }

    @Test fun finishedPerMonthCountsDistinctComics() {
        val list = listOf(s(3, 10, "a", true), s(4, 10, "a", true), s(5, 10, "b", true), s(30, 10, "c", true, month = 9))
        val r = StatsCalculator.compute(list, at(27), zone, months = 3)
        assertEquals(listOf(0, 1, 2), r.finishedPerMonth.map { it.count })
        assertEquals(20 * 60_000L, r.perComicMs["a"])
    }

    @Test fun collectionsMergeByName() {
        val local = listOf(CollectionRecord("Férias", 1, 1, listOf("a")), CollectionRecord("Outra", 1, 1, listOf("x")))
        val inc = listOf(CollectionRecord("férias", 1, 5, listOf("a", "b")), CollectionRecord("Outra", 1, 9, listOf("x")), CollectionRecord("Nova", 2, 2, listOf("c")))
        val m = BackupMerge.mergeCollections(local, inc).associateBy { it.name }
        assertEquals(listOf("a", "b"), m["Férias"]!!.comicIds)
        assertTrue("Outra" !in m)
        assertEquals(listOf("c"), m["Nova"]!!.comicIds)
    }

    @Test fun backupJsonRoundTrip() {
        val b = BackupData(createdAt = 1, device = "x", collections = listOf(CollectionRecord("a", 1, 2, listOf("c"))))
        assertEquals(b, BackupData.json.decodeFromString(BackupData.serializer(), BackupData.json.encodeToString(BackupData.serializer(), b)))
    }
}
