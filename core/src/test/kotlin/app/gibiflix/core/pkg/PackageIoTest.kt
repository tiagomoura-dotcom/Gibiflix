package app.gibiflix.core.pkg

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class PackageIoTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun doc(pages: List<PageInfo>) = GibiflixDoc(
        id = "id-1", sourceHash = "sha256:00", title = "T",
        createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z", pages = pages,
    )

    @Test fun writesStoredPagesAndDeflatedJson() {
        val p1 = tmp.newFile("a.jpg").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
        val p2 = tmp.newFile("b.jpg").apply { writeBytes(ByteArray(10) { 7 }) }
        val out = java.io.File(tmp.root, "lib/x.cbz")
        val json = GibiflixJson.write(doc(listOf(PageInfo("0001.jpg", 10, 20), PageInfo("0002.jpg", 10, 20))))
        PackageIo.write(out, listOf(PackageIo.PageFile("0001.jpg", p1), PackageIo.PageFile("0002.jpg", p2)), json, "<ComicInfo/>".toByteArray())
        ZipFile(out).use { z ->
            assertEquals(ZipEntry.STORED, z.getEntry("0001.jpg").method)
            assertEquals(ZipEntry.DEFLATED, z.getEntry("gibiflix.json").method)
            assertNotNull(z.getEntry("ComicInfo.xml"))
        }
        assertEquals(listOf("0001.jpg", "0002.jpg"), PackageIo.listPages(out))
        assertEquals(1000, PackageIo.readEntry(out, "0001.jpg").size)
        val parsed = GibiflixJson.parse(PackageIo.readJson(out)!!)
        assertEquals(2, parsed.doc.pages.size)
        assertTrue(out.parentFile.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun replaceJsonKeepsPages() {
        val p1 = tmp.newFile("a.jpg").apply { writeBytes(ByteArray(50) { 1 }) }
        val out = tmp.newFile("y.cbz")
        PackageIo.write(out, listOf(PackageIo.PageFile("0001.jpg", p1)), GibiflixJson.write(doc(listOf(PageInfo("0001.jpg", 1, 1)))))
        val d2 = doc(listOf(PageInfo("0001.jpg", 1, 1, reviewed = true))).copy(revision = 3)
        PackageIo.replaceJson(out, GibiflixJson.write(d2))
        val parsed = GibiflixJson.parse(PackageIo.readJson(out)!!)
        assertEquals(3, parsed.doc.revision)
        assertTrue(parsed.doc.pages[0].reviewed)
        assertEquals(50, PackageIo.readEntry(out, "0001.jpg").size)
    }

    @Test fun unknownFieldsArePreserved() {
        val original = """
            {"format":"gibiflix","version":1,"id":"a","revision":1,"sourceHash":"s","title":"T",
             "readingDirection":"ltr","createdAt":"c","updatedAt":"u","futureField":{"k":1},
             "pages":[{"file":"0001.jpg","width":1,"height":1,"extra":"keep",
               "panels":[{"x":0,"y":0,"w":1,"h":1,"source":"auto","note":"n"}]}]}
        """.trimIndent()
        val parsed = GibiflixJson.parse(original)
        val edited = parsed.doc.copy(revision = 2, title = "Novo")
        val out = GibiflixJson.json.parseToJsonElement(GibiflixJson.write(edited, parsed.raw)).jsonObject
        assertEquals(JsonObject(mapOf("k" to JsonPrimitive(1))), out["futureField"])
        val page = out["pages"]!!.jsonArray[0].jsonObject
        assertEquals("keep", page["extra"]!!.jsonPrimitive.content)
        assertEquals("n", page["panels"]!!.jsonArray[0].jsonObject["note"]!!.jsonPrimitive.content)
        assertEquals("Novo", out["title"]!!.jsonPrimitive.content)
    }

    @Test fun newerVersionIsDetected() {
        val r = GibiflixJson.read("""{"format":"gibiflix","version":9}""")
        assertTrue(r is ReadResult.NewerVersion)
    }

    @Test fun hashIsStable() {
        val f = tmp.newFile("h").apply { writeText("abc") }
        assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256(f))
    }
}
