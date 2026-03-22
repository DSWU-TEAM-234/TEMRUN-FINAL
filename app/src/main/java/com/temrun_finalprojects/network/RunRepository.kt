package com.temrun_finalprojects.network

import com.temrun_finalprojects.network.RunApi
import com.temrun_finalprojects.network.RetrofitProvider
import com.temrun_finalprojects.network.RunResultRequest
import retrofit2.Response

class RunRepository(
    private val api: RunApi = RetrofitProvider.retrofit.create(RunApi::class.java)
) {
    suspend fun saveRunResult(
        runId: String,
        req: RunResultRequest,
        bearerToken: String? = null
    ): Response<Unit> {
        val header = bearerToken?.let { "Bearer $it" }
        return api.saveRunResult(runId, req, header)
    }
}
