package com.example.skillsync.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.str
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class CalendarViewMode {
    MONTH_CALENDAR,
    TIMELINE_QUEUE
}

/**
 * Rich Outlook / Bootstrap 5 styled Delivery Operations & Team Calendar.
 * Supports interactive monthly calendar grid with green delivery indicators,
 * date inspection, and timeline queue mode.
 */
@Composable
fun TeamCalendarScreen(
    batches: List<Map<*, *>>,
    modifier: Modifier = Modifier,
    onTrainerClick: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH_CALENDAR) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }

    val current = remember(batches) {
        batches.filter { it["engagement_state"]?.toString() == "current" }
    }
    val upcoming = remember(batches) {
        batches.filter { it["engagement_state"]?.toString() == "upcoming" }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // ── View Mode Switcher Header ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DELIVERY PULSE & SCHEDULE",
                style = MaterialTheme.typography.labelSmall,
                color = sk.cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = sk.cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    CalendarModeTab(
                        label = "Calendar",
                        selected = viewMode == CalendarViewMode.MONTH_CALENDAR,
                        onClick = { viewMode = CalendarViewMode.MONTH_CALENDAR },
                    )
                    CalendarModeTab(
                        label = "Timeline",
                        selected = viewMode == CalendarViewMode.TIMELINE_QUEUE,
                        onClick = { viewMode = CalendarViewMode.TIMELINE_QUEUE },
                    )
                }
            }
        }

        if (viewMode == CalendarViewMode.MONTH_CALENDAR) {
            // ── Month Navigation Bar ─────────────────────────────────────────
            MonthNavigationBar(
                yearMonth = currentYearMonth,
                onPrev = { currentYearMonth = currentYearMonth.minusMonths(1) },
                onNext = { currentYearMonth = currentYearMonth.plusMonths(1) },
                onToday = {
                    currentYearMonth = YearMonth.now()
                    selectedDate = LocalDate.now()
                },
            )

            // ── Outlook / Bootstrap 5 Month Grid ────────────────────────────
            OutlookMonthGrid(
                yearMonth = currentYearMonth,
                batches = batches,
                selectedDate = selectedDate,
                onDateSelected = { selectedDate = it },
            )

            // ── Selected Date Details Inspector ─────────────────────────────
            selectedDate?.let { date ->
                SelectedDateInspector(
                    date = date,
                    batches = batches,
                    onTrainerClick = onTrainerClick,
                )
            }
        } else {
            // ── Timeline / Queue View ───────────────────────────────────────
            if (current.isNotEmpty()) {
                Text(
                    "CURRENTLY DELIVERING (${current.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.good,
                    fontWeight = FontWeight.Bold,
                )
                current.forEach { DeliveryCard(it, sk.good, onTrainerClick) }
            }

            if (upcoming.isNotEmpty()) {
                Text(
                    "LINED UP (${upcoming.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.sky,
                    fontWeight = FontWeight.Bold,
                )
                upcoming.forEach { DeliveryCard(it, sk.sky, onTrainerClick) }
            }

            if (current.isEmpty() && upcoming.isEmpty()) {
                Text(
                    "No batches assigned to the team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sk.subText,
                )
            }
        }
    }
}

