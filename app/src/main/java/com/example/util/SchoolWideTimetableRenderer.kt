package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.example.domain.lunar.VnDate
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolClass
import com.example.domain.model.SchoolConfig
import com.example.domain.model.TimetableCalculator
import com.example.domain.model.WeekInfo
import java.io.File
import java.io.FileOutputStream

enum class SchoolWideShareScope(
    val key: String,
    val label: String,
    val shortTitle: String,
    val fileTag: String
) {
    MORNING(
        key = "school_morning",
        label = "Buổi Sáng",
        shortTitle = "Buổi sáng",
        fileTag = "ToanTruong_BuoiSang"
    ),
    AFTERNOON(
        key = "school_afternoon",
        label = "Buổi Chiều",
        shortTitle = "Buổi chiều",
        fileTag = "ToanTruong_BuoiChieu"
    )
}

/**
 * Renders crisp high-resolution (~2800px wide) School-Wide Timetable PNGs (all classes 6A1..9A3)
 * for both Morning (Weekly matrix matching Sổ đầu bài) and Afternoon (4-week monthly cycle),
 * with MediaStore gallery saving and 1-tap FileProvider sharing (Zalo, Messenger, etc.).
 */
object SchoolWideTimetableRenderer {

    fun suggestedFileName(
        selectedWeekNumber: Int,
        scope: SchoolWideShareScope
    ): String {
        val cycle = TimetableCalculator.getAfternoonMonthCycle(selectedWeekNumber)
        val suffix = when (scope) {
            SchoolWideShareScope.MORNING -> "Tuan$selectedWeekNumber"
            SchoolWideShareScope.AFTERNOON -> "Thang${cycle.month}_Tuan${cycle.weeks.first()}-${cycle.weeks.last()}"
        }
        return "TKB_${scope.fileTag}_${suffix}_${System.currentTimeMillis()}.png"
    }

    fun sortSchoolClasses(
        classes: List<SchoolClass>,
        fallbackPeriods: List<ResolvedPeriod>
    ): List<SchoolClass> {
        val base = if (classes.isNotEmpty()) {
            classes
        } else {
            fallbackPeriods
                .distinctBy { it.classId.lowercase() }
                .map { p ->
                    val short = TimetableCalculator.compactClassName(p.className)
                    val inferredGrade = short.firstOrNull()?.digitToIntOrNull() ?: 6
                    SchoolClass(id = p.classId, name = p.className, grade = inferredGrade)
                }
        }
        return base.sortedWith(
            compareBy<SchoolClass>(
                { effectiveGrade(it) },
                { naturalClassSortKey(TimetableCalculator.compactClassName(it.name)) }
            )
        )
    }

    private fun effectiveGrade(cls: SchoolClass): Int {
        if (cls.grade in 1..12) return cls.grade
        val short = TimetableCalculator.compactClassName(cls.name)
        return Regex("^(\\d+)").find(short)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 99
    }

    private fun naturalClassSortKey(name: String): String {
        return name.replace(Regex("\\d+")) { m -> m.value.padStart(4, '0') }.lowercase()
    }

