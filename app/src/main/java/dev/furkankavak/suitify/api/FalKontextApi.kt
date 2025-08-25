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
import com.google.gson.*
import java.lang.reflect.Type

object FalKontextApi {
    private const val BASE_URL = "https://fal.run/"
    

    const val DEFAULT_PROMPT = "Remove background. Keep face and identity unchanged, with natural head-body proportion. Add realistic dark suit, white shirt and plain tie. Make it look like a professional photo."

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

    private val gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .setPrettyPrinting()
        .registerTypeAdapter(Int::class.java, SafeIntegerDeserializer())
        .registerTypeAdapter(Integer::class.java, SafeIntegerDeserializer())
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val apiService = retrofit.create(FalApiService::class.java)

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
                guidanceScale = 3.5,
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
}


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


data class FalRequest(
    val prompt: String,
    val image_url: String,
    val guidance_scale: Double = 7.0,
    val num_images: Int = 1,
    val output_format: String = "jpeg",
    val safety_tolerance: String = "2",
    val enhance_prompt: Boolean = true,
    val sync_mode: Boolean = true,
    val webhook_url: String? = null,
    val image_size: String = "portrait_4_3"
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
            imageSize: String = "portrait_4_3"
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


data class KontextResult(
    val images: List<KontextImage> = emptyList(),
    val timings: Map<String, Any>? = null,
    val seed: Int? = null,
    val has_nsfw_concepts: List<Boolean>? = null
) {
    val hasNSFW: List<Boolean>? get() = has_nsfw_concepts
    
    companion object {
        fun fromMap(map: Map<String, Any>): KontextResult {
            return try {
                val images = parseImages(map["images"])
                val timings = parseTimings(map["timings"])
                val seed = safeToInt(map["seed"])
                val nsfwConcepts = parseNsfwConcepts(map["has_nsfw_concepts"])

                KontextResult(
                    images = images,
                    timings = timings,
                    seed = seed,
                    has_nsfw_concepts = nsfwConcepts
                )
            } catch (e: Exception) {
                KontextResult(
                    images = emptyList(),
                    timings = null,
                    seed = null,
                    has_nsfw_concepts = null
                )
            }
        }

        private fun parseImages(imagesValue: Any?): List<KontextImage> {
            return try {
                when (imagesValue) {
                    is List<*> -> {
                        imagesValue.mapNotNull { imageItem ->
                            when (imageItem) {
                                is Map<*, *> -> {
                                    val imageMap = imageItem as? Map<String, Any> ?: return@mapNotNull null
                                    KontextImage(
                                        url = imageMap["url"] as? String ?: "",
                                        width = safeToInt(imageMap["width"]),
                                        height = safeToInt(imageMap["height"]),
                                        content_type = imageMap["content_type"] as? String ?: "image/jpeg"
                                    )
                                }
                                else -> null
                            }
                        }
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun parseTimings(timingsValue: Any?): Map<String, Any>? {
            return try {
                timingsValue as? Map<String, Any>
            } catch (e: Exception) {
                null
            }
        }

        private fun parseNsfwConcepts(nsfwValue: Any?): List<Boolean>? {
            return try {
                when (nsfwValue) {
                    is List<*> -> {
                        nsfwValue.mapNotNull { it as? Boolean }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
        

        private fun safeToInt(value: Any?): Int {
            return try {
                when (value) {
                    is Int -> value
                    is Long -> value.toInt()
                    is Double -> {
                        if (value.isFinite()) value.toInt() else 0
                    }
                    is Float -> {
                        if (value.isFinite()) value.toInt() else 0
                    }
                    is String -> {
                        val cleanValue = value.trim()
                        when {
                            cleanValue.isEmpty() -> 0
                            cleanValue.equals("null", ignoreCase = true) -> 0
                            cleanValue.equals("undefined", ignoreCase = true) -> 0
                            cleanValue.equals("NaN", ignoreCase = true) -> 0
                            cleanValue.equals("Infinity", ignoreCase = true) -> 0
                            cleanValue.equals("-Infinity", ignoreCase = true) -> 0
                            else -> {
                                val doubleValue = cleanValue.toDoubleOrNull()
                                if (doubleValue != null && doubleValue.isFinite()) {
                                    doubleValue.toInt()
                                } else {
                                    0
                                }
                            }
                        }
                    }
                    null -> 0
                    else -> {
                        safeToInt(value.toString())
                    }
                }
            } catch (e: NumberFormatException) {
                0
            } catch (e: Exception) {
                0
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


class SafeIntegerDeserializer : JsonDeserializer<Int> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Int {
        return try {
            when {
                json == null || json.isJsonNull -> 0
                json.isJsonPrimitive -> {
                    val primitive = json.asJsonPrimitive
                    when {
                        primitive.isNumber -> {
                            val number = primitive.asNumber
                            when (number) {
                                is Int -> number
                                is Long -> number.toInt()
                                is Double -> if (number.isFinite()) number.toInt() else 0
                                is Float -> if (number.isFinite()) number.toInt() else 0
                                else -> number.toInt()
                            }
                        }
                        primitive.isString -> {
                            val stringValue = primitive.asString.trim()
                            when {
                                stringValue.isEmpty() -> 0
                                stringValue.equals("null", ignoreCase = true) -> 0
                                stringValue.equals("undefined", ignoreCase = true) -> 0
                                stringValue.equals("NaN", ignoreCase = true) -> 0
                                stringValue.equals("Infinity", ignoreCase = true) -> 0
                                stringValue.equals("-Infinity", ignoreCase = true) -> 0
                                else -> {
                                    val doubleValue = stringValue.toDoubleOrNull()
                                    if (doubleValue != null && doubleValue.isFinite()) {
                                        doubleValue.toInt()
                                    } else {
                                        0
                                    }
                                }
                            }
                        }
                        else -> 0
                    }
                }
                else -> 0
            }
        } catch (e: NumberFormatException) {
            0
        } catch (e: Exception) {
            0
        }
    }
}