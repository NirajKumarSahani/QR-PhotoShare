package com.qrphotoshare.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class UploadResponse(
    val success: Boolean,
    val sessionId: String?,
    val downloadUrl: String?,
    val qrCode: String?,
    val expiresAt: String?,
    val error: String?
)

interface ApiService {
    @Multipart
    @POST("api/upload")
    suspend fun uploadPhotos(
        @Part photos: List<MultipartBody.Part>
    ): Response<UploadResponse>
}
