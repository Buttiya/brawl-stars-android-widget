package com.example.brawlwidgetdemo.data.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface OfficialBrawlStarsService {
    @GET("/v1/players/%23{playerTag}")
    suspend fun getPlayer(@Path("playerTag") playerTag: String): Response<JsonObject>

    @GET("/v1/events/rotation")
    suspend fun getEventsRotation(): Response<JsonArray>
}
