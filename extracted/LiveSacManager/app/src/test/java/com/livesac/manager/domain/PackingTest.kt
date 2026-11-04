package com.livesac.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PackingTest {

    private fun ms(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // Semaine du lundi 2 au dimanche 8 novembre 2026, puis semaine suivante.
    private val wed = LiveInfo(1, "LIVE MERCREDI 4 NOVEMBRE 2026", ms(2026, 11, 4), LiveKind.WED)
    private val sat = LiveInfo(2, "LIVE SAMEDI 7 NOVEMBRE 2026", ms(2026, 11, 7), LiveKind.SAT)
    private val sun = LiveInfo(3, "LIVE DIMANCHE 8 NOVEMBRE 2026", ms(2026, 11, 8), LiveKind.SUN)
    private val nextSat = LiveInfo(4, "LIVE SAMEDI 14 NOVEMBRE 2026", ms(2026, 11, 14), LiveKind.SAT)
    private val lives = listOf(wed, sat, sun, nextSat)

    // Marie : samedi (id 10) + dimanche (id 11) + mercredi (id 12). Andry : samedi seulement (id 13).
    private val clients = listOf(
        ClientRow(10, 2, "Marie Rakoto"),
        ClientRow(11, 3, "  marie   RAKOTO "),
        ClientRow(12, 1, "Marie Rakoto"),
        ClientRow(13, 2, "Andry"),
        ClientRow(14, 4, "Marie Rakoto")
    )
    private val bags = listOf(
        Bag(1, 10, "JP12", 25_000),
        Bag(2, 10, "JP45", 40_000),
        Bag(3, 11, "JP77", 15_000),
        Bag(4, 11, "jp12", 5_000),
        Bag(5, 12, "JP90", 100_000),
        Bag(6, 13, "JP8", 55_000),
        Bag(7, 14, "JP1", 1_000)
    )

    private fun groups() = buildPackageGroups(lives, clients, bags)
    private fun marieWeekend() = groups().first { it.clientIds.contains(10L) }

    // ---- Total à payer ----

    @Test
    fun total_isSumOfAllBagPrices() {
        assertEquals(85_000L, marieWeekend().total) // 25 000 + 40 000 + 15 000 + 5 000
        assertEquals(4, marieWeekend().bagCount)
    }

    // ---- Regroupement samedi + dimanche ----

    @Test
    fun saturdayAndSunday_areMergedIntoOnePackage() {
        val m = marieWeekend()
        assertEquals(listOf(10L, 11L), m.clientIds)
        assertEquals(setOf(2L, 3L), m.lives.map { it.id }.toSet())
        // Tous ses codes JP, sans doublon (JP12 / jp12 = même code).
        assertEquals(listOf("JP12", "JP45", "JP77"), m.jpCodes)
    }

    @Test
    fun eachBagKeepsItsOriginLive() {
        val origins = marieWeekend().bags.associate { it.id to it.liveKind }
        assertEquals(LiveKind.SAT, origins[1])
        assertEquals(LiveKind.SAT, origins[2])
        assertEquals(LiveKind.SUN, origins[3])
        assertEquals(LiveKind.SUN, origins[4])
        assertEquals("Samedi 07/11", marieWeekend().bags.first().originLabel)
        assertEquals("Sam 07/11 + Dim 08/11", marieWeekend().originsLabel)
    }

    @Test
    fun wednesdayStaysIndependent() {
        val wedGroup = groups().first { it.clientIds.contains(12L) }
        assertEquals(listOf(12L), wedGroup.clientIds)
        assertEquals(100_000L, wedGroup.total)
        assertEquals(listOf("JP90"), wedGroup.jpCodes)
        assertFalse(wedGroup.clientIds.any { it == 10L || it == 11L })
        assertTrue(wedGroup.batchKey != marieWeekend().batchKey)
    }

    @Test
    fun differentWeekends_areNotMerged() {
        val next = groups().first { it.clientIds.contains(14L) }
        assertEquals(listOf(14L), next.clientIds)
        assertEquals(1_000L, next.total)
    }

    @Test
    fun onePackagePerClientPerBatch() {
        // Marie weekend, Marie mercredi, Marie semaine suivante, Andry.
        assertEquals(4, groups().size)
    }

    @Test
    fun lastTwoWeekendDaysOfSameWeek_sameBatchKey() {
        assertEquals(batchKey(sat), batchKey(sun))
        assertTrue(batchKey(sat) != batchKey(nextSat))
        assertTrue(batchKey(wed) != batchKey(sat))
    }

    @Test
    fun batches_areNewestFirst() {
        val batches = buildBatches(lives)
        assertEquals(3, batches.size)
        assertEquals("Sam 14/11", batches[0].label)
        assertEquals("Sam 07/11 + Dim 08/11", batches[1].label)
        assertEquals("Mer 04/11", batches[2].label)
    }

    // ---- Statuts d'emballage ----

    @Test
    fun packedStatus_requiresAllPartsPacked() {
        val one = clients.map { if (it.id == 10L) it.copy(packed = true) else it }
        assertFalse(buildPackageGroups(lives, one, bags).first { it.clientIds.contains(10L) }.packed)
        val both = clients.map { if (it.id == 10L || it.id == 11L) it.copy(packed = true) else it }
        assertTrue(buildPackageGroups(lives, both, bags).first { it.clientIds.contains(10L) }.packed)
    }

    @Test
    fun progress_countsPackedPackages() {
        val c = clients.map { if (it.id == 13L || it.id == 12L) it.copy(packed = true) else it }
        val p = packProgress(buildPackageGroups(lives, c, bags))
        assertEquals(2, p.done)
        assertEquals(4, p.total)
        assertEquals("Emballages préparés : 2 / 4", p.label)
        assertEquals(0f, packProgress(emptyList()).fraction, 0f)
    }

    // ---- Filtres et classement par jour de livraison ----

    private fun withDelivery(): List<PackageGroup> {
        val c = clients.map {
            when (it.id) {
                10L, 11L -> it.copy(deliveryDay = DeliveryDay.SUN, packed = true)
                12L -> it.copy(deliveryDay = DeliveryDay.WED)
                13L -> it.copy(deliveryDay = DeliveryDay.SAT, packed = true)
                else -> it
            }
        }
        return buildPackageGroups(lives, c, bags)
    }

    @Test
    fun filters_byStatusAndDeliveryDay() {
        val g = withDelivery()
        assertEquals(4, applyPackFilter(g, PackFilter.ALL).size)
        assertEquals(2, applyPackFilter(g, PackFilter.PACKED).size)
        assertEquals(2, applyPackFilter(g, PackFilter.TO_PACK).size)
        assertEquals(listOf("Marie Rakoto"), applyPackFilter(g, PackFilter.DELIVERY_WED).map { it.name })
        assertEquals(listOf("Andry"), applyPackFilter(g, PackFilter.DELIVERY_SAT).map { it.name })
        assertEquals(1, applyPackFilter(g, PackFilter.DELIVERY_SUN).size)
        assertEquals(DeliveryDay.SUN, applyPackFilter(g, PackFilter.DELIVERY_SUN).single().deliveryDay)
    }

    @Test
    fun sorting_byDeliveryDay_thenNoDayLast() {
        val sorted = sortForPacking(withDelivery()).map { it.deliveryDay }
        assertEquals(listOf(DeliveryDay.WED, DeliveryDay.SAT, DeliveryDay.SUN, null), sorted)
    }

    @Test
    fun deliveryDay_isSharedByTheMergedPackage() {
        val c = clients.map { if (it.id == 11L) it.copy(deliveryDay = DeliveryDay.SAT) else it }
        assertEquals(DeliveryDay.SAT, buildPackageGroups(lives, c, bags).first { it.clientIds.contains(10L) }.deliveryDay)
    }

    // ---- Recherche ----

    @Test
    fun search_byNamePartialAndCode_ignoringCase() {
        val m = marieWeekend()
        assertTrue(matchesQuery(m, "marie"))
        assertTrue(matchesQuery(m, "RAKO"))
        assertTrue(matchesQuery(m, "jp45"))
        assertTrue(matchesQuery(m, "JP77"))   // code du dimanche trouvé via la fiche regroupée
        assertTrue(matchesQuery(m, "  "))
        assertFalse(matchesQuery(m, "andry"))
        assertFalse(matchesQuery(m, "JP999"))
    }

    // ---- Doublons ----

    @Test
    fun duplicateName_ignoresCaseAndSpaces() {
        assertEquals("Marie Rakoto", findDuplicateName(listOf("Andry", "Marie Rakoto"), "  MARIE   rakoto"))
        assertNull(findDuplicateName(listOf("Andry"), "Marie"))
        assertNull(findDuplicateName(listOf("Andry"), "   "))
    }

    @Test
    fun jpConflicts_existingAndRepeatedCodes() {
        val existing = listOf(ExistingCode("JP12", "Marie"), ExistingCode("JP20", "Andry"))
        val result = findJpConflicts(listOf("jp12", "JP30", "JP31", "jp31", ""), existing)
        assertEquals(listOf(JpConflict("jp12", "Marie"), JpConflict("jp31", null)), result)
        assertTrue(findJpConflicts(listOf("JP99"), existing).isEmpty())
        assertEquals("• jp12 : déjà utilisé par Marie\n• jp31 : saisi plusieurs fois", describeConflicts(result))
    }

    @Test
    fun suggestions_findExistingClientsBeforeCreatingDuplicate() {
        val all = listOf(
            NameSuggestion("Marie Rakoto", false), NameSuggestion("marie rakoto", true),
            NameSuggestion("Marielle", true), NameSuggestion("Andry", true)
        )
        val s = suggestNames(all, "MARIE")
        assertEquals(2, s.size)
        assertTrue(s[0].inThisLive) // la fiche du Live courant est préférée
        assertTrue(suggestNames(all, "").isEmpty())
    }

    // ---- Création rapide d'un Live ----

    @Test
    fun nextLive_proposesNextDateOfChosenDay() {
        val tuesday = ms(2026, 11, 3)
        assertEquals(ms(2026, 11, 4), nextLiveDateMillis(LiveKind.WED, tuesday))
        assertEquals(ms(2026, 11, 7), nextLiveDateMillis(LiveKind.SAT, tuesday))
        assertEquals(ms(2026, 11, 8), nextLiveDateMillis(LiveKind.SUN, tuesday))
        // Aujourd'hui compte si c'est le bon jour ; sinon la semaine suivante.
        assertEquals(ms(2026, 11, 4), nextLiveDateMillis(LiveKind.WED, ms(2026, 11, 4)))
        assertEquals(ms(2026, 11, 11), nextLiveDateMillis(LiveKind.WED, ms(2026, 11, 5)))
        assertEquals("LIVE SAMEDI 7 NOVEMBRE 2026", proposeLive(LiveKind.SAT, tuesday).title)
    }

    @Test
    fun liveKind_fromDate() {
        assertEquals(LiveKind.WED, LiveKind.fromDate(ms(2026, 11, 4)))
        assertEquals(LiveKind.SAT, LiveKind.fromDate(ms(2026, 11, 7)))
        assertEquals(LiveKind.SUN, LiveKind.fromDate(ms(2026, 11, 8)))
        assertEquals(LiveKind.OTHER, LiveKind.fromDate(ms(2026, 11, 5)))
    }

    // ---- Récapitulatif ----

    @Test
    fun recap_containsAllRequiredInformation() {
        val nbsp = "\u00A0"
        val text = buildRecap(marieWeekend())
        assertTrue(text.contains("Nom Facebook : Marie Rakoto"))
        assertTrue(text.contains("Codes JP : JP12, JP45, JP77"))
        assertTrue(text.contains("1. JP12 : 25${nbsp}000${nbsp}Ar"))
        assertTrue(text.contains("4. jp12 : 5${nbsp}000${nbsp}Ar"))
        assertTrue(text.contains("Nombre total de sacs : 4"))
        assertTrue(text.contains("Total à payer : 85${nbsp}000${nbsp}Ar"))
    }
}
