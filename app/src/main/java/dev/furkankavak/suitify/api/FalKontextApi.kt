package dev.furkankavak.suitify.api

import android.util.Base64
import dev.furkankavak.suitify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import retrofit2.Response

object FalKontextApi {
    private const val BASE_URL = "https://fal.run/"
    private const val MODEL_ID = "fal-ai/flux-pro/kontext"
    
    // Optimize edilmiş vesikalık fotoğraf promptu
    const val PASSPORT_PHOTO_PROMPT = "Professional passport photo with SMALL HEAD and natural lighting: " +
            "HEAD SIZE (CRITICAL - MULTIPLE EMPHASIS): " +
            "- Head MAXIMUM 45% of total frame height " +
            "- SIGNIFICANTLY smaller head than typical photos " +
            "- Compact head proportions for passport standards " +
            "- REDUCE head size, do NOT enlarge " +
            "- Head should appear SMALL in the frame " +
            "- TINY head relative to body and background " +
            "LIGHTING (KEEP CURRENT QUALITY): " +
            "- Natural matte finish, no shine or reflection " +
            "- Flat even lighting like reference photo " +
            "- No artificial enhancement or gloss " +
            "BASIC REQUIREMENTS: " +
            "- Extract person, remove background " +
            "- Add dark business suit, white shirt, dark tie " +
            "- Frontal passport angle " +
            "- Clean gray background " +
            "- Preserve facial identity " +
            "- ONLY the subject person, no other objects or items in the frame"
    
    // API Key - BuildConfig'ten güvenli bir şekilde alınıyor
    // FAL.AI resmi dokümantasyonuna göre: export FAL_KEY="YOUR_API_KEY"
    // Android'de environment variable yerine BuildConfig kullanıyoruz
    private val API_KEY = BuildConfig.FAL_KEY

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Key $API_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val gson = com.google.gson.GsonBuilder()
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val apiService = retrofit.create(FalApiService::class.java)

    // Direkt istek gönder ve sonucu al
    suspend fun processImage(
        imageData: ByteArray,
        prompt: String
    ): Result<KontextResult> = withContext(Dispatchers.IO) {
        try {
            val base64Image = "data:image/jpeg;base64," +
                    Base64.encodeToString(imageData, Base64.NO_WRAP)

            val request = FalRequest.create(
                prompt = prompt,
                imageUrl = base64Image,
                guidanceScale = 7.0,
                numImages = 1,
                outputFormat = "jpeg",
                safetyTolerance = "2",
                enhancePrompt = true
            )

            val response = apiService.processImage(request)
            
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    Result.success(result)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Queue ile asenkron istek gönder
    suspend fun submitImageRequest(
        imageData: ByteArray,
        prompt: String,
        webhookUrl: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Image = "data:image/jpeg;base64," +
                    Base64.encodeToString(imageData, Base64.NO_WRAP)

            val request = FalRequest.create(
                prompt = prompt,
                imageUrl = base64Image,
                guidanceScale = 3.5,
                numImages = 1,
                outputFormat = "jpeg",
                safetyTolerance = "2",
                enhancePrompt = true,
                webhookUrl = webhookUrl
            )

            val response = apiService.submitRequest(request)
            
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    Result.success(result.requestId)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // İstek durumunu kontrol et
    suspend fun checkRequestStatus(
        requestId: String
    ): Result<RequestStatus> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRequestStatus(requestId)
            
            if (response.isSuccessful) {
                response.body()?.let { status ->
                    Result.success(status)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Tamamlanan isteğin sonucunu al
    suspend fun getRequestResult(
        requestId: String
    ): Result<KontextResult> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRequestResult(requestId)
            
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    Result.success(result)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Geriye dönük uyumluluk için
    suspend fun processImageWithSubscribe(
        imageData: ByteArray,
        prompt: String,
        onProgress: (String) -> Unit = {}
    ): Result<KontextResult> = processImage(imageData, prompt)
}

// Retrofit API Service Interface
interface FalApiService {
    @POST("fal-ai/flux-pro/kontext")
    suspend fun processImage(
        @Body request: FalRequest
    ): Response<KontextResult>

    @POST("fal-ai/flux-pro/kontext")
    suspend fun submitRequest(
        @Body request: FalRequest
    ): Response<SubmitResponse>

    @GET("fal-ai/flux-pro/kontext/requests/{requestId}/status")
    suspend fun getRequestStatus(
        @Path("requestId") requestId: String
    ): Response<RequestStatus>

    @GET("fal-ai/flux-pro/kontext/requests/{requestId}")
    suspend fun getRequestResult(
        @Path("requestId") requestId: String
    ): Response<KontextResult>
}

// Request Data Classes
data class FalRequest(
    val prompt: String,
    val image_url: String,
    val guidance_scale: Double = 3.5,
    val num_images: Int = 1,
    val output_format: String = "jpeg",
    val safety_tolerance: String = "2",
    val enhance_prompt: Boolean = true,
    val sync_mode: Boolean = true,  // Hemen sonuç almak için true
    val webhook_url: String? = null,
    val image_size: String = "landscape_4_3"  // Varsayılan 4:3 aspect ratio
) {
    companion object {
        fun create(
            prompt: String,
            imageUrl: String,
            guidanceScale: Double = 3.5,
            numImages: Int = 1,
            outputFormat: String = "jpeg",
            safetyTolerance: String = "2",
            enhancePrompt: Boolean = true,
            syncMode: Boolean = true,
            webhookUrl: String? = null,
            imageSize: String = "landscape_4_3"
        ) = FalRequest(
            prompt = prompt,
            image_url = imageUrl,
            guidance_scale = guidanceScale,
            num_images = numImages,
            output_format = outputFormat,
            safety_tolerance = safetyTolerance,
            enhance_prompt = enhancePrompt,
            sync_mode = syncMode,
            webhook_url = webhookUrl,
            image_size = imageSize
        )
    }
}

data class SubmitResponse(
    val request_id: String
) {
    val requestId: String get() = request_id
}

// Response Data Classes
data class KontextResult(
    val images: List<KontextImage> = emptyList(),
    val timings: Map<String, Any>? = null,
    val seed: Int? = null,
    val has_nsfw_concepts: List<Boolean>? = null
) {
    val hasNSFW: List<Boolean>? get() = has_nsfw_concepts
    
    companion object {
        fun fromMap(map: Map<String, Any>): KontextResult {
            val images = (map["images"] as? List<Map<String, Any>>)?.map { imageMap ->
                KontextImage(
                    url = imageMap["url"] as? String ?: "",
                    width = safeToInt(imageMap["width"]),
                    height = safeToInt(imageMap["height"]),
                    content_type = imageMap["content_type"] as? String ?: "image/jpeg"
                )
            } ?: emptyList()

            return KontextResult(
                images = images,
                timings = map["timings"] as? Map<String, Any>,
                seed = safeToInt(map["seed"]),
                has_nsfw_concepts = map["has_nsfw_concepts"] as? List<Boolean>
            )
        }
        
        // Güvenli integer dönüşümü için yardımcı fonksiyon
        private fun safeToInt(value: Any?): Int {
            return when (value) {
                is Int -> value
                is Double -> value.toInt()
                is Float -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
        }
    }
}

data class KontextImage(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val content_type: String = "image/jpeg"
) {
    val contentType: String get() = content_type
}

data class RequestStatus(
    val status: String,
    val logs: List<String>? = null,
    val completed_at: String? = null
) {
    val isCompleted: Boolean get() = status.lowercase() == "completed"
    val logsText: String? get() = logs?.joinToString("\n")
}