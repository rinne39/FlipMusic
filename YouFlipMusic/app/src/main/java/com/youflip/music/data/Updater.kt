package com.youflip.music.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** What `update.json` on the update server describes: the newest published build. */
@Serializable
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val url: String,
    val notes: String = "",
    val size: Long = 0,
    val sha256: String = "",
)

/**
 * In-app updates from our own server (player.pjsk.store): read the manifest, download the APK with
 * progress, check its SHA-256, then hand it to the system installer.
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    val currentVersionCode: Long
        get() = context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

    /** The published build if it's newer than this one, else null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(MANIFEST_URL).header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("更新服务器返回 ${r.code}")
            val info = json.decodeFromString(UpdateInfo.serializer(), r.body!!.string())
            info.takeIf { it.versionCode > currentVersionCode }
        }
    }

    /** Downloads the APK (reporting 0..1 progress) and verifies it. Returns the file. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // only ever keep the one being installed
        val file = File(dir, "FlipMusic-${info.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("下载失败（${r.code}）")
            val body = r.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); digest.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256.isNotBlank() && !hash.equals(info.sha256, ignoreCase = true)) {
            file.delete()
            throw IllegalStateException("安装包校验失败，请重试")
        }
        file
    }

    /** True once the user has allowed this app to install packages (Android asks the first time). */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val SITE = "https://player.pjsk.store"
        const val MANIFEST_URL = "$SITE/update.json"
    }
}
