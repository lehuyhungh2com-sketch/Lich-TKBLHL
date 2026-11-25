package com.example.data.remote

import androidx.annotation.DrawableRes
import com.example.R
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class OpenMeteoResponse(
    val current: OpenMeteoCurrent?,
    val daily: OpenMeteoDaily?
)

data class OpenMeteoCurrent(
    @Json(name = "temperature_2m") val temperature2m: Double?,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int?,
    @Json(name = "apparent_temperature") val apparentTemperature: Double?,
    @Json(name = "weather_code") val weatherCode: Int?,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double?,
    @Json(name = "is_day") val isDay: Int?
)

data class OpenMeteoDaily(
    val time: List<String>?,
    @Json(name = "weather_code") val weatherCode: List<Int>?,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>?,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>?,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>?
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("timezone") timezone: String = "Asia/Ho_Chi_Minh",
        @Query("forecast_days") forecastDays: Int = 4
    ): OpenMeteoResponse

    companion object {
        fun create(): OpenMeteoApi {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val okHttp = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OpenMeteoApi::class.java)
        }
    }
}

object WeatherCodeMapper {

    fun descriptionVietnamese(code: Int): String = when (code) {
        0 -> "Trời quang"
        1 -> "Ít mây"
        2 -> "Mây rải rác"
        3 -> "Nhiều mây"
        45, 48 -> "Có sương mù"
        51, 53, 55 -> "Mưa phùn"
        56, 57 -> "Mưa phùn lạnh"
        61 -> "Mưa nhẹ"
        63 -> "Mưa vừa"
        65 -> "Mưa to"
        66, 67 -> "Mưa lạnh"
        71, 73, 75, 77 -> "Tuyết nhẹ"
        80 -> "Mưa rào nhẹ"
        81 -> "Mưa rào vừa"
        82 -> "Mưa rào to"
        95 -> "Có dông"
        96, 99 -> "Dông lốc mạnh"
        else -> "Nhiều mây"
    }

    @DrawableRes
    fun widgetIconRes(code: Int): Int = when (code) {
        0 -> R.drawable.ic_weather_sunny
        1, 2 -> R.drawable.ic_weather_partly_cloudy
        3, 45, 48 -> R.drawable.ic_weather_cloudy
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
        95, 96, 99 -> R.drawable.ic_weather_storm
        else -> R.drawable.ic_weather_cloudy
    }
}
