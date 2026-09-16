package com.example.skillsync.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.skillsync.core.ui.Avatar
import com.example.skillsync.core.ui.intOrNull
import com.example.skillsync.core.ui.str
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.material3.Text

enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
    TIMELINE("Timeline")
}

/**
 * `batch_engagement_df` (backend.py `_build_trainer`, the `batch_rows` built
 * from RMS `prevUpcoming`/`assignment`) carries course name, mode, vendor and
 * dates only — no `activity_type`/category field. Every row IS an RMS batch
 * by definition, so DELIVERY is labelled "Delivery / Batch" rather than
 * inventing a second, always-zero "Batches" count that the source data has
 * no way to distinguish from a delivery. MOCK/WEBINAR/UPSKILLING/MEETING are
 * reclassified from that same table by keyword match on course name/remarks
 * (RMS exposes no dedicated field for them either) — LEAVE is the one
 * exception with a genuinely distinct source: the reportee's `next_leave`
 * date list, separate from `batch_engagement_df` entirely.
 */
enum class EventCategory(
    val label: String,
    val icon: String,
    val color: Color,
    val lightBg: Color,
) {
    DELIVERY("Delivery / Batch", "◆", Color(0xFF38BDF8), Color(0x3338BDF8)),
    MOCK("Mock", "◎", Color(0xFF818CF8), Color(0x33818CF8)),
    WEBINAR("Webinar", "▲", Color(0xFFF472B6), Color(0x33F472B6)),
    LEAVE("Leave", "■", Color(0xFFFBBF24), Color(0x33FBBF24)),
    UPSKILLING("Upskilling", "★", Color(0xFF34D399), Color(0x3334D399)),
    MEETING("Meeting", "●", Color(0xFF22D3EE), Color(0x3322D3EE))
}

data class CalendarEventItem(
    val id: String,
    val title: String,
    val category: EventCategory,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timeSlot: String,
    val trainerName: String,
    val trainerEmail: String,
    val customer: String,
    val location: String,
    val deliveryMode: String,
    val pax: Int?,
    val rawBatch: Map<*, *>?,
)

/**
 * Designer-grade Delivery Operations & Scheduling Calendar.
 * Supports Month, Week, Day, and Timeline views with multi-day spanning
 * event banners, color-coded categories, and rich day inspection.
 */
private fun parseFlexibleDate(raw: String): LocalDate? {
    if (raw.isBlank()) return null
    val str = raw.trim()
    val datePart = if (str.contains("T")) str.substringBefore("T") else str
    val clean10 = if (datePart.length >= 10 && datePart[4] == '-' && datePart[7] == '-') datePart.take(10) else datePart

    // 1. ISO YYYY-MM-DD
    try { return LocalDate.parse(clean10) } catch (_: Exception) {}

    // 2. dd-MMM-yyyy e.g. 24-Aug-2026 or 24-AUG-2026
    try {
        val dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
        return LocalDate.parse(str, dtf)
    } catch (_: Exception) {}

    // 3. dd/MM/yyyy
    try {
        val dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
        return LocalDate.parse(str, dtf)
    } catch (_: Exception) {}

    // 4. yyyy/MM/dd
    try {
        val dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH)
        return LocalDate.parse(str, dtf)
    } catch (_: Exception) {}

    return null
}

/**
 * Parses allocated batches and trainer leaves into structured [CalendarEventItem]s.
 * Pulled out to file scope so the summary strip in [DeliveryOperationsWorkspace]
 * can compute the same real counts the calendar renders, instead of a second,
 * possibly-diverging tally.
 */
