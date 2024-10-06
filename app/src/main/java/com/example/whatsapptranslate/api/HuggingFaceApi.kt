package com.example.whatsapptranslate.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface HuggingFaceApi {
    @POST("models/Helsinki-NLP/opus-mt-en-es")
    fun translate(
        @Header("Authorization") token: String,
        @Body input: Map<String, List<String>>
    ): Call<List<Map<String, String>>>
}
