package it.contini.numismatica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing of Numista API v3 answers, including the quirks seen in the wild (numbers sent as strings). */
class NumistaParseTest {

    @Test
    fun search() {
        val json = """
            {"count": 2, "types": [
              {"id": 1234, "title": "500 Lire - Caravelle", "category": "coin",
               "issuer": {"code": "italie", "name": "Italy"}, "min_year": 1958, "max_year": "1967",
               "obverse_thumbnail": "https://en.numista.com/a.jpg", "reverse_thumbnail": "https://en.numista.com/b.jpg"},
              {"id": "99", "title": "Denarius", "category": "coin", "issuer": {"code": "rome", "name": "Roman Empire"},
               "min_year": -2, "max_year": null}
            ]}
        """.trimIndent()
        val page = NumistaApi.parseSearch(json)
        assertEquals(2, page.total)
        assertEquals(1234L, page.items[0].id)
        assertEquals(1967, page.items[0].maxYear)
        assertEquals("Italy", page.items[0].issuerName)
        assertEquals(99L, page.items[1].id)
        assertEquals(-2, page.items[1].minYear)
        assertNull(page.items[1].maxYear)
        assertNull(page.items[1].obverseThumb)
    }

    @Test
    fun typeDetail() {
        val json = """
            {"id": 1234, "url": "https://en.numista.com/catalogue/pieces1234.html", "title": "500 Lire",
             "category": "coin", "issuer": {"code": "italie", "name": "Italy"}, "min_year": 1958, "max_year": 1967,
             "value": {"text": "500 Lire", "currency": {"id": 5, "name": "Lira", "full_name": "Lira (1861-2001)"}},
             "ruler": [{"id": 1, "name": "Italian Republic"}],
             "demonetization": {"is_demonetized": true, "demonetization_date": "2002-03-01"},
             "shape": "Round", "composition": {"text": "Silver (.835)"}, "technique": {"text": "Milled"},
             "weight": 11, "size": "29", "thickness": 1.9,
             "obverse": {"description": "Bust", "lettering": "REPUBBLICA ITALIANA", "picture": "https://x/o.jpg", "thumbnail": "https://x/ot.jpg"},
             "reverse": {"description": "Ships", "picture": "https://x/r.jpg"},
             "edge": {"description": "Lettered"},
             "mints": [{"id": "10", "name": "Rome"}],
             "comments": "<p>First<br/>line &amp; more</p>"}
        """.trimIndent()
        val d = NumistaApi.parseType(json)
        assertEquals("Lira (1861-2001)", d.currency)
        assertEquals(true, d.demonetized)
        assertEquals("11", d.weight)
        assertEquals("1.9", d.thickness)
        assertEquals(listOf("Rome"), d.mints)
        assertEquals(listOf("Italian Republic"), d.rulers)
        assertEquals("https://x/ot.jpg", d.summary.obverseThumb)
        assertEquals("First\nline & more", d.comments)
    }

    @Test
    fun issuesAsArray() {
        val json = """
            [
              {"id": 2, "is_dated": true, "year": 1959, "gregorian_year": 1959, "mint_letter": "R", "mintage": "17910000"},
              {"id": 1, "is_dated": true, "year": 1958, "gregorian_year": 1958, "mintage": 24910000, "comment": "Prova"},
              {"id": 3, "is_dated": true, "year": 1400, "gregorian_year": 1980},
              {"id": 4, "is_dated": false, "min_year": 1950, "max_year": 1960},
              {"id": 5, "is_dated": true, "year": -44, "gregorian_year": -44}
            ]
        """.trimIndent()
        val issues = NumistaApi.parseIssues(json)
        // sorted by gregorian year; undated issues use their first possible year
        assertEquals(listOf("44 a.C.", "Senza data (1950–1960)", "1958", "1959 R", "1400 (1980)"), issues.map { it.label })
        assertEquals(24_910_000L, issues[2].mintage)
        assertEquals(17_910_000L, issues[3].mintage)
        assertEquals("Prova", issues[2].comment)
        assertNull(issues[0].mintage)
    }

    @Test
    fun issuesWrappedInObject() {
        val issues = NumistaApi.parseIssues("""{"issues": [{"id": 1, "year": 2002, "gregorian_year": 2002}]}""")
        assertEquals(listOf("2002"), issues.map { it.label })
    }

    @Test
    fun issuers() {
        val list = NumistaApi.parseIssuers(
            """{"count": 3, "issuers": [
                {"code": "italy", "name": "Italy", "level": 1},
                {"code": "italie", "name": "Italy", "parent": {"code": "italy", "name": "Italy"}, "level": 2},
                {"code": "france_section", "name": "France", "level": "1"}]}"""
        )
        assertEquals(listOf("France", "Italy", "Italy"), list.map { it.name })
        assertEquals("italy", list.first { it.code == "italie" }.parentCode)
        assertEquals(2, list.first { it.code == "italie" }.level)
        assertNull(list.first { it.code == "italy" }.parentCode)
    }

    @Test
    fun banknoteFields() {
        val json = """
            {"id": 7, "title": "500 Lire", "category": "banknote", "issuer": {"code": "x", "name": "X"},
             "object_type": {"id": 79, "name": "Banconota regolare"},
             "issuing_entity": {"id": 412, "name": "Banca d'Italia"},
             "size": 201, "size2": 117,
             "references": [{"catalogue": {"id": 931, "code": "P"}, "number": "3"}, {"number": "12"}],
             "watermark": {"description": "Leonardo"}}
        """.trimIndent()
        val d = NumistaApi.parseType(json)
        assertEquals("201 × 117", d.size)
        assertEquals("Banconota regolare", d.objectType)
        assertEquals("Banca d'Italia", d.issuingEntity)
        assertEquals(listOf("P# 3", "12"), d.references)
        assertEquals("Leonardo", d.watermark)
        assertEquals("banknote", d.summary.category)
    }

    @Test
    fun thousands() {
        assertTrue(formatThousands(24_910_000L) == "24.910.000")
    }
}