fun buildCalendarEvents(
    batches: List<Map<*, *>>,
    readiness: Map<String, Map<String, Any>>,
): List<CalendarEventItem> {
    val list = mutableListOf<CalendarEventItem>()

    // 1. Ingest confirmed team delivery batches
    batches.forEach { b ->
        val course = b.str("course_name").ifBlank { b.str("Course").ifBlank { b.str("demand_id").ifBlank { "Delivery" } } }
        val startRaw = b.str("start_at").ifBlank { b.str("start_date").ifBlank { b.str("StartDate").ifBlank { b.str("StarDate") } } }
        val endRaw = b.str("end_at").ifBlank { b.str("end_date").ifBlank { b.str("EndDate") } }

        val start = parseFlexibleDate(startRaw)
        if (start != null) {
            val endParsed = parseFlexibleDate(endRaw) ?: start
            val actualEnd = if (endParsed.isBefore(start)) start else endParsed
            val mode = b.str("delivery_mode").ifBlank { b.str("Mode") }
            val remarks = b.str("remarks").lowercase()
            val courseLower = course.lowercase()

            val cat = when {
                courseLower.contains("mock") || remarks.contains("mock") || mode.equals("Mock", ignoreCase = true) -> EventCategory.MOCK
                courseLower.contains("webinar") || remarks.contains("webinar") || mode.equals("Webinar", ignoreCase = true) -> EventCategory.WEBINAR
                courseLower.contains("leave") || remarks.contains("leave") || mode.equals("Leave", ignoreCase = true) -> EventCategory.LEAVE
                courseLower.contains("idp") || courseLower.contains("upskill") || remarks.contains("upskill") -> EventCategory.UPSKILLING
                courseLower.contains("meet") || remarks.contains("meeting") -> EventCategory.MEETING
                else -> EventCategory.DELIVERY
            }

            list.add(
                CalendarEventItem(
                    id = b.str("assignment_id").ifBlank { b.str("demand_id").ifBlank { "${course}_${start}" } },
                    title = course,
                    category = cat,
                    startDate = start,
                    endDate = actualEnd,
                    timeSlot = b.str("session_time").ifBlank {
                        val st = b.str("start_time")
                        val et = b.str("end_time")
                        if (st.isNotBlank() && et.isNotBlank()) "$st - $et" else "09:00 - 17:00"
                    },
                    trainerName = b.str("trainer_name").ifBlank { b.str("TrainerName") },
                    trainerEmail = b.str("trainer_email").ifBlank { b.str("official_email") },
                    customer = b.str("vendor").ifBlank { b.str("customer") },
                    location = b.str("location"),
                    deliveryMode = mode,
                    pax = b.intOrNull("participants"),
                    rawBatch = b,
                )
            )
        }
    }

    // 2. Ingest trainer leaves from readiness
    readiness.forEach { (_, r) ->
        val trainerName = r.str("trainer_name").ifBlank { "Trainer" }
        val trainerEmail = r.str("trainer_email")
        val leaves = (r["next_leave"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        leaves.forEach { leaveStr ->
            parseFlexibleDate(leaveStr)?.let { leaveDate ->
                list.add(
                    CalendarEventItem(
                        id = "leave_${trainerEmail}_$leaveDate",
                        title = "Leave: $trainerName",
                        category = EventCategory.LEAVE,
                        startDate = leaveDate,
                        endDate = leaveDate,
                        timeSlot = "Full Day (Approved Leave)",
                        trainerName = trainerName,
                        trainerEmail = trainerEmail,
                        customer = "Approved Absence",
                        location = "Out of Office",
                        deliveryMode = "Leave",
                        pax = null,
                        rawBatch = r,
                    )
                )
            }
        }
    }

    // No synthetic fallback: an empty calendar means nothing is scheduled,
    // and the day panel already says so. Never show sample events.

    return list.sortedBy { it.startDate }
}

/** First name plus last initial ("Abhinav Kumar" -> "Abhinav K.") so a month
 * cell can name the deliverer without the full string blowing out the cell. */
fun shortTrainerName(fullName: String): String {
    val parts = fullName.trim().split(" ").filter { it.isNotBlank() }
    return when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> "${parts.first()} ${parts.last().first()}."
    }
}

/** "PL-300T00: Design and Manage Analytics Solutions" -> "PL-300T00" so a
 * compact calendar cell can show the course code, not a truncated sentence.
 * The full title stays available on the detail sheet/agenda card. */
fun shortCourseTitle(title: String): String {
    val t = title.trim()
    val code = t.substringBefore(":").trim()
    return if (code.isNotBlank() && code.length in 1..16) code else t.take(16)
}

@Composable
fun TeamCalendarScreen(
    batches: List<Map<*, *>> = emptyList(),
    readiness: Map<String, Map<String, Any>> = emptyMap(),
    modifier: Modifier = Modifier,
    yearMonth: YearMonth,
    onYearMonthChange: (YearMonth) -> Unit,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onTrainerClick: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    val currentYearMonth = yearMonth
    var selectedCategoryFilter by remember { mutableStateOf<EventCategory?>(null) }
    var inspectedEvent by remember { mutableStateOf<CalendarEventItem?>(null) }

    val allEvents = remember(batches, readiness) { buildCalendarEvents(batches, readiness) }

    // Filtered events
    val filteredEvents = remember(allEvents, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) allEvents
        else allEvents.filter { it.category == selectedCategoryFilter }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // ── Top Control Bar (Month / Week / Day Switcher + Navigation) ─────────
        CalendarTopHeader(
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            yearMonth = currentYearMonth,
            selectedDate = selectedDate,
            onPrev = {
                when (viewMode) {
                    CalendarViewMode.MONTH -> onYearMonthChange(currentYearMonth.minusMonths(1))
                    CalendarViewMode.WEEK -> {
                        val d = selectedDate.minusWeeks(1)
                        onSelectedDateChange(d)
                        onYearMonthChange(YearMonth.from(d))
                    }
                    CalendarViewMode.DAY, CalendarViewMode.TIMELINE -> {
                        val d = selectedDate.minusDays(1)
                        onSelectedDateChange(d)
                        onYearMonthChange(YearMonth.from(d))
                    }
                }
            },
            onNext = {
                when (viewMode) {
                    CalendarViewMode.MONTH -> onYearMonthChange(currentYearMonth.plusMonths(1))
                    CalendarViewMode.WEEK -> {
                        val d = selectedDate.plusWeeks(1)
                        onSelectedDateChange(d)
                        onYearMonthChange(YearMonth.from(d))
                    }
                    CalendarViewMode.DAY, CalendarViewMode.TIMELINE -> {
                        val d = selectedDate.plusDays(1)
                        onSelectedDateChange(d)
                        onYearMonthChange(YearMonth.from(d))
                    }
                }
            },
            onToday = {
                val now = LocalDate.now()
                onSelectedDateChange(now)
                onYearMonthChange(YearMonth.now())
            },
        )

        // ── Event Category Filter Pills ──────────────────────────────────────
        EventCategoryFilterBar(
            selectedCategory = selectedCategoryFilter,
            onSelectCategory = { selectedCategoryFilter = it },
            allCount = allEvents.size,
            deliveryCount = allEvents.count { it.category == EventCategory.DELIVERY },
            mockCount = allEvents.count { it.category == EventCategory.MOCK },
            webinarCount = allEvents.count { it.category == EventCategory.WEBINAR },
            leaveCount = allEvents.count { it.category == EventCategory.LEAVE },
        )

        // ── Active View Rendering ────────────────────────────────────────────
        // Month/Week/Day switch is a discrete-state change — crossfade rather
        // than snap, so the segmented selection above reads as driving this
        // content instead of two unrelated UI updates.
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
            },
            label = "calendar-view-mode",
        ) { mode ->
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        when (mode) {
            CalendarViewMode.MONTH -> {
                SpanningMonthCalendarGrid(
                    yearMonth = currentYearMonth,
                    events = filteredEvents,
                    selectedDate = selectedDate,
                    onDateSelected = { onSelectedDateChange(it) },
                    onEventClick = { inspectedEvent = it },
                )

                // Day Inspection summary below grid
                SelectedDayInspectionCard(
                    date = selectedDate,
                    eventsOnDay = filteredEvents.filter { !selectedDate.isBefore(it.startDate) && !selectedDate.isAfter(it.endDate) },
                    onTrainerClick = onTrainerClick,
                    onEventClick = { inspectedEvent = it },
                )
            }

            CalendarViewMode.WEEK -> {
                WeekScheduleView(
                    selectedDate = selectedDate,
                    events = filteredEvents,
                    onDateSelected = { onSelectedDateChange(it) },
                    onEventClick = { inspectedEvent = it },
                )
                SelectedDayInspectionCard(
                    date = selectedDate,
                    eventsOnDay = filteredEvents.filter { !selectedDate.isBefore(it.startDate) && !selectedDate.isAfter(it.endDate) },
                    onTrainerClick = onTrainerClick,
                    onEventClick = { inspectedEvent = it },
                )
            }

            CalendarViewMode.DAY -> {
                DayScheduleView(
                    date = selectedDate,
                    events = filteredEvents.filter { !selectedDate.isBefore(it.startDate) && !selectedDate.isAfter(it.endDate) },
                    onTrainerClick = onTrainerClick,
                    onEventClick = { inspectedEvent = it },
                )
            }

            CalendarViewMode.TIMELINE -> {
                TimelineQueueView(
                    events = filteredEvents,
                    onTrainerClick = onTrainerClick,
                    onEventClick = { inspectedEvent = it },
                )
            }
        }
        }
        }

        // Event Inspection Bottom Sheet / Dialog
        inspectedEvent?.let { ev ->
            EventDetailSheet(
                event = ev,
                onDismiss = { inspectedEvent = null },
                onTrainerClick = onTrainerClick,
            )
        }
    }
}

