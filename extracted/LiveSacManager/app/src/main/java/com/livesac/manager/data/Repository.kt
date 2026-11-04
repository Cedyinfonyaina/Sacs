package com.livesac.manager.data

import androidx.room.withTransaction
import com.livesac.manager.domain.Bag
import com.livesac.manager.domain.BackupBag
import com.livesac.manager.domain.BackupClient
import com.livesac.manager.domain.BackupCodec
import com.livesac.manager.domain.BackupData
import com.livesac.manager.domain.BackupLive
import com.livesac.manager.domain.Batch
import com.livesac.manager.domain.ClientSummary
import com.livesac.manager.domain.DeliveryDay
import com.livesac.manager.domain.ExistingCode
import com.livesac.manager.domain.JpConflict
import com.livesac.manager.domain.LiveInfo
import com.livesac.manager.domain.LiveKind
import com.livesac.manager.domain.LiveStats
import com.livesac.manager.domain.NameSuggestion
import com.livesac.manager.domain.PackageGroup
import com.livesac.manager.domain.batchKey
import com.livesac.manager.domain.buildBatches
import com.livesac.manager.domain.buildClientSummaries
import com.livesac.manager.domain.buildPackageGroups
import com.livesac.manager.domain.computeStats
import com.livesac.manager.domain.findJpConflicts
import com.livesac.manager.domain.normalizeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Carte d'un Live dans la liste. */
data class LiveSummary(val id: Long, val title: String, val dateMillis: Long, val kind: LiveKind, val stats: LiveStats)

/** Récapitulatif d'un Live : chiffres + fiches clients de CE Live. */
data class LiveDetail(
    val id: Long,
    val title: String,
    val dateMillis: Long,
    val kind: LiveKind,
    val stats: LiveStats,
    val clients: List<ClientSummary>
)

/** Fiche d'un client : emballage regroupé (samedi + dimanche) et Lives où l'on peut ajouter un sac. */
data class ClientDetail(val group: PackageGroup, val liveOptions: List<LiveInfo>)

/** Tous les emballages + les lots (week-ends, mercredis) qui en contiennent. */
data class PackagingData(val groups: List<PackageGroup>, val batches: List<Batch>)

/** Sac saisi dans le formulaire (pas encore enregistré). */
data class NewBag(val jpCode: String, val price: Long)

/** Vérifications avant l'enregistrement d'un formulaire. */
data class SaveCheck(val existingName: String?, val conflicts: List<JpConflict>) {
    val needsConfirmation: Boolean get() = existingName != null || conflicts.isNotEmpty()
}

data class BackupCounts(val lives: Int, val clients: Int, val bags: Int)

class Repository(private val db: AppDatabase) {

    private val dao = db.dao()

    private data class Snapshot(val lives: List<LiveEntity>, val clients: List<ClientEntity>, val bags: List<BagEntity>) {
        val liveInfos: List<LiveInfo> get() = lives.map { it.toInfo() }
        fun groups(): List<PackageGroup> =
            buildPackageGroups(liveInfos, clients.map { it.toRow() }, bags.map { it.toModel() })
    }

    private fun snapshots(): Flow<Snapshot> =
        combine(dao.observeLives(), dao.observeAllClients(), dao.observeAllBags()) { l, c, b -> Snapshot(l, c, b) }

    // ---------------- Lecture (les écrans se mettent à jour tout seuls) ----------------

    fun observeLiveSummaries(): Flow<List<LiveSummary>> = snapshots().map { snap ->
        val clientsByLive = snap.clients.groupBy { it.liveId }
        val bagsByClient = snap.bags.groupBy { it.clientId }
        snap.lives.map { live ->
            val liveClients = clientsByLive[live.id].orEmpty().map { it.toModel() }
            val liveBags = liveClients.flatMap { c -> bagsByClient[c.id].orEmpty().map { it.toModel() } }
            val summaries = buildClientSummaries(liveClients, liveBags)
            LiveSummary(live.id, live.title, live.dateMillis, LiveKind.fromCode(live.kind), computeStats(summaries))
        }
    }

