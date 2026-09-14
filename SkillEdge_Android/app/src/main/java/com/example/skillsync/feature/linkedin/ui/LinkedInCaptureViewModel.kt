package com.example.skillsync.feature.linkedin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillsync.core.network.CaptureAnalysisRequestDto
import com.example.skillsync.core.network.LinkedInApiClient
import com.example.skillsync.feature.linkedin.data.LinkedInCaptureDataSource
import com.example.skillsync.feature.linkedin.data.LinkedInCaptureRepository
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysis
import com.example.skillsync.feature.linkedin.engine.LinkedInAnalysisResult
import com.example.skillsync.feature.linkedin.engine.LinkedInCapture
import com.example.skillsync.feature.linkedin.engine.LinkedInCaptureParser
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LinkedInCaptureStage { EDITING, RESULT }

/** Flat form-flow state, mirroring the Communication page convention. */
data class LinkedInCaptureUiState(
    val stage: LinkedInCaptureStage = LinkedInCaptureStage.EDITING,
    val captureMethod: String = LinkedInCapture.METHOD_PASTE,
    val sourceApp: String = LinkedInCapture.SOURCE_UNKNOWN,
    val rawText: String = "",
    val authorName: String = "",
    val postUrl: String = "",
    val urlOnlyHint: String = "",
    val analyzing: Boolean = false,
    val error: String? = null,
    val result: LinkedInAnalysis? = null,
) {
    val canSubmit: Boolean get() = rawText.isNotBlank()
}

/**
 * Human-in-the-loop capture flow: an editable preview of a LinkedIn post is
 * submitted to `/captures/analyse`, and the returned decision/comment is shown
 * for the user to copy. No capture history is kept (Phase 6B contract) — the
 * only trace is a metadata-only log line (id / length / hash).
 */
class LinkedInCaptureViewModel(
    private val dataSource: LinkedInCaptureDataSource = LinkedInCaptureRepository(),
    private val isConfigured: Boolean = LinkedInApiClient.isConfigured,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkedInCaptureUiState())
    val uiState: StateFlow<LinkedInCaptureUiState> = _uiState.asStateFlow()

    /** Cold entry from an `ACTION_SEND` share intent. */
    fun startShare(sharedText: String?, sourceAppHint: String? = null) {
        val normalized = LinkedInCaptureParser.normalizeText(sharedText)
        _uiState.value = draftState(LinkedInCapture.METHOD_SHARE_INTENT, normalized)
            .let { if (sourceAppHint.isNullOrBlank()) it else it.copy(sourceApp = sourceAppHint) }
    }

    /** Cold entry from the in-app Today brief action card. */
    fun startPaste(pastedText: String? = null) {
        val normalized = LinkedInCaptureParser.normalizeText(pastedText)
        _uiState.value = draftState(LinkedInCapture.METHOD_PASTE, normalized)
    }

    fun onTextChange(text: String) {
        val s = _uiState.value
        val normalized = LinkedInCaptureParser.normalizeText(text)
        val urlOnly = LinkedInCaptureParser.isUrlOnly(normalized)
        _uiState.value = s.copy(
            rawText = normalized,
            sourceApp = LinkedInCaptureParser.detectSourceApp(normalized),
            postUrl = if (s.postUrl.isBlank()) {
                LinkedInCaptureParser.extractFirstUrl(normalized).orEmpty()
            } else {
                s.postUrl
            },
            urlOnlyHint = if (urlOnly) LinkedInCapture.URL_ONLY_MESSAGE else "",
            error = null,
        )
    }

    fun onAuthorChange(name: String) {
        _uiState.value = _uiState.value.copy(authorName = name)
    }

    fun onUrlChange(url: String) {
        val s = _uiState.value
        val trimmed = url.trim()
        _uiState.value = if (trimmed.isNotEmpty()) {
            s.copy(
                postUrl = trimmed,
                sourceApp = if (trimmed.contains("linkedin.com", ignoreCase = true) ||
                    trimmed.contains("lnkd.in", ignoreCase = true)
                ) {
                    LinkedInCapture.SOURCE_LINKEDIN
                } else {
                    s.sourceApp
                },
            )
        } else {
            s.copy(
                postUrl = "",
                sourceApp = LinkedInCaptureParser.detectSourceApp(s.rawText),
            )
        }
    }

    fun submit() {
        val s = _uiState.value
        if (s.analyzing) return
        val text = s.rawText.trim()
        if (text.isBlank() || LinkedInCaptureParser.isUrlOnly(text)) {
            _uiState.value = s.copy(urlOnlyHint = LinkedInCapture.URL_ONLY_MESSAGE)
            return
        }
        if (!isConfigured) {
            _uiState.value = s.copy(
                error = "LinkedIn analysis is not configured on this build. Set the linkedinBackendBaseUrl property when building.",
            )
            return
        }
        _uiState.value = s.copy(analyzing = true, error = null, urlOnlyHint = "")
        viewModelScope.launch {
            val request = toRequest(_uiState.value)
            when (val outcome = dataSource.analyse(request)) {
                is LinkedInAnalysisResult.Success -> {
                    logMetadataOnly(outcome.analysis)
                    _uiState.value = _uiState.value.copy(
                        analyzing = false,
                        stage = LinkedInCaptureStage.RESULT,
                        result = outcome.analysis,
                        error = null,
                    )
                }
                is LinkedInAnalysisResult.UrlOnly -> {
                    _uiState.value = _uiState.value.copy(analyzing = false, urlOnlyHint = outcome.message)
                }
                is LinkedInAnalysisResult.Failure -> {
                    _uiState.value = _uiState.value.copy(analyzing = false, error = outcome.message)
                }
            }
        }
    }

    fun retry() = submit()

    fun backToEdit() {
        _uiState.value = _uiState.value.copy(
            stage = LinkedInCaptureStage.EDITING,
            analyzing = false,
            error = null,
            result = null,
        )
    }

    fun reset() {
        _uiState.value = LinkedInCaptureUiState()
    }

    private fun draftState(method: String, normalized: String): LinkedInCaptureUiState {
        val urlOnly = LinkedInCaptureParser.isUrlOnly(normalized)
        val url = LinkedInCaptureParser.extractFirstUrl(normalized).orEmpty()
        return LinkedInCaptureUiState(
            stage = LinkedInCaptureStage.EDITING,
            captureMethod = method,
            sourceApp = LinkedInCaptureParser.detectSourceApp(normalized),
            rawText = normalized,
            postUrl = url,
            urlOnlyHint = if (urlOnly) LinkedInCapture.URL_ONLY_MESSAGE else "",
            authorName = if (method == LinkedInCapture.METHOD_PASTE) _uiState.value.authorName else "",
        )
    }

    private fun toRequest(s: LinkedInCaptureUiState): CaptureAnalysisRequestDto =
        CaptureAnalysisRequestDto(
            capture = LinkedInCaptureParser.buildRequest(
                captureMethod = s.captureMethod,
                rawText = s.rawText,
                sourceApp = s.sourceApp,
                authorName = s.authorName,
                postUrl = s.postUrl,
                nowIso = Instant.now().toString(),
            ),
        )

    /** Metadata-only trace — capture id, length and hash; never post text. */
    private fun logMetadataOnly(analysis: LinkedInAnalysis) {
        val captureId = analysis.captureId
        if (captureId.isNullOrBlank()) return
        Log.v(TAG, "analysed capture_id=$captureId text_length=${analysis.textLength} content_hash=${analysis.contentHash}")
    }

    private companion object {
        const val TAG = "LinkedInCapture"
    }
}