// ── Top Header Bar ──────────────────────────────────────────────────────────

@Composable
private fun CalendarTopHeader(
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val headerTitle = when (viewMode) {
        CalendarViewMode.MONTH -> yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        CalendarViewMode.WEEK -> {
            val weekStart = selectedDate.with(DayOfWeek.SUNDAY)
            val weekEnd = selectedDate.with(DayOfWeek.SATURDAY)
            if (weekStart.month == weekEnd.month) {
                "${weekStart.format(DateTimeFormatter.ofPattern("d"))}–${weekEnd.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))}"
            } else {
                "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – ${weekEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"
            }
        }
        CalendarViewMode.DAY, CalendarViewMode.TIMELINE -> {
            selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", Locale.ENGLISH))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Segmented View Mode Tabs (Month | Week | Day) — canonical
            // SegmentedSelector, not a screen-local reimplementation. See
            // AI/DECISIONS.md, Design V3 Phase 1 component consolidation.
            com.example.skillsync.theme.SegmentedSelector(
                options = CalendarViewMode.values().take(3).map { it.name to it.label },
                selected = viewMode.name,
                onSelect = { key -> onViewModeChange(CalendarViewMode.valueOf(key)) },
                modifier = Modifier.width(200.dp),
            )

            // Month / Range Title
            Text(
                headerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = sk.bodyText,
            )

            // Navigation Controls (< > Today)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = onToday,
                    shape = RoundedCornerShape(6.dp),
                    color = sk.brand.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sk.brand.copy(alpha = 0.45f)),
                ) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.sky,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Bold,
                                            )
                }

                IconButton(onClick = onPrev, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron),
                        contentDescription = "Previous",
                        tint = sk.bodyText,
                        modifier = Modifier.size(14.dp),
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron),
                        contentDescription = "Next",
                        tint = sk.bodyText,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ── Category Filter Bar ─────────────────────────────────────────────────────

