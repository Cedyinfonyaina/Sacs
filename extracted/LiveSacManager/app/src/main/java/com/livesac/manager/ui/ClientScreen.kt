@file:OptIn(ExperimentalMaterial3Api::class)

package com.livesac.manager.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livesac.manager.domain.Bag
import com.livesac.manager.domain.PackBag
import com.livesac.manager.domain.PackageGroup
import com.livesac.manager.domain.describeConflicts
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.plural

/** Fiche client : sacs (avec Live d'origine), total, emballage, livraison, récapitulatif à partager. */
@Composable
fun ClientScreen(clientId: Long, onBack: () -> Unit) {
    val repo = rememberRepository()
    val vm: ClientViewModel = viewModel(factory = VmFactory { ClientViewModel(repo, clientId) })
    val state by vm.state.collectAsStateWithLifecycle()

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PackBag?>(null) }
    var deleting by remember { mutableStateOf<PackBag?>(null) }
    var showRecap by remember { mutableStateOf(false) }
    var conflictText by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }

    // La fiche n'existe plus (dernier sac supprimé et confirmé) : on revient en arrière, une seule fois.
    LaunchedEffect(state) {
        if (state is ClientState.Missing) onBack()
    }

    AppScaffold(
        topBar = { AppTopBar("Fiche client", onBack) },
        floatingActionButton = {
            if (state is ClientState.Ready) AppFab("+ Ajouter un sac") { adding = true }
        }
    ) { padding ->
        val detail = (state as? ClientState.Ready)?.detail
        if (detail != null) {
            val group = detail.group
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ClientHeader(group) }
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Emballage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PackStatusBadge(group.packed) { vm.setPacked(group, !group.packed) }
                                DeliveryChip(group.deliveryDay, onChange = { vm.setDelivery(group, it) })
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showRecap = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("  Partager le récapitulatif", fontWeight = FontWeight.SemiBold)
                    }
                }
                item {
                    Text(
                        text = "Sacs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(group.bags, key = { it.id }) { bag ->
                    BagRow(bag = bag, onEdit = { editing = bag }, onDelete = { deleting = bag })
                }
            }

            if (showRecap) RecapDialog(group) { showRecap = false }

            if (adding) {
                BagDialog(
                    title = "Ajouter un sac",
                    initialCode = "",
                    initialPrice = "",
                    liveOptions = detail.liveOptions,
                    initialLiveId = group.bags.lastOrNull()?.liveId,
                    onDismiss = { adding = false },
                    onConfirm = { code, price, liveId ->
                        adding = false
                        val target = liveId ?: detail.liveOptions.lastOrNull()?.id ?: group.lives.last().id
                        vm.checkCode(target, code, null) { conflicts ->
                            if (conflicts.isEmpty()) {
                                vm.addBag(target, code, price)
                            } else {
                                conflictText = describeConflicts(conflicts)
                                pendingSave = { vm.addBag(target, code, price) }
                            }
                        }
                    }
                )
            }

            editing?.let { bag ->
                BagDialog(
                    title = "Modifier le sac",
                    initialCode = bag.jpCode,
                    initialPrice = bag.price.toString(),
                    onDismiss = { editing = null },
                    onConfirm = { code, price, _ ->
                        editing = null
                        val model = Bag(bag.id, bag.clientId, bag.jpCode, bag.price)
                        vm.checkCode(bag.liveId, code, bag.id) { conflicts ->
                            if (conflicts.isEmpty()) {
                                vm.updateBag(model, code, price)
                            } else {
                                conflictText = describeConflicts(conflicts)
                                pendingSave = { vm.updateBag(model, code, price) }
                            }
                        }
                    }
                )
            }

            deleting?.let { bag ->
                DeleteBagDialog(
                    bag = bag,
                    isLastBag = detail.group.bagCount <= 1,
                    onConfirm = {
                        vm.deleteBag(bag.id)
                        deleting = null
                    },
                    onDismiss = { deleting = null }
                )
            }
        }
    }

    conflictText?.let { message ->
        ConflictDialog(
            message = message,
            onConfirm = { pendingSave?.invoke(); pendingSave = null; conflictText = null },
            onDismiss = { pendingSave = null; conflictText = null }
        )
    }
}

/** Confirmation obligatoire avant toute suppression ; avertissement renforcé pour le dernier sac. */
@Composable
private fun DeleteBagDialog(bag: PackBag, isLastBag: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (isLastBag) "Supprimer le dernier sac ?" else "Supprimer ce sac ?") },
        text = {
            Text(
                buildString {
                    append(bag.jpCode).append(" — ").append(formatAr(bag.price))
                    if (isLastBag) {
                        append("\n\nC'est le dernier sac de ce client : sa fiche sera aussi supprimée.")
                    }
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(if (isLastBag) "Supprimer le sac et la fiche" else "Supprimer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ClientHeader(group: PackageGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "JP : " + group.jpCodes.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = group.originsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = plural(group.bagCount, "sac") + " au total",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = "Total à payer",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = formatAr(group.total),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BagRow(bag: PackBag, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = bag.jpCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatAr(bag.price),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                TagBadge(bag.originLabel, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier ce sac")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer ce sac")
            }
        }
    }
}
