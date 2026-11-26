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
import com.example.domain.model.TimetableCalculator
import java.io.File
import java.io.FileOutputStream

enum class TimetableShareScope(
    val key: String,
    val label: String,
    val shortTitle: String,
    val fileTag: String
) {
    FULL_DAY(
        key = "all",
        label = "TKB Cả ngày",
        shortTitle = "Cả ngày",
        fileTag = "CaNgay"
    ),
    MORNING(
        key = "morning",
        label = "TKB Buổi sáng",
        shortTitle = "Buổi sáng",
        fileTag = "BuoiSang"
    ),
    AFTERNOON(
        key = "afternoon",
        label = "TKB Buổi chiều",
        shortTitle = "Buổi chiều",
        fileTag = "BuoiChieu"
    );

    fun filterPeriods(periods: List<ResolvedPeriod>): List<ResolvedPeriod> = when (this) {
        FULL_DAY -> periods
        MORNING -> periods.filter { !it.isAfternoon }
        AFTERNOON -> periods.filter { it.isAfternoon }
    }
}

/**
 * Renders a crisp high-resolution PNG image of a weekly timetable (Full day, Morning only,
 * or Afternoon only), supports sharing via Android Share Sheet (FileProvider), and saving
 * directly to the device gallery/files without needing broad storage permissions.
 */
object TimetableShareHelper {

    fun suggestedFileName(
        weekLabel: String,
        scope: TimetableShareScope
    ): String {
        val safeWeek = weekLabel.replace(Regex("[^a-zA-Z0-9]"), "")
            .ifBlank { "TKB" }
        return "TKB_${scope.fileTag}_${safeWeek}_${System.currentTimeMillis()}.png"
    }

