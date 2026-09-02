package com.example.skillsync.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.components.Pulse
import com.example.skillsync.ui.components.PulseTone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PulseTest {

    @get:Rule val compose = createComposeRule()

    @Test fun pulse_showsTheOneNumberAndItsLabel() {
        compose.setContent {
            SkillSyncTheme {
                Pulse(value = "72", label = "readiness", tone = PulseTone.Calm,
                    refreshing = false, onTap = {})
            }
        }
        compose.onNodeWithText("72").assertIsDisplayed()
        compose.onNodeWithText("READINESS").assertIsDisplayed()
    }

    @Test fun pulse_tapPullsFresh() {
        var tapped = false
        compose.setContent {
            SkillSyncTheme {
                Pulse(value = "3", label = "need you", tone = PulseTone.Critical,
                    refreshing = true, onTap = { tapped = true })
            }
        }
        compose.onNodeWithText("3").performClick()
        assertTrue("Pulse tap must trigger its action", tapped)
    }
}
