package app.gibiflix.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {
    @Test fun numbersInNaturalOrder() {
        assertEquals(listOf("p1.jpg", "p2.jpg", "p10.jpg"), ArchiveNames.sortPages(listOf("p10.jpg", "p2.jpg", "p1.jpg")))
        assertEquals(listOf("a/2.png", "a/10.png", "b/1.png"), ArchiveNames.sortPages(listOf("b/1.png", "a/10.png", "a/2.png")))
    }

    @Test fun filtersSystemFiles() {
        assertFalse(ArchiveNames.isPage("__MACOSX/._001.jpg"))
        assertFalse(ArchiveNames.isPage("folder/.DS_Store"))
        assertFalse(ArchiveNames.isPage("Thumbs.db"))
        assertFalse(ArchiveNames.isPage("notes.txt"))
        assertTrue(ArchiveNames.isPage("Cap 1/001.JPG"))
        assertTrue(ArchiveNames.isPage("x.webp"))
    }

    @Test fun packageNames() {
        assertEquals("0001.jpg", ArchiveNames.packageName(0, "jpeg"))
        assertEquals("0012.png", ArchiveNames.packageName(11, "PNG"))
    }
}