    fun observeLive(liveId: Long): Flow<LiveDetail?> =
        combine(
            dao.observeLive(liveId),
            dao.observeClientsOfLive(liveId),
            dao.observeBagsOfLive(liveId)
        ) { live, clients, bags ->
            if (live == null) {
                null
            } else {
                val summaries = buildClientSummaries(clients.map { it.toModel() }, bags.map { it.toModel() })
                LiveDetail(live.id, live.title, live.dateMillis, LiveKind.fromCode(live.kind), computeStats(summaries), summaries)
            }
        }

    /** Emballages de tous les lots (écrans « Clients » et « Emballages »). */
    fun observePackaging(): Flow<PackagingData> = snapshots().map { snap ->
        val groups = snap.groups()
        val usedKeys = groups.map { it.batchKey }.toSet()
        PackagingData(groups, buildBatches(snap.liveInfos).filter { it.key in usedKeys })
    }

    /** Fiche d'un client à partir de l'une de ses fiches (samedi ou dimanche). `null` = n'existe plus. */
    fun observeClient(clientId: Long): Flow<ClientDetail?> = snapshots().map { snap ->
        val group = snap.groups().firstOrNull { clientId in it.clientIds }
        if (group == null) {
            null
        } else {
            val options = snap.liveInfos
                .filter { batchKey(it) == group.batchKey }
                .sortedWith(compareBy({ it.dateMillis }, { it.id }))
            ClientDetail(group, options)
        }
    }

    /** Clients déjà connus dans le lot du Live (ce Live + l'autre Live du week-end) : pour retrouver un client. */
    fun observeNameSuggestions(liveId: Long): Flow<List<NameSuggestion>> = snapshots().map { snap ->
        val infos = snap.liveInfos
        val live = infos.firstOrNull { it.id == liveId }
        if (live == null) {
            emptyList()
        } else {
            val key = batchKey(live)
            val sameBatch = infos.filter { batchKey(it) == key }.map { it.id }.toSet()
            snap.clients.filter { it.liveId in sameBatch }.map { NameSuggestion(it.name, it.liveId == liveId) }
        }
    }

    // ---------------- Écriture : Lives ----------------

    suspend fun createLive(title: String, dateMillis: Long, kind: LiveKind): Long =
        dao.insertLive(LiveEntity(title = title.trim(), dateMillis = dateMillis, kind = kind.code))

    /** À n'appeler qu'après vérification du mot de passe administrateur. */
    suspend fun updateLive(liveId: Long, title: String, dateMillis: Long) =
        dao.updateLive(liveId, title.trim(), dateMillis)

    // ---------------- Écriture : clients et sacs ----------------

    /** Nom (orthographe d'origine) du client déjà présent dans ce Live, ou null. */
    suspend fun findClientName(liveId: Long, name: String): String? =
        dao.findClient(liveId, normalizeName(name))?.name

    /** Doublons de codes JP dans ce Live (et codes saisis deux fois), en ignorant le sac `excludeBagId`. */
    suspend fun findCodeConflicts(liveId: Long, codes: List<String>, excludeBagId: Long? = null): List<JpConflict> {
        val existing = dao.codesOfLive(liveId)
            .filter { it.bagId != excludeBagId }
            .map { ExistingCode(it.jpCode, it.clientName) }
        return findJpConflicts(codes, existing)
    }

    suspend fun checkBeforeSave(liveId: Long, name: String, codes: List<String>): SaveCheck =
        SaveCheck(findClientName(liveId, name), findCodeConflicts(liveId, codes))

