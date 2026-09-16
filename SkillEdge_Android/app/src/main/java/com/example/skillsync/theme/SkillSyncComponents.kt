package com.example.skillsync.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.R

/**
 * SkillSync Unified Shared Component System — Wave 1 Product Design Foundation.
 *
 * Provides the single source of truth for screen layout, chrome, data cards,
 * metrics, chips, buttons, inputs, list rows, and global states.
 */

// ── Screen Container & Scaffold ──────────────────────────────────────────────

@Composable
fun SkillSyncScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    isOnline: Boolean = true,
    showAtmosphere: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.skill.pageBg)) {
        if (showAtmosphere) {
            AuroraBackground()
        }
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.skill.frost,
            topBar = topBar,
            bottomBar = bottomBar,
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                if (!isOnline) {
                    SkillSyncOfflineBanner()
                }
                Box(Modifier.weight(1f)) {
                    content(PaddingValues(0.dp))
                }
            }
        }
    }
}

// ── Application Chrome / Top Bar ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillSyncTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val sk = MaterialTheme.skill
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                if (subtitle != null) {
                    Text(
                        subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.sky,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = sk.frost,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(Radii.chip))
                            .background(sk.surface2)
                            .border(1.dp, sk.cardBorder, RoundedCornerShape(Radii.chip)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Navigate back",
                            tint = sk.frost,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = sk.pageBg.copy(alpha = 0.94f),
        ),
    )
}

// ── Page Header & Anatomy ───────────────────────────────────────────────────

@Composable
fun SkillSyncPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    contextInfo: String? = null,
    tag: String? = null,
    primaryAction: (@Composable () -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        if (tag != null) {
            Text(
                tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.sky,
                modifier = Modifier.padding(bottom = Space.xs),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = sk.frost,
                    fontWeight = FontWeight.Bold,
                )
                if (contextInfo != null) {
                    Text(
                        contextInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sk.subText,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
            if (primaryAction != null) {
                Spacer(Modifier.width(Space.md))
                primaryAction()
            }
        }
    }
}

// ── Section Container ───────────────────────────────────────────────────────

@Composable
fun SkillSyncSection(
    title: String,
    modifier: Modifier = Modifier,
    conclusion: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sk = MaterialTheme.skill
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
        if (conclusion != null) {
            Text(
                conclusion,
                style = MaterialTheme.typography.titleMedium,
                color = sk.bodyText,
                modifier = Modifier
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.sm),
            )
        }
        content()
    }
}

// ── Cards ───────────────────────────────────────────────────────────────────

/**
 * Thin wrapper over [SkillCard]'s surface (same `glassSurface`/`accentGlass`
 * primitives via the shared `Modifier.cardSurface()`) — kept as its own entry
 * point because call sites expect `Space.sm` internal spacing rather than
 * `SkillCard`'s `Space.md`. See `AI/DECISIONS.md`, Design V3 Phase 1.
 */
@Composable
fun SkillSyncCard(
    modifier: Modifier = Modifier,
    severity: Severity? = null,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .cardSurface(severity, strong, onClick)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

// ── Metric Display ──────────────────────────────────────────────────────────

@Composable
fun SkillSyncMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaGood: Boolean? = null,
    size: FigureSize = FigureSize.Medium,
    tint: Color? = null,
) {
    Figure(
        value = value,
        label = label,
        size = size,
        modifier = modifier,
        tint = tint,
        delta = delta,
        deltaTint = when (deltaGood) {
            true -> MaterialTheme.skill.good
            false -> MaterialTheme.skill.crit
            null -> MaterialTheme.skill.subText
        },
    )
}

// ── Chips & Status Indicators ───────────────────────────────────────────────

@Composable
fun SkillSyncStatusChip(
    text: String,
    severity: Severity,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    ToneChip(
        text = text,
        tint = severity.tint(),
        modifier = modifier,
        solid = solid,
    )
}

@Composable
fun SkillSyncChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val sk = MaterialTheme.skill
    val chipShape = RoundedCornerShape(Radii.chip)
    val background = if (selected) sk.brand.copy(alpha = 0.22f) else sk.surface2
    val border = if (selected) sk.sky.copy(alpha = 0.60f) else sk.cardBorder
    val textColor = if (selected) sk.frost else sk.subText

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(chipShape)
            .background(background)
            .border(1.dp, border, chipShape)
            .pressable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(Space.xs))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) sk.sky else sk.surface3),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (selected) sk.navy else sk.bodyText,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Buttons ─────────────────────────────────────────────────────────────────

