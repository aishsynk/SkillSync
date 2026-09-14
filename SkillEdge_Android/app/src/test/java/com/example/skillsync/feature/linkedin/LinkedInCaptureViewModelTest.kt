package com.example.skillsync.feature.linkedin

import com.example.skillsync.core.network.CaptureAnalysisRequestDto
import com.example.skillsync.feature.linkedin.data.LinkedInCaptureDataSource
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysis
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysisResult
import com.example.skillsync.feature.linkedin.engine.LinkedInCapture
import com.example.skillsync.feature.linkedin.ui.LinkedInCaptureStage
import com.example.skillsync.feature.linkedin.ui.LinkedInCaptureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkedInCaptureViewModelTest {

    private class FakeDataSource(var result: LinkedInAnalysisResult) : LinkedInCaptureDataSource {
        var lastRequest: CaptureAnalysisRequestDto? = null
        var calls = 0
        override suspend fun analyse(request: CaptureAnalysisRequestDto): LinkedInAnalysisResult {
            calls++
            lastRequest = request
            return result
        }
    }

    /** Binds Dispatchers.Main to the runTest scheduler so viewModelScope executes. */
    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun initial_stateIsEditingForPasteFlow() {
        val vm = LinkedInCaptureViewModel(isConfigured = true)
        val s = vm.uiState.value
        assertEquals(LinkedInCaptureStage.EDITING, s.stage)
        assertEquals(LinkedInCapture.METHOD_PASTE, s.captureMethod)
        assertEquals(LinkedInCapture.SOURCE_UNKNOWN, s.sourceApp)
        assertFalse(s.canSubmit)
    }

    @Test
    fun startShare_prefillsRawTextAndUrlOnlyHint() {
        val vm = LinkedInCaptureViewModel(isConfigured = true)
        vm.startShare("https://lnkd.in/gXy", sourceAppHint = null)
        val s = vm.uiState.value
        assertEquals(LinkedInCapture.METHOD_SHARE_INTENT, s.captureMethod)
        assertEquals("https://lnkd.in/gXy", s.rawText)
        assertTrue(s.urlOnlyHint.isNotBlank())
        assertEquals(LinkedInCapture.SOURCE_LINKEDIN, s.sourceApp)
        assertTrue(s.canSubmit)
    }

    @Test
    fun startShare_withTextKeepsItEditable() {
        val vm = LinkedInCaptureViewModel(isConfigured = true)
        vm.startShare("So proud of the team!\nhttps://lnkd.in/gXy")
        val s = vm.uiState.value
        assertEquals(LinkedInCapture.SOURCE_LINKEDIN, s.sourceApp)
        assertEquals("https://lnkd.in/gXy", s.postUrl)
        assertTrue(s.urlOnlyHint.isBlank())
        assertTrue(s.canSubmit)
    }

    @Test
    fun submit_successMovesToResultAndSendsParsedRequest() = runVmTest {
        val fake = FakeDataSource(
            LinkedInAnalysisResult.Success(
                LinkedInAnalysis(
                    action = LinkedInCapture.ACTION_REACTION_AND_COMMENT,
                    comment = "Great work!",
                )
            )
        )
        val vm = LinkedInCaptureViewModel(fake, isConfigured = true)
        vm.onTextChange("A milestone for the team.")
        vm.submit()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(LinkedInCaptureStage.RESULT, s.stage)
        assertEquals("Great work!", s.result?.comment)
        assertFalse(s.analyzing)
        assertEquals("A milestone for the team.", fake.lastRequest?.capture?.raw_text)
        assertEquals(LinkedInCapture.METHOD_PASTE, s.captureMethod)
    }

    @Test
    fun submit_urlOnlyTextIsBlockedClientSideWithoutCallingApi() = runVmTest {
        val fake = FakeDataSource(LinkedInAnalysisResult.Failure("should never run"))
        val vm = LinkedInCaptureViewModel(fake, isConfigured = true)
        vm.startShare("https://lnkd.in/gXy")
        vm.submit()
        advanceUntilIdle()

        assertEquals(LinkedInCaptureStage.EDITING, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.urlOnlyHint.isNotBlank())
        assertEquals(0, fake.calls)
    }

    @Test
    fun submit_blankTextShowsHintAndNeverCallsApi() = runVmTest {
        val fake = FakeDataSource(LinkedInAnalysisResult.Failure("should never run"))
        val vm = LinkedInCaptureViewModel(fake, isConfigured = true)
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.urlOnlyHint.isNotBlank())
        assertEquals(0, fake.calls)
    }

    @Test
    fun submit_notConfiguredBuildShowsErrorAndDoesNotCallApi() = runVmTest {
        val fake = FakeDataSource(LinkedInAnalysisResult.Failure("never"))
        val vm = LinkedInCaptureViewModel(fake, isConfigured = false)
        vm.onTextChange("Real text here.")
        vm.submit()
        advanceUntilIdle()

        assertEquals(LinkedInCaptureStage.EDITING, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.error?.contains("not configured") == true)
        assertEquals(0, fake.calls)
    }

    @Test
    fun submit_failurePostponesResultAndKeepsText() = runVmTest {
        val fake = FakeDataSource(LinkedInAnalysisResult.Failure("Could not analyse the post: boom"))
        val vm = LinkedInCaptureViewModel(fake, isConfigured = true)
        vm.onTextChange("A post.")
        vm.submit()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(LinkedInCaptureStage.EDITING, s.stage)
        assertEquals("Could not analyse the post: boom", s.error)
        assertEquals("A post.", s.rawText)
    }

    @Test
    fun retry_afterFailureGoesThroughAgain() = runVmTest {
        val fake = FakeDataSource(LinkedInAnalysisResult.Failure("first attempt failed"))
        val vm = LinkedInCaptureViewModel(fake, isConfigured = true)
        vm.onTextChange("A post.")
        vm.submit()
        advanceUntilIdle()
        assertEquals(1, fake.calls)

        fake.result = LinkedInAnalysisResult.Success(
            LinkedInAnalysis(action = LinkedInCapture.ACTION_REACTION_ONLY, selectedReaction = "LIKE")
        )
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, fake.calls)
        assertEquals(LinkedInCaptureStage.RESULT, vm.uiState.value.stage)
    }

    @Test
    fun reset_returnsToAEmptyEditingState() = runVmTest {
        val fake = FakeDataSource(
            LinkedInAnalysisResult.Success(LinkedInAnalysis(action = "IGNORE"))
        )
        val vm = LinkedInCaptureViewModel(fake, isConfigured = true)
        vm.onTextChange("A post.")
        vm.submit()
        advanceUntilIdle()
        assertEquals(LinkedInCaptureStage.RESULT, vm.uiState.value.stage)

        vm.reset()
        assertEquals(LinkedInCaptureStage.EDITING, vm.uiState.value.stage)
        assertEquals("", vm.uiState.value.rawText)
        assertNull(vm.uiState.value.result)
    }
}