    /**
     * Ajoute des sacs à un client du Live. Si le nom existe déjà (sans tenir compte des majuscules),
     * les sacs sont ajoutés à la fiche existante : jamais de doublon. L'emballage repasse « À emballer ».
     */
    suspend fun addBags(liveId: Long, name: String, bags: List<NewBag>): Long =
        db.withTransaction {
            val key = normalizeName(name)
            val existing = dao.findClient(liveId, key)
            val clientId = existing?.id
                ?: dao.insertClient(ClientEntity(liveId = liveId, name = name.trim(), nameKey = key))
            bags.forEach { bag ->
                dao.insertBag(BagEntity(clientId = clientId, jpCode = bag.jpCode.trim(), price = bag.price))
            }
            if (existing != null && existing.packed) dao.setPacked(listOf(clientId), false)
            clientId
        }

    /** Ajout rapide d'un sac à une fiche existante (nom non ressaisi). */
    suspend fun addBag(clientId: Long, jpCode: String, price: Long) {
        db.withTransaction {
            dao.insertBag(BagEntity(clientId = clientId, jpCode = jpCode.trim(), price = price))
            dao.setPacked(listOf(clientId), false)
        }
    }

    /** Ajoute un sac au client (identifié par l'une de ses fiches) dans le Live d'origine choisi. */
    suspend fun addBagToClient(anyClientId: Long, liveId: Long, jpCode: String, price: Long) {
        val client = dao.getClient(anyClientId) ?: return
        addBags(liveId, client.name, listOf(NewBag(jpCode, price)))
    }

    suspend fun updateBag(bag: Bag) {
        dao.updateBag(
            BagEntity(id = bag.id, clientId = bag.clientId, jpCode = bag.jpCode.trim(), price = bag.price)
        )
    }

    /** Supprime un sac ; si c'était le dernier de cette fiche, la fiche est supprimée aussi (renvoie true). */
    suspend fun deleteBag(bagId: Long): Boolean =
        db.withTransaction {
            val bag = dao.getBag(bagId)
            if (bag == null) {
                false
            } else {
                dao.deleteBag(bagId)
                if (dao.countBags(bag.clientId) == 0) {
                    dao.deleteClient(bag.clientId)
                    true
                } else {
                    false
                }
            }
        }

    // ---------------- Emballage et livraison ----------------

    suspend fun setPacked(group: PackageGroup, packed: Boolean) = dao.setPacked(group.clientIds, packed)

    suspend fun setDelivery(group: PackageGroup, day: DeliveryDay?) = dao.setDelivery(group.clientIds, day?.code)

    // ---------------- Sauvegarde / restauration ----------------

    suspend fun exportBackupJson(): String {
        val data = BackupData(
            exportedAt = Instant.now().toString(),
            lives = dao.allLivesOnce().map { BackupLive(it.id, it.title, it.dateMillis, it.kind) },
            clients = dao.allClientsOnce().map { BackupClient(it.id, it.liveId, it.name, it.packed, it.deliveryDay) },
            bags = dao.allBagsOnce().map { BackupBag(it.id, it.clientId, it.jpCode, it.price) }
        )
        return BackupCodec.encode(data)
    }

    suspend fun currentCounts(): BackupCounts =
        BackupCounts(dao.allLivesOnce().size, dao.allClientsOnce().size, dao.allBagsOnce().size)

    /** Remplace TOUTES les données par celles de la sauvegarde, en une seule transaction (tout ou rien). */
    suspend fun restoreBackup(data: BackupData) {
        db.withTransaction {
            dao.deleteAllBags()
            dao.deleteAllClients()
            dao.deleteAllLives()
            data.lives.forEach { dao.insertLive(LiveEntity(it.id, it.title, it.dateMillis, it.kind)) }
            data.clients.forEach {
                dao.insertClient(
                    ClientEntity(it.id, it.liveId, it.name, normalizeName(it.name), it.packed, it.deliveryDay)
                )
            }
            data.bags.forEach { dao.insertBag(BagEntity(it.id, it.clientId, it.jpCode, it.price)) }
        }
    }
}
