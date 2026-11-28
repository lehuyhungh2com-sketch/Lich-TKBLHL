package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    @Json(name = "content_type") val contentType: String? = null
)

data class GitHubReleaseDto(
    val id: Long = 0L,
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

interface GitHubReleasesApi {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String = REPO_OWNER,
        @Path("repo") repo: String = REPO_NAME,
        @Query("per_page") perPage: Int = 10
    ): List<GitHubReleaseDto>

    companion object {
        const val REPO_OWNER = "lehuyhungh2com-sketch"
        const val REPO_NAME = "Lich-TKBLHL"
        const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

        fun createClient(): OkHttpClient {
            val headerInterceptor = Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LhlTimetableAndroidApp")
                    .build()
                chain.proceed(req)
            }
            return OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        fun create(client: OkHttpClient = createClient()): GitHubReleasesApi {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GitHubReleasesApi::class.java)
        }
    }
}
