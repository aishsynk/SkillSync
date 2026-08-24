package com.example.skillsync.ui.main

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
import com.example.skillsync.ui.components.intOrNull
import com.example.skillsync.ui.components.str
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
    TIMELINE("Timeline")
}

enum class EventCategory(
    val label: String,
    val icon: String,
    val color: Color,
    val lightBg: Color,
) {
    DELIVERY("Delivery", "📦", Color(0xFF0284C7), Color(0x330284C7)),
    MOCK("Mock", "🎯", Color(0xFF9333EA), Color(0x339333EA)),
    WEBINAR("Webinar", "🎤", Color(0xFFEC4899), Color(0x33EC4899)),
    LEAVE("Leave", "🏖️", Color(0xFFF59E0B), Color(0x33F59E0B)),
    UPSKILLING("Upskilling", "🚀", Color(0xFF10B981), Color(0x3310B981)),
    MEETING("Meeting", "💬", Color(0xFF06B6D4), Color(0x3306B6D4))
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
@Composable
fun TeamCalendarScreen(
    batches: List<Map<*, *>>,
    modifier: Modifier = Modifier,
    onTrainerClick: (String, String) -> Unit = { _, _ -> },
) {
    val sk = MaterialTheme.skill
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate>(LocalDate.now()) }
    var selectedCategoryFilter by remember { mutableStateOf<EventCategory?>(null) }
    var inspectedEvent by remember { mutableStateOf<CalendarEventItem?>(null) }

    // Parse all raw batches into structured CalendarEventItems
    val allEvents = remember(batches) {
        batches.mapNotNull { b ->
            val course = b.str("course_name").ifBlank { b.str("demand_id").ifBlank { "Delivery" } }
            val startStr = b.str("start_date").take(10)
            val endStr = b.str("end_date").take(10).ifBlank { startStr }

            val start = try {
                if (startStr.isNotBlank()) LocalDate.parse(startStr) else null
            } catch (_: Exception) { null }

            if (start == null) return@mapNotNull null

            val end = try {
                if (endStr.isNotBlank()) LocalDate.parse(endStr) else start
            } catch (_: Exception) { start }

            val actualEnd = if (end.isBefore(start)) start else end
            val mode = b.str("delivery_mode")
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

            CalendarEventItem(
                id = b.str("demand_id").ifBlank { "${course}_${start}" },
                title = course,
                category = cat,
                startDate = start,
                endDate = actualEnd,
                timeSlot = b.str("session_time").ifBlank { "09:00 - 17:00" },
                trainerName = b.str("trainer_name"),
                trainerEmail = b.str("trainer_email"),
                customer = b.str("customer"),
                location = b.str("location"),
                deliveryMode = mode,
                pax = b.intOrNull("participants"),
                rawBatch = b,
            )
        }
    }

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
                    CalendarViewMode.MONTH -> currentYearMonth = currentYearMonth.minusMonths(1)
                    CalendarViewMode.WEEK -> {
                        selectedDate = selectedDate.minusWeeks(1)
                        currentYearMonth = YearMonth.from(selectedDate)
                    }
                    CalendarViewMode.DAY, CalendarViewMode.TIMELINE -> {
                        selectedDate = selectedDate.minusDays(1)
                        currentYearMonth = YearMonth.from(selectedDate)
                    }
                }
            },
            onNext = {
                when (viewMode) {
                    CalendarViewMode.MONTH -> currentYearMonth = currentYearMonth.plusMonths(1)
                    CalendarViewMode.WEEK -> {
                        selectedDate = selectedDate.plusWeeks(1)
                        currentYearMonth = YearMonth.from(selectedDate)
                    }
                    CalendarViewMode.DAY, CalendarViewMode.TIMELINE -> {
                        selectedDate = selectedDate.plusDays(1)
                        currentYearMonth = YearMonth.from(selectedDate)
                    }
                }
            },
            onToday = {
                val now = LocalDate.now()
                selectedDate = now
                currentYearMonth = YearMonth.now()
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
        when (viewMode) {
            CalendarViewMode.MONTH -> {
                SpanningMonthCalendarGrid(
                    yearMonth = currentYearMonth,
                    events = filteredEvents,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
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
                    onDateSelected = { selectedDate = it },
                    onEventClick = { inspectedEvent = it },
                    onTrainerClick = onTrainerClick,
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
            // Segmented View Mode Tabs (Month | Week | Day)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = sk.cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, sk.cardBorder),
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    CalendarViewMode.values().take(3).forEach { mode ->
                        val isSelected = viewMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                                .clickable { onViewModeChange(mode) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color.White else sk.subText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

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
                    color = Color(0xFF0284C7).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                ) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
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
            label = "📦 Deliveries ($deliveryCount)",
            selected = selectedCategory == EventCategory.DELIVERY,
            tint = EventCategory.DELIVERY.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.DELIVERY) null else EventCategory.DELIVERY) },
        )
        FilterPill(
            label = "🎯 Mocks ($mockCount)",
            selected = selectedCategory == EventCategory.MOCK,
            tint = EventCategory.MOCK.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.MOCK) null else EventCategory.MOCK) },
        )
        FilterPill(
            label = "🎤 Webinars ($webinarCount)",
            selected = selectedCategory == EventCategory.WEBINAR,
            tint = EventCategory.WEBINAR.color,
            onClick = { onSelectCategory(if (selectedCategory == EventCategory.WEBINAR) null else EventCategory.WEBINAR) },
        )
        FilterPill(
            label = "🏖️ Leaves ($leaveCount)",
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
            fontSize = 11.sp,
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
                    fontSize = 11.sp,
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isSelected -> Color(0xFF0284C7).copy(alpha = 0.3f)
                                isToday -> sk.cyan.copy(alpha = 0.15f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onDateSelected(dayDate) }
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${dayDate.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isSelected -> Color.White
                            isToday -> Color(0xFF38BDF8)
                            !isCurrentMonth -> sk.subText.copy(alpha = 0.3f)
                            isWeekend -> sk.subText.copy(alpha = 0.6f)
                            else -> sk.bodyText
                        },
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
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

                    Box(
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
                        Text(
                            text = if (isMultiDay) "${ev.category.icon} ${ev.title}" else "● ${ev.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    fontSize = 9.sp,
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
                    color = Color(0xFF38BDF8),
                )
                Text(
                    if (eventsOnDay.isEmpty()) "No activities scheduled" else "${eventsOnDay.size} active engagements",
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText,
                )
            }

            if (eventsOnDay.isNotEmpty()) {
                Surface(
                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "${eventsOnDay.size} ACTIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8),
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
    onTrainerClick: (String, String) -> Unit,
) {
    val sk = MaterialTheme.skill
    val weekStart = selectedDate.with(DayOfWeek.SUNDAY)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        for (dayOffset in 0..6) {
            val date = weekStart.plusDays(dayOffset.toLong())
            val isSelected = date == selectedDate
            val isToday = date == LocalDate.now()
            val dayEvents = events.filter { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(RoundedCornerShape(Radii.card))
                    .clickable { onDateSelected(date) }
                    .padding(Space.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isToday) Color(0xFF38BDF8) else sk.bodyText,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                        if (isToday) {
                            Surface(color = Color(0xFF0284C7), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "TODAY",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    Text(
                        "${dayEvents.size} events",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                }

                if (dayEvents.isEmpty()) {
                    Text(
                        "Clear schedule",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText.copy(alpha = 0.5f),
                    )
                } else {
                    dayEvents.forEach { ev ->
                        EventCardRow(event = ev, onTrainerClick = onTrainerClick, onEventClick = onEventClick)
                    }
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
                color = Color(0xFF38BDF8),
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
        // Category Accent Dot / Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(event.category.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(event.category.icon, fontSize = 14.sp)
        }

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
                        event.category.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = event.category.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
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
                        color = Color(0xFF38BDF8),
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
                fontSize = 10.sp,
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
            color = if (onClick != null) Color(0xFF38BDF8) else sk.bodyText,
            fontWeight = if (onClick != null) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
