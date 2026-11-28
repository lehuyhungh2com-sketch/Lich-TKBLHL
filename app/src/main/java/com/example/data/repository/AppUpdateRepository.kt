package com.example.data.repository

import android.content.Context
import com.example.data.remote.GitHubAssetDto
import com.example.data.remote.GitHubReleaseDto
import com.example.data.remote.GitHubReleasesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class VersionChangelogSection(
    val versionName: String,
    val versionCode: Int,
    val publishedDateLabel: String,
    val items: List<String>
) {
    val displayTitle: String
        get() = if (versionCode > 0) "Phiên bản $versionName (Build $versionCode)" else "Phiên bản $versionName"
}

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val publishedDateLabel: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val releasePageUrl: String,
    val changelogSections: List<VersionChangelogSection>
) {
    val formattedSize: String
        get() {
            if (apkSizeBytes <= 0L) return ""
            val mb = apkSizeBytes / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }

    val summaryPreview: String
        get() = changelogSections.firstOrNull()?.items?.firstOrNull()
            ?: "Có bản cập nhật mới với các cải tiến và sửa lỗi."
}

sealed interface UpdateCheckOutcome {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckOutcome
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Int) : UpdateCheckOutcome
    data class Error(val message: String) : UpdateCheckOutcome
}

object AppUpdateParser {

    fun parseRelease(dto: GitHubReleaseDto): ParsedRelease? {
        if (dto.draft || dto.prerelease) return null
        val apkAsset = pickApkAsset(dto.assets) ?: return null
        val versionCode = extractVersionCode(
            tagName = dto.tagName,
            releaseName = dto.name,
            assetName = apkAsset.name,
            body = dto.body
        )
        val versionName = extractVersionName(dto.tagName, dto.name)
        val changelogItems = formatChangelogLines(dto.body).ifEmpty {
            listOf(
                dto.name?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Cập nhật cải tiến hiệu năng và tối ưu trải nghiệm."
            )
        }
        return ParsedRelease(
            versionCode = versionCode,
            versionName = versionName,
            title = dto.name?.trim().orEmpty().ifBlank { "Phiên bản $versionName" },
            publishedDateLabel = formatIsoDateToVn(dto.publishedAt),
            apkDownloadUrl = apkAsset.browserDownloadUrl,
            apkFileName = apkAsset.name.ifBlank { "Lich-TKB-LHL-v$versionName.apk" },
            apkSizeBytes = apkAsset.size,
            releasePageUrl = dto.htmlUrl.ifBlank { GitHubReleasesApi.RELEASES_PAGE_URL },
            changelogItems = changelogItems
        )
    }

