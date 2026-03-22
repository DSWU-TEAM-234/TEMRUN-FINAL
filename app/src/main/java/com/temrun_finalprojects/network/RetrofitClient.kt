package com.temrun_finalprojects.network

import com.google.gson.GsonBuilder
import com.temrun_finalprojects.util.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // (선택) 토큰이 필요하면 여기서 헤더 주입
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        // val token = /* SharedPreferences 등에서 읽기 */
        // if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        chain.proceed(builder.build())
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .build()

    private val gson = GsonBuilder().create()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}
