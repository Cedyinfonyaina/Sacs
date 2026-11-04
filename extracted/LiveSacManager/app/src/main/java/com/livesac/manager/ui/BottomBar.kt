package com.livesac.manager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

const val TAB_CLIENTS = "clients"
const val TAB_LIVES = "lives"
const val TAB_PACKING = "packing"

/**
 * Barre de navigation fixe à exactement trois boutons :
 * Clients (faUsers) à gauche, Lives (faVideo) au centre surélevé dans un cercle coloré,
 * Emballages (faBox) à droite.
 */
@Composable
fun BottomNavBar(selected: String, onSelect: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(RAISED_BAR_HEIGHT)) {
            // Fond de la barre (la partie haute reste transparente pour laisser dépasser le cercle).
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    .background(Color.White)
            )
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BAR_HEIGHT),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(Modifier.fillMaxWidth().height(RAISED_BAR_HEIGHT)) {
                SideItem(
                    modifier = Modifier.weight(1f),
                    icon = FaIcons.Users,
                    label = "Clients",
                    active = selected == TAB_CLIENTS,
                    activeColor = primary,
                    mutedColor = muted
                ) { onSelect(TAB_CLIENTS) }

                CenterItem(
                    modifier = Modifier.weight(1f),
                    active = selected == TAB_LIVES,
                    primary = primary,
                    muted = muted
                ) { onSelect(TAB_LIVES) }

                SideItem(
                    modifier = Modifier.weight(1f),
                    icon = FaIcons.Box,
                    label = "Emballages",
                    active = selected == TAB_PACKING,
                    activeColor = primary,
                    mutedColor = muted
                ) { onSelect(TAB_PACKING) }
            }
        }
        // Zone de la barre de navigation Android (gestes / boutons) : même fond blanc.
        Spacer(Modifier.fillMaxWidth().background(Color.White).windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private val BAR_HEIGHT = 64.dp
private val RAISED_BAR_HEIGHT = 82.dp

@Composable
private fun SideItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    mutedColor: Color,
    onClick: () -> Unit
) {
    val color = if (active) activeColor else mutedColor
    Column(
        modifier = modifier
            .height(RAISED_BAR_HEIGHT)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 18 dp de zone surélevée + 10 dp pour centrer icône et texte dans la barre.
        Spacer(Modifier.height(28.dp))
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CenterItem(modifier: Modifier, active: Boolean, primary: Color, muted: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(RAISED_BAR_HEIGHT)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(6.dp, CircleShape)
                .background(primary, CircleShape)
                .border(BorderStroke(4.dp, Color.White), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(FaIcons.Video, contentDescription = "Lives", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Lives",
            color = if (active) primary else muted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}
