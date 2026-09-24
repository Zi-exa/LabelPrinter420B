package com.raphael.labelprinter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class UpdateManifest(
    val version: String,
    val apkUrl: String,
    val title: String? = null,
    val notes: String? = null,
    val mandatory: Boolean = false,
)

object AppUpdateChecker {
    // Raw GitHub URL — update.json di root repo. Tambah cache-bust via ?t=timestamp saat fetch
    const val DEFAULT_MANIFEST_URL =
        "https://raw.githubusercontent.com/Zi-exa/LabelPrinter420B/master/update.json"

    private val executor = Executors.newSingleThreadExecutor()

    fun currentVersion(context: Context): String = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "0.0.0"
    } catch (_: Exception) { "0.0.0" }

    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val av = pa.getOrElse(i) { 0 }
            val bv = pb.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun checkAsync(
        manifestUrl: String = DEFAULT_MANIFEST_URL,
        currentVersion: String,
        onResult: (UpdateManifest?) -> Unit,
        onError: (Exception) -> Unit = {},
    ) {
        executor.execute {
            try {
                val url = URL("$manifestUrl?t=${System.currentTimeMillis()}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val code = conn.responseCode
                if (code !in 200..299) throw Exception("HTTP $code")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                // support flat or { android: {...}} like SUNAN
                val payload = if (json.has("android") && json.optJSONObject("android") != null) {
                    json.getJSONObject("android")
                } else json

                val version = payload.optString("version", "").trim()
                val apkUrl = payload.optString("apkUrl", "").trim()
                if (version.isEmpty() || apkUrl.isEmpty()) throw Exception("Manifest invalid")

                val manifest = UpdateManifest(
                    version = version,
                    apkUrl = apkUrl,
                    title = payload.optString("title", "").takeIf { it.isNotBlank() },
                    notes = payload.optString("notes", "").takeIf { it.isNotBlank() },
                    mandatory = payload.optBoolean("mandatory", false),
                )

                val newer = compareVersions(manifest.version, currentVersion) > 0
                onResult(if (newer) manifest else null)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun openUpdateUrl(context: Context, apkUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // fallback: try chooser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                context.startActivity(Intent.createChooser(intent, "Download Update"))
            } catch (_: Exception) { }
        }
    }
}
