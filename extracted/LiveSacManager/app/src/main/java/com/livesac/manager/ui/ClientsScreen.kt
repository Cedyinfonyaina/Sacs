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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livesac.manager.domain.PackageGroup
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.matchesQuery
import com.livesac.manager.domain.plural
import com.livesac.manager.domain.sortForClientList

/** Onglet « Clients » : tous les clients (un emballage = une ligne), avec recherche par nom ou code JP. */
@Composable
fun ClientsScreen(onOpenClient: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val repo = rememberRepository()
    val vm: ClientsViewModel = viewModel(factory = VmFactory { ClientsViewModel(repo) })
    val dataState by vm.data.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    AppScaffold(
        topBar = { AppTopBar("Clients", actions = { SettingsAction(onOpenSettings) }) }
    ) { padding ->
        val data = dataState
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query) { query = it }
            if (data != null) {
                val visible = remember(data, query) {
                    sortForClientList(data.groups.filter { matchesQuery(it, query) }, data.batches)
                }
                val labels = remember(data) { data.batches.associate { it.key to it.label } }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        data.groups.isEmpty() ->
                            EmptyMessage("Aucun client pour le moment.\nCréez un Live puis ajoutez des JP.")

                        visible.isEmpty() ->
                            EmptyMessage("Aucun résultat pour « ${query.trim()} ».")

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            var lastBatch: String? = null
                            visible.forEach { group ->
                                if (group.batchKey != lastBatch) {
                                    lastBatch = group.batchKey
                                    item(key = "h-" + group.batchKey) {
                                        Text(
                                            text = labels[group.batchKey].orEmpty(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                                item(key = group.id) {
                                    ClientGroupCard(group) { onOpenClient(group.representativeClientId) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientGroupCard(group: PackageGroup, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "JP : " + group.jpCodes.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PackStatusBadge(group.packed, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plural(group.bagCount, "sac") + " · " + group.originsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatAr(group.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
