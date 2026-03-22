package com.temrun_finalprojects.network

import com.temrun_finalprojects.calendar.data.CalendarSummary
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("/api/users/{userId}/calendar")
    suspend fun getCalendar(
        @Path("userId") userId: String,
        @Query("month") month: String // "YYYY-MM"
    ): Response<CalendarSummary>
}
