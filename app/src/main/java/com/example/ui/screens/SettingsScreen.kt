package com.example.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.local.AppAccentPalette
import com.example.data.local.AppDensityMode
import com.example.data.local.AppFontScale
import com.example.data.local.AppThemeMode
import com.example.data.local.UserPreferencesState
import com.example.data.local.WidgetCornerStyle
import com.example.data.local.WidgetTextTheme
import com.example.domain.lunar.VnTime
import com.example.domain.model.TimetableCalculator
import com.example.notification.AlarmScheduler
import com.example.ui.MainUiState
import com.example.ui.components.LiveTimetableOnlyPreviewCard
import com.example.ui.components.LiveWeatherTimetablePreviewCard
import com.example.ui.components.LiveWidgetPreviewCard
import com.example.ui.components.SegmentOption
import com.example.ui.components.SegmentedOptionBar
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.OceanAccent
import com.example.ui.theme.SunsetOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onSyncNow: () -> Unit,
    onUpdateAppTheme: (AppThemeMode) -> Unit,
    onUpdateAccentPalette: (AppAccentPalette) -> Unit,
    onUpdateFontScale: (AppFontScale) -> Unit,
    onUpdateDensityMode: (AppDensityMode) -> Unit,
    onUpdateDailyReminder: (Boolean, Int, Int) -> Unit,
    onTogglePeriodReminder: (Boolean) -> Unit,
    onUpdatePeriodLeadMinutes: (Int) -> Unit,
    onSendTestNotification: () -> Unit,
    onToggleGpsLocation: (Boolean) -> Unit,
    onUpdateWidgetAppearance: (Boolean, Float, WidgetTextTheme, WidgetCornerStyle, Float, Boolean) -> Unit,
    onRefreshWidgetsNow: () -> Unit,
    onLogout: () -> Unit,
    onCheckAppUpdate: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = uiState.preferences
    val teacher = uiState.currentTeacher

    var canScheduleExactAlarms by remember {
        mutableStateOf(AlarmScheduler.canScheduleExactAlarms(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExactAlarms = AlarmScheduler.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSendTestNotification()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleGpsLocation(granted)
    }

    val todayAllPeriods = uiState.todayMorningPeriods + uiState.todayAfternoonPeriods
    val widgetTodayPeriods = if (uiState.effectiveShowHomeroom) {
        uiState.teacherTodayPeriods
    } else {
        uiState.teacherTodayPeriods.ifEmpty { todayAllPeriods }
    }
    val availablePalettes = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AppAccentPalette.entries
        } else {
            AppAccentPalette.entries.filter { it != AppAccentPalette.DYNAMIC }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cài đặt",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Phiên bản ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("settings_version_badge")
                        )
                    }
                }
            }

            // 1. TEACHER ACCOUNT & SYNC CARD
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Person,
                    title = "Tài khoản giáo viên"
                ) {
                    if (teacher != null) {
                        Text(
                            text = teacher.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = teacher.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!uiState.homeroomClassId.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val hcLabel = TimetableCalculator.compactClassName(
                                uiState.homeroomClass?.name ?: uiState.homeroomClassId.orEmpty()
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "GVCN $hcLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val lastSyncText = uiState.syncMetadata?.lastSyncedAtMillis?.takeIf { it > 0L }?.let { ts ->
                        val fmt = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN"))
                        "Đồng bộ gần nhất: ${fmt.format(Date(ts))}"
                    } ?: "Chưa đồng bộ"

                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val effectiveFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackButtons = (maxWidth.value / effectiveFontScale) < 285f
                        val syncContent: @Composable () -> Unit = {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang đồng bộ...", textAlign = TextAlign.Center, maxLines = 2)
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đồng bộ TKB", textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                        val logoutContent: @Composable () -> Unit = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Đăng xuất",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đăng xuất", textAlign = TextAlign.Center, maxLines = 2)
                        }

                        if (stackButtons) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onSyncNow,
                                    enabled = !uiState.isSyncing,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_sync_now_button")
                                ) { syncContent() }

                                OutlinedButton(
                                    onClick = onLogout,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_logout_button")
                                ) { logoutContent() }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onSyncNow,
                                    enabled = !uiState.isSyncing,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .testTag("settings_sync_now_button")
                                ) { syncContent() }

                                OutlinedButton(
                                    onClick = onLogout,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_logout_button")
                                ) { logoutContent() }
                            }
                        }
                    }
                }
            }

            // 1B. APP IN-APP UPDATE CARD (GitHub Releases)
            item {
                val appUpdate = uiState.appUpdate
                val available = appUpdate.availableUpdate
                SettingsSectionCard(
                    icon = if (available != null) Icons.Rounded.NewReleases else Icons.Rounded.SystemUpdate,
                    title = "Cập nhật ứng dụng"
                ) {
                    Text(
                        text = "Đang cài: Phiên bản ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    if (available != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Có phiên bản mới ${available.versionName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Điểm mới: ${available.summaryPreview}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                                )
                            }
                        }
                    } else if (!appUpdate.statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = appUpdate.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!appUpdate.errorMessage.isNullOrBlank()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kiểm tra và tải bản cập nhật mới trực tiếp từ GitHub Releases, giữ nguyên dữ liệu đã lưu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (available != null) {
                        Button(
                            onClick = onOpenUpdateDialog,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Xem thay đổi & Cập nhật", maxLines = 1, softWrap = false)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCheckAppUpdate,
                            enabled = !appUpdate.isChecking,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_check_update_button")
                        ) {
                            if (appUpdate.isChecking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Đang kiểm tra...", maxLines = 1, softWrap = false)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kiểm tra cập nhật", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // 2. INTERFACE & PERSONALIZATION (Theme, Accent Palette, Font Size, Density)
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Palette,
                    title = "Giao diện & Cá nhân hóa"
                ) {
                    Text(
                        text = "Chế độ sáng / tối",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SegmentedOptionBar(
                        options = AppThemeMode.entries.map {
                            SegmentOption(it, it.label, "app_theme_${it.key}")
                        },
                        selected = prefs.appThemeMode,
                        onSelect = onUpdateAppTheme
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Bảng màu chủ đạo",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availablePalettes.forEach { palette ->
                            val isSelected = prefs.appAccentPalette == palette
                            AccentSwatchItem(
                                palette = palette,
                                isSelected = isSelected,
                                onClick = { onUpdateAccentPalette(palette) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Cỡ chữ trong ứng dụng",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SegmentedOptionBar(
                        options = AppFontScale.entries.map {
                            SegmentOption(it, it.label, "app_font_${it.key}")
                        },
                        selected = prefs.appFontScale,
                        onSelect = onUpdateFontScale
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Mật độ hiển thị",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SegmentedOptionBar(
                        options = AppDensityMode.entries.map {
                            SegmentOption(it, it.label, "app_density_${it.key}")
                        },
                        selected = prefs.appDensityMode,
                        onSelect = onUpdateDensityMode
                    )
                }
            }

            // 3. DAILY REMINDER, PERIOD LEAD TIME & EXACT ALARM REAL-TIME PERMISSION
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Alarm,
                    title = "Nhắc lịch dạy & Thời gian thực"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nhắc buổi sáng (Thứ 2 – Thứ 7)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Báo lịch lúc ${VnTime(prefs.reminderHour, prefs.reminderMinute).formatHHmm()} khi có tiết",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.dailyReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onUpdateDailyReminder(enabled, prefs.reminderHour, prefs.reminderMinute)
                            },
                            modifier = Modifier.testTag("switch_daily_reminder")
                        )
                    }

                    if (prefs.dailyReminderEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Giờ gửi thông báo",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> onUpdateDailyReminder(true, h, m) },
                                        prefs.reminderHour,
                                        prefs.reminderMinute,
                                        true
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("pick_reminder_time_button")
                            ) {
                                Text(
                                    text = VnTime(prefs.reminderHour, prefs.reminderMinute).formatHHmm(),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nhắc trước mỗi tiết dạy",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Đang đặt báo trước ${prefs.periodReminderLeadMinutes} phút",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.periodReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onTogglePeriodReminder(enabled)
                            },
                            modifier = Modifier.testTag("switch_period_reminder")
                        )
                    }

                    if (prefs.periodReminderEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SegmentedOptionBar(
                            options = UserPreferencesState.LEAD_MINUTE_OPTIONS.map { mins ->
                                SegmentOption(mins, "$mins phút", "period_lead_$mins")
                            },
                            selected = prefs.periodReminderLeadMinutes,
                            onSelect = onUpdatePeriodLeadMinutes
                        )
                    }

                    // Android 12+ Exact Alarm Permission helper for reliable live widget ticks
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (canScheduleExactAlarms) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Đã cấp quyền báo thức chính xác — Widget tự đổi tiết chuẩn từng phút.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Cấp quyền chạy đúng giờ cho Widget",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Bật quyền \"Báo thức & lời nhắc\" để Widget tự động đánh dấu ĐANG DẠY đúng phút bắt đầu tiết ngay cả khi khóa màn hình.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(
                                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("grant_exact_alarm_button")
                                    ) {
                                        Text("Mở quyền Báo thức & lời nhắc")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSendTestNotification()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gửi thử thông báo")
                    }
                }
            }

            // 4. WEATHER & GPS SETTINGS
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.LocationOn,
                    title = "Thời tiết"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dùng vị trí GPS",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (prefs.useGpsLocation) {
                                    prefs.lastLocationName
                                } else {
                                    "Mặc định: Hậu Lộc, Thanh Hóa"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.useGpsLocation,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    onToggleGpsLocation(false)
                                }
                            },
                            modifier = Modifier.testTag("switch_gps_weather")
                        )
                    }
                }
            }

            // 5. HOME SCREEN WIDGET CUSTOMIZATION + 3-WIDGET LIVE PREVIEW
            item {
                val persistedOpacity = if (prefs.widgetTransparentMode) 0f else prefs.widgetOpacity.coerceIn(0f, 1f)
                var liveOpacity by remember { mutableFloatStateOf(persistedOpacity) }
                var liveFontScale by remember { mutableFloatStateOf(prefs.widgetFontScale) }
                var previewWidgetTab by remember { mutableIntStateOf(0) } // 0 = TKB Hôm nay, 1 = Đồng hồ & TKB, 2 = Thời tiết 4 ngày
                LaunchedEffect(prefs.widgetTransparentMode, prefs.widgetOpacity) {
                    liveOpacity = if (prefs.widgetTransparentMode) 0f else prefs.widgetOpacity.coerceIn(0f, 1f)
                }
                LaunchedEffect(prefs.widgetFontScale) {
                    liveFontScale = prefs.widgetFontScale
                }
                val liveIsTransparent = liveOpacity <= 0.02f

                fun saveWidgetConfig(
                    transparent: Boolean = liveIsTransparent,
                    opacity: Float = if (transparent) 0f else liveOpacity,
                    textTheme: WidgetTextTheme = prefs.widgetTextTheme,
                    corner: WidgetCornerStyle = prefs.widgetCornerStyle,
                    fontScale: Float = liveFontScale,
                    showSubject: Boolean = prefs.widgetShowSubject
                ) {
                    onUpdateWidgetAppearance(transparent, opacity, textTheme, corner, fontScale, showSubject)
                }

                SettingsSectionCard(
                    icon = Icons.Rounded.Widgets,
                    title = "Widget màn hình chính"
                ) {
                    SegmentedOptionBar(
                        options = listOf(
                            SegmentOption(0, "TKB Hôm nay", "preview_widget_tkb"),
                            SegmentOption(1, "Đồng hồ", "preview_widget_clock"),
                            SegmentOption(2, "Thời tiết 4N", "preview_widget_weather")
                        ),
                        selected = previewWidgetTab,
                        onSelect = { previewWidgetTab = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    when (previewWidgetTab) {
                        0 -> LiveTimetableOnlyPreviewCard(
                            today = uiState.todayDate,
                            now = uiState.nowTime,
                            teacherName = uiState.currentTeacher?.displayShortName ?: "Giáo viên",
                            todayPeriods = widgetTodayPeriods,
                            weekNumber = uiState.currentWeekNumber,
                            transparentMode = liveIsTransparent,
                            opacity = liveOpacity,
                            textTheme = prefs.widgetTextTheme,
                            cornerStyle = prefs.widgetCornerStyle,
                            fontScale = liveFontScale,
                            showSubject = prefs.widgetShowSubject,
                            isSundayPreview = uiState.isSundayPreview
                        )
                        1 -> LiveWidgetPreviewCard(
                            today = uiState.todayDate,
                            now = uiState.nowTime,
                            weather = uiState.weather,
                            todayPeriods = widgetTodayPeriods,
                            weekNumber = uiState.currentWeekNumber,
                            transparentMode = liveIsTransparent,
                            opacity = liveOpacity,
                            textTheme = prefs.widgetTextTheme,
                            cornerStyle = prefs.widgetCornerStyle,
                            fontScale = liveFontScale,
                            showSubject = prefs.widgetShowSubject
                        )
                        else -> LiveWeatherTimetablePreviewCard(
                            today = uiState.todayDate,
                            weather = uiState.weather,
                            todayPeriods = widgetTodayPeriods,
                            weekNumber = uiState.currentWeekNumber,
                            transparentMode = liveIsTransparent,
                            opacity = liveOpacity,
                            textTheme = prefs.widgetTextTheme,
                            cornerStyle = prefs.widgetCornerStyle,
                            fontScale = liveFontScale,
                            showSubject = prefs.widgetShowSubject
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trong suốt hoàn toàn",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Khử toàn bộ khung nền, hòa vào hình nền máy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = liveIsTransparent,
                            onCheckedChange = { transparent ->
                                val nextOpacity = if (transparent) 0f else 0.55f
                                liveOpacity = nextOpacity
                                saveWidgetConfig(transparent = transparent, opacity = nextOpacity)
                            },
                            modifier = Modifier.testTag("switch_widget_transparent")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Độ mờ nền Widget",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (liveIsTransparent) {
                                "0% (Trong suốt)"
                            } else {
                                "${(liveOpacity * 100).roundToInt()}%"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = liveOpacity,
                        onValueChange = { newOpacity ->
                            liveOpacity = if (newOpacity <= 0.03f) 0f else newOpacity
                        },
                        onValueChangeFinished = {
                            val isTrans = liveOpacity <= 0.02f
                            saveWidgetConfig(transparent = isTrans, opacity = if (isTrans) 0f else liveOpacity)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("widget_opacity_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Màu chữ Widget",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SegmentedOptionBar(
                        options = WidgetTextTheme.entries.map {
                            SegmentOption(it, it.label, "widget_theme_${it.key}")
                        },
                        selected = prefs.widgetTextTheme,
                        onSelect = { saveWidgetConfig(textTheme = it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Kiểu bo góc khung Widget",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SegmentedOptionBar(
                        options = WidgetCornerStyle.entries.map {
                            SegmentOption(it, it.label, "widget_corner_${it.key}")
                        },
                        selected = prefs.widgetCornerStyle,
                        onSelect = { saveWidgetConfig(corner = it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val fontPct = (liveFontScale * 100).roundToInt()
                    val fontDesc = when {
                        liveFontScale < 0.93f -> "Nhỏ"
                        liveFontScale <= 1.07f -> "Vừa"
                        liveFontScale <= 1.24f -> "Lớn"
                        else -> "Rất lớn"
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Cỡ chữ trên Widget",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$fontPct% ($fontDesc)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (fontPct != 100) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable {
                                            liveFontScale = UserPreferencesState.DEFAULT_WIDGET_FONT_SCALE
                                            saveWidgetConfig(fontScale = UserPreferencesState.DEFAULT_WIDGET_FONT_SCALE)
                                        }
                                        .testTag("widget_font_reset_button")
                                ) {
                                    Text(
                                        text = "Mặc định",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Slider(
                        value = liveFontScale,
                        onValueChange = { newScale ->
                            liveFontScale = ((newScale * 20f).roundToInt() / 20f).coerceIn(
                                UserPreferencesState.MIN_WIDGET_FONT_SCALE,
                                UserPreferencesState.MAX_WIDGET_FONT_SCALE
                            )
                        },
                        onValueChangeFinished = {
                            saveWidgetConfig(fontScale = liveFontScale)
                        },
                        valueRange = UserPreferencesState.MIN_WIDGET_FONT_SCALE..UserPreferencesState.MAX_WIDGET_FONT_SCALE,
                        steps = 13,
                        modifier = Modifier.testTag("widget_font_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nhỏ (80%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Vừa (100%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Rất lớn (150%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hiển thị tên môn trên Widget",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Tắt để chỉ hiện tên lớp gọn hơn (tiện cho GV dạy 1 môn)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.widgetShowSubject,
                            onCheckedChange = { saveWidgetConfig(showSubject = it) },
                            modifier = Modifier.testTag("switch_widget_show_subject")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onRefreshWidgetsNow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_refresh_widgets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Làm mới tất cả Widget ngay")
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Phần mềm được phát triển bởi Lê Huy Hùng - Trường THCS Lê Hữu Lập",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("settings_copyright_text")
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatchItem(
    palette: AppAccentPalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val swatchColor = when (palette) {
        AppAccentPalette.SUNSET_ORANGE -> SunsetOrange
        AppAccentPalette.EMERALD -> EmeraldAccent
        AppAccentPalette.OCEAN_BLUE -> OceanAccent
        AppAccentPalette.ROYAL_INDIGO -> IndigoAccent
        AppAccentPalette.DYNAMIC -> EmeraldLive
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("accent_palette_${palette.key}")
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(
                    if (palette == AppAccentPalette.DYNAMIC) {
                        Modifier.background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFEA580C),
                                    Color(0xFF0D9488),
                                    Color(0xFF0284C7),
                                    Color(0xFF4F46E5),
                                    Color(0xFFEA580C)
                                )
                            )
                        )
                    } else {
                        Modifier.background(swatchColor)
                    }
                )
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else Modifier
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Đã chọn ${palette.label}",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
