package com.example.brawlwidgetdemo.data.network

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface BrawlApiService {
    @GET("/v1/graphs/player/{playerTag}")
    suspend fun getPlayerGraphs(@Path("playerTag") playerTag: String): Response<JsonObject>

    @GET("/v1/events")
    suspend fun getEvents(): Response<JsonObject>

    @GET("/v1/icons")
    suspend fun getIcons(): Response<JsonObject>
}
