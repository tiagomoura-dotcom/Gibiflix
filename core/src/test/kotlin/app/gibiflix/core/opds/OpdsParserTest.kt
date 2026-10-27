package app.gibiflix.core.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsParserTest {
    private val base = "http://192.168.0.5:25600/opds/v1.2/catalog"

    private val navigation = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
  <id>root</id>
  <title>Komga</title>
  <link rel="self" href="/opds/v1.2/catalog" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  <link rel="start" href="/opds/v1.2/catalog" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  <link rel="search" href="/opds/v1.2/search" type="application/opensearchdescription+xml"/>
  <entry>
    <title>Bibliotecas</title>
    <id>libraries</id>
    <content type="text">Todas as bibliotecas</content>
    <link rel="subsection" href="libraries" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  </entry>
  <entry>
    <title>Séries</title>
    <id>series</id>
    <link href="/opds/v1.2/series?page=0" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  </entry>
</feed>"""

    private val acquisition = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Saga</title>
  <link rel="next" href="/opds/v1.2/series/12?page=1" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
  <entry>
    <title>Saga #1</title>
    <id>urn:book:1</id>
    <author><name>Brian K. Vaughan</name></author>
    <summary>Volume um</summary>
    <link rel="http://opds-spec.org/image" href="/api/v1/books/1/thumbnail" type="image/jpeg"/>
    <link rel="http://opds-spec.org/image/thumbnail" href="/api/v1/books/1/thumbnail?small" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition/open-access" href="/api/v1/books/1/file.pdf" type="application/pdf"/>
    <link rel="http://opds-spec.org/acquisition" href="/api/v1/books/1/file" type="application/vnd.comicbook+zip" title="Saga 001.cbz"/>
  </entry>
</feed>"""

    @Test fun parsesNavigationFeed() {
        val f = OpdsParser.parseFeed(navigation.toByteArray(), base)
        assertEquals("Komga", f.title)
        assertEquals(2, f.entries.size)
        assertEquals("http://192.168.0.5:25600/opds/v1.2/libraries", f.entries[0].navigation!!.href)
        assertEquals("http://192.168.0.5:25600/opds/v1.2/series?page=0", f.entries[1].navigation!!.href)
        assertEquals("Todas as bibliotecas", f.entries[0].summary)
        assertNull(f.entries[0].acquisition)
        assertEquals("http://192.168.0.5:25600/opds/v1.2/search", f.searchLink!!.href)
    }

    @Test fun parsesAcquisitionPreferringCbz() {
        val f = OpdsParser.parseFeed(acquisition.toByteArray(), base)
        val e = f.entries.single()
        assertEquals("Saga #1", e.title)
        assertEquals(listOf("Brian K. Vaughan"), e.authors)
        assertEquals("http://192.168.0.5:25600/api/v1/books/1/file", e.acquisition!!.href)
        assertTrue(e.cover!!.href.endsWith("thumbnail?small"))
        assertNull(e.navigation)
        assertNotNull(f.next)
    }

    @Test fun openSearchTemplate() {
        val xml = """<?xml version="1.0"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
  <ShortName>Busca</ShortName>
  <Url type="application/atom+xml;profile=opds-catalog;kind=acquisition" template="/opds/v1.2/search?search={searchTerms}"/>
</OpenSearchDescription>"""
        val t = OpdsParser.parseOpenSearch(xml.toByteArray(), base)!!
        assertEquals("http://192.168.0.5:25600/opds/v1.2/search?search={searchTerms}", t)
        assertEquals("http://192.168.0.5:25600/opds/v1.2/search?search=turma%20da%20m%C3%B4nica", OpdsParser.searchUrl(t, "turma da mônica"))
    }

    @Test fun kavitaStyleApiKeyUrlIsKept() {
        val kavita = "http://10.0.0.2:5000/api/opds/abc-123"
        assertEquals("http://10.0.0.2:5000/api/opds/abc-123/series/4", OpdsParser.resolve(kavita + "/", "series/4"))
        assertEquals("http://10.0.0.2:5000/api/image?x=1", OpdsParser.resolve(kavita, "/api/image?x=1"))
    }
}