@Composable
fun SkillSyncPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    val sk = MaterialTheme.skill
    val shape = RoundedCornerShape(Radii.chip)
    val bg = if (enabled) {
        Brush.horizontalGradient(listOf(sk.brand, sk.royal))
    } else {
        SolidColor(sk.surface3)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (enabled) sk.sky.copy(alpha = 0.5f) else sk.cardBorder, shape)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    tint = if (enabled) sk.frost else sk.subText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Space.sm))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) sk.frost else sk.subText,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SkillSyncSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    val sk = MaterialTheme.skill
    val shape = RoundedCornerShape(Radii.chip)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(sk.surface2)
            .border(1.dp, sk.cardBorder, shape)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    tint = if (enabled) sk.sky else sk.subText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Space.sm))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) sk.frost else sk.subText,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Text Fields & Inputs ────────────────────────────────────────────────────

@Composable
fun SkillSyncTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: Int? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val sk = MaterialTheme.skill
    val shape = RoundedCornerShape(Radii.chip)

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = sk.labelText,
                modifier = Modifier.padding(bottom = Space.xs),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(shape)
                .background(sk.surface2)
                .border(1.dp, sk.cardBorder, shape)
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    tint = sk.subText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.sm))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sk.subText.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = sk.frost),
                    cursorBrush = SolidColor(sk.sky),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = true,
                )
            }
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun SkillSyncSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search trainers, courses, batches...",
    onClear: () -> Unit = { onQueryChange("") },
) {
    val sk = MaterialTheme.skill
    SkillSyncTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = placeholder,
        leadingIcon = R.drawable.ic_search,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                    )
                }
            }
        },
    )
}

// ── Action row ───────────────────────────────────────────────────────────────

/**
 * The reusable operational row: leading marker, title/support/metadata,
 * trailing value, one primary action, one optional secondary action. This is
 * the D1 answer to "stop wrapping every list item in a full bordered card" —
 * This Week, Capacity, Pipeline and Skill Requests all shape into this same
 * row rather than each screen inventing its own.
 *
 * Every slot is optional except [title] — a row with only a title and an
 * `onClick` still works, so a screen never has to fill fields it doesn't have
 * data for.
 */
@Composable
fun ActionRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String = "",
    metadata: String = "",
    tint: Color? = null,
    trailingValue: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    secondaryContent: (@Composable () -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    val clickModifier = if (onClick != null) Modifier.pressable(onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().then(clickModifier).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tint != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(Space.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = sk.frost, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supportingText.isNotBlank()) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = sk.subText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (metadata.isNotBlank()) {
                Text(metadata, style = MaterialTheme.typography.labelSmall, color = sk.labelText)
            }
        }
        if (trailingValue != null) {
            Spacer(Modifier.width(Space.sm))
            Text(trailingValue, style = NumericInline.copy(fontSize = 13.sp), color = sk.frost, fontWeight = FontWeight.SemiBold)
        }
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(Modifier.width(Space.sm))
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radii.chip))
                    .background(sk.brand.copy(alpha = 0.14f))
                    .border(1.dp, sk.brand.copy(alpha = 0.30f), RoundedCornerShape(Radii.chip))
                    .pressable(onPrimaryAction)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(primaryActionLabel, color = sk.brand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        secondaryContent?.invoke()
    }
}

// ── Segmented selector ───────────────────────────────────────────────────────

/**
 * A two-or-more-way segmented choice: filter tabs, view switches, workflow
 * states. This is the shared implementation behind the live
 * `TodayWorkspaceSwitch`/`PeopleWorkspaceSwitch` (`Version2Workspaces.kt`) —
 * D1 promoted their pattern here rather than duplicating it; those two keep
 * their own names and call sites unchanged, delegating to this underneath.
 */
