package com.example.financehome.data.model

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val isNewer: Boolean
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(
        val percentage: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()
    data class Success(val apkFile: java.io.File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

sealed class UpdateCheckResult {
    object Idle : UpdateCheckResult()
    object Checking : UpdateCheckResult()
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
