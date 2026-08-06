package com.example.skillsync.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Trainer360Screen(
    trainerEmail: String,
    trainerName: String,
    onBack: () -> Unit,
    viewModel: Trainer360ViewModel = viewModel(),
) {
    LaunchedEffect(trainerEmail) { viewModel.load(trainerEmail) }
    val state by viewModel.state.collectAsState()
    StatusBarIcons(lightIcons = true)

    Scaffold(
        containerColor = MaterialTheme.skill.pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            trainerName.ifBlank { "Trainer" },
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("Trainer 360", fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.78f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { pv ->
        Box(Modifier.fillMaxSize().padding(pv)) {
            when (val s = state) {
                is Trainer360State.Loading -> Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBox(height = 120.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                    repeat(4) {
                        ShimmerBox(height = 92.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                    }
                }
                is Trainer360State.Error -> Box(
                    Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center
                ) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.skill.subText)
                }
                is Trainer360State.Success -> Trainer360Content(s.data)
            }
        }
    }
}

@Composable
private fun Trainer360Content(data: Map<String, Any>) {
    val sk = MaterialTheme.skill
    val identity = data.obj("identity")
    val util     = data.obj("utilization")
    val cap      = data.obj("capability")
    val certs    = data.obj("certifications")
    val delivery = data.obj("delivery")
    val feedback = data.obj("feedback")
    val avail    = data.obj("availability")

    val series = util?.list("series").orEmpty()
    val courses = cap?.list("courses").orEmpty()
    val assignments = delivery?.list("assignments").orEmpty()
    val held = certs?.strings("held").orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().background(sk.pageBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Identity + headline utilisation
        item {
            Appear(0) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = sk.heroBg),
                ) {
                    Column(
                        Modifier
                            .background(Brush.linearGradient(listOf(sk.heroBg, sk.heroBgAlt)))
                            .padding(16.dp)
                    ) {
                        Text(
                            identity?.str("name").orEmpty().ifBlank { "Trainer" },
                            style = MaterialTheme.typography.headlineSmall, color = sk.heroText,
                        )
                        Text(
                            identity?.str("email").orEmpty(),
                            style = MaterialTheme.typography.labelSmall, color = sk.heroMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            HeroFigure("Utilisation", "${util?.int("current") ?: 0}%", sk.teal)
                            HeroFigure("Peak", "${util?.int("peak") ?: 0}%", sk.blue)
                            HeroFigure("Courses", "${cap?.int("total_courses") ?: 0}", sk.indigo)
                            HeroFigure("Certs", "${certs?.int("count") ?: 0}", sk.amber)
                        }
                        val doj = identity?.str("date_of_joining").orEmpty()
                        val emp = identity?.str("emp_code").orEmpty()
                        if (doj.isNotBlank() || emp.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                listOfNotNull(
                                    emp.takeIf { it.isNotBlank() }?.let { "Emp $it" },
                                    doj.takeIf { it.isNotBlank() }?.let { "Joined ${it.shortDate()}" },
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall, color = sk.heroMuted,
                            )
                        }
                    }
                }
            }
        }

        // Utilisation trend
        if (series.isNotEmpty()) {
            item {
                Appear(1) {
                    SectionCard("Utilisation trend", "${series.size} months") {
                        UtilisationBars(series)
                    }
                }
            }
        }

        // Capability
        item {
            Appear(2) {
                SectionCard(
                    "Capability",
                    "${cap?.int("approved_courses") ?: 0} approved · avg Qubits ${cap?.int("avg_qubits") ?: 0}",
                ) {
                    if (courses.isEmpty()) {
                        EmptyNote("RMS returned no course capability for this trainer.")
                    } else {
                        courses.take(12).forEach { CourseRow(it) }
                        if (courses.size > 12) {
                            Text(
                                "+ ${courses.size - 12} more courses",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.subText,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // Certifications
        item {
            Appear(3) {
                SectionCard("Certifications", "${certs?.int("count") ?: 0} held") {
                    if (held.isEmpty()) EmptyNote("No vendor certifications recorded.")
                    else FlowChips(held, sk.amber)
                }
            }
        }

        // Delivery history
        item {
            Appear(4) {
                SectionCard(
                    "Delivery",
                    "${delivery?.int("total") ?: 0} assignments · ${delivery?.int("upcoming") ?: 0} upcoming",
                ) {
                    if (assignments.isEmpty()) EmptyNote("No assignments in the last 12 months.")
                    else assignments.take(10).forEach { AssignmentRow(it) }
                }
            }
        }

        // Feedback — explicitly distinguishes "clean" from "no data"
        item {
            Appear(5) {
                SectionCard("Feedback & incidents", null) {
                    val neg = feedback?.int("negative_total") ?: 0
                    val hrP = feedback?.int("hr_positive") ?: 0
                    val hrN = feedback?.int("hr_negative") ?: 0
                    val details = feedback?.list("negative_details").orEmpty()
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        HeroFigure("Negative", "$neg", if (neg > 0) sk.red else sk.green, dark = false)
                        HeroFigure("HR positive", "$hrP", sk.green, dark = false)
                        HeroFigure("HR negative", "$hrN", if (hrN > 0) sk.red else sk.subText, dark = false)
                    }
                    if (details.isEmpty() && neg == 0 && hrP == 0 && hrN == 0) {
                        Spacer(Modifier.height(8.dp))
                        EmptyNote("RMS returned no feedback records — this is an absence of data, not a clean record.")
                    }
                    details.take(5).forEach { d ->
                        Spacer(Modifier.height(8.dp))
                        Column {
                            Text(
                                d.str("feedback_question").ifBlank { "Feedback" },
                                style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                            )
                            Text(
                                listOf(d.str("client_name"), d.str("dates"), d.str("delivery_mode"))
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = sk.subText,
                            )
                        }
                    }
                }
            }
        }

        // Availability
        item {
            Appear(6) {
                SectionCard("Availability", null) {
                    val off = avail?.obj("off_dates")
                    if (off == null || off.isEmpty()) {
                        EmptyNote("RMS exposes no leave or absence endpoint, and no off-dates are recorded for this trainer.")
                    } else {
                        off.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    k.toString().replace('_', ' ').replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall, color = sk.subText,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    v.toString(),
                                    style = MaterialTheme.typography.labelSmall, color = sk.bodyText,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroFigure(label: String, value: String, tint: Color, dark: Boolean = true) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) MaterialTheme.skill.heroMuted else MaterialTheme.skill.subText,
        )
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String?, body: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.skill.bodyText)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
            }
            Spacer(Modifier.height(10.dp))
            body()
        }
    }
}

/** Utilisation history as proportional bars — a chart library would be overkill here. */
@Composable
private fun UtilisationBars(series: List<Map<*, *>>) {
    val sk = MaterialTheme.skill
    val recent = series.takeLast(12)
    val peak = (recent.maxOfOrNull { it.int("utilization") } ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        recent.forEach { m ->
            val v = m.int("utilization")
            val frac = (v.toFloat() / peak).coerceIn(0.04f, 1f)
            val tint = when {
                v > 85 -> sk.red
                v >= 60 -> sk.teal
                v >= 30 -> sk.amber
                else -> sk.subText
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text("$v", style = MaterialTheme.typography.labelSmall, color = tint, fontSize = 8.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(frac * 0.72f)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(tint)
                )
                Text(
                    m.str("month").take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                    fontSize = 8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CourseRow(c: Map<*, *>) {
    val sk = MaterialTheme.skill
    val q = c.int("qubits_score")
    val tint = when {
        q >= 85 -> sk.green
        q >= 60 -> sk.teal
        q > 0 -> sk.amber
        else -> sk.subText
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.str("course"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    c.str("vendor").takeIf { it.isNotBlank() },
                    c.str("skill_level").takeIf { it.isNotBlank() }?.let { "L$it" },
                    c.int("delivered").takeIf { it > 0 }?.let { "$it delivered" },
                    if (c.bool("future_skill")) "future skill" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (c.bool("approved")) {
            Icon(
                painterResource(R.drawable.ic_check), null,
                tint = sk.green, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "Q$q",
                style = MaterialTheme.typography.labelSmall, color = tint,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AssignmentRow(a: Map<*, *>) {
    val sk = MaterialTheme.skill
    val state = a.str("state")
    val tint = when (state) {
        "current" -> sk.teal
        "upcoming" -> sk.blue
        "completed" -> sk.subText
        else -> sk.subText
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(tint))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.str("course"),
                style = MaterialTheme.typography.bodySmall,
                color = sk.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    a.str("start_at").takeIf { it.isNotBlank() }?.shortDate(),
                    a.str("mode").takeIf { it.isNotBlank() },
                    a.intOrNull("participants")?.takeIf { it > 0 }?.let { "$it pax" },
                    a.str("location").takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = sk.subText, maxLines = 1,
            )
        }
        Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
            Text(
                state.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall, color = tint,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach {
                    Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall, color = tint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.subText)
}
