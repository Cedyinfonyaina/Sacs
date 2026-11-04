package com.livesac.manager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livesac.manager.data.ClientDetail
import com.livesac.manager.data.LiveDetail
import com.livesac.manager.data.LiveSummary
import com.livesac.manager.data.NewBag
import com.livesac.manager.data.PackagingData
import com.livesac.manager.data.Repository
import com.livesac.manager.data.SaveCheck
import com.livesac.manager.domain.Bag
import com.livesac.manager.domain.DeliveryDay
import com.livesac.manager.domain.JpConflict
import com.livesac.manager.domain.LiveKind
import com.livesac.manager.domain.NameSuggestion
import com.livesac.manager.domain.PackageGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fabrique simple pour créer un ViewModel avec ses paramètres. */
class VmFactory(private val builder: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
}

/** Onglet Lives : liste des Lives. `null` = chargement en cours. */
class LivesViewModel(private val repo: Repository) : ViewModel() {

    val lives: StateFlow<List<LiveSummary>?> =
        repo.observeLiveSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createLive(title: String, dateMillis: Long, kind: LiveKind, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.createLive(title, dateMillis, kind)
            onCreated(id)
        }
    }
}

/** Récapitulatif d'un Live. */
class LiveViewModel(private val repo: Repository, private val liveId: Long) : ViewModel() {

    val live: StateFlow<LiveDetail?> =
        repo.observeLive(liveId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** À n'appeler qu'après le mot de passe administrateur. */
    fun updateLive(title: String, dateMillis: Long) {
        viewModelScope.launch { repo.updateLive(liveId, title, dateMillis) }
    }

    /** Ajout rapide d'un sac à un client existant de ce Live. */
    fun addBag(clientId: Long, code: String, price: Long) {
        viewModelScope.launch { repo.addBag(clientId, code, price) }
    }

    fun checkCode(code: String, onResult: (List<JpConflict>) -> Unit) {
        viewModelScope.launch { onResult(repo.findCodeConflicts(liveId, listOf(code))) }
    }
}

/** Onglet Clients. */
class ClientsViewModel(repo: Repository) : ViewModel() {
    val data: StateFlow<PackagingData?> =
        repo.observePackaging().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Onglet Emballages. */
class PackingViewModel(private val repo: Repository) : ViewModel() {

    val data: StateFlow<PackagingData?> =
        repo.observePackaging().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePacked(group: PackageGroup) {
        viewModelScope.launch { repo.setPacked(group, !group.packed) }
    }

    fun setDelivery(group: PackageGroup, day: DeliveryDay?) {
        viewModelScope.launch { repo.setDelivery(group, day) }
    }
}

/** Une ligne « sac » du formulaire (code JP + prix saisi en texte). */
class BagInput(val key: Int) {
    var code by mutableStateOf("")
    var price by mutableStateOf("")
}

/** Formulaire « Ajouter un JP ». L'état du formulaire survit à la rotation de l'écran. */
class AddJpViewModel(private val repo: Repository, private val liveId: Long) : ViewModel() {

    var name by mutableStateOf("")
    val rows = mutableStateListOf(BagInput(0))
    private var nextKey = 1

    /** Clients déjà connus (ce Live + l'autre Live du week-end) pour retrouver un client existant. */
    val knownClients: StateFlow<List<NameSuggestion>> =
        repo.observeNameSuggestions(liveId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addRow() {
        rows.add(BagInput(nextKey++))
    }

    fun removeRow(row: BagInput) {
        if (rows.size > 1) rows.remove(row)
    }

    /** Détecte un client déjà présent (casse ignorée) et les codes JP déjà utilisés dans le Live. */
    fun check(onResult: (SaveCheck) -> Unit) {
        val currentName = name
        val codes = rows.map { it.code }
        viewModelScope.launch { onResult(repo.checkBeforeSave(liveId, currentName, codes)) }
    }

    fun save(onSaved: () -> Unit) {
        val currentName = name
        val bags = rows.map { NewBag(it.code.trim(), it.price.toLongOrNull() ?: 0L) }
        viewModelScope.launch {
            repo.addBags(liveId, currentName, bags)
            onSaved()
        }
    }
}

/** État de la fiche client : chargement, supprimée, ou prête. */
sealed interface ClientState {
    data object Loading : ClientState
    data object Missing : ClientState
    data class Ready(val detail: ClientDetail) : ClientState
}

/** Fiche détaillée d'un client. */
class ClientViewModel(private val repo: Repository, private val clientId: Long) : ViewModel() {

    val state: StateFlow<ClientState> =
        repo.observeClient(clientId)
            .map { if (it == null) ClientState.Missing else ClientState.Ready(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientState.Loading)

    fun addBag(liveId: Long, code: String, price: Long) {
        viewModelScope.launch { repo.addBagToClient(clientId, liveId, code, price) }
    }

    fun updateBag(bag: Bag, code: String, price: Long) {
        viewModelScope.launch { repo.updateBag(bag.copy(jpCode = code, price = price)) }
    }

    /** Si c'était le dernier sac, la fiche disparaît et l'état passe à `Missing` (l'écran se ferme). */
    fun deleteBag(bagId: Long) {
        viewModelScope.launch { repo.deleteBag(bagId) }
    }

    fun setPacked(group: PackageGroup, packed: Boolean) {
        viewModelScope.launch { repo.setPacked(group, packed) }
    }

    fun setDelivery(group: PackageGroup, day: DeliveryDay?) {
        viewModelScope.launch { repo.setDelivery(group, day) }
    }

    fun checkCode(liveId: Long, code: String, excludeBagId: Long?, onResult: (List<JpConflict>) -> Unit) {
        viewModelScope.launch { onResult(repo.findCodeConflicts(liveId, listOf(code), excludeBagId)) }
    }
}
