package com.example.skillsync.ui.batch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.theme.SkillCard
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStaffingSheet(
    courseName: String,
    onDismiss: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }
    var networkData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedType by remember { mutableStateOf("All") }

    LaunchedEffect(courseName) {
        loading = true
        try {
            val res = RetrofitClient.instance.getNetworkTrainers(course = courseName)
            networkData = res
        } catch (_: Exception) {
            networkData = null
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Wider Trainer Network",
                        style = MaterialTheme.typography.titleMedium,
                        color = sk.bodyText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "In-house & freelance staffing for $courseName",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", color = sk.subText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "In-House", "Freelance").forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val lookupAvailable = networkData?.bool("available") == true
                val rawTrainers = networkData?.list("trainers") ?: emptyList()
                val trainers = rawTrainers.filter { t ->
                    if (selectedType == "All") true
                    else t.str("trainer_type").contains(selectedType, ignoreCase = true)
                }

                if (!lookupAvailable) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Wider network search unavailable", style = MaterialTheme.typography.titleSmall, color = sk.warn)
                            Text(
                                networkData?.str("note").orEmpty().ifBlank { "RMS could not verify the wider trainer network for this course." },
                                style = MaterialTheme.typography.bodySmall,
                                color = sk.subText,
                            )
                        }
                    }
                } else if (trainers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (rawTrainers.isEmpty()) "The verified wider-network search returned no matching trainers."
                            else "No trainers match the '$selectedType' filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sk.subText,
                        )
                    }
                } else {
                    Text(
                        "${trainers.size} trainer${if (trainers.size == 1) "" else "s"} matched across the Koenig network; verify dates before allocation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(trainers) { trainer ->
                            val name = trainer.str("name").ifBlank { "Trainer" }
                            val email = trainer.str("email")
                            val type = trainer.str("trainer_type").ifBlank { "In-House" }
                            val location = trainer.str("location")
                            val phone = trainer.str("phone")

                            SkillCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(name, style = MaterialTheme.typography.titleSmall, color = sk.bodyText, fontWeight = FontWeight.Bold)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (type.contains("In-House", ignoreCase = true)) sk.good.copy(alpha = 0.14f) else sk.sky.copy(alpha = 0.14f),
                                        ) {
                                            Text(
                                                type,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (type.contains("In-House", ignoreCase = true)) sk.good else sk.sky,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            )
                                        }
                                    }

                                    if (location.isNotBlank()) {
                                        Text("Base Location: $location", style = MaterialTheme.typography.bodySmall, color = sk.subText)
                                    }

                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (email.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                            data = Uri.parse("mailto:$email")
                                                            putExtra(Intent.EXTRA_SUBJECT, "Staffing Opportunity: $courseName")
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = sk.indigo),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Text("Email ✉", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }

                                        if (phone.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = sk.cyan),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Text("Call 📞", style = MaterialTheme.typography.labelSmall, color = Color.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
