package com.example.brawlwidgetdemo.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkFactory {
    private const val OFFICIAL_BASE_URL = "https://api.brawlstars.com"

    fun createOfficialRetrofit(token: String): Retrofit = createRetrofit(OFFICIAL_BASE_URL, token)

    private fun createRetrofit(baseUrl: String, bearerToken: String?): Retrofit {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("User-Agent", "BrawlWidgetDemo/0.2.1")

                if (!bearerToken.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $bearerToken")
                }

                chain.proceed(builder.build())
            }
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }
}
