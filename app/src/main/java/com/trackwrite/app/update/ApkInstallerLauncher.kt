package com.trackwrite.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class ApkInstallerLauncher(
    private val context: Context,
) {
    fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(): Boolean =
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ),
        )

    fun launchInstaller(apkFile: File, originatingUrl: String): Boolean {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
        return startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(apkUri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_ORIGINATING_URI, Uri.parse(originatingUrl)),
        )
    }

    private fun startActivity(intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
