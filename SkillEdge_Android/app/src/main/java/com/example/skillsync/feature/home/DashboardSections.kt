package com.example.skillsync.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.skillsync.R
import com.example.skillsync.theme.skill
import kotlin.math.roundToInt

// ── Profile and Session Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuBottomSheet(
    email: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit,
    onMySchedule: () -> Unit = {},
) {
    val sk = MaterialTheme.skill
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sk.cardBg,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Session Information", style = MaterialTheme.typography.titleMedium, color = sk.bodyText)
            Spacer(Modifier.height(8.dp))
            Text("Logged in as: $email", color = sk.subText)
            Text("Secure manager session", color = sk.subText)
            Text("Background sync: Automatic", color = sk.subText)
            
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = sk.frost.copy(alpha = 0.12f))
            Spacer(Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("View My Profile", color = sk.bodyText) },
                leadingContent = { Icon(painterResource(R.drawable.ic_people), null, tint = sk.bodyText) },
                modifier = Modifier.clickable { onViewProfile() },
                colors = ListItemDefaults.colors(containerColor = sk.cardBg)
            )
            ListItem(
                headlineContent = { Text("My schedule & leave bands", color = sk.bodyText) },
                supportingContent = { Text("Your own delivery — you deliver too", color = sk.subText) },
                leadingContent = { Icon(painterResource(R.drawable.ic_calendar), null, tint = sk.bodyText) },
                modifier = Modifier.clickable { onMySchedule() },
                colors = ListItemDefaults.colors(containerColor = sk.cardBg)
            )
            ListItem(
                headlineContent = { Text("Logout", color = sk.crit) },
                leadingContent = { Icon(painterResource(R.drawable.ic_alert), null, tint = sk.crit) },
                modifier = Modifier.clickable { onLogout() },
                colors = ListItemDefaults.colors(containerColor = sk.cardBg)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Utilization trend projection utilities ─────────────────────────────────────

data class UtilProjection(val projected: Int, val direction: String)

/**
 * Shared by the single-trainer utilisation section (Trainer360Screen).
 * Null when fewer than 3 months are on record — a slope from less
 * than that is noise, not signal.
 */
internal fun projectNextUtilization(values: List<Int>): UtilProjection? {
    if (values.size < 3) return null
    val slope = averageMonthOverMonthDelta(values)
    val projected = (values.last() + slope).roundToInt().coerceIn(0, 100)
    val direction = when {
        slope > 3f -> "Rising"
        slope < -3f -> "Falling"
        else -> "Flat"
    }
    return UtilProjection(projected, direction)
}

private fun averageMonthOverMonthDelta(values: List<Int>): Float {
    if (values.size < 2) return 0f
    var sum = 0
    for (i in 1 until values.size) sum += values[i] - values[i - 1]
    return sum.toFloat() / (values.size - 1)
}
