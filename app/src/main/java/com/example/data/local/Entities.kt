package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.lunar.VnDate
import com.example.domain.model.Assignment
import com.example.domain.model.DailyForecast
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.Subject
import com.example.domain.model.SyncMetadata
import com.example.domain.model.Teacher
import com.example.domain.model.TimeConfig
import com.example.domain.model.TimetableSlot
import com.example.domain.model.WeatherSnapshot
import com.example.domain.model.WeekInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LocalJson {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapStringStringAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )
    private val listStringAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )
    private val listSubjectAdapter = moshi.adapter<List<Subject>>(
        Types.newParameterizedType(List::class.java, Subject::class.java)
    )
    private val listAssignmentAdapter = moshi.adapter<List<Assignment>>(
        Types.newParameterizedType(List::class.java, Assignment::class.java)
    )
    private val listForecastAdapter = moshi.adapter<List<DailyForecastDto>>(
        Types.newParameterizedType(List::class.java, DailyForecastDto::class.java)
    )

    fun mapToJson(map: Map<String, String>): String = mapStringStringAdapter.toJson(map)
    fun jsonToMap(json: String?): Map<String, String> =
        if (json.isNullOrBlank()) emptyMap() else runCatching { mapStringStringAdapter.fromJson(json) }.getOrNull().orEmpty()

    fun stringListToJson(list: List<String>): String = listStringAdapter.toJson(list)
    fun jsonToStringList(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList() else runCatching { listStringAdapter.fromJson(json) }.getOrNull().orEmpty()

    fun subjectsToJson(list: List<Subject>?): String? = list?.let { listSubjectAdapter.toJson(it) }
    fun jsonToSubjects(json: String?): List<Subject>? =
        if (json.isNullOrBlank()) null else runCatching { listSubjectAdapter.fromJson(json) }.getOrNull()

    fun assignmentsToJson(list: List<Assignment>?): String? = list?.let { listAssignmentAdapter.toJson(it) }
    fun jsonToAssignments(json: String?): List<Assignment>? =
        if (json.isNullOrBlank()) null else runCatching { listAssignmentAdapter.fromJson(json) }.getOrNull()

    fun forecastsToJson(list: List<DailyForecast>): String {
        val dtos = list.map {
            DailyForecastDto(
                isoDate = it.date.toIsoString(),
                weatherCode = it.weatherCode,
                tempMax = it.tempMax,
                tempMin = it.tempMin,
                precipitationProbability = it.precipitationProbability
            )
        }
        return listForecastAdapter.toJson(dtos)
    }

    fun jsonToForecasts(json: String?): List<DailyForecast> {
        if (json.isNullOrBlank()) return emptyList()
        val dtos = runCatching { listForecastAdapter.fromJson(json) }.getOrNull().orEmpty()
        return dtos.mapNotNull { dto ->
            val date = VnDate.parseIso(dto.isoDate) ?: return@mapNotNull null
            DailyForecast(
                date = date,
                weatherCode = dto.weatherCode,
                tempMax = dto.tempMax,
                tempMin = dto.tempMin,
                precipitationProbability = dto.precipitationProbability
            )
        }
    }
}

data class DailyForecastDto(
    val isoDate: String,
    val weatherCode: Int,
    val tempMax: Int,
    val tempMin: Int,
    val precipitationProbability: Int
)

@Entity(tableName = "school_config")
data class SchoolConfigEntity(
    @PrimaryKey val id: String = "thcs_lehuulap",
    val name: String,
    val academicYear: String,
    val startDate: String,
    val morningStart: String,
    val afternoonStart: String,
    val periodDuration: Int,
    val recessDuration: Int,
    val transitionDuration: Int
) {
    fun toDomain() = SchoolConfig(
        name = name,
        academicYear = academicYear,
        startDate = startDate,
        timeConfig = TimeConfig(
            morningStart = morningStart,
            afternoonStart = afternoonStart,
            periodDuration = periodDuration,
            recessDuration = recessDuration,
            transitionDuration = transitionDuration
        )
    )
}

fun SchoolConfig.toEntity() = SchoolConfigEntity(
    id = "thcs_lehuulap",
    name = name,
    academicYear = academicYear,
    startDate = startDate,
    morningStart = timeConfig.morningStart,
    afternoonStart = timeConfig.afternoonStart,
    periodDuration = timeConfig.periodDuration,
    recessDuration = timeConfig.recessDuration,
    transitionDuration = timeConfig.transitionDuration
)

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val email: String,
    val department: String,
    val mainSubject: String,
    val homeroomClass: String?,
    val accountRole: String?,
    val isBGH: Boolean,
    val password: String?
) {
    fun toDomain() = Teacher(
        id = id,
        name = name,
        shortName = shortName,
        email = email,
        department = department,
        mainSubject = mainSubject,
        homeroomClass = homeroomClass,
        accountRole = accountRole,
        isBGH = isBGH,
        password = password
    )
}

fun Teacher.toEntity() = TeacherEntity(
    id = id,
    name = name,
    shortName = shortName,
    email = email,
    department = department,
    mainSubject = mainSubject,
    homeroomClass = homeroomClass,
    accountRole = accountRole,
    isBGH = isBGH,
    password = password
)

@Entity(tableName = "school_classes")
data class SchoolClassEntity(
    @PrimaryKey val id: String,
    val name: String,
    val grade: Int,
    val homeroomTeacherId: String,
    val room: String,
    val campusId: String
) {
    fun toDomain() = SchoolClass(id, name, grade, homeroomTeacherId, room, campusId)
}

