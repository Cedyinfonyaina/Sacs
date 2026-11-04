@file:OptIn(ExperimentalMaterial3Api::class)

package com.livesac.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livesac.manager.data.LiveDetail
import com.livesac.manager.domain.ClientSummary
import com.livesac.manager.domain.describeConflicts
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.formatDateLong
import com.livesac.manager.domain.matchesQuery
import com.livesac.manager.domain.plural

/** Récapitulatif d'un Live : recherche et clients de ce Live. */
@Composable
fun LiveScreen(
    liveId: Long,
    onBack: () -> Unit,
    onAddJp: () -> Unit,
    onOpenClient: (Long) -> Unit
) {
    val repo = rememberRepository()
    val auth = rememberAdminAuth()
    val vm: LiveViewModel = viewModel(factory = VmFactory { LiveViewModel(repo, liveId) })
    val liveState by vm.live.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    // Modification protégée : mot de passe d'abord, puis formulaire.
    var askPassword by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    // Ajout rapide d'un sac à un client existant.
    var quickAddFor by remember { mutableStateOf<ClientSummary?>(null) }
    var conflictText by remember { mutableStateOf<String?>(null) }
    var pendingAdd by remember { mutableStateOf<(() -> Unit)?>(null) }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Récapitulatif",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { askPassword = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Modifier le titre ou la date (mot de passe)")
                    }
                }
            )
        },
        floatingActionButton = { AppFab("+ Ajouter un JP", onAddJp) }
    ) { padding ->
        val live = liveState
        if (live != null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                SummaryHeader(live)
                SearchField(query) { query = it }

                val visible = remember(live, query) { live.clients.filter { matchesQuery(it, query) } }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        live.clients.isEmpty() ->
                            EmptyMessage("Aucun client pour le moment.\nAppuyez sur « + Ajouter un JP » pour commencer.")

                        visible.isEmpty() ->
                            EmptyMessage("Aucun résultat pour « ${query.trim()} ».")

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visible, key = { it.id }) { client ->
                                ClientCard(
                                    client = client,
                                    onClick = { onOpenClient(client.id) },
                                    onQuickAdd = { quickAddFor = client }
                                )
                            }
                        }
                    }
                }
            }

            if (askPassword) {
                PasswordPromptDialog(
                    auth = auth,
                    onSuccess = { askPassword = false; editing = true },
                    onDismiss = { askPassword = false }
                )
            }
            if (editing) {
                LiveEditDialog(
                    initialTitle = live.title,
                    initialDateMillis = live.dateMillis,
                    onDismiss = { editing = false },
                    onSave = { title, date ->
                        vm.updateLive(title, date)
                        editing = false
                    }
                )
            }
        }
    }

    quickAddFor?.let { client ->
        BagDialog(
            title = "Ajouter un sac à " + client.name,
            initialCode = "",
            initialPrice = "",
            onDismiss = { quickAddFor = null },
            onConfirm = { code, price, _ ->
                quickAddFor = null
                vm.checkCode(code) { conflicts ->
                    if (conflicts.isEmpty()) {
                        vm.addBag(client.id, code, price)
                    } else {
                        conflictText = describeConflicts(conflicts)
                        pendingAdd = { vm.addBag(client.id, code, price) }
                    }
                }
            }
        )
    }

    conflictText?.let { message ->
        ConflictDialog(
            message = message,
            onConfirm = { pendingAdd?.invoke(); pendingAdd = null; conflictText = null },
            onDismiss = { pendingAdd = null; conflictText = null }
        )
    }
}

@Composable
private fun SummaryHeader(live: LiveDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = live.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDateLong(live.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                HeaderStat("Clients", live.stats.clientCount.toString(), Modifier.weight(1f))
                HeaderStat("Sacs", live.stats.bagCount.toString(), Modifier.weight(1f))
                HeaderStat("Total ventes", formatAr(live.stats.total), Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ClientCard(client: ClientSummary, onClick: () -> Unit, onQuickAdd: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)) {
            Text(
                text = client.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "JP : " + client.jpCodes.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plural(client.bagCount, "sac"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatAr(client.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onQuickAdd) { Text("+ Sac", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
