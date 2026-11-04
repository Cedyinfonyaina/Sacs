@file:OptIn(ExperimentalMaterial3Api::class)

package com.livesac.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livesac.manager.data.SaveCheck
import com.livesac.manager.domain.describeConflicts
import com.livesac.manager.domain.findDuplicateName
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.suggestNames

/** Formulaire « Ajouter un JP » (un client, un ou plusieurs sacs). */
@Composable
fun AddJpScreen(liveId: Long, onBack: () -> Unit) {
    val repo = rememberRepository()
    val vm: AddJpViewModel = viewModel(factory = VmFactory { AddJpViewModel(repo, liveId) })
    val known by vm.knownClients.collectAsStateWithLifecycle()

    var saving by remember { mutableStateOf(false) }
    var pendingCheck by remember { mutableStateOf<SaveCheck?>(null) }

    val valid = vm.name.isNotBlank() && vm.rows.all { row ->
        row.code.isNotBlank() && (row.price.toLongOrNull() ?: 0L) > 0L
    }
    val suggestions = remember(known, vm.name) { suggestNames(known, vm.name) }
    val sameLiveMatch = remember(known, vm.name) {
        findDuplicateName(known.filter { it.inThisLive }.map { it.name }, vm.name)
    }

    fun doSave() {
        saving = true
        vm.save { onBack() }
    }

    AppScaffold(
        topBar = { AppTopBar("Ajouter un JP", onBack) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        saving = true
                        vm.check { result ->
                            if (!result.needsConfirmation) {
                                doSave()
                            } else {
                                saving = false
                                pendingCheck = result
                            }
                        }
                    },
                    enabled = valid && !saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
                ) {
                    Text("Enregistrer", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("Nom Facebook") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            // Retrouver un client existant avant de créer un doublon.
            if (sameLiveMatch != null) {
                Text(
                    "« $sameLiveMatch » est déjà dans ce Live : les sacs seront ajoutés à sa fiche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (suggestions.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Client déjà enregistré ?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                        )
                        suggestions.forEach { s ->
                            TextButton(
                                onClick = { vm.name = s.name },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = s.name + if (s.inThisLive) "  (dans ce Live)" else "  (autre Live du week-end)",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            vm.rows.forEachIndexed { index, row ->
                key(row.key) {
                    BagRowCard(
                        number = index + 1,
                        row = row,
                        canRemove = vm.rows.size > 1,
                        onRemove = { vm.removeRow(row) }
                    )
                }
            }

            OutlinedButton(
                onClick = { vm.addRow() },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("+ Ajouter un autre sac", fontSize = 16.sp)
            }
        }
    }

    pendingCheck?.let { check ->
        val message = buildString {
            if (check.existingName != null) {
                append("« ${check.existingName} » existe déjà dans ce Live : les sacs seront ajoutés à sa fiche existante.")
            }
            if (check.conflicts.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Code JP déjà utilisé dans ce Live :\n")
                append(describeConflicts(check.conflicts))
            }
        }
        AlertDialog(
            onDismissRequest = { pendingCheck = null },
            containerColor = Color.White,
            title = {
                Text(if (check.conflicts.isNotEmpty()) "Vérifiez avant d'enregistrer" else "Client déjà dans ce Live")
            },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = {
                    pendingCheck = null
                    doSave()
                }) { Text(if (check.conflicts.isNotEmpty()) "Enregistrer quand même" else "Ajouter à sa fiche") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCheck = null }) {
                    Text(if (check.conflicts.isNotEmpty()) "Modifier" else "Changer le nom")
                }
            }
        )
    }
}

@Composable
private fun BagRowCard(number: Int, row: BagInput, canRemove: Boolean, onRemove: () -> Unit) {
    val amount = row.price.toLongOrNull() ?: 0L

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sac $number",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Retirer ce sac")
                    }
                }
            }
            OutlinedTextField(
                value = row.code,
                onValueChange = { row.code = it },
                label = { Text("Code JP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = row.price,
                onValueChange = { typed -> row.price = typed.filter { c -> c in '0'..'9' }.take(9) },
                label = { Text("Prix du sac") },
                suffix = { Text("Ar") },
                supportingText = { if (amount > 0) Text(formatAr(amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
