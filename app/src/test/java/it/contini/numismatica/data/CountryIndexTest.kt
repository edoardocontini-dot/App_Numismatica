package it.contini.numismatica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryIndexTest {

    // Shape copied from Numista's /issuers answer (codes are real, names in English as Numista sends them)
    private val issuers = listOf(
        Issuer("italy", "Italy", null, 1),
        Issuer("italie", "Italy", "italy", 2),
        Issuer("italie_etats", "Italian States", "italy", 2),
        Issuer("kingdom_of_italy", "Italy, Napoleonic Kingdom of", "italie_etats", 3),
        Issuer("italian_notgeld", "Italian notgeld", "italy", 2),
        Issuer("italy_ancient", "Italy (ancient)", null, 1),
        Issuer("central_italy_ancient", "Central Italy", "italy_ancient", 2),
        Issuer("swaziland", "Eswatini", null, 1),
        Issuer("swaziland_eswatini_section", "Eswatini", null, 1),
        Issuer("swazi_child", "Swaziland, Kingdom of", "swaziland_eswatini_section", 2),
        Issuer("seborga", "Seborga", null, 1),
        Issuer("carolingian_empire_section", "Carolingian Empire", null, 1),
        Issuer("carolingian_states", "Carolingian States", "carolingian_empire_section", 2),
        Issuer("italy_kingdom", "Italy, Kingdom of", "carolingian_states", 3),
        Issuer("orphan", "Orphan", "missing_parent", 2),
    )
    private val index = CountryIndex(issuers)

    @Test
    fun italianVariantsBecomeItalia() {
        assertEquals("Italia", index.countryName("italy"))
        assertEquals("Italia", index.countryName("italie"))
        assertEquals("Italia", index.countryName("italie_etats"))
        assertEquals("Italia", index.countryName("kingdom_of_italy"))
        assertEquals("Italia (antica)", index.countryName("central_italy_ancient"))
        // moved by the app's own override
        assertEquals("Italia", index.countryName("italy_kingdom"))
        assertEquals("Impero carolingio", index.countryName("carolingian_states"))
    }

    @Test
    fun placeLabels() {
        assertEquals("Italia", index.placeLabel("italy", "Italy"))
        assertEquals("Italia", index.placeLabel("italie", "Italy"))
        assertEquals("Italia · Italy, Napoleonic Kingdom of", index.placeLabel("kingdom_of_italy", "Italy, Napoleonic Kingdom of"))
        assertEquals("Unknown issuer", index.placeLabel("nope", "Unknown issuer"))
    }

    @Test
    fun sameItalianNameIsOneCountry() {
        val eswatini = index.countries.single { it.name == "eSwatini" }
        assertEquals(2, eswatini.rootCodes.size)
        assertEquals(3, eswatini.issuerCount)
        assertEquals(listOf("Swaziland, Kingdom of"), index.issuersOf(eswatini).map { it.name })
    }

    @Test
    fun countriesAreSortedAndComplete() {
        val names = index.countries.map { it.name }
        assertEquals(listOf("eSwatini", "Impero carolingio", "Italia", "Italia (antica)", "Orphan", "Seborga"), names)
        val italia = index.countries.single { it.name == "Italia" }
        assertEquals(
            listOf("Italian notgeld", "Italian States", "Italy, Napoleonic Kingdom of", "Italy", "Italy, Kingdom of"),
            index.issuersOf(italia).map { it.name },
        )
        val carolingi = index.countries.single { it.name == "Impero carolingio" }
        assertEquals(listOf("Carolingian States"), index.issuersOf(carolingi).map { it.name })
    }

    @Test
    fun unknownCodes() {
        assertNull(index.countryName("does_not_exist"))
        // an issuer whose parent is missing counts as its own country
        assertEquals("Orphan", index.countryName("orphan"))
    }
}
