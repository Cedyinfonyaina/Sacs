package com.livesac.manager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---- Lives ----
    @Insert
    suspend fun insertLive(live: LiveEntity): Long

    @Query("SELECT * FROM lives ORDER BY dateMillis DESC, id DESC")
    fun observeLives(): Flow<List<LiveEntity>>

    @Query("SELECT * FROM lives WHERE id = :id")
    fun observeLive(id: Long): Flow<LiveEntity?>

    // ---- Lecture globale (liste des Lives) ----
    @Query("SELECT * FROM clients")
    fun observeAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM bags")
    fun observeAllBags(): Flow<List<BagEntity>>

    // ---- Un Live ----
    @Query("SELECT * FROM clients WHERE liveId = :liveId")
    fun observeClientsOfLive(liveId: Long): Flow<List<ClientEntity>>

    @Query(
        "SELECT bags.* FROM bags " +
            "INNER JOIN clients ON bags.clientId = clients.id " +
            "WHERE clients.liveId = :liveId ORDER BY bags.id"
    )
    fun observeBagsOfLive(liveId: Long): Flow<List<BagEntity>>

    // ---- Un client ----
    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeClient(id: Long): Flow<ClientEntity?>

    @Query("SELECT * FROM bags WHERE clientId = :clientId ORDER BY id")
    fun observeBagsOfClient(clientId: Long): Flow<List<BagEntity>>

    @Query("SELECT * FROM clients WHERE liveId = :liveId AND nameKey = :nameKey LIMIT 1")
    suspend fun findClient(liveId: Long, nameKey: String): ClientEntity?

    @Insert
    suspend fun insertClient(client: ClientEntity): Long

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClient(id: Long)

    // ---- Sacs ----
    @Insert
    suspend fun insertBag(bag: BagEntity): Long

    @Update
    suspend fun updateBag(bag: BagEntity)

    @Query("SELECT * FROM bags WHERE id = :id")
    suspend fun getBag(id: Long): BagEntity?

    @Query("DELETE FROM bags WHERE id = :id")
    suspend fun deleteBag(id: Long)

    @Query("SELECT COUNT(*) FROM bags WHERE clientId = :clientId")
    suspend fun countBags(clientId: Long): Int

    // ---- Mise à jour d'un Live ----
    @Query("UPDATE lives SET title = :title, dateMillis = :dateMillis WHERE id = :id")
    suspend fun updateLive(id: Long, title: String, dateMillis: Long)

    // ---- Emballage et livraison (appliqués à toutes les fiches d'un même emballage) ----
    @Query("UPDATE clients SET packed = :packed WHERE id IN (:ids)")
    suspend fun setPacked(ids: List<Long>, packed: Boolean)

    @Query("UPDATE clients SET deliveryDay = :day WHERE id IN (:ids)")
    suspend fun setDelivery(ids: List<Long>, day: String?)

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClient(id: Long): ClientEntity?

    // ---- Doublons de codes JP ----
    @Query(
        "SELECT bags.id AS bagId, bags.jpCode AS jpCode, clients.name AS clientName FROM bags " +
            "INNER JOIN clients ON bags.clientId = clients.id WHERE clients.liveId = :liveId"
    )
    suspend fun codesOfLive(liveId: Long): List<CodeOwner>

    @Query("SELECT * FROM clients WHERE liveId = :liveId")
    suspend fun clientsOfLiveOnce(liveId: Long): List<ClientEntity>

    // ---- Sauvegarde / restauration ----
    @Query("SELECT * FROM lives ORDER BY id")
    suspend fun allLivesOnce(): List<LiveEntity>

    @Query("SELECT * FROM clients ORDER BY id")
    suspend fun allClientsOnce(): List<ClientEntity>

    @Query("SELECT * FROM bags ORDER BY id")
    suspend fun allBagsOnce(): List<BagEntity>

    @Query("DELETE FROM bags")
    suspend fun deleteAllBags()

    @Query("DELETE FROM clients")
    suspend fun deleteAllClients()

    @Query("DELETE FROM lives")
    suspend fun deleteAllLives()
}

/** Résultat de la requête « codes JP d'un Live » : code + nom du client propriétaire. */
data class CodeOwner(val bagId: Long, val jpCode: String, val clientName: String)