@Composable
private fun CalendarModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else sk.subText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MonthNavigationBar(
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val monthTitle = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = sk.bodyText,
            )
            Spacer(Modifier.width(Space.sm))
            Surface(
                onClick = onToday,
                shape = RoundedCornerShape(6.dp),
                color = sk.cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
            ) {
                Text(
                    "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.cyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = "Previous Month",
                    tint = sk.bodyText,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = "Next Month",
                    tint = sk.bodyText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun OutlookMonthGrid(
    yearMonth: YearMonth,
    batches: List<Map<*, *>>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    val sk = MaterialTheme.skill
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    
    // Day of week offset (Mon = 1 ... Sun = 7)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 (Mon) to 7 (Sun)
    val leadingEmptyDays = firstDayOfWeek - 1

    val daysOfWeek = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Day of Week Header (Mon-Sun)
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) sk.subText.copy(alpha = 0.6f) else sk.labelText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }

        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

        // Day cells in rows of 7
        val totalCells = leadingEmptyDays + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - leadingEmptyDays + 1
                    
                    if (dayNum in 1..daysInMonth) {
                        val cellDate = yearMonth.atDay(dayNum)
                        val isSelected = selectedDate == cellDate
                        val isToday = cellDate == LocalDate.now()

                        // Calculate delivering batches on this day
                        val deliveringOnDay = batches.filter { b ->
                            val s = b.str("start_date").take(10)
                            val e = b.str("end_date").take(10).ifBlank { s }
                            if (s.isNotBlank()) {
                                try {
                                    val st = LocalDate.parse(s)
                                    val en = if (e.isNotBlank()) LocalDate.parse(e) else st
                                    !cellDate.isBefore(st) && !cellDate.isAfter(en)
                                } catch (_: Exception) { false }
                            } else false
                        }

                        OutlookDayCell(
                            dayNum = dayNum,
                            date = cellDate,
                            isToday = isToday,
                            isSelected = isSelected,
                            deliveryCount = deliveringOnDay.size,
                            modifier = Modifier.weight(1f),
                            onClick = { onDateSelected(cellDate) },
                        )
                    } else {
                        // Empty slot
                        Box(modifier = Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlookDayCell(
    dayNum: Int,
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    deliveryCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val isDelivering = deliveryCount > 0
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        isDelivering -> sk.good.copy(alpha = 0.12f)
        isToday -> sk.cyan.copy(alpha = 0.12f)
        isWeekend -> Color.Black.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> sk.cyan
        isDelivering -> sk.good.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$dayNum",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isSelected -> Color.White
                    isToday -> sk.cyan
                    isDelivering -> sk.good
                    isWeekend -> sk.subText.copy(alpha = 0.6f)
                    else -> sk.bodyText
                },
                fontWeight = if (isToday || isSelected || isDelivering) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
            )

            if (isDelivering) {
                // Bright Green Active Delivery Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(sk.good)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = if (deliveryCount == 1) "1 live" else "$deliveryCount",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SelectedDateInspector(
    date: LocalDate,
    batches: List<Map<*, *>>,
    onTrainerClick: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    val formattedDate = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH))

    val deliveries = remember(date, batches) {
        batches.filter { b ->
            val s = b.str("start_date").take(10)
            val e = b.str("end_date").take(10).ifBlank { s }
            if (s.isNotBlank()) {
                try {
                    val st = LocalDate.parse(s)
                    val en = if (e.isNotBlank()) LocalDate.parse(e) else st
                    !date.isBefore(st) && !date.isAfter(en)
                } catch (_: Exception) { false }
            } else false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formattedDate,
                style = MaterialTheme.typography.titleSmall,
                color = sk.cyan,
                fontWeight = FontWeight.Bold,
            )
            if (deliveries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(sk.good.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${deliveries.size} DELIVERING",
                        color = sk.good,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (deliveries.isEmpty()) {
            Text(
                "No team deliveries scheduled on this date.",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            deliveries.forEach { batch ->
                DayDeliveryRow(batch, onTrainerClick)
            }
        }
    }
}

@Composable
private fun DayDeliveryRow(
    batch: Map<*, *>,
    onTrainerClick: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    val course = batch.str("course_name").ifBlank { batch.str("demand_id").ifBlank { "Course" } }
    val trainer = batch.str("trainer_name")
    val email = batch.str("trainer_email")
    val mode = batch.str("delivery_mode")
    val customer = batch.str("customer")
    val location = batch.str("location")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(sk.cardBg.copy(alpha = 0.6f))
            .clickable(enabled = email.isNotBlank() || trainer.isNotBlank()) {
                onTrainerClick(email, trainer)
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Green active dot
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sk.good))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                course,
                style = MaterialTheme.typography.bodyMedium,
                color = sk.bodyText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trainer.isNotBlank()) {
                    Text(
                        trainer,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.cyan,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (customer.isNotBlank()) {
                    Text("·", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Text(customer, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
                if (location.isNotBlank()) {
                    Text("·", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Text(location, style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
            }
        }

        if (mode.isNotBlank()) {
            ModeBadge(mode, sk.good)
        }
    }
}

@Composable
private fun DeliveryCard(
    batch: Map<*, *>,
    tint: Color,
    onTrainerClick: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    val course = batch.str("course_name").ifBlank { batch.str("demand_id").ifBlank { "Unknown course" } }
    val trainer = batch.str("trainer_name")
    val email = batch.str("trainer_email")
    val start = batch.str("start_date")
    val end = batch.str("end_date")
    val mode = batch.str("delivery_mode")
    val customer = batch.str("customer")
    val pax = batch.intOrNull("participants")
    val location = batch.str("location")
    val days = batch.intOrNull("days")
    val recStatus = batch.str("recording_status")

    val dateText = when {
        start.isNotBlank() && end.isNotBlank() -> "$start – $end"
        start.isNotBlank() -> "From $start"
        else -> "Dates pending"
    }

    val recTint: Color? = when {
        recStatus.equals("uploaded", ignoreCase = true) || recStatus.equals("compliant", ignoreCase = true) -> sk.good
        recStatus.equals("overdue", ignoreCase = true) || recStatus.equals("missing", ignoreCase = true) -> sk.crit
        recStatus.isNotBlank() && !recStatus.equals("N/A", ignoreCase = true) && !recStatus.equals("na", ignoreCase = true) -> sk.warn
        else -> null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .clickable(enabled = email.isNotBlank() || trainer.isNotBlank()) {
                onTrainerClick(email, trainer)
            },
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.25f)))),
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                course,
                style = MaterialTheme.typography.titleSmall,
                color = sk.bodyText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val hasMetaRow = mode.isNotBlank() || customer.isNotBlank() || pax != null
            if (hasMetaRow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode.isNotBlank()) ModeBadge(mode, tint)
                    if (customer.isNotBlank()) {
                        Text(
                            customer,
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (pax != null) {
                        Text(
                            "$pax pax",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.subText,
                        )
                    }
                }
            }

            val trainerLocation = listOfNotNull(
                trainer.takeIf { it.isNotBlank() },
                location.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (trainerLocation.isNotBlank()) {
                Text(
                    trainerLocation,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.labelText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(dateText, style = MaterialTheme.typography.labelSmall, color = tint)
                if (days != null) {
                    Text("${days}d", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                }
                if (recTint != null) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(recTint.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "REC ${recStatus.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = recTint,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: String, tint: Color) {
    val label = when (mode.uppercase().trim()) {
        "INSTRUCTOR LED TRAINING", "ILT" -> "ILT"
        "INSTRUCTOR LED ONLINE", "ILO" -> "ILO"
        "FACE TO FACE", "FMAT" -> "FMAT"
        "VIRTUAL", "VIL" -> "VIL"
        else -> mode.take(4).uppercase()
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}
