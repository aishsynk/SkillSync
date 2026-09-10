package com.example.skillsync.ui.main

import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.example.skillsync.feature.home.MainScreenViewModel
import com.example.skillsync.feature.home.DashboardState

class MainScreenViewModelTest {

    @Test
    fun uiState_initiallyLoading() = runTest {
        val viewModel = MainScreenViewModel()
        // Before loadData() is called, state should be Loading
        assertTrue(viewModel.uiState.first() is DashboardState.Loading)
    }
}
