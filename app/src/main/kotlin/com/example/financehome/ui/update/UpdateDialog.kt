package com.example.financehome.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehome.BuildConfig
import com.example.financehome.data.model.AppUpdateInfo
import com.example.financehome.data.model.DownloadState
import com.example.financehome.domain.AppInstaller
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadState: DownloadState,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Progress) {
                onDismiss()
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Version ${updateInfo.versionName}  •  Current: v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (updateInfo.fileSizeBytes > 0) {
                    Text(
                        text = "Size: ${formatFileSize(updateInfo.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Release notes box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = "Release Notes:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "Performance improvements and bug fixes." },
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Download Progress / Status
                when (downloadState) {
                    is DownloadState.Idle -> {
                        // Ready to download
                    }
                    is DownloadState.Progress -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (downloadState.percentage / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading... ${downloadState.percentage}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (downloadState.totalBytes > 0) {
                                    Text(
                                        text = "${formatFileSize(downloadState.bytesDownloaded)} / ${formatFileSize(downloadState.totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is DownloadState.Success -> {
                        Text(
                            text = "✓ Download complete. Tap Install below.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is DownloadState.Error -> {
                        Text(
                            text = "Error: ${downloadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Idle -> {
                    Button(
                        onClick = onStartDownload,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download & Install")
                    }
                }
                is DownloadState.Progress -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Downloading...")
                    }
                }
                is DownloadState.Success -> {
                    Button(
                        onClick = {
                            if (!AppInstaller.canInstallPackages(context)) {
                                context.startActivity(AppInstaller.getInstallPermissionIntent(context))
                            } else {
                                AppInstaller.installApk(context, downloadState.apkFile)
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Install Now")
                    }
                }
                is DownloadState.Error -> {
                    Button(
                        onClick = onStartDownload,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState !is DownloadState.Progress) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Later")
                }
            }
        },
        modifier = modifier
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