@Composable
fun SegmentedSelector(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.chip))
            .background(sk.surface2)
            .border(1.dp, sk.cardBorder, RoundedCornerShape(Radii.chip))
            .padding(3.dp),
    ) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            // snappy — small discrete-state selection change, see SkillMotion.
            val bg by animateColorAsState(
                if (isSelected) sk.brand.copy(alpha = 0.22f) else Color.Transparent,
                animationSpec = com.example.skillsync.theme.SkillMotion.snappy(),
                label = "segment-bg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radii.chip - 2.dp))
                    .background(bg)
                    .pressable { onSelect(key) }
                    .padding(vertical = Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) sk.frost else sk.subText,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

// ── Global States: Loading, Empty, Error, Offline, Banner ───────────────────

@Composable
fun SkillSyncLoadingState(
    modifier: Modifier = Modifier,
    itemCount: Int = 3,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        repeat(itemCount) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(Radii.card))
                    .background(MaterialTheme.skill.surface2)
                    .border(1.dp, MaterialTheme.skill.cardBorder, RoundedCornerShape(Radii.card)),
            )
        }
    }
}

@Composable
fun SkillSyncEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface()
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(sk.surface3)
                .border(1.dp, sk.cardBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = sk.sky,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(Space.md))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = sk.frost,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = sk.subText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Space.lg),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.lg))
            SkillSyncSecondaryButton(
                text = actionLabel,
                onClick = onAction,
            )
        }
    }
}

@Composable
fun SkillSyncErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    Column(
        modifier = modifier
            .fillMaxWidth()
            .accentGlass(sk.crit)
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Operational Alert",
            style = MaterialTheme.typography.titleMedium,
            color = sk.crit,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = sk.bodyText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.lg))
        SkillSyncPrimaryButton(
            text = "Retry",
            onClick = onRetry,
        )
    }
}

/**
 * Some of a screen's data loaded, some didn't — deliberately distinct from
 * [SkillSyncErrorState] (nothing loaded) per the D1 instruction: "Demand
 * loaded, availability source unavailable" is not the same fact as "Capacity
 * screen failed to load," and showing one generic red error card for both
 * would throw away the data that *did* arrive.
 */
@Composable
fun SkillSyncPartialDataState(
    message: String,
    modifier: Modifier = Modifier,
) {
    val sk = MaterialTheme.skill
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.chip))
            .background(sk.warn.copy(alpha = 0.10f))
            .border(1.dp, sk.warn.copy(alpha = 0.26f), RoundedCornerShape(Radii.chip))
            .padding(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).size(8.dp).clip(CircleShape).background(sk.warn))
        Spacer(Modifier.width(Space.md))
        Text(message, style = MaterialTheme.typography.bodySmall, color = sk.bodyText, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SkillSyncOfflineBanner(modifier: Modifier = Modifier) {
    val sk = MaterialTheme.skill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(sk.warn.copy(alpha = 0.14f))
            .border(1.dp, sk.warn.copy(alpha = 0.30f))
            .padding(vertical = Space.xs, horizontal = Space.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(sk.warn),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            "Offline Mode · Showing cached data · Edits queued for sync",
            style = MaterialTheme.typography.labelSmall,
            color = sk.warn,
        )
    }
}

@Composable
fun SkillSyncInfoBanner(
    title: String,
    message: String,
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    val tint = severity.tint()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.chip))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Radii.chip))
            .padding(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.skill.frost,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.skill.bodyText,
            )
        }
    }
}

// ── List Item ───────────────────────────────────────────────────────────────

@Composable
fun SkillSyncListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: Int? = null,
    leadingTint: Color? = null,
    trailingValue: String? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    val clickModifier = if (onClick != null) Modifier.pressable(onClick) else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(Radii.chip))
            .background(sk.surface2)
            .border(1.dp, sk.cardBorder, RoundedCornerShape(Radii.chip))
            .then(clickModifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radii.icon))
                    .background((leadingTint ?: sk.sky).copy(alpha = 0.14f))
                    .border(1.dp, (leadingTint ?: sk.sky).copy(alpha = 0.28f), RoundedCornerShape(Radii.icon)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    tint = leadingTint ?: sk.sky,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(Space.md))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = sk.frost,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = sk.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailingValue != null) {
            Spacer(Modifier.width(Space.sm))
            Text(
                trailingValue,
                style = NumericInline.copy(fontSize = 13.sp),
                color = sk.frost,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (trailingBadge != null) {
            Spacer(Modifier.width(Space.sm))
            trailingBadge()
        }

        if (onClick != null) {
            Spacer(Modifier.width(Space.xs))
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = sk.subText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