@Composable
private fun EventCategoryFilterBar(
    selectedCategory: EventCategory?,
    onSelectCategory: (EventCategory?) -> Unit,
    allCount: Int,
    deliveryCount: Int,
    mockCount: Int,
    webinarCount: Int,
    leaveCount: Int,
) {
    val sk = MaterialTheme.skill

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterPill(
            label = "All Events ($allCount)",
            selected = selectedCategory == null,
            tint = sk.cyan,
            onClick = { onSelectCategory(null) },
        )
        FilterPill(
            label = "${EventCategory.DELIVERY.label} ($deliveryCount)",
            selected = selectedCategory == EventCategory.DELIVERY,
            tint = EventCategory.DELIVERY.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.DELIVERY) null else EventCategory.DELIVERY) },
        )
        FilterPill(
            label = "Mocks ($mockCount)",
            selected = selectedCategory == EventCategory.MOCK,
            tint = EventCategory.MOCK.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.MOCK) null else EventCategory.MOCK) },
        )
        FilterPill(
            label = "Webinars ($webinarCount)",
            selected = selectedCategory == EventCategory.WEBINAR,
            tint = EventCategory.WEBINAR.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.WEBINAR) null else EventCategory.WEBINAR) },
        )
        FilterPill(
            label = "Leaves ($leaveCount)",
            selected = selectedCategory == EventCategory.LEAVE,
            tint = EventCategory.LEAVE.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.LEAVE) null else EventCategory.LEAVE) },
        )
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) tint.copy(alpha = 0.22f) else sk.cardBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) tint else sk.cardBorder,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) tint else sk.subText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
    }
}

