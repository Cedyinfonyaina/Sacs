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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livesac.manager.data.LiveSummary
import com.livesac.manager.domain.LiveKind
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.formatDateShort
import com.livesac.manager.domain.plural
import com.livesac.manager.domain.proposeLive
import com.livesac.manager.domain.todayUtcMillis

/** Onglet « Lives » : liste des Lives et création rapide. */
@Composable
fun LivesScreen(onOpenLive: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val repo = rememberRepository()
    val vm: LivesViewModel = viewModel(factory = VmFactory { LivesViewModel(repo) })
    val livesState by vm.lives.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }

    AppScaffold(
        topBar = { AppTopBar("Lives", actions = { SettingsAction(onOpenSettings) }) },
        floatingActionButton = { AppFab("+ Nouveau Live") { showNew = true } }
    ) { padding ->
        val lives = livesState
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                lives == null -> Unit
                lives.isEmpty() -> EmptyMessage("Aucun Live pour le moment.\nAppuyez sur « + Nouveau Live » pour commencer.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(lives, key = { it.id }) { live ->
                        LiveCard(live) { onOpenLive(live.id) }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewLiveDialog(
            existing = livesState.orEmpty(),
            onDismiss = { showNew = false },
            onCreate = { title, dateMillis, kind ->
                vm.createLive(title, dateMillis, kind) { newId ->
                    showNew = false
                    onOpenLive(newId)
                }
            }
        )
    }
}

@Composable
private fun LiveCard(live: LiveSummary, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = live.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (live.kind != LiveKind.OTHER) {
                    TagBadge(live.kind.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(
                text = formatDateShort(live.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = plural(live.stats.clientCount, "client"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = plural(live.stats.bagCount, "sac"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatAr(live.stats.total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Création rapide : on choisit Mercredi, Samedi ou Dimanche et l'application propose le titre
 * et la date du prochain Live de ce jour. Le titre reste modifiable avant la création.
 */
@Composable
private fun NewLiveDialog(
    existing: List<LiveSummary>,
    onDismiss: () -> Unit,
    onCreate: (String, Long, LiveKind) -> Unit
) {
    val today = remember { todayUtcMillis() }
    var kind by remember { mutableStateOf(LiveKind.WED) }
    var proposal by remember { mutableStateOf(proposeLive(LiveKind.WED, today)) }
    var title by remember { mutableStateOf(proposal.title) }
    var dateMillis by remember { mutableStateOf(proposal.dateMillis) }
    var titleEdited by remember { mutableStateOf(false) }

    fun choose(newKind: LiveKind) {
        kind = newKind
        proposal = proposeLive(newKind, today)
        dateMillis = proposal.dateMillis
        if (!titleEdited) title = proposal.title
    }

    val alreadyExists = existing.any { it.kind == kind && it.dateMillis == dateMillis }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Nouveau Live") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveKind.weekly.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { choose(k) },
                            label = { Text(k.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleEdited = true
                    },
                    label = { Text("Titre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(dateMillis) { picked ->
                    dateMillis = picked
                    if (!titleEdited) title = com.livesac.manager.domain.defaultLiveTitle(picked)
                }
                if (alreadyExists) {
                    Text(
                        "Un Live « ${kind.label} » existe déjà à cette date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.trim(), dateMillis, kind) },
                enabled = title.isNotBlank()
            ) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
