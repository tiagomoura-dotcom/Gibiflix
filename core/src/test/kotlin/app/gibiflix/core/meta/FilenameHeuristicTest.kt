package app.gibiflix.core.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameHeuristicTest {
    private fun p(s: String) = FilenameHeuristic.parse(s)

    @Test fun hashNumberAndYear() {
        val m = p("Saga #12 (2020).cbz")
        assertEquals("Saga", m.series); assertEquals("12", m.number); assertEquals(2020, m.year)
    }

    @Test fun volume() {
        val m = p("Akira v02.cbr")
        assertEquals("Akira", m.series); assertEquals(2, m.volume); assertNull(m.number)
    }

    @Test fun volumeAndChapter() {
        val m = p("Berserk Vol. 3 #15.cb7")
        assertEquals("Berserk", m.series); assertEquals(3, m.volume); assertEquals("15", m.number)
    }

    @Test fun plainTrailingNumber() {
        val m = p("Batman 015.cbz")
        assertEquals("Batman", m.series); assertEquals("15", m.number)
    }

    @Test fun seriesDashTitle() {
        val m = p("Sandman - Prelúdios e Noturnos.cbz")
        assertEquals("Sandman", m.series); assertEquals("Prelúdios e Noturnos", m.title)
    }

    @Test fun numberThenDashTitle() {
        val m = p("Hellboy 03 - O Despertar do Demônio (1996) [scan].cbr")
        assertEquals("Hellboy", m.series); assertEquals("3", m.number); assertEquals("O Despertar do Demônio", m.title); assertEquals(1996, m.year)
    }

    @Test fun underscoresAndNoNumber() {
        val m = p("Watchmen_Edicao_Definitiva.pdf")
        assertEquals("Watchmen Edicao Definitiva", m.title); assertNull(m.series)
    }

    @Test fun displayTitle() {
        assertEquals("Saga #12", FilenameHeuristic.displayTitle(p("Saga #12.cbz"), "x"))
        assertEquals("Sandman — Prelúdios", FilenameHeuristic.displayTitle(ComicMeta(series = "Sandman", title = "Prelúdios"), "x"))
    }
}