    fun shareSchoolWideTimetableImage(
        context: Context,
        classes: List<SchoolClass>,
        morningPeriods: List<ResolvedPeriod>,
        afternoonCyclePeriods: List<ResolvedPeriod>,
        scope: SchoolWideShareScope,
        selectedWeekNumber: Int,
        dateRange: String,
        schoolConfig: SchoolConfig = SchoolConfig(),
        weeks: List<WeekInfo> = emptyList()
    ): Boolean {
        return runCatching {
            val bitmap = renderSchoolWideTimetableBitmap(
                classes = classes,
                morningPeriods = morningPeriods,
                afternoonCyclePeriods = afternoonCyclePeriods,
                scope = scope,
                selectedWeekNumber = selectedWeekNumber,
                dateRange = dateRange,
                schoolConfig = schoolConfig,
                weeks = weeks
            )

            val dir = File(context.cacheDir, "shared_timetables").apply { mkdirs() }
            val file = File(dir, suggestedFileName(selectedWeekNumber, scope))
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } finally {
                bitmap.recycle()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val cycle = TimetableCalculator.getAfternoonMonthCycle(selectedWeekNumber)
            val subjectLine = when (scope) {
                SchoolWideShareScope.MORNING ->
                    "TKB Toàn trường Buổi sáng - Tuần $selectedWeekNumber ($dateRange)"
                SchoolWideShareScope.AFTERNOON ->
                    "TKB Toàn trường Buổi chiều - Tháng ${cycle.month} (Tuần ${cycle.weeks.joinToString(", ")})"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subjectLine)
                putExtra(Intent.EXTRA_TEXT, "$subjectLine • ${schoolConfig.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Chia sẻ ảnh TKB Toàn trường").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveSchoolWideTimetableToGallery(
        context: Context,
        classes: List<SchoolClass>,
        morningPeriods: List<ResolvedPeriod>,
        afternoonCyclePeriods: List<ResolvedPeriod>,
        scope: SchoolWideShareScope,
        selectedWeekNumber: Int,
        dateRange: String,
        schoolConfig: SchoolConfig = SchoolConfig(),
        weeks: List<WeekInfo> = emptyList()
    ): Boolean {
        val resolver = context.contentResolver
        val fileName = suggestedFileName(selectedWeekNumber, scope)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/${TimetableShareHelper.GALLERY_SUBFOLDER}"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values) ?: return false

        return runCatching {
            val bitmap = renderSchoolWideTimetableBitmap(
                classes = classes,
                morningPeriods = morningPeriods,
                afternoonCyclePeriods = afternoonCyclePeriods,
                scope = scope,
                selectedWeekNumber = selectedWeekNumber,
                dateRange = dateRange,
                schoolConfig = schoolConfig,
                weeks = weeks
            )
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Bitmap compress failed")
                    }
                } ?: error("Cannot open output stream")
            } finally {
                bitmap.recycle()
            }

            val clearPending = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, clearPending, null, null)
            true
        }.getOrElse {
            runCatching { resolver.delete(itemUri, null, null) }
            false
        }
    }

    fun writeSchoolWideTimetableToUri(
        context: Context,
        uri: Uri,
        classes: List<SchoolClass>,
        morningPeriods: List<ResolvedPeriod>,
        afternoonCyclePeriods: List<ResolvedPeriod>,
        scope: SchoolWideShareScope,
        selectedWeekNumber: Int,
        dateRange: String,
        schoolConfig: SchoolConfig = SchoolConfig(),
        weeks: List<WeekInfo> = emptyList()
    ): Boolean {
        return runCatching {
            val bitmap = renderSchoolWideTimetableBitmap(
                classes = classes,
                morningPeriods = morningPeriods,
                afternoonCyclePeriods = afternoonCyclePeriods,
                scope = scope,
                selectedWeekNumber = selectedWeekNumber,
                dateRange = dateRange,
                schoolConfig = schoolConfig,
                weeks = weeks
            )
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Bitmap compress failed")
                    }
                } ?: error("Cannot open output stream")
            } finally {
                bitmap.recycle()
            }
            true
        }.getOrDefault(false)
    }

    fun renderSchoolWideTimetableBitmap(
        classes: List<SchoolClass>,
        morningPeriods: List<ResolvedPeriod>,
        afternoonCyclePeriods: List<ResolvedPeriod>,
        scope: SchoolWideShareScope,
        selectedWeekNumber: Int,
        dateRange: String,
        schoolConfig: SchoolConfig = SchoolConfig(),
        weeks: List<WeekInfo> = emptyList()
    ): Bitmap {
        val sortedClasses = sortSchoolClasses(
            classes = classes,
            fallbackPeriods = morningPeriods + afternoonCyclePeriods
        )
        return when (scope) {
            SchoolWideShareScope.MORNING -> renderMorningSchoolWideBitmap(
                sortedClasses = sortedClasses,
                morningPeriods = morningPeriods.filter { !it.isAfternoon },
                selectedWeekNumber = selectedWeekNumber,
                dateRange = dateRange,
                schoolConfig = schoolConfig
            )

            SchoolWideShareScope.AFTERNOON -> renderAfternoonSchoolWideBitmap(
                sortedClasses = sortedClasses,
                afternoonPeriods = afternoonCyclePeriods.filter { it.isAfternoon },
                selectedWeekNumber = selectedWeekNumber,
                schoolConfig = schoolConfig,
                weeks = weeks
            )
        }
    }

    private fun renderMorningSchoolWideBitmap(
        sortedClasses: List<SchoolClass>,
        morningPeriods: List<ResolvedPeriod>,
        selectedWeekNumber: Int,
        dateRange: String,
        schoolConfig: SchoolConfig
    ): Bitmap {
        val classCount = sortedClasses.size.coerceAtLeast(1)
        val pad = 56f
        val dayColW = 92f
        val periodColW = 74f
        val leftColsW = dayColW + periodColW
        val width = maxOf(2800, (pad * 2 + leftColsW + classCount * 204f).toInt())
        val classColW = (width - pad * 2 - leftColsW) / classCount

        val days = TimetableCalculator.SCHOOL_DAYS
        val hasSatPeriod5 = morningPeriods.any { it.day == "T7" && it.period == 5 }
        val dayPeriodRows: List<Pair<String, IntRange>> = days.map { d ->
            val range = if (d == "T7" && !hasSatPeriod5) 1..4 else 1..5
            d to range
        }
        val totalRows = dayPeriodRows.sumOf { it.second.count() }

        val headerHeight = 160f
        val gradeBandH = 44f
        val classHeaderH = 54f
        val rowH = 78f
        val footerH = 66f
        val tableTop = pad + headerHeight + 22f
        val tableBottom = tableTop + gradeBandH + classHeaderH + totalRows * rowH
        val height = (tableBottom + footerH + pad).toInt()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F6F3EE"))

        drawHeaderCard(
            canvas = canvas,
            width = width,
            pad = pad,
            headerHeight = headerHeight,
            title = "THỜI KHÓA BIỂU TOÀN TRƯỜNG — TRƯỜNG THCS LÊ HỮU LẬP",
            subtitle = "Buổi Sáng • Năm học ${schoolConfig.academicYear} • ${sortedClasses.size} lớp",
            badgeTitle = "Tuần $selectedWeekNumber",
            badgeSub = "($dateRange)",
            badgeBgHex = "#EA580C",
            badgeSubHex = "#FFEDD5"
        )

        val tableRect = RectF(pad, tableTop, width - pad, tableBottom)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val majorDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val gradeDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }

        canvas.drawRoundRect(tableRect, 24f, 24f, whitePaint)

        // Draw Grade & Class Header Band
        drawGradeAndClassHeader(
            canvas = canvas,
            sortedClasses = sortedClasses,
            tableLeft = pad,
            tableTop = tableTop,
            leftColsW = leftColsW,
            leftCol1W = dayColW,
            leftCol1Title = "Thứ",
            leftCol2Title = "Tiết",
            classColW = classColW,
            gradeBandH = gradeBandH,
            classHeaderH = classHeaderH,
            gridPaint = gridPaint,
            gradeDividerPaint = gradeDividerPaint
        )

        // Lookup map: (day, period, classIdLower) -> ResolvedPeriod
        val slotLookup = HashMap<String, ResolvedPeriod>(morningPeriods.size * 2)
        for (p in morningPeriods) {
            val key = "${p.day}_${p.period}_${p.classId.lowercase()}"
            if (!slotLookup.containsKey(key)) {
                slotLookup[key] = p
            }
        }

        val dayAltBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F8FC") }
        val cellCardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF8EE") }
        val cellCardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F3DEC3")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val dayBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dayBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val periodTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA580C")
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val mainCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        val dayBadgeColors = listOf(
            Color.parseColor("#EA580C"),
            Color.parseColor("#0284C7"),
            Color.parseColor("#0D9488"),
            Color.parseColor("#7C3AED"),
            Color.parseColor("#D97706"),
            Color.parseColor("#E11D48")
        )

        var curTop = tableTop + gradeBandH + classHeaderH
        dayPeriodRows.forEachIndexed { dayIdx, (dayCode, periodRange) ->
            val dayRowsCount = periodRange.count()
            val dayTop = curTop
            val dayBottom = dayTop + dayRowsCount * rowH

            // Alternating day shading
            if (dayIdx % 2 == 1) {
                canvas.drawRect(pad + 2f, dayTop, width - pad - 2f, dayBottom, dayAltBgPaint)
            }

            // Merged Day badge on left column
            val dayBadgeRect = RectF(
                pad + 14f,
                dayTop + (dayBottom - dayTop) / 2f - 26f,
                pad + dayColW - 14f,
                dayTop + (dayBottom - dayTop) / 2f + 26f
            )
            dayBadgePaint.color = dayBadgeColors[dayIdx % dayBadgeColors.size]
            canvas.drawRoundRect(dayBadgeRect, 14f, 14f, dayBadgePaint)
            canvas.drawText(dayCode, dayBadgeRect.centerX(), dayBadgeRect.centerY() + 9f, dayBadgeTextPaint)

            // Period rows within day
            periodRange.forEachIndexed { pOffset, pNum ->
                val rowTop = dayTop + pOffset * rowH
                val rowBottom = rowTop + rowH

                if (pOffset > 0) {
                    canvas.drawLine(pad + dayColW, rowTop, width - pad, rowTop, gridPaint)
                }
                canvas.drawText(
                    "T$pNum",
                    pad + dayColW + periodColW / 2f,
                    rowTop + rowH / 2f + 8f,
                    periodTextPaint
                )

                sortedClasses.forEachIndexed { clsIdx, cls ->
                    val colLeft = pad + leftColsW + clsIdx * classColW
                    val colRight = colLeft + classColW
                    val match = slotLookup["${dayCode}_${pNum}_${cls.id.lowercase()}"]
                    if (match != null) {
                        val inner = RectF(colLeft + 5f, rowTop + 5f, colRight - 5f, rowBottom - 5f)
                        canvas.drawRoundRect(inner, 10f, 10f, cellCardFillPaint)
                        canvas.drawRoundRect(inner, 10f, 10f, cellCardStrokePaint)
                        val maxTextW = (inner.width() - 10f).coerceAtLeast(60f)
                        drawFittedText(canvas, match.subjectName, inner.centerX(), inner.centerY() - 5f, maxTextW, mainCellPaint)
                        drawFittedText(canvas, match.teacherShortName, inner.centerX(), inner.centerY() + 20f, maxTextW, subCellPaint)
                    }
                }
            }

            // Bold horizontal day boundary line
            canvas.drawLine(pad, dayTop, width - pad, dayTop, majorDividerPaint)
            curTop = dayBottom
        }

        // Vertical column lines + bold grade separators
        drawVerticalColumnGrid(
            canvas = canvas,
            sortedClasses = sortedClasses,
            tableLeft = pad,
            tableTop = tableTop,
            headerTopForCols = tableTop + gradeBandH,
            tableBottom = tableBottom,
            leftCol1W = dayColW,
            leftColsW = leftColsW,
            classColW = classColW,
            gridPaint = gridPaint,
            gradeDividerPaint = gradeDividerPaint
        )
        canvas.drawRoundRect(tableRect, 24f, 24f, majorDividerPaint)

        drawFooter(canvas, width, height, pad, selectedWeekNumber)
        return bmp
    }

    private fun renderAfternoonSchoolWideBitmap(
        sortedClasses: List<SchoolClass>,
        afternoonPeriods: List<ResolvedPeriod>,
        selectedWeekNumber: Int,
        schoolConfig: SchoolConfig,
        weeks: List<WeekInfo>
    ): Bitmap {
        val classCount = sortedClasses.size.coerceAtLeast(1)
        val pad = 56f
        val weekColW = 192f
        val dayColW = 76f
        val leftColsW = weekColW + dayColW
        val width = maxOf(2800, (pad * 2 + leftColsW + classCount * 200f).toInt())
        val classColW = (width - pad * 2 - leftColsW) / classCount

        val cycle = TimetableCalculator.getAfternoonMonthCycle(selectedWeekNumber)
        val hasSatAfternoon = afternoonPeriods.any { it.day == "T7" }
        val activeDays = if (hasSatAfternoon) {
            TimetableCalculator.SCHOOL_DAYS
        } else {
            listOf("T2", "T3", "T4", "T5", "T6")
        }

        // Precompute dynamic row height per (week, day)
        val groupedByCell = afternoonPeriods.groupBy { "${it.week}_${it.day}_${it.classId.lowercase()}" }
        val rowSpecs = mutableListOf<Triple<Int, String, Float>>()
        for (wNum in cycle.weeks) {
            for (d in activeDays) {
                val maxItems = sortedClasses.maxOfOrNull { cls ->
                    groupedByCell["${wNum}_${d}_${cls.id.lowercase()}"]?.size ?: 0
                } ?: 0
                val rH = if (maxItems <= 1) 76f else (14f + maxItems * 54f + (maxItems - 1) * 6f)
                rowSpecs.add(Triple(wNum, d, rH))
            }
        }

        val headerHeight = 160f
        val gradeBandH = 44f
        val classHeaderH = 54f
        val footerH = 66f
        val tableTop = pad + headerHeight + 22f
        val tableBottom = tableTop + gradeBandH + classHeaderH + rowSpecs.sumOf { it.third.toDouble() }.toFloat()
        val height = (tableBottom + footerH + pad).toInt()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F6F3EE"))

        drawHeaderCard(
            canvas = canvas,
            width = width,
            pad = pad,
            headerHeight = headerHeight,
            title = "THỜI KHÓA BIỂU TOÀN TRƯỜNG — TRƯỜNG THCS LÊ HỮU LẬP",
            subtitle = "Buổi Chiều (HSG / CLB) • Năm học ${schoolConfig.academicYear} • ${sortedClasses.size} lớp",
            badgeTitle = "Tháng ${cycle.month}",
            badgeSub = "Tuần ${cycle.weeks.joinToString(", ")}",
            badgeBgHex = "#0284C7",
            badgeSubHex = "#E0F2FE"
        )

        val tableRect = RectF(pad, tableTop, width - pad, tableBottom)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val majorDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val gradeDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }

        canvas.drawRoundRect(tableRect, 24f, 24f, whitePaint)

        drawGradeAndClassHeader(
            canvas = canvas,
            sortedClasses = sortedClasses,
            tableLeft = pad,
            tableTop = tableTop,
            leftColsW = leftColsW,
            leftCol1W = weekColW,
            leftCol1Title = "Tuần",
            leftCol2Title = "Thứ",
            classColW = classColW,
            gradeBandH = gradeBandH,
            classHeaderH = classHeaderH,
            gridPaint = gridPaint,
            gradeDividerPaint = gradeDividerPaint
        )

        val weekAltBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F9FF") }
        val selectedWeekBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0F2FE") }
        val clubFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#ECFDF5") }
        val clubStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A7F3D0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val subjectFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0F9FF") }
        val subjectStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BAE6FD")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val weekTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val weekRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }
        val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0369A1")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val mainCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        var curTop = tableTop + gradeBandH + classHeaderH
        val daysPerWeek = activeDays.size
        cycle.weeks.forEachIndexed { wIdx, wNum ->
            val weekRows = rowSpecs.subList(wIdx * daysPerWeek, (wIdx + 1) * daysPerWeek)
            val weekHeight = weekRows.sumOf { it.third.toDouble() }.toFloat()
            val weekTop = curTop
            val weekBottom = weekTop + weekHeight

            if (wIdx % 2 == 1) {
                canvas.drawRect(pad + 2f, weekTop, width - pad - 2f, weekBottom, weekAltBgPaint)
            }
            if (wNum == selectedWeekNumber) {
                canvas.drawRect(pad + 2f, weekTop + 1f, pad + weekColW - 1f, weekBottom - 1f, selectedWeekBgPaint)
            }

            val (ws, _) = TimetableCalculator.getWeekDateRange(wNum, schoolConfig, weeks)
            val shortRange = "${ws.formatDayMonth()}–${ws.plusDays(5).formatDayMonth()}"
            val weekCenterY = weekTop + weekHeight / 2f
            canvas.drawText("Tuần $wNum", pad + weekColW / 2f, weekCenterY - 6f, weekTitlePaint)
            canvas.drawText(shortRange, pad + weekColW / 2f, weekCenterY + 24f, weekRangePaint)

            var dayTop = weekTop
            weekRows.forEachIndexed { dIdx, (_, dayCode, rH) ->
                val dayBottom = dayTop + rH
                if (dIdx > 0) {
                    canvas.drawLine(pad + weekColW, dayTop, width - pad, dayTop, gridPaint)
                }
                canvas.drawText(dayCode, pad + weekColW + dayColW / 2f, dayTop + rH / 2f + 8f, dayTextPaint)

                sortedClasses.forEachIndexed { clsIdx, cls ->
                    val colLeft = pad + leftColsW + clsIdx * classColW
                    val colRight = colLeft + classColW
                    val matches = groupedByCell["${wNum}_${dayCode}_${cls.id.lowercase()}"].orEmpty()
                    if (matches.size == 1) {
                        val m = matches.first()
                        val inner = RectF(colLeft + 5f, dayTop + 5f, colRight - 5f, dayBottom - 5f)
                        drawAfternoonSchoolCell(
                            canvas, inner, m,
                            clubFillPaint, clubStrokePaint, subjectFillPaint, subjectStrokePaint,
                            mainCellPaint, subCellPaint
                        )
                    } else if (matches.size > 1) {
                        val pillH = 54f
                        val gap = 6f
                        matches.forEachIndexed { mIdx, m ->
                            val pTop = dayTop + 7f + mIdx * (pillH + gap)
                            val inner = RectF(colLeft + 5f, pTop, colRight - 5f, pTop + pillH)
                            drawAfternoonSchoolCell(
                                canvas, inner, m,
                                clubFillPaint, clubStrokePaint, subjectFillPaint, subjectStrokePaint,
                                mainCellPaint, subCellPaint
                            )
                        }
                    }
                }
                dayTop = dayBottom
            }

            canvas.drawLine(pad, weekTop, width - pad, weekTop, majorDividerPaint)
            curTop = weekBottom
        }

        drawVerticalColumnGrid(
            canvas = canvas,
            sortedClasses = sortedClasses,
            tableLeft = pad,
            tableTop = tableTop,
            headerTopForCols = tableTop + gradeBandH,
            tableBottom = tableBottom,
            leftCol1W = weekColW,
            leftColsW = leftColsW,
            classColW = classColW,
            gridPaint = gridPaint,
            gradeDividerPaint = gradeDividerPaint
        )
        canvas.drawRoundRect(tableRect, 24f, 24f, majorDividerPaint)

        drawFooter(canvas, width, height, pad, selectedWeekNumber)
        return bmp
    }

    private fun drawAfternoonSchoolCell(
        canvas: Canvas,
        inner: RectF,
        match: ResolvedPeriod,
        clubFillPaint: Paint,
        clubStrokePaint: Paint,
        subjectFillPaint: Paint,
        subjectStrokePaint: Paint,
        mainCellPaint: Paint,
        subCellPaint: Paint
    ) {
        val fill = if (match.hideAfternoonTeacher) clubFillPaint else subjectFillPaint
        val stroke = if (match.hideAfternoonTeacher) clubStrokePaint else subjectStrokePaint
        canvas.drawRoundRect(inner, 10f, 10f, fill)
        canvas.drawRoundRect(inner, 10f, 10f, stroke)

        val maxTextW = (inner.width() - 10f).coerceAtLeast(60f)
        val line2 = if (match.hideAfternoonTeacher) "" else match.teacherShortName
        if (line2.isBlank()) {
            drawFittedText(canvas, match.subjectName, inner.centerX(), inner.centerY() + 7f, maxTextW, mainCellPaint)
        } else {
            drawFittedText(canvas, match.subjectName, inner.centerX(), inner.centerY() - 5f, maxTextW, mainCellPaint)
            drawFittedText(canvas, line2, inner.centerX(), inner.centerY() + 19f, maxTextW, subCellPaint)
        }
    }

    private fun drawHeaderCard(
        canvas: Canvas,
        width: Int,
        pad: Float,
        headerHeight: Float,
        title: String,
        subtitle: String,
        badgeTitle: String,
        badgeSub: String,
        badgeBgHex: String,
        badgeSubHex: String
    ) {
        val headerRect = RectF(pad, pad, width - pad, pad + headerHeight)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2DCD2")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(headerRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(headerRect, 28f, 28f, borderPaint)

        val badgeW = 470f
        val pillRect = RectF(width - pad - badgeW - 28f, pad + 24f, width - pad - 28f, pad + headerHeight - 24f)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(badgeBgHex) }
        canvas.drawRoundRect(pillRect, 24f, 24f, badgePaint)

        val badgeMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val badgeSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(badgeSubHex)
            textSize = 23f
            textAlign = Paint.Align.CENTER
        }
        drawFittedText(canvas, badgeTitle, pillRect.centerX(), pillRect.top + 48f, badgeW - 28f, badgeMainPaint)
        drawFittedText(canvas, badgeSub, pillRect.centerX(), pillRect.top + 88f, badgeW - 28f, badgeSubPaint)

        val titleMaxW = (pillRect.left - pad - 60f).coerceAtLeast(400f)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2430")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 26f
            textAlign = Paint.Align.LEFT
        }
        drawFittedLeftText(canvas, title, pad + 36f, pad + 68f, titleMaxW, titlePaint)
        drawFittedLeftText(canvas, subtitle, pad + 36f, pad + 118f, titleMaxW, subPaint)
    }

    private fun drawGradeAndClassHeader(
        canvas: Canvas,
        sortedClasses: List<SchoolClass>,
        tableLeft: Float,
        tableTop: Float,
        leftColsW: Float,
        leftCol1W: Float,
        leftCol1Title: String,
        leftCol2Title: String,
        classColW: Float,
        gradeBandH: Float,
        classHeaderH: Float,
        gridPaint: Paint,
        gradeDividerPaint: Paint
    ) {
        val headerLeftBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F1F5F9") }
        canvas.drawRect(
            tableLeft + 2f,
            tableTop + 2f,
            tableLeft + leftColsW,
            tableTop + gradeBandH + classHeaderH,
            headerLeftBg
        )

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2430")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val leftCenterY = tableTop + (gradeBandH + classHeaderH) / 2f + 8f
        canvas.drawText(leftCol1Title, tableLeft + leftCol1W / 2f, leftCenterY, headerTextPaint)
        canvas.drawText(leftCol2Title, tableLeft + leftCol1W + (leftColsW - leftCol1W) / 2f, leftCenterY, headerTextPaint)

        val gradeBandColors = mapOf(
            6 to (Color.parseColor("#FFEDD5") to Color.parseColor("#C2410C")),
            7 to (Color.parseColor("#D1FAE5") to Color.parseColor("#047857")),
            8 to (Color.parseColor("#E0F2FE") to Color.parseColor("#0369A1")),
            9 to (Color.parseColor("#E0E7FF") to Color.parseColor("#4338CA"))
        )
        val gradeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gradeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val classBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        val classTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Group consecutive classes by grade for the top Khối band
        var idx = 0
        while (idx < sortedClasses.size) {
            val grade = effectiveGrade(sortedClasses[idx])
            var end = idx
            while (end + 1 < sortedClasses.size && effectiveGrade(sortedClasses[end + 1]) == grade) {
                end++
            }
            val groupLeft = tableLeft + leftColsW + idx * classColW
            val groupRight = tableLeft + leftColsW + (end + 1) * classColW
            val (bgColor, fgColor) = gradeBandColors[grade]
                ?: (Color.parseColor("#F1F5F9") to Color.parseColor("#334155"))

            gradeFillPaint.color = bgColor
            gradeTextPaint.color = fgColor
            canvas.drawRect(groupLeft, tableTop + 2f, groupRight - 2f, tableTop + gradeBandH, gradeFillPaint)
            canvas.drawText(
                "KHỐI $grade",
                (groupLeft + groupRight) / 2f,
                tableTop + gradeBandH / 2f + 8f,
                gradeTextPaint
            )

            // Draw individual class header cells
            for (c in idx..end) {
                val cLeft = tableLeft + leftColsW + c * classColW
                val cRight = cLeft + classColW
                canvas.drawRect(cLeft, tableTop + gradeBandH, cRight - 1f, tableTop + gradeBandH + classHeaderH, classBgPaint)
                val label = TimetableCalculator.compactClassName(sortedClasses[c].name)
                drawFittedText(
                    canvas,
                    label,
                    (cLeft + cRight) / 2f,
                    tableTop + gradeBandH + classHeaderH / 2f + 9f,
                    classColW - 12f,
                    classTextPaint
                )
            }

            if (idx > 0) {
                canvas.drawLine(groupLeft, tableTop, groupLeft, tableTop + gradeBandH + classHeaderH, gradeDividerPaint)
            }
            idx = end + 1
        }

        val tableRight = tableLeft + leftColsW + sortedClasses.size * classColW
        canvas.drawLine(tableLeft + leftColsW, tableTop + gradeBandH, tableRight, tableTop + gradeBandH, gridPaint)
    }

    private fun drawVerticalColumnGrid(
        canvas: Canvas,
        sortedClasses: List<SchoolClass>,
        tableLeft: Float,
        tableTop: Float,
        headerTopForCols: Float,
        tableBottom: Float,
        leftCol1W: Float,
        leftColsW: Float,
        classColW: Float,
        gridPaint: Paint,
        gradeDividerPaint: Paint
    ) {
        canvas.drawLine(tableLeft + leftCol1W, tableTop, tableLeft + leftCol1W, tableBottom, gridPaint)
        canvas.drawLine(tableLeft + leftColsW, tableTop, tableLeft + leftColsW, tableBottom, gradeDividerPaint)

        for (i in 1 until sortedClasses.size) {
            val x = tableLeft + leftColsW + i * classColW
            val prevGrade = effectiveGrade(sortedClasses[i - 1])
            val curGrade = effectiveGrade(sortedClasses[i])
            if (prevGrade != curGrade) {
                canvas.drawLine(x, tableTop, x, tableBottom, gradeDividerPaint)
            } else {
                canvas.drawLine(x, headerTopForCols, x, tableBottom, gridPaint)
            }
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        width: Int,
        height: Int,
        pad: Float,
        selectedWeekNumber: Int
    ) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 25f
            textAlign = Paint.Align.CENTER
        }
        val today = VnDate.today()
        canvas.drawText(
            "Trường THCS Lê Hữu Lập • Tuần $selectedWeekNumber • Xuất ngày ${today.formatDayMonth()}/${today.year}",
            width / 2f,
            height - pad + 4f,
            footerPaint
        )
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        baselineY: Float,
        maxWidth: Float,
        basePaint: Paint
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val paint = Paint(basePaint)
        while (paint.measureText(trimmed) > maxWidth && paint.textSize > 14f) {
            paint.textSize -= 1f
        }
        var output = trimmed
        if (paint.measureText(output) > maxWidth) {
            while (output.length > 2 && paint.measureText("$output…") > maxWidth) {
                output = output.dropLast(1)
            }
            output = "$output…"
        }
        canvas.drawText(output, cx, baselineY, paint)
    }

    private fun drawFittedLeftText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        maxWidth: Float,
        basePaint: Paint
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val paint = Paint(basePaint)
        while (paint.measureText(trimmed) > maxWidth && paint.textSize > 18f) {
            paint.textSize -= 1f
        }
        var output = trimmed
        if (paint.measureText(output) > maxWidth) {
            while (output.length > 2 && paint.measureText("$output…") > maxWidth) {
                output = output.dropLast(1)
            }
            output = "$output…"
        }
        canvas.drawText(output, x, baselineY, paint)
    }
}
