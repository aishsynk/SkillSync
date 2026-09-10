package com.example.skillsync.data.network.generated

import com.google.gson.annotations.SerializedName

data class AddTrainerSkillIdpRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class AddTrainerSkillIdpResponse()

data class AddTrainerSkillIdpBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<AddTrainerSkillIdpResponse>? = null
)

data class AssignmentApiRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class AssignmentApiResponse()

data class AssignmentApiBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<AssignmentApiResponse>? = null
)

data class CheckCourseAvailabilityInRmsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class CheckCourseAvailabilityInRmsResponse()

data class CheckCourseAvailabilityInRmsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<CheckCourseAvailabilityInRmsResponse>? = null
)

data class CourseAndTechnologyListRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class CourseAndTechnologyListResponse()

data class CourseAndTechnologyListBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<CourseAndTechnologyListResponse>? = null
)

data class CourseListRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class CourseListResponse()

data class CourseListBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<CourseListResponse>? = null
)

data class CourseWhitoutExamRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class CourseWhitoutExamResponse()

data class CourseWhitoutExamBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<CourseWhitoutExamResponse>? = null
)

data class ExamCourseLinkedApiRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class ExamCourseLinkedApiResponse()

data class ExamCourseLinkedApiBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<ExamCourseLinkedApiResponse>? = null
)

data class GetActiveScDateRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetActiveScDateResponse()

data class GetActiveScDateBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetActiveScDateResponse>? = null
)

data class GetAssignmentPaxRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetAssignmentPaxResponse()

data class GetAssignmentPaxBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetAssignmentPaxResponse>? = null
)

data class GetCourseAndDomainRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetCourseAndDomainResponse()

data class GetCourseAndDomainBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetCourseAndDomainResponse>? = null
)

data class GetCourseContentUrlRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetCourseContentUrlResponse()

data class GetCourseContentUrlBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetCourseContentUrlResponse>? = null
)

data class GetCourseModuleRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetCourseModuleResponse()

data class GetCourseModuleBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetCourseModuleResponse>? = null
)

data class GetCourseNameRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetCourseNameResponse()

data class GetCourseNameBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetCourseNameResponse>? = null
)

data class GetCourseScheduleRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetCourseScheduleResponse()

data class GetCourseScheduleBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetCourseScheduleResponse>? = null
)

data class GetCourseSyllabusTocRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetCourseSyllabusTocResponse()

data class GetCourseSyllabusTocBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetCourseSyllabusTocResponse>? = null
)

data class GetDirectIndirectReporteeRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetDirectIndirectReporteeResponse()

data class GetDirectIndirectReporteeBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetDirectIndirectReporteeResponse>? = null
)

data class GetHrIncidentPositiveNegativeRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetHrIncidentPositiveNegativeResponse()

data class GetHrIncidentPositiveNegativeBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetHrIncidentPositiveNegativeResponse>? = null
)

data class GetInhouseAndFlTrainersOfCoursesRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetInhouseAndFlTrainersOfCoursesResponse()

data class GetInhouseAndFlTrainersOfCoursesBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetInhouseAndFlTrainersOfCoursesResponse>? = null
)

data class GetLatestVersionOfCoursesRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetLatestVersionOfCoursesResponse()

data class GetLatestVersionOfCoursesBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetLatestVersionOfCoursesResponse>? = null
)

data class GetNegativeFeedbackCountRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetNegativeFeedbackCountResponse()

data class GetNegativeFeedbackCountBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetNegativeFeedbackCountResponse>? = null
)

data class GetRecordingDetailsByAssignmentIdRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetRecordingDetailsByAssignmentIdResponse()

data class GetRecordingDetailsByAssignmentIdBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetRecordingDetailsByAssignmentIdResponse>? = null
)

data class GetScidRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetScidResponse()

data class GetScidBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetScidResponse>? = null
)

data class GetTrainerDetailsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetTrainerDetailsResponse()

data class GetTrainerDetailsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetTrainerDetailsResponse>? = null
)

data class GetTrainerFeedbackDetailsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetTrainerFeedbackDetailsResponse()

data class GetTrainerFeedbackDetailsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetTrainerFeedbackDetailsResponse>? = null
)

data class GetTrainerFreeSheduleAndDetailsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetTrainerFreeSheduleAndDetailsResponse()

data class GetTrainerFreeSheduleAndDetailsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetTrainerFreeSheduleAndDetailsResponse>? = null
)

data class GetTrainerNegativeFeedbackRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetTrainerNegativeFeedbackResponse()

data class GetTrainerNegativeFeedbackBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetTrainerNegativeFeedbackResponse>? = null
)

data class GetTrainerSkillsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetTrainerSkillsResponse()

data class GetTrainerSkillsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetTrainerSkillsResponse>? = null
)

data class GetTrainerVenderCertificationCountRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetTrainerVenderCertificationCountResponse()

data class GetTrainerVenderCertificationCountBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetTrainerVenderCertificationCountResponse>? = null
)

data class GetUniqueCertificationsCountValueRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetUniqueCertificationsCountValueResponse()

data class GetUniqueCertificationsCountValueBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetUniqueCertificationsCountValueResponse>? = null
)

data class GetUtilizationRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class GetUtilizationResponse()

data class GetUtilizationBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<GetUtilizationResponse>? = null
)

data class PreviousAndUpcommingAssignmentsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class PreviousAndUpcommingAssignmentsResponse()

data class PreviousAndUpcommingAssignmentsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<PreviousAndUpcommingAssignmentsResponse>? = null
)

data class TrainerAvailabilityRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class TrainerAvailabilityResponse()

data class TrainerAvailabilityBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<TrainerAvailabilityResponse>? = null
)

data class TrainerLast3MonthsUtilizationRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class TrainerLast3MonthsUtilizationResponse()

data class TrainerLast3MonthsUtilizationBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<TrainerLast3MonthsUtilizationResponse>? = null
)

data class TrainerResumeDetailsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class TrainerResumeDetailsResponse()

data class TrainerResumeDetailsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<TrainerResumeDetailsResponse>? = null
)

data class UnallocatedAssignmentRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class UnallocatedAssignmentResponse()

data class UnallocatedAssignmentBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<UnallocatedAssignmentResponse>? = null
)

data class UpcomingAssignmentsRequest(
    @SerializedName("userName") val userName: String = "",
    @SerializedName("userPassword") val userPassword: String = "",
    @SerializedName("userRole") val userRole: String = "",
)

class UpcomingAssignmentsResponse()

data class UpcomingAssignmentsBaseResponse(
    @SerializedName("statuscode") val statuscode: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: List<UpcomingAssignmentsResponse>? = null
)

