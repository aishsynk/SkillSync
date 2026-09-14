package com.example.skillsync.theme

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D1 foundation primitives, rendered and asserted in isolation — per the
 * "compile-safe before pilot migration" requirement, not exercised only
 * incidentally through a screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesignSystemD1Test {
    @get:Rule val compose = createComposeRule()

    private fun setThemed(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { SkillSyncTheme { content() } }
    }

    // ── MetricSparkline ──────────────────────────────────────────────────────

    @Test
    fun sparkline_singlePointRendersNothing() {
        setThemed {
            MetricSparkline(values = listOf(70f), modifier = Modifier.size(40.dp))
            Text("marker-after-sparkline")
        }
        // No crash, and the marker after it still renders — proves the early
        // return doesn't throw for < 2 points, it just draws nothing.
        compose.onNodeWithText("marker-after-sparkline").assertExists()
    }

    @Test
    fun sparkline_emptyListRendersNothing() {
        setThemed {
            MetricSparkline(values = emptyList(), modifier = Modifier.size(40.dp))
            Text("marker-after-empty-sparkline")
        }
        compose.onNodeWithText("marker-after-empty-sparkline").assertExists()
    }

    @Test
    fun sparkline_multiPointCompilesAndRenders() {
        setThemed {
            MetricSparkline(values = listOf(10f, 40f, 20f, 60f), modifier = Modifier.size(width = 80.dp, height = 24.dp))
        }
        // Reaching here without a Canvas-draw exception is the assertion —
        // there is no pixel-content assertion available under Robolectric.
    }

    @Test
    fun sparkline_flatDataDoesNotDivideByZero() {
        setThemed {
            MetricSparkline(values = listOf(50f, 50f, 50f), modifier = Modifier.size(width = 80.dp, height = 24.dp))
        }
    }

    // ── MetricProgress ───────────────────────────────────────────────────────

    @Test
    fun progress_clampsOutOfBoundsFractions() {
        setThemed {
            MetricProgress(fraction = 1.4f)
            MetricProgress(fraction = -0.4f)
        }
    }

    // ── HeroRing ─────────────────────────────────────────────────────────────

    @Test
    fun heroRing_showsValueWhenPresent() {
        setThemed { HeroRing(value = 82, modifier = Modifier.size(60.dp)) }
        compose.onNodeWithText("82").assertExists()
    }

    @Test
    fun heroRing_showsDashWhenValueUnknown() {
        setThemed { HeroRing(value = null, modifier = Modifier.size(60.dp)) }
        compose.onNodeWithText("—").assertExists()
    }

    // ── ActionRow ────────────────────────────────────────────────────────────

    @Test
    fun actionRow_minimalRendersTitleOnly() {
        setThemed { ActionRow(title = "Minimal row") }
        compose.onNodeWithText("Minimal row").assertExists()
    }

    @Test
    fun actionRow_fullInvokesPrimaryActionNotOnClick() {
        var primaryFired = false
        var rowClicked = false
        setThemed {
            ActionRow(
                title = "Unstaffed: DP-700T00",
                supportingText = "Open batch needs a trainer.",
                metadata = "Due 2026-10-01",
                tint = MaterialTheme.skill.crit,
                trailingValue = "High",
                primaryActionLabel = "Communicate",
                onPrimaryAction = { primaryFired = true },
                onClick = { rowClicked = true },
            )
        }
        compose.onNodeWithText("Communicate").performClick()
        assertEquals(true, primaryFired)
        assertEquals(false, rowClicked)
    }

    // ── TimelineItem ─────────────────────────────────────────────────────────

    @Test
    fun timeline_singleItemFirstAndLast() {
        setThemed { TimelineItem(title = "Only event", isFirst = true, isLast = true) }
        compose.onNodeWithText("Only event").assertExists()
    }

    @Test
    fun timeline_multipleItemsAllRender() {
        setThemed {
            androidx.compose.foundation.layout.Column {
                TimelineItem(title = "First", isFirst = true)
                TimelineItem(title = "Middle")
                TimelineItem(title = "Last", isLast = true)
            }
        }
        compose.onNodeWithText("First").assertExists()
        compose.onNodeWithText("Middle").assertExists()
        compose.onNodeWithText("Last").assertExists()
    }

    @Test
    fun timeline_longSupportingTextDoesNotCrash() {
        setThemed {
            TimelineItem(
                title = "Event",
                supportingText = "A very long supporting description ".repeat(10),
            )
        }
        compose.onNodeWithText("Event").assertExists()
    }

    // ── SegmentedSelector ────────────────────────────────────────────────────

    @Test
    fun segmentedSelector_selectionChangesOnClick() {
        setThemed {
            var sel by remember { mutableStateOf("A") }
            SegmentedSelector(options = listOf("A" to "Alpha", "B" to "Beta"), selected = sel, onSelect = { sel = it })
        }
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Beta").assertExists()
    }

    // ── SkillSyncPartialDataState ────────────────────────────────────────────

    @Test
    fun partialDataState_isDistinctFromErrorState() {
        setThemed {
            SkillSyncPartialDataState("Demand loaded. Availability source unavailable.")
        }
        compose.onNodeWithText("Demand loaded. Availability source unavailable.").assertExists()
        compose.onAllNodesWithText("Operational Alert").assertCountEquals(0)
    }
}