    fun shareWeeklyTimetableImage(
        context: Context,
        title: String,
        subtitle: String,
        weekLabel: String,
        dateRange: String,
        periods: List<ResolvedPeriod>,
        showTeacher: Boolean,
        scope: TimetableShareScope = TimetableShareScope.FULL_DAY
    ): Boolean {
        return runCatching {
            val bitmap = renderWeeklyTimetableBitmap(
                title = title,
                subtitle = subtitle,
                weekLabel = weekLabel,
                dateRange = dateRange,
                periods = periods,
                showTeacher = showTeacher,
                scope = scope
            )

            val dir = File(context.cacheDir, "shared_timetables").apply { mkdirs() }
            val file = File(dir, suggestedFileName(weekLabel, scope))
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$title - $weekLabel (${scope.shortTitle})")
                putExtra(Intent.EXTRA_TEXT, "$title • $weekLabel (${scope.shortTitle}) • $dateRange")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Chia sẻ ảnh ${scope.label}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveWeeklyTimetableToGallery(
        context: Context,
        title: String,
        subtitle: String,
        weekLabel: String,
        dateRange: String,
        periods: List<ResolvedPeriod>,
        showTeacher: Boolean,
        scope: TimetableShareScope = TimetableShareScope.FULL_DAY
    ): Boolean {
        val resolver = context.contentResolver
        val fileName = suggestedFileName(weekLabel, scope)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TKB_LeHuuLap")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values) ?: return false

        return runCatching {
            val bitmap = renderWeeklyTimetableBitmap(
                title = title,
                subtitle = subtitle,
                weekLabel = weekLabel,
                dateRange = dateRange,
                periods = periods,
                showTeacher = showTeacher,
                scope = scope
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

    fun writeWeeklyTimetableToUri(
        context: Context,
        uri: Uri,
        title: String,
        subtitle: String,
        weekLabel: String,
        dateRange: String,
        periods: List<ResolvedPeriod>,
        showTeacher: Boolean,
        scope: TimetableShareScope = TimetableShareScope.FULL_DAY
    ): Boolean {
        return runCatching {
            val bitmap = renderWeeklyTimetableBitmap(
                title = title,
                subtitle = subtitle,
                weekLabel = weekLabel,
                dateRange = dateRange,
                periods = periods,
                showTeacher = showTeacher,
                scope = scope
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

    fun renderWeeklyTimetableBitmap(
        title: String,
        subtitle: String,
        weekLabel: String,
        dateRange: String,
        periods: List<ResolvedPeriod>,
        showTeacher: Boolean,
        scope: TimetableShareScope = TimetableShareScope.FULL_DAY
    ): Bitmap {
        val scopedPeriods = scope.filterPeriods(periods)
        val morning = scopedPeriods.filter { !it.isAfternoon }
        val afternoon = scopedPeriods.filter { it.isAfternoon }

        val drawMorning = when (scope) {
            TimetableShareScope.MORNING -> true
            TimetableShareScope.AFTERNOON -> false
            TimetableShareScope.FULL_DAY -> morning.isNotEmpty() || afternoon.isEmpty()
        }
        val drawAfternoon = when (scope) {
            TimetableShareScope.MORNING -> false
            TimetableShareScope.AFTERNOON -> true
            TimetableShareScope.FULL_DAY -> afternoon.isNotEmpty()
        }

        val morningMaxPeriod = 5
        val afternoonMaxPeriod = (afternoon.maxOfOrNull { it.period } ?: 4).coerceIn(4, 5)

        val width = 1240
        val pad = 44f
        val headerHeight = 165f
        val tableTitleOffset = 46f
        val tableHeaderH = 50f
        val rowH = 86f
        val tableBottomGap = 10f

        val morningBlockH = if (drawMorning) {
            tableTitleOffset + tableHeaderH + rowH * morningMaxPeriod + tableBottomGap
        } else 0f
        val betweenGap = if (drawMorning && drawAfternoon) 18f else 0f
        val afternoonBlockH = if (drawAfternoon) {
            tableTitleOffset + tableHeaderH + rowH * afternoonMaxPeriod + tableBottomGap
        } else 0f
        val footerH = 56f
        val height = (pad + headerHeight + 12f + morningBlockH + betweenGap + afternoonBlockH + footerH + pad).toInt()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Warm editorial background
        canvas.drawColor(Color.parseColor("#F6F3EE"))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2430")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 24f
        }
        val badgeColorHex = if (scope == TimetableShareScope.AFTERNOON) "#0284C7" else "#EA580C"
        val badgeSubHex = if (scope == TimetableShareScope.AFTERNOON) "#E0F2FE" else "#FFEDD5"
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(badgeColorHex)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Top Header Card
        val headerRect = RectF(pad, pad, width - pad, pad + headerHeight - 16f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2DCD2")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(headerRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(headerRect, 28f, 28f, borderPaint)

        canvas.drawText(truncateText(title, 28), pad + 32f, pad + 62f, titlePaint)
        canvas.drawText("$subtitle • ${scope.shortTitle}", pad + 32f, pad + 104f, subPaint)

        // Right pill for Week + Date range
        val pillW = 350f
        val pillRect = RectF(width - pad - pillW - 24f, pad + 26f, width - pad - 24f, pad + 114f)
        canvas.drawRoundRect(pillRect, 22f, 22f, badgePaint)
        canvas.drawText("$weekLabel • ${scope.shortTitle}", pillRect.centerX(), pillRect.top + 38f, badgeTextPaint)
        val pillSubPaint = Paint(badgeTextPaint).apply {
            textSize = 19f
            typeface = Typeface.DEFAULT
            color = Color.parseColor(badgeSubHex)
        }
        canvas.drawText(dateRange, pillRect.centerX(), pillRect.top + 70f, pillSubPaint)

        var y = pad + headerHeight + 12f
        if (drawMorning) {
            y = drawSessionTable(
                canvas = canvas,
                startY = y,
                width = width,
                pad = pad,
                sectionTitle = "BUỔI SÁNG",
                accentHex = "#EA580C",
                cellFillHex = "#FFF7ED",
                periods = morning,
                maxPeriod = morningMaxPeriod,
                showTeacher = showTeacher
            )
        }

        if (drawAfternoon) {
            if (drawMorning) y += betweenGap
            y = drawSessionTable(
                canvas = canvas,
                startY = y,
                width = width,
                pad = pad,
                sectionTitle = "BUỔI CHIỀU (HSG / CLB)",
                accentHex = "#0284C7",
                cellFillHex = "#F0F9FF",
                periods = afternoon,
                maxPeriod = afternoonMaxPeriod,
                showTeacher = showTeacher
            )
        }

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val today = VnDate.today()
        canvas.drawText(
            "Trường THCS Lê Hữu Lập • ${scope.label} • Xuất ngày ${today.formatDayMonth()}/${today.year} • Tổng ${scopedPeriods.size} tiết",
            width / 2f,
            height - pad + 4f,
            footerPaint
        )

        return bmp
    }

    private fun drawSessionTable(
        canvas: Canvas,
        startY: Float,
        width: Int,
        pad: Float,
        sectionTitle: String,
        accentHex: String,
        cellFillHex: String,
        periods: List<ResolvedPeriod>,
        maxPeriod: Int,
        showTeacher: Boolean
    ): Float {
        val days = TimetableCalculator.SCHOOL_DAYS
        val accentColor = Color.parseColor(accentHex)
        val cellFill = Color.parseColor(cellFillHex)

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(sectionTitle, pad + 8f, startY + 32f, sectionPaint)

        val tableTop = startY + 46f
        val tableHeaderH = 50f
        val rowH = 86f
        val tableBottom = tableTop + tableHeaderH + rowH * maxPeriod
        val tableRect = RectF(pad, tableTop, width - pad, tableBottom)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(tableRect, 22f, 22f, bgPaint)
        canvas.drawRoundRect(tableRect, 22f, 22f, gridPaint)

        val periodColW = 92f
        val dayColW = (width - pad * 2 - periodColW) / days.size

        // Header strip
        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        canvas.drawRect(pad + 2f, tableTop + 2f, width - pad - 2f, tableTop + tableHeaderH, headerBg)

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2430")
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Tiết", pad + periodColW / 2f, tableTop + 34f, headerTextPaint)
        days.forEachIndexed { idx, d ->
            val cx = pad + periodColW + idx * dayColW + dayColW / 2f
            canvas.drawText(d, cx, tableTop + 34f, headerTextPaint)
        }

        val periodLabelPaint = Paint(headerTextPaint).apply { color = accentColor }
        val mainCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }
        val filledBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cellFill }

        for (p in 1..maxPeriod) {
            val rowTop = tableTop + tableHeaderH + (p - 1) * rowH
            val rowBottom = rowTop + rowH
            canvas.drawLine(pad, rowTop, width - pad, rowTop, gridPaint)
            canvas.drawText("T$p", pad + periodColW / 2f, rowTop + rowH / 2f + 8f, periodLabelPaint)

            days.forEachIndexed { idx, d ->
                val colLeft = pad + periodColW + idx * dayColW
                val colRight = colLeft + dayColW
                canvas.drawLine(colLeft, tableTop, colLeft, tableBottom, gridPaint)

                val match = periods.firstOrNull { it.day == d && it.period == p }
                if (match != null) {
                    val inner = RectF(colLeft + 6f, rowTop + 6f, colRight - 6f, rowBottom - 6f)
                    canvas.drawRoundRect(inner, 14f, 14f, filledBgPaint)

                    val line1 = if (showTeacher) {
                        truncateText(match.subjectName, 13)
                    } else {
                        TimetableCalculator.compactClassName(match.className)
                    }
                    val line2 = if (showTeacher) {
                        truncateText(match.teacherShortName, 14)
                    } else {
                        truncateText(match.subjectName, 14)
                    }

                    canvas.drawText(line1, inner.centerX(), inner.centerY() - 6f, mainCellPaint)
                    canvas.drawText(line2, inner.centerX(), inner.centerY() + 22f, subCellPaint)
                }
            }
        }

        return tableBottom + 10f
    }

    private fun truncateText(text: String, maxChars: Int): String {
        val t = text.trim()
        return if (t.length <= maxChars) t else t.take(maxChars - 1) + "…"
    }
}
