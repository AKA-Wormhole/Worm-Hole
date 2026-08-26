package com.wormhole.browser.core.weather

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    STORM,
    FOG,
    UNKNOWN;

    companion object {

        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> UNKNOWN
        }
    }
}

data class WeatherSnapshot(
    val condition: WeatherCondition,
    val temperatureCelsius: Double,
    val isDay: Boolean,
)

class WeatherClient(private val httpClient: OkHttpClient = defaultHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCurrent(latitude: Double, longitude: Double): WeatherSnapshot? =
        suspendCancellableCoroutine { continuation ->
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code,is_day" +
                "&timezone=auto"
            val request = Request.Builder().url(url).get().build()
            val call = httpClient.newCall(request)

            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) {
                            continuation.resume(null)
                            return
                        }
                        val body = it.body?.string().orEmpty()
                        val parsed = runCatching {
                            json.decodeFromString(OpenMeteoResponse.serializer(), body)
                        }.getOrNull()
                        val current = parsed?.current
                        if (current == null) {
                            continuation.resume(null)
                            return
                        }
                        continuation.resume(
                            WeatherSnapshot(
                                condition = WeatherCondition.fromWmoCode(current.weatherCode),
                                temperatureCelsius = current.temperature,
                                isDay = current.isDay == 1,
                            ),
                        )
                    }
                }
            })
        }

    companion object {
        val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
        }
    }
}

@Serializable
private data class OpenMeteoResponse(
    @SerialName("current") val current: CurrentBlock? = null,
)

@Serializable
private data class CurrentBlock(
    @SerialName("temperature_2m") val temperature: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0,
    @SerialName("is_day") val isDay: Int = 1,
)
