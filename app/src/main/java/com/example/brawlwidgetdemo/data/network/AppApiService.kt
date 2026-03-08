package com.example.brawlwidgetdemo.data.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface AppApiService {
    @GET("/api/players/{playerTag}")
    suspend fun getPlayer(@Path("playerTag") playerTag: String): Response<JsonObject>

    @GET("/api/events/rotation")
    suspend fun getEventsRotation(): Response<JsonArray>

    @GET("/api/gamemodes")
    suspend fun getGameModes(): Response<JsonObject>

    @GET("/api/maps/tracked/{modeKey}")
    suspend fun getTrackedMap(@Path("modeKey") modeKey: String): Response<JsonObject>

    @GET("/api/predictions/{modeKey}")
    suspend fun getPredictedMode(@Path("modeKey") modeKey: String): Response<JsonObject>

    @POST("/api/auth/register")
    suspend fun register(@Body body: AuthCredentialsRequest): Response<AuthResponse>

    @POST("/api/auth/login")
    suspend fun login(@Body body: AuthCredentialsRequest): Response<AuthResponse>

    @GET("/api/auth/me")
    suspend fun getCurrentAccount(@Header("Authorization") authorization: String): Response<AuthResponse>

    @POST("/api/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<LogoutResponse>

    @POST("/api/auth/link-tag")
    suspend fun linkTag(
        @Header("Authorization") authorization: String,
        @Body body: AuthTagRequest
    ): Response<AuthResponse>

    @POST("/api/auth/set-verified")
    suspend fun setVerified(
        @Header("Authorization") authorization: String,
        @Body body: AuthVerifiedRequest
    ): Response<AuthResponse>

    @GET("/health")
    suspend fun getHealth(@Query("ping") ping: String = "1"): Response<JsonObject>
}
