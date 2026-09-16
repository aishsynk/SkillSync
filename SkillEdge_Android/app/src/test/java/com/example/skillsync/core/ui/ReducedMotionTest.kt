package com.example.skillsync.core.ui

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.skillsync.theme.SkillSyncTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reduced motion is centralized in `rememberReducedMotion()` (core/ui/Motion.kt)
 * so animated primitives resolve immediately to their final state instead of
 * each screen re-implementing its own accessibility check. See
 * AI/DECISIONS.md, Design V3 Phase 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReducedMotionTest {

    @get:Rule val compose = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun animatedCount_resolvesImmediately_whenAnimatorScaleIsZero() {
        setAnimatorScale(0f)
        compose.setContent {
            SkillSyncTheme {
                AnimatedCount(
                    target = 78,
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        // No clock advance at all — a normal-motion run would still show 0
        // (or an intermediate value) at this point; reduced motion must not.
        compose.onNodeWithText("78").assertExists()
    }

    @Test
    fun animatedCount_startsFromZero_whenAnimatorScaleIsNormal() {
        setAnimatorScale(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SkillSyncTheme {
                AnimatedCount(
                    target = 78,
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        // Before the clock advances, the count animator has not reached 78
        // yet — proves the zero-scale test above is testing something real,
        // not a coincidence of how AnimatedCount always renders.
        compose.onNodeWithText("78").assertDoesNotExist()
    }
}
