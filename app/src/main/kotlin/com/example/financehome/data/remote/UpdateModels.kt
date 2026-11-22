package com.example.financehome.data.remote

import com.google.gson.annotations.SerializedName

data class GithubReleaseResponse(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("assets") val assets: List<GithubAssetResponse>? = null
)

data class GithubAssetResponse(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long,
    @SerializedName("content_type") val contentType: String? = null
)

data class GenericUpdateManifest(
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("releaseNotes") val releaseNotes: String? = null,
    @SerializedName("fileSizeBytes") val fileSizeBytes: Long = 0L
)
