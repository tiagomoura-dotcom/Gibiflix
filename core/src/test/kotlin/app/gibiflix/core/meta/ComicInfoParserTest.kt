package app.gibiflix.core.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicInfoParserTest {
    @Test fun parsesMainFields() {
        val xml = """<?xml version="1.0"?>
            <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <Title>O Retorno</Title><Series>Turma da Mônica</Series><Number>12</Number>
              <Volume>2</Volume><Year>2020</Year><Writer>Mauricio</Writer><Penciller>Ana</Penciller>
              <Summary>Resumo</Summary><LanguageISO>pt</LanguageISO><Manga>No</Manga>
              <Pages><Page Image="0"/></Pages>
            </ComicInfo>"""
        val m = ComicInfoParser.parse(xml.toByteArray())!!
        assertEquals("O Retorno", m.title)
        assertEquals("Turma da Mônica", m.series)
        assertEquals("12", m.number)
        assertEquals(2, m.volume)
        assertEquals(2020, m.year)
        assertEquals("Mauricio", m.writer)
        assertEquals("Ana", m.penciller)
        assertEquals("pt", m.language)
        assertEquals("ltr", m.readingDirection)
    }

    @Test fun mangaRightToLeft() {
        val m = ComicInfoParser.parse("<ComicInfo><Series>One Piece</Series><Manga>YesAndRightToLeft</Manga></ComicInfo>".toByteArray())!!
        assertEquals("rtl", m.readingDirection)
    }

    @Test fun missingMangaIsUnknown() {
        val m = ComicInfoParser.parse("<ComicInfo><Series>X</Series></ComicInfo>".toByteArray())!!
        assertNull(m.readingDirection)
        assertNull(m.year)
    }

    @Test fun invalidXmlReturnsNull() {
        assertNull(ComicInfoParser.parse("not xml".toByteArray()))
    }
}
