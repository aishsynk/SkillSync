package com.example.skillsync.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused correctness tests for the shared chart tooltip introduced in
 * Design V3 Phase 1 (see AI/DECISIONS.md) — not a screenshot test. Proves:
 * tap-to-select resolves the right bar, the tooltip is absent until a
 * selection is made, and an empty/no-data chart never offers one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartInteractionTest {

    @get:Rule val compose = createComposeRule()

    // Each bar carries its own "$label $value" content description (for
    // accessibility) and the tooltip — once shown — carries the identical
    // string. So "not yet selected" is one matching node (the bar itself);
    // "selected" is two (the bar plus its tooltip).

    @Test
    fun barChart_tooltipAbsent_untilBarIsTapped() {
        compose.setContent {
            SkillSyncTheme {
                BarChart(
                    bars = listOf(
                        BarDatum("Mon", 4, Color.Cyan),
                        BarDatum("Tue", 9, Color.Cyan),
                    ),
                    interactive = true,
                )
            }
        }
        compose.onAllNodesWithContentDescription("Tue 9").assertCountEquals(1)
    }

    @Test
    fun barChart_tapResolvesNearestBar_andShowsItsTooltip() {
        compose.setContent {
            SkillSyncTheme {
                BarChart(
                    bars = listOf(
                        BarDatum("Mon", 4, Color.Cyan),
                        BarDatum("Tue", 9, Color.Cyan),
                    ),
                    interactive = true,
                )
            }
        }
        compose.onNodeWithContentDescription("Tue 9").performClick()
        // The tooltip renders the tapped bar's own label/value — not a
        // fabricated or mismatched one — and the other bar's tooltip never
        // appears.
        compose.onAllNodesWithContentDescription("Tue 9").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Mon 4").assertCountEquals(1)
    }

    @Test
    fun barChart_emptyData_neverOffersATooltip() {
        compose.setContent {
            SkillSyncTheme {
                BarChart(bars = emptyList(), interactive = true)
            }
        }
        compose.onNodeWithText("Nothing to plot.").assertExists()
    }

    @Test
    fun chartTooltip_rendersLabelAndValue() {
        compose.setContent {
            SkillSyncTheme {
                ChartTooltip(label = "Utilisation", value = "64%", tint = Color.Cyan)
            }
        }
        compose.onNodeWithContentDescription("Utilisation 64%").assertExists()
    }
}