fun SchoolClass.toEntity() = SchoolClassEntity(id, name, grade, homeroomTeacherId, room, campusId)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val afternoonOverrideJson: String
) {
    fun toDomain() = Subject(
        id = id,
        name = name,
        code = code,
        afternoonTeacherOverride = LocalJson.jsonToMap(afternoonOverrideJson)
    )
}

fun Subject.toEntity() = SubjectEntity(
    id = id,
    name = name,
    code = code,
    afternoonOverrideJson = LocalJson.mapToJson(afternoonTeacherOverride)
)

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val id: String,
    val teacherId: String,
    val subjectId: String,
    val classId: String,
    val periodsPerWeek: Int,
    val session: String?
) {
    fun toDomain() = Assignment(id, teacherId, subjectId, classId, periodsPerWeek, session)
}

fun Assignment.toEntity() = AssignmentEntity(id, teacherId, subjectId, classId, periodsPerWeek, session)

@Entity(tableName = "timetable_slots")
data class TimetableSlotEntity(
    @PrimaryKey val id: String,
    val week: Int,
    val classId: String,
    val day: String,
    val period: Int,
    val subjectId: String,
    val teacherId: String,
    val room: String?,
    val note: String?,
    val customLabel: String?,
    val session: String
) {
    fun toDomain() = TimetableSlot(
        id = id,
        week = week,
        classId = classId,
        day = day,
        period = period,
        subjectId = subjectId,
        teacherId = teacherId,
        room = room,
        note = note,
        customLabel = customLabel,
        session = session
    )
}

fun TimetableSlot.toEntity() = TimetableSlotEntity(
    id = id,
    week = week,
    classId = classId,
    day = day,
    period = period,
    subjectId = subjectId,
    teacherId = teacherId,
    room = room,
    note = note,
    customLabel = customLabel,
    session = session
)

@Entity(tableName = "weeks_info")
data class WeekInfoEntity(
    @PrimaryKey val weekNumber: Int,
    val startDate: String,
    val endDate: String,
    val name: String,
    val isLocked: Boolean,
    val lockedSubjectsJson: String?,
    val lockedAssignmentsJson: String?
) {
    fun toDomain() = WeekInfo(
        weekNumber = weekNumber,
        startDate = startDate,
        endDate = endDate,
        name = name,
        isLocked = isLocked,
        lockedSubjects = LocalJson.jsonToSubjects(lockedSubjectsJson),
        lockedAssignments = LocalJson.jsonToAssignments(lockedAssignmentsJson)
    )
}

fun WeekInfo.toEntity() = WeekInfoEntity(
    weekNumber = weekNumber,
    startDate = startDate,
    endDate = endDate,
    name = name,
    isLocked = isLocked,
    lockedSubjectsJson = LocalJson.subjectsToJson(lockedSubjects),
    lockedAssignmentsJson = LocalJson.assignmentsToJson(lockedAssignments)
)

@Entity(tableName = "deleted_slots")
data class DeletedSlotsEntity(
    @PrimaryKey val id: Int = 1,
    val keysJson: String
) {
    fun toKeySet(): Set<String> = LocalJson.jsonToStringList(keysJson).toSet()
}

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val slotsVersion: Long = 0L,
    val assignmentsVersion: Long = 0L,
    val catalogVersion: Long = 0L,
    val weeksVersion: Long = 0L,
    val constraintsVersion: Long = 0L,
    val deletedSlotsVersion: Long = 0L,
    val updatedAt: String = "",
    val lastSyncedAtMillis: Long = 0L
) {
    fun toDomain() = SyncMetadata(
        slotsVersion = slotsVersion,
        assignmentsVersion = assignmentsVersion,
        catalogVersion = catalogVersion,
        weeksVersion = weeksVersion,
        constraintsVersion = constraintsVersion,
        deletedSlotsVersion = deletedSlotsVersion,
        updatedAt = updatedAt
    )
}

@Entity(tableName = "week_sync_versions")
data class WeekSyncVersionEntity(
    @PrimaryKey val weekNumber: Int,
    val slotsVersion: Long,
    val syncedAtMillis: Long
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Int = 1,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val currentTemp: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windSpeedKmh: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val todayHigh: Int,
    val todayLow: Int,
    val todayRainProb: Int,
    val dailyForecastsJson: String,
    val updatedAtMillis: Long
) {
    fun toDomain() = WeatherSnapshot(
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        currentTemp = currentTemp,
        feelsLike = feelsLike,
        humidity = humidity,
        windSpeedKmh = windSpeedKmh,
        weatherCode = weatherCode,
        isDay = isDay,
        todayHigh = todayHigh,
        todayLow = todayLow,
        todayRainProb = todayRainProb,
        dailyForecasts = LocalJson.jsonToForecasts(dailyForecastsJson),
        updatedAtMillis = updatedAtMillis
    )
}

fun WeatherSnapshot.toEntity() = WeatherCacheEntity(
    id = 1,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    currentTemp = currentTemp,
    feelsLike = feelsLike,
    humidity = humidity,
    windSpeedKmh = windSpeedKmh,
    weatherCode = weatherCode,
    isDay = isDay,
    todayHigh = todayHigh,
    todayLow = todayLow,
    todayRainProb = todayRainProb,
    dailyForecastsJson = LocalJson.forecastsToJson(dailyForecasts),
    updatedAtMillis = updatedAtMillis
)
