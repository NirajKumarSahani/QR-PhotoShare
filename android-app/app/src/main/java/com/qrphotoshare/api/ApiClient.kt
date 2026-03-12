package com.qrphotoshare.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Replace with your actual backend IP or domain
    // For local emulator: 10.0.2.2:3000
    // For physical device: Use computer's local IP (e.g., 192.168.1.x:3000)
    private const val BASE_URL = "https://qr-photoshare.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS) // Large files need longer write timeout
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
