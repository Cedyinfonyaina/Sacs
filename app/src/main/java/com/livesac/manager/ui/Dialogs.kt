@file:OptIn(ExperimentalMaterial3Api::class)

package com.livesac.manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.livesac.manager.domain.AdminAuth
import com.livesac.manager.domain.DeliveryDay
import com.livesac.manager.domain.LiveInfo
import com.livesac.manager.domain.PackageGroup
import com.livesac.manager.domain.buildRecap
import com.livesac.manager.domain.formatAr
import com.livesac.manager.domain.formatDateLong
import com.livesac.manager.domain.liveLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Mot de passe administrateur
// ---------------------------------------------------------------------------------------------

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Première utilisation : choix obligatoire du mot de passe administrateur. */
@Composable
fun PasswordSetupDialog(auth: AdminAuth, onDone: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tooShort = first.length < AdminAuth.MIN_LENGTH
    val mismatch = second.isNotEmpty() && first != second

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = Color.White,
        title = { Text("Mot de passe administrateur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choisissez un mot de passe. Il sera demandé uniquement pour modifier le titre ou la date d'un Live. " +
                        "Notez-le bien : il ne peut pas être récupéré.",
                    style = MaterialTheme.typography.bodyMedium
                )
                PasswordField(first, { first = it }, "Mot de passe (${AdminAuth.MIN_LENGTH} caractères minimum)")
                PasswordField(second, { second = it }, "Confirmer le mot de passe")
                if (mismatch) {
                    Text("Les deux mots de passe sont différents.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && !tooShort && first == second,
                onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { auth.setPassword(first) }
                        onDone()
                    }
                }
            ) { Text("Enregistrer") }
        }
    )
}

/** Demande le mot de passe avant une modification protégée. */
@Composable
fun PasswordPromptDialog(auth: AdminAuth, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Mot de passe administrateur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("La modification du titre ou de la date d'un Live est protégée.")
                PasswordField(password, { password = it; wrong = false }, "Mot de passe")
                if (wrong) Text("Mot de passe incorrect.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { auth.verify(password) }
                        busy = false
                        if (ok) onSuccess() else wrong = true
                    }
                }
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Changement du mot de passe (ancien mot de passe obligatoire). */
@Composable
fun ChangePasswordDialog(auth: AdminAuth, onDone: () -> Unit, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val valid = old.isNotEmpty() && first.length >= AdminAuth.MIN_LENGTH && first == second

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Changer le mot de passe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(old, { old = it; wrong = false }, "Mot de passe actuel")
                PasswordField(first, { first = it }, "Nouveau mot de passe (${AdminAuth.MIN_LENGTH} caractères minimum)")
                PasswordField(second, { second = it }, "Confirmer le nouveau mot de passe")
                if (wrong) Text("Mot de passe actuel incorrect.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { auth.change(old, first) }
                        busy = false
                        if (ok) onDone() else wrong = true
                    }
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Date
// ---------------------------------------------------------------------------------------------

/** Bouton « Date : … » qui ouvre le sélecteur de date. */
@Composable
fun DateField(dateMillis: Long, onChange: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Date : " + formatDateLong(dateMillis)) }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onChange)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
    }
}

/** Modification du titre et de la date d'un Live (à ouvrir seulement après le mot de passe). */
@Composable
fun LiveEditDialog(
    initialTitle: String,
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var dateMillis by remember { mutableStateOf(initialDateMillis) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Modifier le Live") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(dateMillis) { dateMillis = it }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.trim(), dateMillis) }, enabled = title.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Sacs
// ---------------------------------------------------------------------------------------------

/**
 * Ajout / modification d'un sac. Si `liveOptions` contient plusieurs Lives (samedi + dimanche),
 * on choisit le Live d'origine du sac.
 */
@Composable
fun BagDialog(
    title: String,
    initialCode: String,
    initialPrice: String,
    onDismiss: () -> Unit,
    onConfirm: (code: String, price: Long, liveId: Long?) -> Unit,
    liveOptions: List<LiveInfo> = emptyList(),
    initialLiveId: Long? = null
) {
    var code by remember { mutableStateOf(initialCode) }
    var price by remember { mutableStateOf(initialPrice) }
    var liveId by remember { mutableStateOf(initialLiveId ?: liveOptions.lastOrNull()?.id) }
    val amount = price.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (liveOptions.size > 1) {
                    Text("Live d'origine", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        liveOptions.forEach { live ->
                            FilterChip(
                                selected = liveId == live.id,
                                onClick = { liveId = live.id },
                                label = { Text(liveLabel(live.kind, live.dateMillis)) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code JP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { typed -> price = typed.filter { c -> c in '0'..'9' }.take(9) },
                    label = { Text("Prix du sac") },
                    suffix = { Text("Ar") },
                    supportingText = { if (amount > 0) Text(formatAr(amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code.trim(), amount, liveId) },
                enabled = code.isNotBlank() && amount > 0
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Avertissement « code JP déjà utilisé dans ce Live » : l'utilisateur peut enregistrer quand même. */
@Composable
fun ConflictDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Code JP déjà utilisé") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("Enregistrer quand même") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Modifier") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Livraison
// ---------------------------------------------------------------------------------------------

/** Pastille « Livraison : Mer » qui ouvre un menu (Non défini / Mercredi / Samedi / Dimanche). */
@Composable
fun DeliveryChip(day: DeliveryDay?, onChange: (DeliveryDay?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = if (day == null) Color.White else MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.heightIn(min = 40.dp)
        ) {
            Text(
                text = "Livraison : " + (day?.short ?: "—"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Non défini") }, onClick = { open = false; onChange(null) })
            DeliveryDay.values().forEach { d ->
                DropdownMenuItem(text = { Text(d.label) }, onClick = { open = false; onChange(d) })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Récapitulatif client
// ---------------------------------------------------------------------------------------------

/** Texte prêt à copier ou à envoyer (Messenger, SMS…) : généré localement, sans Internet. */
@Composable
fun RecapDialog(group: PackageGroup, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(group) { buildRecap(group) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Récapitulatif client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copyText(context, text) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Copier") }
                    Button(
                        onClick = { shareText(context, text) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("  Partager")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Récapitulatif", text))
    Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Partager le récapitulatif"))
}
