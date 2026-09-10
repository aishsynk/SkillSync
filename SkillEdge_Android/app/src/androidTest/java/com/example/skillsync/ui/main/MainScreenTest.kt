package com.example.skillsync.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.skillsync.feature.home.MainScreen
import com.example.skillsync.navigation.HomeTab
import org.junit.Rule
import org.junit.Test

/** Shell smoke test: the MainScreen bottom bar renders the active tab label. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun dashboardTabLabel_isShown() {
    composeTestRule.setContent {
      MainScreen(
        email = "smoke@koenig-solutions.com",
        tab = HomeTab.DASHBOARD,
        onTabChange = {},
        onTrainerClick = { _, _ -> },
      )
    }
    composeTestRule.onNodeWithText("Today").assertExists()
  }
}