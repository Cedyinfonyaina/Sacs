package com.livesac.manager.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Logique « pure » (sans Android) des Lives, du regroupement samedi + dimanche,
 * des emballages, des livraisons, des doublons et du récapitulatif client.
 * Testée dans app/src/test/.../PackingTest.kt
 */

private fun utcDate(dateMillis: Long): LocalDate =
    Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** « 07/11 » */
fun formatDayMonth(dateMillis: Long): String =
    utcDate(dateMillis).format(DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH))

// ---------------------------------------------------------------------------------------------
// Types de Live et jours de livraison
// ---------------------------------------------------------------------------------------------

/** Type d'un Live. `OTHER` = Lives créés avant la mise à jour sur un autre jour : indépendants. */
enum class LiveKind(val code: String, val label: String, val short: String, val dayOfWeek: DayOfWeek?) {
    WED("WED", "Mercredi", "Mer", DayOfWeek.WEDNESDAY),
    SAT("SAT", "Samedi", "Sam", DayOfWeek.SATURDAY),
    SUN("SUN", "Dimanche", "Dim", DayOfWeek.SUNDAY),
    OTHER("OTHER", "Autre", "Live", null);

    /** Samedi et dimanche sont regroupés pour l'emballage et la livraison. */
    val isWeekend: Boolean get() = this == SAT || this == SUN

    companion object {
        /** Les trois Lives hebdomadaires proposés à la création. */
        val weekly: List<LiveKind> = listOf(WED, SAT, SUN)

        fun fromCode(code: String?): LiveKind = values().firstOrNull { it.code == code } ?: OTHER

        fun fromDate(dateMillis: Long): LiveKind = when (utcDate(dateMillis).dayOfWeek) {
            DayOfWeek.WEDNESDAY -> WED
            DayOfWeek.SATURDAY -> SAT
            DayOfWeek.SUNDAY -> SUN
            else -> OTHER
        }
    }
}

/** Jour de livraison d'un client. `order` sert au classement automatique. */
enum class DeliveryDay(val code: String, val label: String, val short: String, val order: Int) {
    WED("WED", "Mercredi", "Mer", 0),
    SAT("SAT", "Samedi", "Sam", 1),
    SUN("SUN", "Dimanche", "Dim", 2);

    companion object {
        fun fromCode(code: String?): DeliveryDay? = values().firstOrNull { it.code == code }
    }
}

/** Titre + date proposés automatiquement pour un nouveau Live. */
data class LiveProposal(val title: String, val dateMillis: Long)

/** Prochaine date (aujourd'hui incluse) correspondant au jour du Live. */
fun nextLiveDateMillis(kind: LiveKind, fromMillis: Long): Long {
    val dow = kind.dayOfWeek ?: return fromMillis
    return utcDate(fromMillis).with(TemporalAdjusters.nextOrSame(dow)).toUtcMillis()
}

/** « LIVE SAMEDI 7 NOVEMBRE 2026 » + sa date, pour le prochain Live du jour choisi. */
fun proposeLive(kind: LiveKind, todayMillis: Long): LiveProposal {
    val date = nextLiveDateMillis(kind, todayMillis)
    return LiveProposal(defaultLiveTitle(date), date)
}

// ---------------------------------------------------------------------------------------------
// Modèles
// ---------------------------------------------------------------------------------------------

data class LiveInfo(val id: Long, val title: String, val dateMillis: Long, val kind: LiveKind)

/** Un client d'un Live (ligne de la table clients) avec son état d'emballage. */
data class ClientRow(
    val id: Long,
    val liveId: Long,
    val name: String,
    val packed: Boolean = false,
    val deliveryDay: DeliveryDay? = null
)

/** Un sac avec l'information de son Live d'origine. */
data class PackBag(
    val id: Long,
    val clientId: Long,
    val liveId: Long,
    val liveKind: LiveKind,
    val liveTitle: String,
    val liveDateMillis: Long,
    val jpCode: String,
    val price: Long
) {
    /** « Samedi 07/11 » */
    val originLabel: String get() = liveLabel(liveKind, liveDateMillis)
}

/** « Samedi 07/11 » (ou la date complète pour un Live d'un autre jour). */
fun liveLabel(kind: LiveKind, dateMillis: Long): String =
    if (kind == LiveKind.OTHER) formatDateShort(dateMillis) else kind.label + " " + formatDayMonth(dateMillis)

/** « Sam 07/11 » */
fun liveShortLabel(kind: LiveKind, dateMillis: Long): String =
    if (kind == LiveKind.OTHER) formatDateShort(dateMillis) else kind.short + " " + formatDayMonth(dateMillis)

/**
 * Un emballage = un client dans un « lot ».
 * Lot du week-end = tous les Lives du samedi et du dimanche d'une même semaine ;
 * le mercredi (ou tout autre Live) forme un lot à lui seul.
 */
