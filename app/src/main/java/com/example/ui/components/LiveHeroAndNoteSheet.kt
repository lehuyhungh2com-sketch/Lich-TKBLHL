package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Room
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WidgetCornerStyle
import com.example.data.local.WidgetFontSize
import com.example.data.local.WidgetTextTheme
import com.example.domain.lunar.LunarCalendar
import com.example.domain.lunar.VnDate
import com.example.domain.lunar.VnTime
import com.example.domain.model.PeriodLiveStatus
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.TimetableCalculator
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SunsetOrange
import com.example.widget.WidgetDataBuilder

/**
 * Prominent real-time teaching focus card at the top of TodayScreen.
 * Shows live 45-min progress bar when TEACHING_NOW, countdown when NEXT_PERIOD,
 * or a calm completion summary when all periods for today are finished.
 */
@Composable
fun LivePeriodHeroCard(
    todayPeriods: List<ResolvedPeriod>,
    nowTime: VnTime,
    isSundayPreview: Boolean,
    showTeacherName: Boolean,
    personalNoteLookup: (ResolvedPeriod) -> String?,
    onPeriodClick: (ResolvedPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSundayPreview || todayPeriods.isEmpty()) return

    val teachingNow = todayPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.TEACHING_NOW }
    val nextPeriod = todayPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.NEXT_PERIOD }
    val finishedCount = todayPeriods.count { it.liveStatus == PeriodLiveStatus.PAST }
    val totalCount = todayPeriods.size

    when {
        teachingNow != null -> {
            val totalMins = (teachingNow.endTime.totalMinutes - teachingNow.startTime.totalMinutes).coerceAtLeast(1)
            val elapsedMins = (nowTime.totalMinutes - teachingNow.startTime.totalMinutes).coerceIn(0, totalMins)
            val leftMins = (totalMins - elapsedMins).coerceAtLeast(1)
            val progress = (elapsedMins.toFloat() / totalMins.toFloat()).coerceIn(0.03f, 1f)
            val shortClass = TimetableCalculator.compactClassName(teachingNow.className)
            val note = personalNoteLookup(teachingNow)

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF065F46),
                                Color(0xFF047857),
                                Color(0xFF0D9488)
                            )
                        )
                    )
                    .clickable { onPeriodClick(teachingNow) }
                    .testTag("live_hero_teaching_now")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6EE7B7))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ĐANG DẠY • ${if (teachingNow.isAfternoon) "C" else "T"}${teachingNow.period}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = "Còn $leftMins phút",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA7F3D0)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (showTeacherName) {
                            "${teachingNow.subjectName} (${teachingNow.teacherShortName})"
                        } else {
                            "Lớp $shortClass • ${teachingNow.subjectName}"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFD1FAE5),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = teachingNow.timeRangeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD1FAE5)
                            )
                        }
                        if (teachingNow.room.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Room,
                                    contentDescription = null,
                                    tint = Color(0xFFD1FAE5),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = teachingNow.room,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD1FAE5)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color(0xFF6EE7B7),
                        trackColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Đã dạy $elapsedMins/$totalMins phút",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD1FAE5)
                        )
                        Text(
                            text = if (note != null) "✎ $note" else "Chạm để thêm ghi chú",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }

        nextPeriod != null -> {
            val minsUntil = (nextPeriod.startTime.totalMinutes - nowTime.totalMinutes).coerceAtLeast(1)
            val countdownLabel = if (minsUntil >= 60) {
                val h = minsUntil / 60
                val m = minsUntil % 60
                if (m == 0) "Còn ${h} giờ nữa" else "Còn ${h}g ${m}p nữa"
            } else {
                "Còn $minsUntil phút nữa"
            }
            val shortClass = TimetableCalculator.compactClassName(nextPeriod.className)
            val note = personalNoteLookup(nextPeriod)

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = modifier
                    .fillMaxWidth()
                    .clickable { onPeriodClick(nextPeriod) }
                    .testTag("live_hero_next_period")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Upcoming,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TIẾT TIẾP THEO • ${if (nextPeriod.isAfternoon) "C" else "T"}${nextPeriod.period}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = countdownLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (showTeacherName) {
                            "${nextPeriod.subjectName} (${nextPeriod.teacherShortName})"
                        } else {
                            "Lớp $shortClass • ${nextPeriod.subjectName}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${nextPeriod.timeRangeText}" +
                                if (nextPeriod.room.isNotBlank()) " • ${nextPeriod.room}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "Đã xong $finishedCount/$totalCount tiết",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    if (!note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hoàn thành lịch dạy hôm nay!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Đã dạy xong $finishedCount/$totalCount tiết. Chúc thầy/cô nghỉ ngơi vui vẻ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyStatsRibbon(
    todayDone: Int,
    todayTotal: Int,
    weekMorning: Int,
    weekAfternoon: Int,
    notesCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatMiniTile(
            label = "Hôm nay",
            value = "$todayDone/$todayTotal tiết",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        StatMiniTile(
            label = "Tuần này",
            value = "${weekMorning + weekAfternoon} tiết",
            sub = "${weekMorning}S · ${weekAfternoon}C",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatMiniTile(
            label = "Ghi chú",
            value = "$notesCount mục",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniTile(
    label: String,
    value: String,
    tint: Color,
    sub: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonNoteEditorSheet(
    period: ResolvedPeriod,
    existingNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember(period.slotId, existingNote) { mutableStateOf(existingNote) }
    val shortClass = TimetableCalculator.compactClassName(period.className)
    val quickTags = listOf("Kiểm tra 15p", "Kiểm tra giữa kỳ", "Mang đồ dùng", "Dạy bù", "Thu vở bài tập")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ghi chú riêng cho tiết dạy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${period.day} • ${if (period.isAfternoon) "Chiều" else "Sáng"} T${period.period} • Lớp $shortClass (${period.subjectName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lưu cục bộ trên máy của thầy/cô (không ảnh hưởng dữ liệu TKB nhà trường).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Nội dung nhắc việc") },
                placeholder = { Text("Ví dụ: Kiểm tra 15p chương 2, mang loa...") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lesson_note_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Chèn nhanh:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTags.forEach { tag ->
                    AssistChip(
                        onClick = {
                            noteText = if (noteText.isBlank()) tag else "${noteText.trim()}, $tag"
                        },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingNote.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("lesson_note_delete_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa")
                    }
                }
                Button(
                    onClick = {
                        onSave(noteText)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lesson_note_save_button")
                ) {
                    Text("Lưu ghi chú")
                }
            }
        }
    }
}

/**
 * Live Preview Card in Settings for Widget 3 (TimetableOnlyWidget) reflecting real-time
 * Teaching Now (emerald), Next Period (orange), Past (dimmed ✓), corner radius, font size,
 * and Show/Hide subject toggle.
 */
@Composable
fun LiveTimetableOnlyPreviewCard(
    today: VnDate,
    now: VnTime,
    teacherName: String,
    todayPeriods: List<ResolvedPeriod>,
    weekNumber: Int,
    transparentMode: Boolean,
    opacity: Float,
    textTheme: WidgetTextTheme,
    cornerStyle: WidgetCornerStyle,
    fontSize: WidgetFontSize,
    showSubject: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTransparent = transparentMode || opacity <= 0.02f
    val clampedAlpha = opacity.coerceIn(0f, 1f)
    val useLightText = WidgetDataBuilder.detectUseLightText(
        context = context,
        isFullyTransparent = isTransparent,
        textTheme = textTheme
    )
    val bgBase = if (useLightText) Color(0xFF151A24) else Color(0xFFFFFCF7)
    val tileBase = if (useLightText) Color(0xFF232B3A) else Color(0xFFF3EFE8)
    val primaryText = if (useLightText) Color.White else SlateInk
    val secondaryText = if (useLightText) Color(0xFFDDE5F0) else Color(0xFF334155)
    val mutedPastText = if (useLightText) Color(0x88CBD5E1) else Color(0x7A475569)
    val accentOrange = if (useLightText) Color(0xFFFB923C) else SunsetOrange
    val liveAccent = if (useLightText) Color(0xFF34D399) else Color(0xFF047857)
    val liveBg = if (useLightText) Color(0x3810B981) else Color(0x28059669)
    val nextBg = if (useLightText) Color(0x28FB923C) else Color(0x22EA580C)
    val tileBg = if (isTransparent) Color.Transparent else tileBase.copy(alpha = (clampedAlpha * 0.90f).coerceIn(0f, 1f))
    val rootBg = if (isTransparent) Color.Transparent else bgBase.copy(alpha = clampedAlpha)
    val delta = fontSize.deltaSp
    val lunar = LunarCalendar.convertSolar2Lunar(today)
    val lunarShort = "${lunar.day}/${lunar.month}${if (lunar.isLeap) "N" else ""} ÂL"

    // Sample preview fallback if teacher has no classes today so Settings preview always looks informative
    val displayPeriods = todayPeriods.ifEmpty {
        listOf(
            ResolvedPeriod("s1", weekNumber, today.shortDayCode, 1, "morning", "8A1", "8A1", "s", "Âm nhạc", "Nhạc", "t", teacherName, "", null, null, VnTime(7, 15), VnTime(8, 0), PeriodLiveStatus.PAST),
            ResolvedPeriod("s2", weekNumber, today.shortDayCode, 2, "morning", "8A3", "8A3", "s", "Âm nhạc", "Nhạc", "t", teacherName, "", null, null, VnTime(8, 0), VnTime(8, 45), PeriodLiveStatus.TEACHING_NOW),
            ResolvedPeriod("s3", weekNumber, today.shortDayCode, 3, "morning", "8A2", "8A2", "s", "Âm nhạc", "Nhạc", "t", teacherName, "", null, null, VnTime(8, 55), VnTime(9, 40), PeriodLiveStatus.NEXT_PERIOD),
            ResolvedPeriod("s4", weekNumber, today.shortDayCode, 4, "morning", "7A3", "7A3", "s", "Âm nhạc", "Nhạc", "t", teacherName, "", null, null, VnTime(9, 40), VnTime(10, 25), PeriodLiveStatus.UPCOMING)
        )
    }
    val teachingNow = displayPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.TEACHING_NOW }
    val nextPeriod = displayPeriods.firstOrNull { it.liveStatus == PeriodLiveStatus.NEXT_PERIOD }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = if (useLightText) {
                        listOf(Color(0xFF1A1C24), Color(0xFF2B2832), Color(0xFF111319))
                    } else {
                        listOf(Color(0xFFBFD7EA), Color(0xFFE6DDD0), Color(0xFF9BB7A7))
                    }
                )
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (isTransparent) 0.dp else cornerStyle.radiusDp.dp))
                .background(rootBg)
                .padding(if (isTransparent) 6.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TKB ${today.vietnameseWeekday} · Tuần $weekNumber",
                        fontSize = (13f + delta).sp,
                        fontWeight = FontWeight.Bold,
                        color = accentOrange,
                        maxLines = 1
                    )
                    Text(
                        text = "${today.formatFullVietnamese()} ($lunarShort)",
                        fontSize = (10f + delta * 0.4f).sp,
                        color = secondaryText,
                        maxLines = 1
                    )
                }
                if (teacherName.isNotBlank()) {
                    Text(
                        text = teacherName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val statusText = when {
                teachingNow != null -> {
                    val left = (teachingNow.endTime.totalMinutes - now.totalMinutes).coerceIn(1, 45)
                    "● Đang dạy T${teachingNow.period} (${TimetableCalculator.compactClassName(teachingNow.className)}) · còn ${left}p"
                }
                nextPeriod != null -> "Tiếp: T${nextPeriod.period} · ${nextPeriod.startTime.formatHHmm()}"
                else -> "✓ Đã xong hôm nay"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (teachingNow != null) liveBg else tileBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = (10.5f + delta * 0.4f).sp,
                    fontWeight = FontWeight.Bold,
                    color = if (teachingNow != null) liveAccent else accentOrange,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayPeriods.take(4).forEach { p ->
                    val isNow = p.liveStatus == PeriodLiveStatus.TEACHING_NOW
                    val isNext = p.liveStatus == PeriodLiveStatus.NEXT_PERIOD
                    val isPast = p.liveStatus == PeriodLiveStatus.PAST
                    val tag = when {
                        isNow -> "● T${p.period}"
                        isPast -> "✓ T${p.period}"
                        else -> "T${p.period}"
                    }
                    val rowBg = when {
                        isNow -> liveBg
                        isNext -> nextBg
                        else -> tileBg
                    }
                    val tagColor = when {
                        isNow -> liveAccent
                        isNext -> accentOrange
                        isPast -> mutedPastText
                        p.isAfternoon -> SkyBlue
                        else -> accentOrange
                    }
                    val cls = TimetableCalculator.compactClassName(p.className)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(rowBg)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag,
                            fontSize = (11f + delta).sp,
                            fontWeight = FontWeight.Bold,
                            color = tagColor,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = if (showSubject) "$cls · ${p.subjectName}" else "Lớp $cls",
                            fontSize = (11.5f + delta).sp,
                            fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPast) mutedPastText else primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isNow) "${p.startTime.formatHHmm()}–${p.endTime.formatHHmm()}" else p.startTime.formatHHmm(),
                            fontSize = (10f + delta * 0.4f).sp,
                            fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isNow -> liveAccent
                                isNext -> accentOrange
                                isPast -> mutedPastText
                                else -> secondaryText
                            }
                        )
                    }
                }
            }
        }
    }
}