    fun findPendingUpdate(
        releases: List<GitHubReleaseDto>,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo? {
        val parsed = releases.mapNotNull(::parseRelease)
        val newer = parsed
            .filter { rel ->
                isNewerRelease(
                    remoteCode = rel.versionCode,
                    remoteName = rel.versionName,
                    currentCode = currentVersionCode,
                    currentName = currentVersionName
                )
            }
            .sortedWith(
                compareByDescending<ParsedRelease> { it.versionCode }
                    .thenComparator { a, b -> compareSemanticVersions(b.versionName, a.versionName) }
            )

        val latest = newer.firstOrNull() ?: return null
        val sections = newer.take(6).map { rel ->
            VersionChangelogSection(
                versionName = rel.versionName,
                versionCode = rel.versionCode,
                publishedDateLabel = rel.publishedDateLabel,
                items = rel.changelogItems
            )
        }
        return AppUpdateInfo(
            versionCode = latest.versionCode,
            versionName = latest.versionName,
            title = latest.title,
            publishedDateLabel = latest.publishedDateLabel,
            apkDownloadUrl = latest.apkDownloadUrl,
            apkFileName = latest.apkFileName,
            apkSizeBytes = latest.apkSizeBytes,
            releasePageUrl = latest.releasePageUrl,
            changelogSections = sections
        )
    }

    fun pickApkAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        val apks = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        }
        if (apks.isEmpty()) return null
        return apks.firstOrNull { !it.name.contains("unsigned", ignoreCase = true) } ?: apks.first()
    }

    fun extractVersionCode(
        tagName: String,
        releaseName: String?,
        assetName: String?,
        body: String?
    ): Int {
        val tagMatch = Regex("(?:^|[-_])(?:b|vc|build)(\\d+)", RegexOption.IGNORE_CASE)
            .find(tagName)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (tagMatch != null && tagMatch > 0) return tagMatch

        val assetMatch = assetName?.let {
            Regex("[-_]vc(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        if (assetMatch != null && assetMatch > 0) return assetMatch

        val titleMatch = releaseName?.let {
            Regex("Build\\s*(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        if (titleMatch != null && titleMatch > 0) return titleMatch

        val bodyMatch = body?.let {
            Regex("(?:versionCode|Build)\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        return bodyMatch ?: 0
    }

    fun extractVersionName(tagName: String, releaseName: String?): String {
        val cleanTag = tagName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .replace(Regex("[-_](?:b|vc|build)\\d+$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanTag.isNotBlank() && cleanTag.first().isDigit()) {
            return cleanTag
        }
        val fromTitle = releaseName?.let {
            Regex("v?(\\d+(?:\\.\\d+)+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)
        }
        return fromTitle ?: cleanTag.ifBlank { "Mới" }
    }

    fun isNewerRelease(
        remoteCode: Int,
        remoteName: String,
        currentCode: Int,
        currentName: String
    ): Boolean {
        if (remoteCode > 0 && currentCode > 0) {
            if (remoteCode != currentCode) return remoteCode > currentCode
            return compareSemanticVersions(remoteName, currentName) > 0
        }
        return compareSemanticVersions(remoteName, currentName) > 0
    }

    fun compareSemanticVersions(v1: String, v2: String): Int {
        val parts1 = Regex("\\d+").findAll(v1).map { it.value.toIntOrNull() ?: 0 }.toList()
        val parts2 = Regex("\\d+").findAll(v2).map { it.value.toIntOrNull() ?: 0 }.toList()
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    fun formatIsoDateToVn(isoString: String?): String {
        val match = isoString?.let { Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(it) } ?: return ""
        val year = match.groupValues[1]
        val month = match.groupValues[2]
        val day = match.groupValues[3]
        return "$day/$month/$year"
    }

    fun formatChangelogLines(markdown: String?): List<String> {
        if (markdown.isNullOrBlank()) return emptyList()
        val result = mutableListOf<String>()
        for (rawLine in markdown.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty() || line == "---" || line == "***") continue
            if (line.startsWith("#")) {
                line = line.trimStart('#', ' ')
                // Skip redundant version heading line if it only says vX.Y (Build N)
                if (Regex("^v?\\d+(?:\\.\\d+)+(?:\\s*\\(Build\\s*\\d+\\))?$", RegexOption.IGNORE_CASE).matches(line)) {
                    continue
                }
            }
            if (Regex("^(?:Phiên bản|versionCode)\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                continue
            }
            line = line
                .removePrefix("- ")
                .removePrefix("* ")
                .removePrefix("• ")
                .replace(Regex("^\\+\\s+"), "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim()
            if (line.isNotBlank()) {
                result.add(line)
            }
        }
        return result
    }

    data class ParsedRelease(
        val versionCode: Int,
        val versionName: String,
        val title: String,
        val publishedDateLabel: String,
        val apkDownloadUrl: String,
        val apkFileName: String,
        val apkSizeBytes: Long,
        val releasePageUrl: String,
        val changelogItems: List<String>
    )
}

class AppUpdateRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = GitHubReleasesApi.createClient(),
    private val api: GitHubReleasesApi = GitHubReleasesApi.create(httpClient)
) {

    suspend fun checkForUpdate(
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val releases = api.getReleases()
            val pending = AppUpdateParser.findPendingUpdate(
                releases = releases,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )
            if (pending != null) {
                UpdateCheckOutcome.UpdateAvailable(pending)
            } else {
                UpdateCheckOutcome.UpToDate(currentVersionName, currentVersionCode)
            }
        }.getOrElse { err ->
            UpdateCheckOutcome.Error(
                "Không kiểm tra được bản cập nhật (${err.localizedMessage ?: "lỗi kết nối mạng"})."
            )
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        fileName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "app_updates").apply { mkdirs() }
            // Clean previous downloaded APKs before saving the new one
            updatesDir.listFiles()?.forEach { old ->
                runCatching { old.delete() }
            }

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
            val tempFile = File(updatesDir, "$safeName.tmp")
            val finalFile = File(updatesDir, safeName)

            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Máy chủ phản hồi mã ${response.code}")
                }
                val body = response.body ?: error("Tệp tải về trống")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalBytes)
                        }
                        output.flush()
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() < 1024L) {
                tempFile.delete()
                error("File APK tải về không hợp lệ.")
            }
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            finalFile
        }
    }
}
