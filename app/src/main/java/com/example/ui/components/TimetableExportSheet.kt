package com.example.ui.components

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ResolvedPeriod
import com.example.domain.model.SchoolConfig
import com.example.domain.model.WeekInfo
import com.example.util.TimetableShareHelper
import com.example.util.TimetableShareScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableExportSheet(
    title: String,
    subtitle: String,
    weekLabel: String,
    dateRange: String,
    periods: List<ResolvedPeriod>,
    showTeacher: Boolean,
    initialScope: TimetableShareScope = TimetableShareScope.FULL_DAY,
    selectedWeekNumber: Int = Regex("(\\d+)").find(weekLabel)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1,
    schoolConfig: SchoolConfig = SchoolConfig(),
    weeks: List<WeekInfo> = emptyList(),
    afternoonCyclePeriods: List<ResolvedPeriod>? = null,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedScope by remember(initialScope) { mutableStateOf(initialScope) }

    val effectiveAfternoon = remember(periods, afternoonCyclePeriods) {
        (afternoonCyclePeriods ?: periods.filter { it.isAfternoon }).filter { it.isAfternoon }
    }
    val morningCount = remember(periods) { periods.count { !it.isAfternoon } }
    val afternoonCount = effectiveAfternoon.size
    val allCount = morningCount + afternoonCount

    val hasScopedData = when (selectedScope) {
        TimetableShareScope.FULL_DAY -> morningCount > 0 || afternoonCount > 0
        TimetableShareScope.MORNING -> morningCount > 0
        TimetableShareScope.AFTERNOON -> afternoonCount > 0
    }

    val previewBitmap: Bitmap? = remember(
        title,
        subtitle,
        weekLabel,
        dateRange,
        periods,
        effectiveAfternoon,
        showTeacher,
        selectedScope,
        selectedWeekNumber,
        schoolConfig,
        weeks
    ) {
        if (!hasScopedData) null
        else TimetableShareHelper.renderWeeklyTimetableBitmap(
            title = title,
            subtitle = subtitle,
            weekLabel = weekLabel,
            dateRange = dateRange,
            periods = periods,
            showTeacher = showTeacher,
            scope = selectedScope,
            selectedWeekNumber = selectedWeekNumber,
            schoolConfig = schoolConfig,
            weeks = weeks,
            afternoonPeriods = effectiveAfternoon
        )
    }

    fun notifyUser(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onNotify(message)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            val ok = TimetableShareHelper.writeWeeklyTimetableToUri(
                context = context,
                uri = uri,
                title = title,
                subtitle = subtitle,
                weekLabel = weekLabel,
                dateRange = dateRange,
                periods = periods,
                showTeacher = showTeacher,
                scope = selectedScope,
                selectedWeekNumber = selectedWeekNumber,
                schoolConfig = schoolConfig,
                weeks = weeks,
                afternoonPeriods = effectiveAfternoon
            )
            notifyUser(
                if (ok) "Đã tải ảnh ${selectedScope.label} về máy."
                else "Không thể lưu ảnh vào vị trí đã chọn."
            )
            if (ok) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Gửi hoặc tải ảnh TKB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$title • $weekLabel ($dateRange)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Đóng")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Chọn phần Thời khóa biểu muốn xuất",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    Triple(TimetableShareScope.FULL_DAY, allCount, "export_scope_all"),
                    Triple(TimetableShareScope.MORNING, morningCount, "export_scope_morning"),
                    Triple(TimetableShareScope.AFTERNOON, afternoonCount, "export_scope_afternoon")
                )
                options.forEach { (scope, count, tag) ->
                    val isSelected = selectedScope == scope
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedScope = scope }
                            .testTag(tag)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = scope.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$count tiết",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (previewBitmap != null && !previewBitmap.isRecycled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val ratio = previewBitmap.width.toFloat() / previewBitmap.height.coerceAtLeast(1).toFloat()
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Xem trước ảnh ${selectedScope.label}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(vertical = 28.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = "Không có tiết học nào trong mục ${selectedScope.label} của $weekLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val ok = TimetableShareHelper.saveWeeklyTimetableToGallery(
                                context = context,
                                title = title,
                                subtitle = subtitle,
                                weekLabel = weekLabel,
                                dateRange = dateRange,
                                periods = periods,
                                showTeacher = showTeacher,
                                scope = selectedScope,
                                selectedWeekNumber = selectedWeekNumber,
                                schoolConfig = schoolConfig,
                                weeks = weeks,
                                afternoonPeriods = effectiveAfternoon
                            )
                            notifyUser(
                                if (ok) "Đã lưu ảnh ${selectedScope.label} vào Thư viện (Pictures/TKB_LeHuuLap)."
                                else "Không thể tải ảnh về máy lúc này."
                            )
                            if (ok) onDismiss()
                        } else {
                            createDocumentLauncher.launch(
                                TimetableShareHelper.suggestedFileName(weekLabel, selectedScope)
                            )
                        }
                    },
                    enabled = hasScopedData,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_download_tkb_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tải ảnh về máy", textAlign = TextAlign.Center, maxLines = 2)
                }

                Button(
                    onClick = {
                        val ok = TimetableShareHelper.shareWeeklyTimetableImage(
                            context = context,
                            title = title,
                            subtitle = subtitle,
                            weekLabel = weekLabel,
                            dateRange = dateRange,
                            periods = periods,
                            showTeacher = showTeacher,
                            scope = selectedScope,
                            selectedWeekNumber = selectedWeekNumber,
                            schoolConfig = schoolConfig,
                            weeks = weeks,
                            afternoonPeriods = effectiveAfternoon
                        )
                        if (!ok) {
                            notifyUser("Không thể tạo ảnh chia sẻ lúc này.")
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = hasScopedData,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_share_tkb_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gửi / Chia sẻ", textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
