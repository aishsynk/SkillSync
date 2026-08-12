package com.example.skillsync.data.network.generated

import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Body
import com.google.gson.annotations.SerializedName

interface GeneratedApiService {
    @POST("api/Kites/Operator/common")
    suspend fun addTrainerSkillIdp(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: AddTrainerSkillIdpRequest
    ): AddTrainerSkillIdpBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun assignmentApi(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: AssignmentApiRequest
    ): AssignmentApiBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun checkCourseAvailabilityInRms(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: CheckCourseAvailabilityInRmsRequest
    ): CheckCourseAvailabilityInRmsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun checkCourseAvailabilityInRms2(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: CheckCourseAvailabilityInRmsRequest
    ): CheckCourseAvailabilityInRmsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun courseAndTechnologyList(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: CourseAndTechnologyListRequest
    ): CourseAndTechnologyListBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun courseList(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: CourseListRequest
    ): CourseListBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun courseWhitoutExam(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: CourseWhitoutExamRequest
    ): CourseWhitoutExamBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun examCourseLinkedApi(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: ExamCourseLinkedApiRequest
    ): ExamCourseLinkedApiBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getActiveScDate(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetActiveScDateRequest
    ): GetActiveScDateBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getAssignmentPax(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetAssignmentPaxRequest
    ): GetAssignmentPaxBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getCourseAndDomain(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetCourseAndDomainRequest
    ): GetCourseAndDomainBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getCourseContentUrl(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetCourseContentUrlRequest
    ): GetCourseContentUrlBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getCourseModule(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetCourseModuleRequest
    ): GetCourseModuleBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getCourseName(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetCourseNameRequest
    ): GetCourseNameBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getCourseSchedule(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetCourseScheduleRequest
    ): GetCourseScheduleBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getCourseSyllabusToc(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetCourseSyllabusTocRequest
    ): GetCourseSyllabusTocBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getDirectIndirectReportee(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetDirectIndirectReporteeRequest
    ): GetDirectIndirectReporteeBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getHrIncidentPositiveNegative(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetHrIncidentPositiveNegativeRequest
    ): GetHrIncidentPositiveNegativeBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getInhouseAndFlTrainersOfCourses(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetInhouseAndFlTrainersOfCoursesRequest
    ): GetInhouseAndFlTrainersOfCoursesBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getLatestVersionOfCourses(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetLatestVersionOfCoursesRequest
    ): GetLatestVersionOfCoursesBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getNegativeFeedbackCount(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetNegativeFeedbackCountRequest
    ): GetNegativeFeedbackCountBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getRecordingDetailsByAssignmentId(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetRecordingDetailsByAssignmentIdRequest
    ): GetRecordingDetailsByAssignmentIdBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getScid(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetScidRequest
    ): GetScidBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getTrainerDetails(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetTrainerDetailsRequest
    ): GetTrainerDetailsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getTrainerFeedbackDetails(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetTrainerFeedbackDetailsRequest
    ): GetTrainerFeedbackDetailsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getTrainerFreeSheduleAndDetails(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetTrainerFreeSheduleAndDetailsRequest
    ): GetTrainerFreeSheduleAndDetailsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getTrainerNegativeFeedback(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetTrainerNegativeFeedbackRequest
    ): GetTrainerNegativeFeedbackBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getTrainerSkills(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetTrainerSkillsRequest
    ): GetTrainerSkillsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getTrainerVenderCertificationCount(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetTrainerVenderCertificationCountRequest
    ): GetTrainerVenderCertificationCountBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getUniqueCertificationsCountValue(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetUniqueCertificationsCountValueRequest
    ): GetUniqueCertificationsCountValueBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun getUtilization(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: GetUtilizationRequest
    ): GetUtilizationBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun previousAndUpcommingAssignments(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: PreviousAndUpcommingAssignmentsRequest
    ): PreviousAndUpcommingAssignmentsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun trainerAvailability(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: TrainerAvailabilityRequest
    ): TrainerAvailabilityBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun trainerLast3MonthsUtilization(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: TrainerLast3MonthsUtilizationRequest
    ): TrainerLast3MonthsUtilizationBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun trainerResumeDetails(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: TrainerResumeDetailsRequest
    ): TrainerResumeDetailsBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun unallocatedAssignment(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: UnallocatedAssignmentRequest
    ): UnallocatedAssignmentBaseResponse

    @POST("api/Kites/Operator/common")
    suspend fun upcomingAssignments(
        @Query("apikey") apiKey: String,
        @Query("accessToken") accessToken: String,
        @Query("deviceToken") deviceToken: String,
        @Body request: UpcomingAssignmentsRequest
    ): UpcomingAssignmentsBaseResponse

}