// ── Multi-Day Spanning Month Calendar Grid ──────────────────────────────────

@Composable
private fun SpanningMonthCalendarGrid(
    yearMonth: YearMonth,
    events: List<CalendarEventItem>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill
    val firstOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()

    // Week starts on Sunday (value 7 in Java Time DayOfWeek)
    // Sunday = 0, Monday = 1, ... Saturday = 6
    val leadingDays = firstOfMonth.dayOfWeek.value % 7

    val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    val totalSlots = leadingDays + daysInMonth
    val totalWeeks = (totalSlots + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Day of Week Headers (Sun - Sat) ──────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEachIndexed { idx, name ->
                val isWeekend = idx == 0 || idx == 6
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWeekend) sk.subText.copy(alpha = 0.5f) else sk.labelText,
                    fontWeight = FontWeight.Bold,
                                    )
            }
        }

        HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)

        // ── Week Rows with Spanning Bars ─────────────────────────────────────
        for (weekIdx in 0 until totalWeeks) {
            val weekStartDate = if (weekIdx == 0 && leadingDays > 0) {
                firstOfMonth.minusDays(leadingDays.toLong())
            } else {
                firstOfMonth.plusDays((weekIdx * 7 - leadingDays).toLong())
            }

            MonthWeekRow(
                weekStartDate = weekStartDate,
                yearMonth = yearMonth,
                events = events,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                onEventClick = onEventClick,
            )
        }
    }
}