data class PackageGroup(
    val id: String,
    val batchKey: String,
    val name: String,
    val clientIds: List<Long>,
    val jpCodes: List<String>,
    val bags: List<PackBag>,
    val lives: List<LiveInfo>,
    val packed: Boolean,
    val deliveryDay: DeliveryDay?
) {
    val representativeClientId: Long get() = clientIds.first()
    val bagCount: Int get() = bags.size
    val total: Long get() = bags.sumOf { it.price }
    val originsLabel: String get() = lives.joinToString(" + ") { liveShortLabel(it.kind, it.dateMillis) }
}

/** Un lot d'emballage (week-end samedi + dimanche, ou un Live seul). */
data class Batch(val key: String, val label: String, val sortDateMillis: Long, val liveIds: List<Long>)

// ---------------------------------------------------------------------------------------------
// Regroupement samedi + dimanche
// ---------------------------------------------------------------------------------------------

/** Clé du lot d'un Live : semaine (lundi→dimanche) pour samedi/dimanche, identifiant du Live sinon. */
fun batchKey(live: LiveInfo): String =
    if (live.kind.isWeekend) {
        "W" + utcDate(live.dateMillis).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    } else {
        "L" + live.id
    }

/**
 * Construit un emballage par client et par lot. Deux fiches de même nom Facebook (sans tenir compte
 * des majuscules) dans le Live du samedi et celui du dimanche d'un même week-end deviennent un seul
 * emballage. Le mercredi reste toujours indépendant.
 */
fun buildPackageGroups(lives: List<LiveInfo>, clients: List<ClientRow>, bags: List<Bag>): List<PackageGroup> {
    val livesById = lives.associateBy { it.id }
    val bagsByClient = bags.groupBy { it.clientId }
    val buckets = LinkedHashMap<String, MutableList<ClientRow>>()
    for (client in clients.sortedBy { it.id }) {
        val live = livesById[client.liveId] ?: continue
        val key = batchKey(live) + "|" + normalizeName(client.name)
        buckets.getOrPut(key) { mutableListOf() }.add(client)
    }
    return buckets.map { (key, rows) ->
        val packBags = rows
            .flatMap { row ->
                val live = livesById.getValue(row.liveId)
                bagsByClient[row.id].orEmpty().map { b ->
                    PackBag(b.id, row.id, live.id, live.kind, live.title, live.dateMillis, b.jpCode.trim(), b.price)
                }
            }
            .sortedWith(compareBy({ it.liveDateMillis }, { it.liveId }, { it.id }))
        val groupLives = rows
            .map { livesById.getValue(it.liveId) }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.dateMillis }, { it.id }))
        PackageGroup(
            id = key,
            batchKey = batchKey(groupLives.first()),
            name = rows.first().name,
            clientIds = rows.map { it.id },
            jpCodes = packBags.map { it.jpCode }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) },
            bags = packBags,
            lives = groupLives,
            packed = rows.all { it.packed },
            deliveryDay = rows.mapNotNull { it.deliveryDay }.firstOrNull()
        )
    }
}

/** Les lots existants, du plus récent au plus ancien. */
fun buildBatches(lives: List<LiveInfo>): List<Batch> =
    lives.groupBy { batchKey(it) }
        .map { (key, group) ->
            val sorted = group.sortedWith(compareBy({ it.dateMillis }, { it.id }))
            Batch(
                key = key,
                label = sorted.joinToString(" + ") { liveShortLabel(it.kind, it.dateMillis) },
                sortDateMillis = sorted.last().dateMillis,
                liveIds = sorted.map { it.id }
            )
        }
        .sortedWith(compareByDescending<Batch> { it.sortDateMillis }.thenByDescending { it.liveIds.max() })

// ---------------------------------------------------------------------------------------------
// Tri, filtres, progression
// ---------------------------------------------------------------------------------------------

/** Classement automatique : par jour de livraison (mercredi, samedi, dimanche, puis sans jour), puis par nom. */
fun sortForPacking(groups: List<PackageGroup>): List<PackageGroup> =
    groups.sortedWith(
        compareBy<PackageGroup>(
            { it.deliveryDay?.order ?: Int.MAX_VALUE },
            { normalizeName(it.name) },
            { it.clientIds.first() }
        )
    )

/** Liste des clients : lots récents d'abord, puis ordre alphabétique. */
fun sortForClientList(groups: List<PackageGroup>, batches: List<Batch>): List<PackageGroup> {
    val rank = batches.withIndex().associate { it.value.key to it.index }
    return groups.sortedWith(
        compareBy<PackageGroup>(
            { rank[it.batchKey] ?: Int.MAX_VALUE },
            { normalizeName(it.name) },
            { it.clientIds.first() }
        )
    )
}

enum class PackFilter(val label: String) {
    ALL("Tous"),
    TO_PACK("À emballer"),
    PACKED("Emballés"),
    DELIVERY_WED("Livraison mercredi"),
    DELIVERY_SAT("Livraison samedi"),
    DELIVERY_SUN("Livraison dimanche");

