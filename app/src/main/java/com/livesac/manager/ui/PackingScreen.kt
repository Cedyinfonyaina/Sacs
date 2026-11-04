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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import com.livesac.manager.domain.PackFilter
import com.livesac.manager.domain.PackageGroup
import com.livesac.manager.domain.applyPackFilter
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.packProgress
import com.livesac.manager.domain.plural
import com.livesac.manager.domain.sortForPacking

private const val SCOPE_LATEST = ""
private const val SCOPE_ALL = "*"

/** Onglet « Emballages » : préparation des commandes et livraisons (un seul emballage par client). */
@Composable
fun PackingScreen(onOpenClient: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val repo = rememberRepository()
    val vm: PackingViewModel = viewModel(factory = VmFactory { PackingViewModel(repo) })
    val dataState by vm.data.collectAsStateWithLifecycle()
    var filterName by rememberSaveable { mutableStateOf(PackFilter.ALL.name) }
    var scope by rememberSaveable { mutableStateOf(SCOPE_LATEST) }
    val filter = PackFilter.valueOf(filterName)

    AppScaffold(
        topBar = { AppTopBar("Emballages", actions = { SettingsAction(onOpenSettings) }) }
    ) { padding ->
        val data = dataState
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (data != null) {
                if (data.groups.isEmpty()) {
                    EmptyMessage("Aucun emballage à préparer.\nAjoutez des JP dans un Live.")
                } else {
                    // Lot affiché : le plus récent par défaut, ou un autre au choix, ou tout.
                    val selectedKey = when (scope) {
                        SCOPE_ALL -> null
                        SCOPE_LATEST -> data.batches.firstOrNull()?.key
                        else -> if (data.batches.any { it.key == scope }) scope else data.batches.firstOrNull()?.key
                    }
                    val inScope = remember(data, selectedKey) {
                        if (selectedKey == null) data.groups else data.groups.filter { it.batchKey == selectedKey }
                    }
                    val progress = remember(inScope) { packProgress(inScope) }
                    val visible = remember(inScope, filter) { sortForPacking(applyPackFilter(inScope, filter)) }

                    Column(Modifier.fillMaxSize()) {
                        ProgressCard(progress.label, progress.fraction)

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(data.batches, key = { it.key }) { batch ->
                                FilterChip(
                                    selected = selectedKey == batch.key,
                                    onClick = { scope = batch.key },
                                    label = { Text(batch.label) }
                                )
                            }
                            item(key = "all-batches") {
                                FilterChip(
                                    selected = selectedKey == null,
                                    onClick = { scope = SCOPE_ALL },
                                    label = { Text("Tout") }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(PackFilter.values().toList(), key = { it.name }) { f ->
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filterName = f.name },
                                    label = { Text(f.label) }
                                )
                            }
                        }

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (visible.isEmpty()) {
                                EmptyMessage("Aucun emballage dans « ${filter.label} ».")
                            } else {
                                val hasDelivery = visible.any { it.deliveryDay != null }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    var lastDay: Any? = Unit
                                    visible.forEach { group ->
                                        // Classement automatique par jour de livraison, avec un titre par jour.
                                        if (hasDelivery && group.deliveryDay != lastDay) {
                                            lastDay = group.deliveryDay
                                            item(key = "h-" + (group.deliveryDay?.code ?: "none")) {
                                                Text(
                                                    text = group.deliveryDay?.let { "Livraison " + it.label.lowercase() }
                                                        ?: "Livraison non définie",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 8.dp)
                                                )
                                            }
                                        }
                                        item(key = group.id) {
                                            PackCard(
                                                group = group,
                                                onOpen = { onOpenClient(group.representativeClientId) },
                                                onToggle = { vm.togglePacked(group) },
                                                onDelivery = { vm.setDelivery(group, it) }
                                            )
                                        }
                                    }
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
private fun ProgressCard(label: String, fraction: Float) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun PackCard(
    group: PackageGroup,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDelivery: (com.livesac.manager.domain.DeliveryDay?) -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
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
                Text(
                    text = formatAr(group.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = plural(group.bagCount, "sac") + " · " + group.originsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackStatusBadge(group.packed, onClick = onToggle)
                DeliveryChip(group.deliveryDay, onChange = onDelivery)
            }
        }
    }
}
