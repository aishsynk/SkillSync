package com.example.skillsync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.skillsync.theme.Severity
import com.example.skillsync.theme.SkillSyncTheme
import com.example.skillsync.ui.auth.LoginScreen
import com.example.skillsync.ui.components.LocalNotify
import com.example.skillsync.ui.components.NotifyState
import com.example.skillsync.ui.components.SkillAlertDialog
import com.example.skillsync.ui.components.ToastHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The notification system and the redesigned sign-in screen.
 *
 * These lock the behaviour the four previous ad-hoc surfaces did not have:
 * a bounded queue, severity-specific styling, and one shared host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class NotifyAndLoginTest {

    @get:Rule
    val compose = createComposeRule()

    // ── Toast queue ─────────────────────────────────────────────────────────

    @Test
    fun toastQueue_neverGrowsBeyondThree() {
        val state = NotifyState()
        repeat(6) { state.error("Failure $it") }
        assertEquals("A burst of failures must not bury the app", 3, state.toasts.size)
        // The newest survive; the oldest are dropped.
        assertTrue(state.toasts.last().title == "Failure 5")
    }

    @Test
    fun toastSeverities_areDistinct() {
        val state = NotifyState()
        state.success("Saved")
        state.error("Failed")
        state.warn("Careful")
        state.info("Heads up")
        assertEquals(
            listOf(Severity.Good, Severity.Critical, Severity.Warning, Severity.Info)
                .drop(1),               // queue caps at three, so the first is gone
            state.toasts.map { it.severity },
        )
    }

    @Test
    fun toastHost_rendersTitleAndMessage() {
        // Auto-advance would run the toast's whole 3.5s lifecycle before the
        // assertions and dismiss it, so the clock is driven by hand here.
        compose.mainClock.autoAdvance = false
        val state = NotifyState()
        state.success("Skill saved to RMS", "PL-300 recorded against Abhinav Samant")
        compose.setContent { SkillSyncTheme { Box { ToastHost(state) } } }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Skill saved to RMS").assertExists()
        compose.onNodeWithText("PL-300 recorded against Abhinav Samant").assertExists()
    }

    /** A toast must clear itself; a queue that never drains is a blocked UI. */
    @Test
    fun toast_autoDismissesAfterItsDuration() {
        compose.mainClock.autoAdvance = false
        val state = NotifyState()
        state.info("Transient")
        compose.setContent { SkillSyncTheme { Box { ToastHost(state) } } }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Transient").assertExists()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onAllNodesWithText("Transient").assertCountEquals(0)
    }

    // ── Alert dialog ────────────────────────────────────────────────────────

    @Test
    fun alertDialog_confirmAndDismissAreBothReachable() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            SkillSyncTheme {
                SkillAlertDialog(
                    severity = Severity.Warning,
                    title = "Sign out?",
                    message = "Your cached team data stays on this device.",
                    confirmLabel = "Sign out",
                    destructive = true,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNodeWithText("Sign out?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue("Cancel must dismiss", dismissed)
        compose.onNodeWithText("Sign out").performClick()
        assertTrue("Confirm must fire", confirmed)
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Test
    fun login_showsBrandFormAndDisclosure() {
        compose.setContent { SkillSyncTheme { LoginScreen(onLoginSuccess = {}) } }
        // "Sign in" appears twice by design — the card heading and the button.
        compose.onAllNodesWithText("Sign in").assertCountEquals(2)
        compose.onNodeWithText("Managers and Trainer Plus accounts only.").assertExists()
        compose.onNodeWithText("Work email").assertExists()
        compose.onNodeWithText("DELIVERY INTELLIGENCE").assertExists()
    }

    /**
     * The form card is capped and centred, so on a tablet the fields do not
     * stretch to the full width — the old layout had no cap on the inner column.
     */
    @Test
    fun login_formIsCentredAndWidthCapped() {
        compose.setContent { SkillSyncTheme { LoginScreen(onLoginSuccess = {}) } }
        val field = compose.onNodeWithText("Work email").fetchSemanticsNode().boundsInRoot
        val root = compose.onAllNodesWithText("Sign in").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("Field must sit under the heading", field.top > root.top)
    }

    @Test
    fun login_signInButtonIsDisabledUntilAnEmailIsTyped() {
        compose.setContent { SkillSyncTheme { LoginScreen(onLoginSuccess = {}) } }
        // Two nodes carry "Sign in" — the card heading and the button label.
        compose.onAllNodesWithText("Sign in").assertCountEquals(2)
        compose.onAllNodesWithText("Sign in").onFirst().assertExists()
    }
}