    fun accepts(group: PackageGroup): Boolean = when (this) {
        ALL -> true
        TO_PACK -> !group.packed
        PACKED -> group.packed
        DELIVERY_WED -> group.deliveryDay == DeliveryDay.WED
        DELIVERY_SAT -> group.deliveryDay == DeliveryDay.SAT
        DELIVERY_SUN -> group.deliveryDay == DeliveryDay.SUN
    }
}

fun applyPackFilter(groups: List<PackageGroup>, filter: PackFilter): List<PackageGroup> =
    groups.filter { filter.accepts(it) }

data class PackProgress(val done: Int, val total: Int) {
    val label: String get() = "Emballages préparés : $done / $total"
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

fun packProgress(groups: List<PackageGroup>): PackProgress =
    PackProgress(done = groups.count { it.packed }, total = groups.size)

// ---------------------------------------------------------------------------------------------
// Recherche, doublons, suggestions
// ---------------------------------------------------------------------------------------------

/** Recherche par nom Facebook (complet ou partiel) ou code JP, sans tenir compte de la casse. */
fun matchesQuery(name: String, jpCodes: List<String>, query: String): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    if (name.lowercase(Locale.ROOT).contains(q)) return true
    return jpCodes.any { it.lowercase(Locale.ROOT).contains(q) }
}

fun matchesQuery(group: PackageGroup, query: String): Boolean = matchesQuery(group.name, group.jpCodes, query)

/** Code JP déjà utilisé dans le Live. `clientName == null` : saisi deux fois dans le formulaire. */
data class JpConflict(val code: String, val clientName: String?)

/** Un code JP existant dans un Live, avec le nom de son client. */
data class ExistingCode(val code: String, val clientName: String)

private fun codeKey(code: String): String = code.trim().lowercase(Locale.ROOT)

/** Codes saisis qui existent déjà (ou qui sont répétés dans la saisie). Les codes vides sont ignorés. */
fun findJpConflicts(codes: List<String>, existing: List<ExistingCode>): List<JpConflict> {
    val existingByKey = LinkedHashMap<String, ExistingCode>()
    for (e in existing) existingByKey.putIfAbsent(codeKey(e.code), e)
    val seen = HashSet<String>()
    val reported = HashSet<String>()
    val result = mutableListOf<JpConflict>()
    for (raw in codes) {
        val key = codeKey(raw)
        if (key.isEmpty()) continue
        val owner = existingByKey[key]
        if (owner != null) {
            if (reported.add(key)) result.add(JpConflict(raw.trim(), owner.clientName))
        } else if (!seen.add(key)) {
            if (reported.add(key)) result.add(JpConflict(raw.trim(), null))
        }
    }
    return result
}

/** Texte lisible décrivant les conflits de codes JP. */
fun describeConflicts(conflicts: List<JpConflict>): String =
    conflicts.joinToString("\n") { c ->
        if (c.clientName == null) "• ${c.code} : saisi plusieurs fois"
        else "• ${c.code} : déjà utilisé par ${c.clientName}"
    }

/** Nom existant correspondant (sans tenir compte des majuscules/espaces), ou null. */
fun findDuplicateName(existingNames: List<String>, name: String): String? {
    val key = normalizeName(name)
    if (key.isEmpty()) return null
    return existingNames.firstOrNull { normalizeName(it) == key }
}

/** Un client déjà connu, proposé pendant la saisie du nom. */
data class NameSuggestion(val name: String, val inThisLive: Boolean)

/** Clients existants dont le nom contient le texte saisi (casse ignorée), meilleurs résultats d'abord. */
fun suggestNames(candidates: List<NameSuggestion>, typed: String, limit: Int = 5): List<NameSuggestion> {
    val q = normalizeName(typed)
    if (q.isEmpty()) return emptyList()
    return candidates
        .filter { normalizeName(it.name).contains(q) }
        .groupBy { normalizeName(it.name) }
        .map { (_, same) -> same.firstOrNull { it.inThisLive } ?: same.first() }
        .sortedWith(
            compareByDescending<NameSuggestion> { normalizeName(it.name).startsWith(q) }
                .thenBy { normalizeName(it.name) }
        )
        .take(limit)
}

// ---------------------------------------------------------------------------------------------
// Récapitulatif client (fonctionne hors ligne)
// ---------------------------------------------------------------------------------------------

fun buildRecap(group: PackageGroup): String = buildString {
    appendLine("Récapitulatif de votre commande")
    appendLine()
    appendLine("Nom Facebook : ${group.name}")
    appendLine("Codes JP : ${group.jpCodes.joinToString(", ")}")
    appendLine()
    appendLine("Sacs :")
    group.bags.forEachIndexed { index, bag ->
        appendLine("${index + 1}. ${bag.jpCode} : ${formatAr(bag.price)}")
    }
    appendLine()
    appendLine("Nombre total de sacs : ${group.bagCount}")
    appendLine("Total à payer : ${formatAr(group.total)}")
    appendLine()
    append("Merci pour votre commande !")
}
