package com.example.skillsync.feature.linkedin.data

import com.example.skillsync.core.common.userMessage
import com.example.skillsync.core.network.CaptureAnalysisRequestDto
import com.example.skillsync.core.network.LinkedInApi
import com.example.skillsync.core.network.LinkedInApiClient
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysisMapper
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysisResult
import retrofit2.HttpException

/**
 * Data boundary for the LinkedIn Capture workflow.
 *
 * Deliberately stateless (Phase 6B keeps no capture history) and thin: it only
 * translates transport errors into the [LinkedInAnalysisResult] sealed type.
 * HTTP 422 is the backend's contract for "only a link was shared" and maps to
 * [LinkedInAnalysisResult.UrlOnly]; everything else becomes a retry-able
 * [LinkedInAnalysisResult.Failure].
 */
interface LinkedInCaptureDataSource {
    suspend fun analyse(request: CaptureAnalysisRequestDto): LinkedInAnalysisResult
}

class LinkedInCaptureRepository(
    private val api: () -> LinkedInApi = { LinkedInApiClient.api },
) : LinkedInCaptureDataSource {

    override suspend fun analyse(request: CaptureAnalysisRequestDto): LinkedInAnalysisResult =
        try {
            val response = api().analyseCapture(request)
            LinkedInAnalysisResult.Success(LinkedInAnalysisMapper.map(response))
        } catch (e: HttpException) {
            if (e.code() == 422) {
                LinkedInAnalysisResult.UrlOnly(URL_ONLY_422_MESSAGE)
            } else {
                LinkedInAnalysisResult.Failure(e.userMessage("analyse the post"))
            }
        } catch (e: Exception) {
            LinkedInAnalysisResult.Failure(e.userMessage("analyse the post"))
        }

    private companion object {
        const val URL_ONLY_422_MESSAGE = "CAPTURE_TEXT_REQUIRED: LinkedIn shared only a post link. Paste or capture the visible post text before analysis."
    }
}