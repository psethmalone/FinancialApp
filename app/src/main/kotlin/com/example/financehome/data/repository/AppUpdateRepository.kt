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

    /**
     * Checks remote source (GitHub Releases or JSON manifest) for a newer app version.
     */
    suspend fun checkForUpdate(repoOrUrlOverride: String? = null): Result<AppUpdateInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val source = (repoOrUrlOverride ?: getUpdateRepo()).trim()
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

                if (source.startsWith("http://") || source.startsWith("https://")) {
                    checkCustomUrl(source)
                } else {
                    checkGithubRelease(source)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun checkGithubRelease(repo: String): Result<AppUpdateInfo?> {
        val cleanRepo = repo.removePrefix("https://github.com/").trim('/')
        val url = "https://api.github.com/repos/$cleanRepo/releases/latest"

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", "FinanceHome-Android-App")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (response.code == 404) {
            return Result.failure(Exception("No releases found on GitHub for repository '$cleanRepo'."))
        }
        if (!response.isSuccessful) {
            return Result.failure(Exception("GitHub API returned error ${response.code}: ${response.message}"))
        }

        val json = response.body?.string()
            ?: return Result.failure(Exception("Empty response received from GitHub"))

        val release = gson.fromJson(json, GithubReleaseResponse::class.java)

        // Find APK asset
        val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return Result.failure(Exception("Release '${release.tagName}' found, but no .apk asset was attached to it."))

        val remoteVersionName = release.tagName.removePrefix("v").removePrefix("V")
        val isNewer = VersionComparator.isRemoteNewer(remoteVersionName, BuildConfig.VERSION_NAME)

        val info = AppUpdateInfo(
            versionName = remoteVersionName,
            versionCode = 0,
            releaseNotes = release.body?.takeIf { it.isNotBlank() }
                ?: release.name
                ?: "New version $remoteVersionName is available.",
            downloadUrl = apkAsset.downloadUrl,
            fileSizeBytes = apkAsset.size,
            isNewer = isNewer
        )

        return Result.success(info)
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
            fileSizeBytes = manifest.fileSizeBytes ?: 0L,
            isNewer = isNewer
        )

        return Result.success(info)
    }

    /**
     * Downloads the APK file to local cache with per-chunk progress reporting.
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

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadState.Error("Download failed with HTTP ${response.code}"))
                return@flow
            }

            val responseBody = response.body
                ?: throw IllegalStateException("Empty response body from download")

            val totalBytes = responseBody.contentLength()
            var downloadedBytes = 0L

            val buffer = ByteArray(8192)
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

            emit(DownloadState.Success(destFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.localizedMessage ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)

    fun getCachedApk(): File? {
        val file = File(context.cacheDir, "updates/app-update.apk")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun clearCachedApk() {
        val file = File(context.cacheDir, "updates/app-update.apk")
        if (file.exists()) {
            file.delete()
        }
    }
}
