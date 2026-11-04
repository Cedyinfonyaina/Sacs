package com.livesac.manager.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Logique « pure » de l'application (sans Android) :
 * calcul des totaux, regroupement par client, recherche, formats d'affichage.
 * Elle est testée dans app/src/test/.../LogicTest.kt
 */

/** Un sac acheté par un client. Le prix est en Ariary (entier). */
data class Bag(val id: Long, val clientId: Long, val jpCode: String, val price: Long)

/** Un client d'un Live (nom Facebook tel qu'il a été saisi la première fois). */
data class Client(val id: Long, val name: String)

/** Fiche client prête à afficher : codes JP distincts, nombre de sacs, total à payer. */
data class ClientSummary(
    val id: Long,
    val name: String,
    val jpCodes: List<String>,
    val bagCount: Int,
    val total: Long
)

/** Chiffres d'un Live : nombre de clients, nombre de sacs, montant total. */
data class LiveStats(val clientCount: Int, val bagCount: Int, val total: Long)

private const val NBSP = " "
private val SPACES = Regex("\\s+")

/** Clé de comparaison d'un nom Facebook : sans majuscules, sans espaces en trop. */
fun normalizeName(name: String): String =
    name.trim().replace(SPACES, " ").lowercase(Locale.ROOT)

/** Regroupe les sacs par client, calcule les totaux et trie les clients par nom (A → Z). */
fun buildClientSummaries(clients: List<Client>, bags: List<Bag>): List<ClientSummary> {
    val bagsByClient = bags.groupBy { it.clientId }
    return clients
        .map { client ->
            val own = bagsByClient[client.id].orEmpty()
            ClientSummary(
                id = client.id,
                name = client.name,
                jpCodes = own
                    .map { it.jpCode.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase(Locale.ROOT) },
                bagCount = own.size,
                total = own.sumOf { it.price }
            )
        }
        .sortedWith(compareBy({ normalizeName(it.name) }, { it.id }))
}

/** Totaux d'un Live à partir des fiches clients. */
fun computeStats(summaries: List<ClientSummary>): LiveStats =
    LiveStats(
        clientCount = summaries.size,
        bagCount = summaries.sumOf { it.bagCount },
        total = summaries.sumOf { it.total }
    )

/** Vrai si la recherche correspond au nom Facebook ou à l'un des codes JP (sans tenir compte de la casse). */
fun matchesQuery(summary: ClientSummary, query: String): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    if (summary.name.lowercase(Locale.ROOT).contains(q)) return true
    return summary.jpCodes.any { it.lowercase(Locale.ROOT).contains(q) }
}

/** 25000 -> « 25 000 Ar » (espaces insécables pour ne jamais couper le montant). */
fun formatAr(amount: Long): String {
    val negative = amount < 0
    val digits = amount.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(NBSP).reversed()
    return (if (negative) "-" else "") + grouped + NBSP + "Ar"
}

/** « 1 client », « 2 clients », « 0 client ». */
fun plural(count: Int, word: String): String = "$count $word" + if (count > 1) "s" else ""

// ---- Dates (stockées en millisecondes UTC à minuit, comme le sélecteur de date Compose) ----

private fun toLocalDate(dateMillis: Long): LocalDate =
    Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** Aujourd'hui, à minuit UTC. */
fun todayUtcMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** « mercredi 27 octobre 2026 » */
fun formatDateLong(dateMillis: Long): String =
    toLocalDate(dateMillis).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH))

/** « 27/10/2026 » */
fun formatDateShort(dateMillis: Long): String =
    toLocalDate(dateMillis).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH))

/** « LIVE MERCREDI 27 OCTOBRE 2026 » */
fun defaultLiveTitle(dateMillis: Long): String =
    "LIVE " + formatDateLong(dateMillis).uppercase(Locale.FRENCH)