@Composable
private fun MonthWeekRow(
    weekStartDate: LocalDate,
    yearMonth: YearMonth,
    events: List<CalendarEventItem>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill
    val weekEndDate = weekStartDate.plusDays(6)

    // Events active in this week
    val weekEvents = remember(events, weekStartDate) {
        events.filter { ev ->
            !ev.endDate.isBefore(weekStartDate) && !ev.startDate.isAfter(weekEndDate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, sk.cardBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 1. Day Number Headers
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dayOffset in 0..6) {
                val dayDate = weekStartDate.plusDays(dayOffset.toLong())
                val isCurrentMonth = dayDate.month == yearMonth.month
                val isToday = dayDate == LocalDate.now()
                val isSelected = dayDate == selectedDate
                val isWeekend = dayOffset == 0 || dayOffset == 6
                // snappy — the selected-day highlight is a small discrete
                // state change, not content entrance.
                val dayBg by animateColorAsState(
                    when {
                        isSelected -> sk.brand.copy(alpha = 0.35f)
                        isToday -> sk.cyan.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    },
                    animationSpec = com.example.skillsync.theme.SkillMotion.snappy(),
                    label = "day-select-bg",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(dayBg)
                        .clickable { onDateSelected(dayDate) }
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${dayDate.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isSelected -> sk.frost
                            isToday -> sk.sky
                            !isCurrentMonth -> sk.subText.copy(alpha = 0.3f)
                            isWeekend -> sk.subText.copy(alpha = 0.6f)
                            else -> sk.bodyText
                        },
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // 2. Multi-Day Spanning Event Banners
        if (weekEvents.isNotEmpty()) {
            val topEvents = weekEvents.take(3)
            topEvents.forEach { ev ->
                val startCol = ChronoUnit.DAYS.between(weekStartDate, ev.startDate).toInt().coerceIn(0, 6)
                val endCol = ChronoUnit.DAYS.between(weekStartDate, ev.endDate).toInt().coerceIn(0, 6)
                val isMultiDay = ev.startDate != ev.endDate || startCol != endCol

                Row(modifier = Modifier.fillMaxWidth()) {
                    if (startCol > 0) {
                        Spacer(modifier = Modifier.weight(startCol.toFloat()))
                    }

                    val spanLength = (endCol - startCol + 1).coerceAtLeast(1)
                    val isStart = ev.startDate == weekStartDate.plusDays(startCol.toLong())
                    val isEnd = ev.endDate == weekStartDate.plusDays(endCol.toLong())

                    val who = shortTrainerName(ev.trainerName)
                    Column(
                        modifier = Modifier
                            .weight(spanLength.toFloat())
                            .padding(vertical = 1.dp, horizontal = 1.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = if (isStart) 4.dp else 0.dp,
                                    bottomStart = if (isStart) 4.dp else 0.dp,
                                    topEnd = if (isEnd) 4.dp else 0.dp,
                                    bottomEnd = if (isEnd) 4.dp else 0.dp,
                                )
                            )
                            .background(ev.category.color)
                            .clickable { onEventClick(ev) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        // WHAT then WHO, max two lines — never a truncated
                        // "Course: Full Sentence Title... — Trainer" run-on.
                        Text(
                            text = "${ev.category.icon} ${shortCourseTitle(ev.title)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sk.cardBg,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (who.isNotBlank()) {
                            Text(
                                text = who,
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.cardBg.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    val remainingCols = 6 - endCol
                    if (remainingCols > 0) {
                        Spacer(modifier = Modifier.weight(remainingCols.toFloat()))
                    }
                }
            }

            if (weekEvents.size > 3) {
                Text(
                    "+${weekEvents.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.cyan,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

// ── Selected Date Inspector Card ────────────────────────────────────────────

@Composable
private fun SelectedDayInspectionCard(
    date: LocalDate,
    eventsOnDay: List<CalendarEventItem>,
    onTrainerClick: (String, String) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill
    val formatted = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))

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
            Column {
                Text(
                    formatted,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = sk.sky,
                )
                Text(
                    if (eventsOnDay.isEmpty()) "No activities scheduled" else "${eventsOnDay.size} active engagements",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                )
            }

            if (eventsOnDay.isNotEmpty()) {
                Surface(
                    color = sk.brand.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "${eventsOnDay.size} ACTIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.sky,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (eventsOnDay.isEmpty()) {
            Text(
                "No team deliveries, mocks, webinars or leaves on this day.",
                style = MaterialTheme.typography.bodySmall,
                color = sk.subText,
                modifier = Modifier.padding(vertical = Space.xs),
            )
        } else {
            eventsOnDay.forEach { ev ->
                EventCardRow(event = ev, onTrainerClick = onTrainerClick, onEventClick = onEventClick)
            }
        }
    }
}

// ── Week View ───────────────────────────────────────────────────────────────

@Composable
private fun WeekScheduleView(
    selectedDate: LocalDate,
    events: List<CalendarEventItem>,
    onDateSelected: (LocalDate) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill
    val weekStart = selectedDate.with(DayOfWeek.SUNDAY)
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }

    // A true 7-column calendar grid for the week — same visual language as the
    // month grid, one column per day with the day's events stacked as chips.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(Radii.card))
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            days.forEachIndexed { idx, date ->
                val isToday = date == LocalDate.now()
                val isSelected = date == selectedDate
                val isWeekend = idx == 0 || idx == 6
                val dayEvents = events.filter { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isSelected -> sk.brand.copy(alpha = 0.25f)
                                isToday -> sk.cyan.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onDateSelected(date) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWeekend) sk.subText.copy(alpha = 0.5f) else sk.labelText,
                        fontWeight = FontWeight.Bold,                     )
                    Text(
                        "${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            isSelected -> sk.frost
                            isToday -> sk.sky
                            isWeekend -> sk.subText.copy(alpha = 0.7f)
                            else -> sk.bodyText
                        },
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(1.dp))
                    dayEvents.take(4).forEach { ev ->
                        val who = shortTrainerName(ev.trainerName)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(ev.category.color)
                                .clickable { onEventClick(ev) }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "${ev.category.icon} ${shortCourseTitle(ev.title)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = sk.cardBg,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (who.isNotBlank()) {
                                Text(
                                    who,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.cardBg.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (ev.timeSlot.isNotBlank()) {
                                Text(
                                    ev.timeSlot,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sk.cardBg.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (dayEvents.size > 4) {
                        Text("+${dayEvents.size - 4}", style = MaterialTheme.typography.labelSmall,
                            color = sk.cyan)
                    }
                }
                if (idx < 6) {
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(sk.cardBorder.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

// ── Day View ────────────────────────────────────────────────────────────────

@Composable
private fun DayScheduleView(
    date: LocalDate,
    events: List<CalendarEventItem>,
    onTrainerClick: (String, String) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(RoundedCornerShape(Radii.card))
                    .padding(Space.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No deliveries or events scheduled for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sk.subText,
                )
            }
        } else {
            events.forEach { ev ->
                EventCardRow(event = ev, onTrainerClick = onTrainerClick, onEventClick = onEventClick)
            }
        }
    }
}

// ── Timeline Queue View ─────────────────────────────────────────────────────

@Composable
private fun TimelineQueueView(
    events: List<CalendarEventItem>,
    onTrainerClick: (String, String) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill
    val today = LocalDate.now()

    val current = remember(events) { events.filter { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) } }
    val upcoming = remember(events) { events.filter { it.startDate.isAfter(today) }.sortedBy { it.startDate } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (current.isNotEmpty()) {
            Text(
                "CURRENTLY DELIVERING (${current.size})",
                style = MaterialTheme.typography.labelSmall,
                color = EventCategory.DELIVERY.color,
                fontWeight = FontWeight.Bold,
            )
            current.forEach { EventCardRow(it, onTrainerClick, onEventClick) }
        }

        if (upcoming.isNotEmpty()) {
            Text(
                "UPCOMING ENGAGEMENTS (${upcoming.size})",
                style = MaterialTheme.typography.labelSmall,
                color = sk.sky,
                fontWeight = FontWeight.Bold,
            )
            upcoming.forEach { EventCardRow(it, onTrainerClick, onEventClick) }
        }
    }
}

// ── Event Card Row ──────────────────────────────────────────────────────────

@Composable
private fun EventCardRow(
    event: CalendarEventItem,
    onTrainerClick: (String, String) -> Unit,
    onEventClick: (CalendarEventItem) -> Unit,
) {
    val sk = MaterialTheme.skill

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(event.category.color.copy(alpha = 0.08f))
            .border(1.dp, event.category.color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { onEventClick(event) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Trainer avatar — real photo where the model has one, initials
        // otherwise. This is who is delivering, not just a category glyph.
        Avatar(
            name = event.trainerName.ifBlank { event.title },
            photoUrl = null,
            size = 32.dp,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sk.bodyText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    color = event.category.color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "${event.category.icon} ${event.category.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = event.category.color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (event.trainerName.isNotBlank()) {
                    Text(
                        event.trainerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.sky,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onTrainerClick(event.trainerEmail, event.trainerName)
                        },
                    )
                }
                if (event.customer.isNotBlank()) {
                    Text("·", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Text(
                        event.customer,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (event.deliveryMode.isNotBlank()) {
                    Text("·", style = MaterialTheme.typography.labelSmall, color = sk.subText)
                    Text(
                        event.deliveryMode,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.labelText,
                    )
                }
            }

            Text(
                "${event.startDate.format(DateTimeFormatter.ofPattern("d MMM"))} – ${event.endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · ${event.timeSlot}",
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText.copy(alpha = 0.8f),
                            )
        }
    }
}

// ── Event Detail Bottom Sheet / Modal ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    event: CalendarEventItem,
    onDismiss: () -> Unit,
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sk.cardBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = event.category.color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "${event.category.icon} ${event.category.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = event.category.color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    "${event.startDate} to ${event.endDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                )
            }

            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = sk.bodyText,
            )

            HorizontalDivider(color = sk.cardBorder.copy(alpha = 0.5f))

            // Metadata Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Trainer", event.trainerName.ifBlank { "Unassigned" }) {
                    if (event.trainerName.isNotBlank()) {
                        onTrainerClick(event.trainerEmail, event.trainerName)
                        onDismiss()
                    }
                }
                DetailRow("Client / Customer", event.customer.ifBlank { "Internal / Retail" })
                DetailRow("Timing Slot", event.timeSlot)
                DetailRow("Delivery Mode", event.deliveryMode.ifBlank { "Standard ILT" })
                if (event.location.isNotBlank()) DetailRow("Location", event.location)
                if (event.pax != null) DetailRow("Enrolled Attendees", "${event.pax} pax")
            }

            Spacer(Modifier.height(Space.sm))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = event.category.color),
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val sk = MaterialTheme.skill
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick != null) sk.sky else sk.bodyText,
            fontWeight = if (onClick != null) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
