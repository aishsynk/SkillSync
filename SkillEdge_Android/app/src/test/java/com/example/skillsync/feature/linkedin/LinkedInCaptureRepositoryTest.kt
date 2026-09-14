package com.example.skillsync.feature.linkedin

import com.example.skillsync.core.network.CaptureAnalysisRequestDto
import com.example.skillsync.core.network.CaptureAnalyzeResponseDto
import com.example.skillsync.core.network.CaptureMetadataDto
import com.example.skillsync.core.network.CapturedPostDto
import com.example.skillsync.core.network.EngagementDecisionDto
import com.example.skillsync.core.network.LinkedInApi
import com.example.skillsync.feature.linkedin.data.LinkedInCaptureRepository
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysisResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LinkedInCaptureRepositoryTest {

    private val request = CaptureAnalysisRequestDto(
        capture = CapturedPostDto(
            capture_method = "PASTE",
            source_app = "LINKEDIN",
            raw_text = "A post with real text.",
        ),
    )

    private fun successResponse() = CaptureAnalyzeResponseDto(
        capture = CaptureMetadataDto(capture_id = "capture_7", text_length = 22, content_hash = "h1"),
        decision = EngagementDecisionDto(action = "IGNORE", should_comment = false),
    )

    @Test
    fun success_mapsToSuccessAnalysis() = runTest {
        val repo = LinkedInCaptureRepository(api = { FakeApi(successResponse()) })
        val result = repo.analyse(request)
        assertTrue(result is LinkedInAnalysisResult.Success)
        assertEquals("capture_7", (result as LinkedInAnalysisResult.Success).analysis.captureId)
    }

    @Test
    fun http422_mapsToUrlOnlyWithContractMessage() = runTest {
        val body = "".toResponseBody("application/json".toMediaType())
        val http = HttpException(Response.error<CaptureAnalyzeResponseDto>(422, body))
        val repo = LinkedInCaptureRepository(api = { throw http })
        val result = repo.analyse(request)
        assertTrue(result is LinkedInAnalysisResult.UrlOnly)
        val urlOnly = result as LinkedInAnalysisResult.UrlOnly
        assertTrue(urlOnly.message.startsWith("CAPTURE_TEXT_REQUIRED"))
        assertEquals(
            "CAPTURE_TEXT_REQUIRED: LinkedIn shared only a post link. Paste or capture the visible post text before analysis.",
            urlOnly.message,
        )
    }

    @Test
    fun http500_mapsToFailureWithHttpCode() = runTest {
        val body = "".toResponseBody("application/json".toMediaType())
        val http = HttpException(Response.error<CaptureAnalyzeResponseDto>(500, body))
        val repo = LinkedInCaptureRepository(api = { throw http })
        val result = repo.analyse(request)
        assertTrue(result is LinkedInAnalysisResult.Failure)
        assertTrue((result as LinkedInAnalysisResult.Failure).message.contains("500"))
    }

    @Test
    fun transportError_mapsToFailure() = runTest {
        val repo = LinkedInCaptureRepository(api = { throw IOException("socket closed") })
        val result = repo.analyse(request)
        assertTrue(result is LinkedInAnalysisResult.Failure)
        assertTrue((result as LinkedInAnalysisResult.Failure).message.contains("socket closed"))
    }

    private class FakeApi(private val response: CaptureAnalyzeResponseDto) : LinkedInApi {
        override suspend fun analyseCapture(body: CaptureAnalysisRequestDto): CaptureAnalyzeResponseDto = response
    }
}
