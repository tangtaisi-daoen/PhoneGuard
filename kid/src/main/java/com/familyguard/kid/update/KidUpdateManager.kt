package com.familyguard.kid.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.familyguard.core.update.UpdateHttpClient
import com.familyguard.core.update.UpdateManifest
import com.familyguard.core.update.UpdateManifestCheck
import com.familyguard.core.update.UpdateManifestVerifier
import com.familyguard.kid.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.Base64

sealed interface KidUpdateResult {
    data object UpToDate : KidUpdateResult
    data class Ready(val manifest: UpdateManifest, val apk: File) : KidUpdateResult
    data class Failed(val reason: String) : KidUpdateResult
}

sealed interface KidUpdateAvailability {
    data object UpToDate : KidUpdateAvailability
    data class Available(val manifest: UpdateManifest) : KidUpdateAvailability
    data class Failed(val reason: String) : KidUpdateAvailability
}

object KidUpdateManager {
    const val ACTION_CHECK_UPDATE = "com.familyguard.kid.action.CHECK_UPDATE"
    // 自托管：构建时注入（-PupdateManifestUrl）；占位符时禁用更新检查
    private val MANIFEST_URL: String? = BuildConfig.UPDATE_MANIFEST_URL.takeIf { !it.startsWith("YOUR_") }
    private const val EXPECTED_PACKAGE = "com.familyguard.kid"
    private const val MAX_APK_OVERHEAD_BYTES = 1024L
    private const val UPDATE_DIRECTORY = "verified-updates"
    private const val UPDATE_APK_NAME = "kid-update.apk"
    private const val PUBLIC_KEY_DER_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEm0Oju++9WsBDfX15UJhPjNt/wGmU9ApxjOaNToumWxCl0RY8iSto0vgmIVzhAaXE3/BuZKUunx9n1Ud9C06ObA=="

    suspend fun checkAvailability(context: Context): KidUpdateAvailability {
        val url = MANIFEST_URL ?: return KidUpdateAvailability.Failed("未配置更新清单地址（自托管需构建注入）")
        val manifest = UpdateHttpClient.fetchManifest(url)
            ?: return KidUpdateAvailability.Failed("无法获取更新清单")
        val check = UpdateManifestVerifier.verify(
            manifest,
            Base64.getDecoder().decode(PUBLIC_KEY_DER_BASE64),
            EXPECTED_PACKAGE,
            installedVersionCode(context),
            System.currentTimeMillis(),
        )
        return when (check) {
            UpdateManifestCheck.NotNewer -> KidUpdateAvailability.UpToDate
            UpdateManifestCheck.Accepted -> KidUpdateAvailability.Available(manifest)
            else -> KidUpdateAvailability.Failed("更新清单校验失败：${check.javaClass.simpleName}")
        }
    }

    suspend fun checkAndDownload(context: Context): KidUpdateResult {
        val url = MANIFEST_URL ?: return KidUpdateResult.Failed("未配置更新清单地址（自托管需构建注入）")
        val manifest = UpdateHttpClient.fetchManifest(url)
            ?: return KidUpdateResult.Failed("无法获取更新清单")
        val installed = installedVersionCode(context)
        val check = UpdateManifestVerifier.verify(
            manifest,
            Base64.getDecoder().decode(PUBLIC_KEY_DER_BASE64),
            EXPECTED_PACKAGE,
            installed,
            System.currentTimeMillis(),
        )
        if (check == UpdateManifestCheck.NotNewer) return KidUpdateResult.UpToDate
        if (check != UpdateManifestCheck.Accepted) {
            return KidUpdateResult.Failed("更新清单校验失败：${check.javaClass.simpleName}")
        }
        val updateDir = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
        val target = File(updateDir, UPDATE_APK_NAME)
        target.delete()
        val downloaded = UpdateHttpClient.downloadApk(
            manifest.url,
            target,
            manifest.sizeBytes + MAX_APK_OVERHEAD_BYTES,
        ) ?: return KidUpdateResult.Failed("下载更新失败")
        if (downloaded.sizeBytes != manifest.sizeBytes ||
            !downloaded.sha256.equals(manifest.apkSha256, ignoreCase = true)
        ) {
            target.delete()
            return KidUpdateResult.Failed("安装包大小或 SHA-256 不匹配")
        }
        val archiveError = verifyArchive(context, manifest, target)
        if (archiveError != null) {
            target.delete()
            return KidUpdateResult.Failed(archiveError)
        }
        return KidUpdateResult.Ready(manifest, target)
    }

    fun requestInstall(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    /** 已验证 APK 位于应用私有缓存中；若被系统清理则返回 null 并由后台重新下载。 */
    fun preparedApk(context: Context): File? =
        File(File(context.cacheDir, UPDATE_DIRECTORY), UPDATE_APK_NAME)
            .takeIf { it.isFile && it.length() > 0L }

    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    private fun verifyArchive(context: Context, manifest: UpdateManifest, apk: File): String? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return "无法解析安装包"
        if (info.packageName != EXPECTED_PACKAGE) return "安装包包名不匹配"
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        if (version != manifest.versionCode) return "安装包版本不匹配"
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION") info.signatures?.map { it.toByteArray() }.orEmpty()
        }
        val expectedCert = manifest.signingCertSha256.lowercase()
        if (certs.none { sha256(it) == expectedCert }) return "安装包签名证书不匹配"
        return null
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
