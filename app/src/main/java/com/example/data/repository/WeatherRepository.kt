package com.example.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.AppPreferences
import com.example.data.local.SchoolDao
import com.example.data.local.UserPreferencesState
import com.example.data.local.toEntity
import com.example.data.remote.OpenMeteoApi
import com.example.domain.lunar.VnDate
import com.example.domain.model.DailyForecast
import com.example.domain.model.WeatherSnapshot
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class WeatherRepository(
    private val context: Context,
    private val dao: SchoolDao,
    private val api: OpenMeteoApi,
    private val preferences: AppPreferences
) {

    val weatherFlow: Flow<WeatherSnapshot?> = dao.observeWeatherCache()
        .map { it?.toDomain() }

    suspend fun getCachedWeather(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        dao.getWeatherCache()?.toDomain()
    }

    suspend fun refreshWeather(): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferences.getSnapshot()
            var lat = UserPreferencesState.DEFAULT_LAT
            var lon = UserPreferencesState.DEFAULT_LON
            var locName = UserPreferencesState.DEFAULT_LOCATION_NAME

            if (prefs.useGpsLocation && hasLocationPermission()) {
                val gpsLoc = fetchCurrentLocationOrNull()
                if (gpsLoc != null) {
                    lat = gpsLoc.first
                    lon = gpsLoc.second
                    locName = reverseGeocodeName(lat, lon) ?: prefs.lastLocationName
                    preferences.saveResolvedLocation(lat, lon, locName)
                } else {
                    lat = prefs.lastLatitude
                    lon = prefs.lastLongitude
                    locName = prefs.lastLocationName
                }
            }

            val response = api.getForecast(latitude = lat, longitude = lon)
            val current = response.current
                ?: throw IllegalStateException("Không nhận được dữ liệu thời tiết hiện tại.")
            val daily = response.daily

            val dates = daily?.time.orEmpty()
            val codes = daily?.weatherCode.orEmpty()
            val highs = daily?.temperature2mMax.orEmpty()
            val lows = daily?.temperature2mMin.orEmpty()
            val rainProbs = daily?.precipitationProbabilityMax.orEmpty()

            val forecasts = dates.indices.mapNotNull { idx ->
                val date = VnDate.parseIso(dates[idx]) ?: return@mapNotNull null
                DailyForecast(
                    date = date,
                    weatherCode = codes.getOrNull(idx) ?: (current.weatherCode ?: 3),
                    tempMax = (highs.getOrNull(idx) ?: current.temperature2m ?: 24.0).roundToInt(),
                    tempMin = (lows.getOrNull(idx) ?: current.temperature2m ?: 18.0).roundToInt(),
                    precipitationProbability = rainProbs.getOrNull(idx) ?: 0
                )
            }

            val todayForecast = forecasts.firstOrNull()
            val snapshot = WeatherSnapshot(
                locationName = locName,
                latitude = lat,
                longitude = lon,
                currentTemp = (current.temperature2m ?: 22.0).roundToInt(),
                feelsLike = (current.apparentTemperature ?: current.temperature2m ?: 22.0).roundToInt(),
                humidity = current.relativeHumidity2m ?: 65,
                windSpeedKmh = (current.windSpeed10m ?: 8.0).roundToInt(),
                weatherCode = current.weatherCode ?: 3,
                isDay = (current.isDay ?: 1) == 1,
                todayHigh = todayForecast?.tempMax ?: (current.temperature2m ?: 25.0).roundToInt(),
                todayLow = todayForecast?.tempMin ?: (current.temperature2m ?: 18.0).roundToInt(),
                todayRainProb = todayForecast?.precipitationProbability ?: 0,
                dailyForecasts = forecasts,
                updatedAtMillis = System.currentTimeMillis()
            )

            dao.upsertWeatherCache(snapshot.toEntity())
            snapshot
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocationOrNull(): Pair<Double, Double>? {
        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .await()
                ?: client.lastLocation.await()
            loc?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeName(lat: Double, lon: Double): String? {
        return runCatching {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result: List<android.location.Address>? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                geocoder.getFromLocation(lat, lon, 1) { list ->
                    result = list
                    latch.countDown()
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                result
            } else {
                geocoder.getFromLocation(lat, lon, 1)
            }
            val addr = addresses?.firstOrNull() ?: return null
            addr.subAdminArea?.takeIf { it.isNotBlank() }
                ?: addr.locality?.takeIf { it.isNotBlank() }
                ?: addr.adminArea?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
