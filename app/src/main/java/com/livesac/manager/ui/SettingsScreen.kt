@file:OptIn(ExperimentalMaterial3Api::class)

package com.livesac.manager.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livesac.manager.data.BackupCounts
import com.livesac.manager.domain.BackupCodec
import com.livesac.manager.domain.BackupData
import com.livesac.manager.domain.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Paramètres : sauvegarde, restauration, mot de passe administrateur. Accessible par l'icône en haut. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val auth = rememberAdminAuth()
    val scope = rememberCoroutineScope()

    var changePassword by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<BackupData, BackupCounts>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    // Export : l'utilisateur choisit où enregistrer le fichier (aucune permission requise).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = repo.exportBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("Impossible d'écrire le fichier.")
                    }
                    message = "Sauvegarde exportée."
                    toast("Sauvegarde exportée")
                } catch (e: Exception) {
                    message = "Échec de l'export : " + (e.message ?: "erreur inconnue")
                }
            }
        }
    }

    // Restauration : on lit et on VALIDE le fichier, puis on demande confirmation.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                            ?: throw BackupException("Impossible de lire le fichier.")
                    }
                    val data = BackupCodec.decode(text)
                    pendingRestore = data to repo.currentCounts()
                } catch (e: BackupException) {
                    message = e.message
                } catch (e: Exception) {
                    message = "Échec de la lecture : " + (e.message ?: "erreur inconnue")
                }
            }
        }
    }

    AppScaffold(topBar = { AppTopBar("Paramètres", onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sauvegarde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Un fichier JSON contenant les Lives, clients, commandes, emballages et jours de livraison. " +
                            "Rien n'est envoyé sur Internet : choisissez vous-même où l'enregistrer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { exportLauncher.launch("livesac-sauvegarde-" + LocalDate.now() + ".json") },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Exporter une sauvegarde") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Restaurer une sauvegarde") }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mot de passe administrateur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Demandé uniquement pour modifier le titre ou la date d'un Live. Il n'est pas inclus dans la sauvegarde.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { changePassword = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Changer le mot de passe") }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("À propos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Live Sac Manager 2.0 — fonctionne entièrement hors ligne, sans compte ni publicité.\n" +
                            "Icônes : Font Awesome Free (CC BY 4.0).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (changePassword) {
        ChangePasswordDialog(
            auth = auth,
            onDone = { changePassword = false; toast("Mot de passe modifié") },
            onDismiss = { changePassword = false }
        )
    }

    pendingRestore?.let { (data, current) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = Color.White,
            title = { Text("Remplacer toutes les données ?") },
            text = {
                Text(
                    "Les données actuelles (${current.lives} Lives, ${current.clients} clients, ${current.bags} sacs) " +
                        "seront EFFACÉES et remplacées par la sauvegarde (${data.lives.size} Lives, " +
                        "${data.clients.size} clients, ${data.bags.size} sacs).\n\nCette action est définitive."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    scope.launch {
                        try {
                            repo.restoreBackup(data)
                            message = "Sauvegarde restaurée."
                            toast("Sauvegarde restaurée")
                        } catch (e: Exception) {
                            message = "Échec de la restauration : " + (e.message ?: "erreur inconnue")
                        }
                    }
                }) { Text("Remplacer") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Annuler") } }
        )
    }
}
