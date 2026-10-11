package com.malayalamlive.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Checks GitHub for a newer release, downloads its APK and hands it to the system installer. */
class Updater(private val context: Context) {

    companion object {
        private const val LATEST_RELEASE =
            "https://api.github.com/repos/vishnuszipstack/malayalam-live-tv/releases/latest"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** The latest release if it is newer than this build, otherwise null (also on any network error). */
    suspend fun availableUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(LATEST_RELEASE)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body!!.string())
                val tag = json.getString("tag_name")
                val assets = json.getJSONArray("assets")
                val apkUrl = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.getString("browser_download_url") ?: return@use null
                val code = ReleaseInfo.versionCodeFromTag(tag) ?: return@use null
                ReleaseInfo(code, tag.removePrefix("v"), apkUrl)
            }
        }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    suspend fun download(release: ReleaseInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val target = apkFile()
        target.parentFile?.mkdirs()
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) withContext(Dispatchers.Main) { onProgress((done * 100 / total).toInt()) }
                    }
                }
            }
        }
        // Pre-N installers read the file directly, so it must be world-readable.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) target.setReadable(true, false)
        target
    }

    /** Android 8+ needs per-app permission to install; older versions use the global setting. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "install unknown apps" switch for this app. Returns false if the TV has no such screen. */
    fun openInstallPermissionSettings(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun install(activity: Activity, apk: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        } else {
            Uri.fromFile(apk)
        }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun apkFile(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            File(context.cacheDir, "updates")
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").also {
                it.mkdirs()
                it.setExecutable(true, false)
                it.setReadable(true, false)
            }
        }
        return File(dir, "MalayalamLiveTV.apk")
    }
}
