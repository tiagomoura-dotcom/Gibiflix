package app.gibiflix.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {
    private fun p(id: String, page: Int, at: Long, dev: String = "a") = ProgressRecord(id, page, 0, "panel", at, dev)
    private fun b(id: String, at: Long, deleted: Boolean = false) = BookmarkRecord(id, "c", 1, 0, "", 0, at, deleted, "x")

    @Test fun newerRemoteProgressWins() {
        val local = listOf(p("c1", 5, 100), p("c2", 3, 300))
        val remote = listOf(p("c1", 9, 200, "b"), p("c2", 1, 250, "b"), p("c3", 2, 10, "b"))
        val changes = SyncRules.mergeProgress(local, remote).associateBy { it.comicId }
        assertEquals(9, changes["c1"]!!.page)
        assertTrue("c2" !in changes)
        assertEquals(2, changes["c3"]!!.page)
    }

    @Test fun bothDirectionsConverge() {
        val a = listOf(p("c1", 5, 100), p("c2", 7, 500))
        val b = listOf(p("c1", 9, 200, "b"), p("c2", 1, 400, "b"))
        val aAfter = (a.associateBy { it.comicId } + SyncRules.mergeProgress(a, b).associateBy { it.comicId }).values
        val bAfter = (b.associateBy { it.comicId } + SyncRules.mergeProgress(b, a).associateBy { it.comicId }).values
        assertEquals(aAfter.sortedBy { it.comicId }, bAfter.sortedBy { it.comicId })
        assertEquals(9, aAfter.first { it.comicId == "c1" }.page)
        assertEquals(7, aAfter.first { it.comicId == "c2" }.page)
    }

    @Test fun duplicateRemoteRecordsUseLatest() {
        val changes = SyncRules.mergeProgress(emptyList(), listOf(p("c", 1, 10), p("c", 4, 20)))
        assertEquals(listOf(4), changes.map { it.page })
    }

    @Test fun deletedBookmarkTravels() {
        val changes = SyncRules.mergeBookmarks(listOf(b("m1", 100)), listOf(b("m1", 200, deleted = true), b("m2", 50)))
        assertEquals(2, changes.size)
        assertTrue(changes.first { it.id == "m1" }.deleted)
        assertTrue(SyncRules.mergeBookmarks(listOf(b("m1", 300)), listOf(b("m1", 200, true))).isEmpty())
    }

    @Test fun classifyNewUpdatedHave() {
        fun s(id: String, rev: Int, hash: String = "h-$id") = ComicSummary(id, hash, "t", null, null, 1, 1, 0, 0, rev, 0)
        val local = mapOf("a" to 3, "b" to 5)
        val hashes = setOf("h-a", "h-b", "h-z")
        assertEquals(RemoteState.NEW, SyncRules.classify(s("c", 1), local, hashes))
        assertEquals(RemoteState.UPDATED, SyncRules.classify(s("a", 4), local, hashes))
        assertEquals(RemoteState.HAVE, SyncRules.classify(s("b", 5), local, hashes))
        assertEquals(RemoteState.HAVE, SyncRules.classify(s("z", 1, "h-z"), local, hashes))
    }

    @Test fun syncFileRoundTrip() {
        val f = SyncFile(device = "Tab", exportedAt = 1, progress = listOf(p("c", 1, 2)), bookmarks = listOf(b("m", 3)))
        val back = SyncJson.json.decodeFromString(SyncFile.serializer(), SyncJson.json.encodeToString(SyncFile.serializer(), f))
        assertEquals(f, back)
    }
}
