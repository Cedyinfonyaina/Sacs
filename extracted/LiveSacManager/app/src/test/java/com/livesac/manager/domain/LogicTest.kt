package com.livesac.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LogicTest {

    private val nbsp = " "

    private fun utcMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun formatAr_groupsThousands() {
        assertEquals("25${nbsp}000${nbsp}Ar", formatAr(25_000))
        assertEquals("0${nbsp}Ar", formatAr(0))
        assertEquals("999${nbsp}Ar", formatAr(999))
        assertEquals("1${nbsp}000${nbsp}Ar", formatAr(1_000))
        assertEquals("1${nbsp}250${nbsp}000${nbsp}Ar", formatAr(1_250_000))
        assertEquals("999${nbsp}999${nbsp}999${nbsp}Ar", formatAr(999_999_999))
    }

    @Test
    fun normalizeName_ignoresCaseAndExtraSpaces() {
        assertEquals(normalizeName("marie rakoto"), normalizeName("  Marie   RAKOTO "))
        assertEquals(normalizeName("Éloïse"), normalizeName("éLOÏSE"))
    }

    @Test
    fun totals_areCorrect_andBagsAreGroupedPerClient() {
        val clients = listOf(Client(1, "Marie Rakoto"), Client(2, "andry"))
        val bags = listOf(
            Bag(1, 1, "JP12", 25_000),
            Bag(2, 1, "JP45", 40_000),
            Bag(3, 1, "JP12", 10_000),
            Bag(4, 2, "JP7", 55_000)
        )
        val summaries = buildClientSummaries(clients, bags)

        // Tri alphabétique sans tenir compte de la casse : andry avant Marie.
        assertEquals(listOf("andry", "Marie Rakoto"), summaries.map { it.name })

        val marie = summaries.first { it.id == 1L }
        assertEquals(3, marie.bagCount)
        assertEquals(75_000L, marie.total)
        assertEquals(listOf("JP12", "JP45"), marie.jpCodes) // JP12 apparaît une seule fois

        val stats = computeStats(summaries)
        assertEquals(2, stats.clientCount)
        assertEquals(4, stats.bagCount)
        assertEquals(130_000L, stats.total)
    }

    @Test
    fun total_isRecalculatedAfterEditAndDelete() {
        val clients = listOf(Client(1, "Marie"))
        var bags = listOf(Bag(1, 1, "JP1", 20_000), Bag(2, 1, "JP2", 30_000))
        assertEquals(50_000L, buildClientSummaries(clients, bags)[0].total)

        // modification du prix du sac 2
        bags = bags.map { if (it.id == 2L) it.copy(price = 35_000) else it }
        assertEquals(55_000L, buildClientSummaries(clients, bags)[0].total)

        // suppression du sac 1
        bags = bags.filter { it.id != 1L }
        val s = buildClientSummaries(clients, bags)[0]
        assertEquals(35_000L, s.total)
        assertEquals(1, s.bagCount)
    }

    @Test
    fun emptyLive_hasZeroStats() {
        val stats = computeStats(buildClientSummaries(emptyList(), emptyList()))
        assertEquals(LiveStats(0, 0, 0L), stats)
    }

    @Test
    fun search_findsByFacebookNameOrJpCode_ignoringCase() {
        val s = ClientSummary(1, "Marie Rakoto", listOf("JP12", "ab-345"), 2, 10_000)
        assertTrue(matchesQuery(s, ""))
        assertTrue(matchesQuery(s, "   "))
        assertTrue(matchesQuery(s, "marie"))
        assertTrue(matchesQuery(s, "RAKO"))
        assertTrue(matchesQuery(s, "jp12"))
        assertTrue(matchesQuery(s, "JP1"))
        assertTrue(matchesQuery(s, "AB-3"))
        assertFalse(matchesQuery(s, "andry"))
        assertFalse(matchesQuery(s, "jp99"))
    }

    @Test
    fun plural_followsFrenchRule() {
        assertEquals("0 client", plural(0, "client"))
        assertEquals("1 sac", plural(1, "sac"))
        assertEquals("2 sacs", plural(2, "sac"))
    }

    @Test
    fun dates_areFormattedInFrench() {
        val millis = utcMillis(2026, 10, 27) // c'est un mardi
        assertEquals("mardi 27 octobre 2026", formatDateLong(millis))
        assertEquals("27/10/2026", formatDateShort(millis))
        assertEquals("LIVE MARDI 27 OCTOBRE 2026", defaultLiveTitle(millis))
    }
}
