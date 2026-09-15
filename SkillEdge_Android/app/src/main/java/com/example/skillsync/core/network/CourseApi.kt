package com.example.skillsync.core.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Course/curriculum-catalogue domain — see [com.example.skillsync.core.data.ManagerRepository]'s
 * `syllabus`/`searchCourses`/`courseIntelligence`/`courseCurriculum`. `ManagerRepository` composes
 * this alongside [SkillEdgeApi] rather than owning a dedicated small repository, since these reads
 * are consumed only as part of the manager-intelligence surface today.
 */
interface CourseApi {
    /** Syllabus lookup for one course. Returns { found, syllabus_url, ... }.
     *  RMS holds a link to a syllabus PDF, not table-of-contents content. */
    @GET("api/data/course-syllabus")
    suspend fun getCourseSyllabus(@Query("courseName") courseName: String): Map<String, Any>

    /** Full RMS catalogue search, including courses not mapped to this team. */
    @GET("api/data/course-search")
    suspend fun searchCourses(@Query("q") query: String): Map<String, Any>

    /** Verified catalogue metadata plus future public schedule dates. */
    @GET("api/data/course-intelligence")
    suspend fun getCourseIntelligence(@Query("courseName") courseName: String): Map<String, Any>

    /** V2 Course Curriculum: Modules, lab URLs, TOC, public schedules (Keys 206, 156, 246, 248) */
    @GET("api/v2/course/curriculum")
    suspend fun getCourseCurriculum(
        @Query("courseName") courseName: String = "",
        @Query("courseId") courseId: String = "",
    ): Map<String, Any>
}
