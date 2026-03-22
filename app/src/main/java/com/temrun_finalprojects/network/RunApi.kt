package com.temrun_finalprojects.network

import com.temrun_finalprojects.network.RunResultRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface RunApi {
    /**
     * 러닝 결과 저장
     * POST /api/runs/{runId}/results
     * @param auth 필요하면 "Bearer xxx" 형태로 전달(없으면 null)
     */
    @POST("/api/runs/{runId}/results")
    suspend fun saveRunResult(
        @Path("runId") runId: String,
        @Body body: RunResultRequest,
        @Header("Authorization") auth: String? = null
    ): Response<Unit>
}