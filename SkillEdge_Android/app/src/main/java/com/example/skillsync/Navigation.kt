package com.example.skillsync

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.skillsync.ui.main.MainScreen

import com.example.skillsync.ui.auth.LoginScreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun MainNavigation() {
  var currentScreen by remember { mutableStateOf<NavKey>(Login) }

  when (currentScreen) {
      is Login -> {
          LoginScreen(onLoginSuccess = { currentScreen = Main })
      }
      is Main -> {
          MainScreen(
              onItemClick = { navKey -> currentScreen = navKey }, 
              modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
      }
  }
}
