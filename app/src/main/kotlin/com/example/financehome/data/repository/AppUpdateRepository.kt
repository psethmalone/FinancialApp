package com.example.financehome.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.financehome.BuildConfig
import com.example.financehome.data.model.AppUpdateInfo
import com.example.financehome.data.model.DownloadState
import com.example.financehome.data.remote.GenericUpdateManifest
import com.example.financehome.data.remote.GithubReleaseResponse
import com.example.financehome.domain.VersionComparator
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("finance_home_updater_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Dedicated streaming client for downloading large APK files:
     * - Long timeouts (10 minutes) so slow mobile/Wi-Fi connections never drop mid-download
     * - No HttpLoggingInterceptor(Level.BODY), which buffers the entire 28MB in memory and freezes progress
     * - Automatic SSL and HTTP redirect following
     */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    companion object {
        private const val KEY_UPDATE_REPO = "update_repo"
        private const val KEY_AUTO_CHECK = "auto_check_updates"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        const val DEFAULT_REPO = "psethmalone/FinancialApp"
    }

    fun getUpdateRepo(): String {
        return prefs.getString(KEY_UPDATE_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun setUpdateRepo(repo: String) {
        prefs.edit().putString(KEY_UPDATE_REPO, repo.trim()).apply()
    }

    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CHECK, true)
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun getLastCheckTime(): Long {
        return prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
    }

    fun cleanGithubRepo(input: String): String {
        val trimmed = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val segments = trimmed.split('/')
        return if (segments.size >= 2) {
            "${segments[0]}/${segments[1]}"
        } else {
            trimmed
        }
    }

    /**
     * Checks remote source (GitHub Releases or JSON manifest) for a newer app version.
     */
    suspend fun checkForUpdate(repoOrUrlOverride: String? = null): Result<AppUpdateInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val source = (repoOrUrlOverride ?: getUpdateRepo()).trim()
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

                val isGithub = source.contains("github.com", ignoreCase = true) || !source.startsWith("http")
                if (isGithub) {
                    checkGithubRelease(cleanGithubRepo(source))
                } else {
                    checkCustomUrl(source)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun checkGithubRelease(repo: String): Result<AppUpdateInfo?> {
        val cleanRepo = cleanGithubRepo(repo)
        val apiUrl = "https://api.github.com/repos/$cleanRepo/releases/latest"

        // Strategy 1: Attempt GitHub API request
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "FinanceHome-Android-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                if (json != null) {
                    val release = gson.fromJson(json, GithubReleaseResponse::class.java)
                    val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    if (apkAsset != null) {
                        val remoteVersionName = release.tagName.removePrefix("v").removePrefix("V")
                        val isNewer = VersionComparator.isRemoteNewer(remoteVersionName, BuildConfig.VERSION_NAME)
                        return Result.success(
                            AppUpdateInfo(
                                versionName = remoteVersionName,
                                versionCode = 0,
                                releaseNotes = release.body?.takeIf { it.isNotBlank() }
                                    ?: release.name
                                    ?: "Version $remoteVersionName is available.",
                                downloadUrl = apkAsset.downloadUrl,
                                fileSizeBytes = apkAsset.size,
                                isNewer = isNewer
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // API failed or timed out — seamlessly try direct redirect fallback
        }

        // Strategy 2: Direct GitHub redirect fallback (no 60 req/hr rate limiting, always works!)
        return checkGithubDirectRedirect(cleanRepo)
    }

    private fun checkGithubDirectRedirect(cleanRepo: String): Result<AppUpdateInfo?> {
        try {
            val directClient = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val url = "https://github.com/$cleanRepo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .head()
                .addHeader("User-Agent", "FinanceHome-Android-App")
                .build()

            val response = directClient.newCall(request).execute()
            val redirectLocation = response.header("Location")
            if (redirectLocation != null && (response.code in 300..399)) {
                val tagName = redirectLocation.substringAfterLast("/")
                val remoteVersionName = tagName.removePrefix("v").removePrefix("V")
                val isNewer = VersionComparator.isRemoteNewer(remoteVersionName, BuildConfig.VERSION_NAME)
                val downloadUrl = "https://github.com/$cleanRepo/releases/download/$tagName/app-debug.apk"
                return Result.success(
                    AppUpdateInfo(
                        versionName = remoteVersionName,
                        versionCode = 0,
                        releaseNotes = "New version $remoteVersionName is ready on GitHub.",
                        downloadUrl = downloadUrl,
                        fileSizeBytes = 0L,
                        isNewer = isNewer
                    )
                )
            }
            return Result.failure(Exception("Could not query latest release from GitHub (status ${response.code})."))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun checkCustomUrl(url: String): Result<AppUpdateInfo?> {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "FinanceHome-Android-App")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return Result.failure(Exception("Server returned error ${response.code}: ${response.message}"))
        }

        val json = response.body?.string()
            ?: return Result.failure(Exception("Empty response received from server"))

        val manifest = gson.fromJson(json, GenericUpdateManifest::class.java)
        val isNewer = if (manifest.versionCode > 0) {
            manifest.versionCode > BuildConfig.VERSION_CODE
        } else {
            VersionComparator.isRemoteNewer(manifest.versionName, BuildConfig.VERSION_NAME)
        }

        val info = AppUpdateInfo(
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            releaseNotes = manifest.releaseNotes ?: "Update available.",
            downloadUrl = manifest.downloadUrl,
            fileSizeBytes = manifest.fileSizeBytes,
            isNewer = isNewer
        )

        return Result.success(info)
    }

    /**
     * Downloads the APK file to local app cache with verified integrity and chunked streaming.
     */
    fun downloadApk(downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destFile = File(updatesDir, "app-update.apk")
            if (destFile.exists()) {
                destFile.delete()
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "FinanceHome-Android-App")
                .build()

            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadState.Error("Download failed with HTTP ${response.code}"))
                return@flow
            }

            val responseBody = response.body
                ?: throw IllegalStateException("Empty response body from download")

            val totalBytes = responseBody.contentLength()
            var downloadedBytes = 0L

            val buffer = ByteArray(16384)
            var bytesRead: Int
            val inputStream: InputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(destFile)

            var lastReportedPercent = -1

            outputStream.use { out ->
                inputStream.use { `in` ->
                    while (`in`.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            0
                        }

                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            emit(
                                DownloadState.Progress(
                                    percentage = percent,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }
            }

            // Verify completed size if Content-Length was provided
            if (totalBytes > 0 && downloadedBytes < totalBytes) {
                destFile.delete()
                emit(DownloadState.Error("Download interrupted ($downloadedBytes / $totalBytes bytes). Please retry."))
                return@flow
            }

            // Verify package integrity using Android's PackageManager
            val packageInfo = context.packageManager.getPackageArchiveInfo(destFile.absolutePath, 0)
            if (packageInfo == null) {
                destFile.delete()
                emit(DownloadState.Error("Downloaded update is invalid or corrupted. Please retry."))
                return@flow
            }

            emit(DownloadState.Success(destFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.localizedMessage ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)

    fun getCachedApk(expectedVersionName: String? = null): File? {
        val file = File(context.cacheDir, "updates/app-update.apk")
        if (!file.exists() || file.length() == 0L) return null
        val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        if (expectedVersionName != null) {
            val cleanExpected = expectedVersionName.removePrefix("v").removePrefix("V").trim()
            val cleanActual = (pkgInfo.versionName ?: "").removePrefix("v").removePrefix("V").trim()
            if (cleanExpected.isNotEmpty() && cleanActual.isNotEmpty() && cleanExpected != cleanActual) {
                file.delete()
                return null
            }
        }
        return file
    }

    fun clearCachedApk() {
        val file = File(context.cacheDir, "updates/app-update.apk")
        if (file.exists()) {
            file.delete()
        }
    }
}
