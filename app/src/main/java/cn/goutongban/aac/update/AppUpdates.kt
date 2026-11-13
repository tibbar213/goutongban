package cn.goutongban.aac.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import cn.goutongban.aac.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class UpdateFileProvider : FileProvider()

internal data class AppRelease(val version: String, val url: String, val size: Long, val sha256: String)
internal data class UpdateState(
    val release: AppRelease? = null,
    val file: File? = null,
    val busy: Boolean = false,
    val message: String = ""
)

internal class AppUpdates(
    private val context: Context,
    private val manifestUrls: List<String> = metadataUrls
) {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).followSslRedirects(false).build()
    private val probeClient = client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    private val metadataClient = client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        var reachable = false
        var latest: AppRelease? = null
        for ((index, url) in manifestUrls.withIndex()) {
            ensureActive()
            try {
                val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
                val release = metadataClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Release request failed")
                    val body = response.body?.source() ?: throw IOException("Empty release response")
                    if (body.request(131_073)) {
                        throw IOException("Release response too large")
                    }
                    parseRelease(body.readUtf8(), BuildConfig.VERSION_NAME)
                }
                if (index == 0) return@withContext release
                reachable = true
                if (release != null && (latest == null || compareVersions(release.version, latest.version) > 0)) {
                    latest = release
                }
            } catch (_: IOException) {
                continue
            } catch (_: org.json.JSONException) {
                continue
            }
        }
        if (!reachable) throw IOException("Could not reach GitHub Releases")
        latest
    }

    suspend fun download(release: AppRelease, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val urls = routes.map { it + release.url }
        val ranked = coroutineScope {
            urls.map { url -> async { url to sampleSpeed(url) } }.awaitAll().sortedByDescending { it.second }
        }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(directory, "goutongban-${release.version}.apk")
        val partial = File.createTempFile("update-", ".apk", directory)
        for ((url, _) in ranked) {
            ensureActive()
            try {
                onProgress(0)
                downloadFrom(url, partial, release, onProgress)
                verifyPackage(partial, release.version)
                if (!partial.renameTo(apk)) throw IOException("Could not save update")
                directory.listFiles()?.filter { it != apk && it.name.startsWith("goutongban-") }
                    ?.forEach { it.delete() }
                return@withContext apk
            } catch (_: IOException) {
                continue
            } finally {
                partial.delete()
            }
        }
        throw IOException("No download route returned a valid update")
    }

    private fun sampleSpeed(url: String): Long {
        val start = System.nanoTime()
        return try {
            val request = Request.Builder().url(url).header("Range", "bytes=0-131071").build()
            probeClient.newCall(request).execute().use { response ->
                val body = response.body?.source() ?: return 0
                if (!response.isSuccessful || !body.request(131_072)) return 0
                val prefix = body.peek().readByteArray(4)
                if (!prefix.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) return 0
                131_072_000_000L / ((System.nanoTime() - start) / 1_000).coerceAtLeast(1)
            }
        } catch (_: IOException) {
            0
        }
    }

    private suspend fun downloadFrom(url: String, file: File, release: AppRelease, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Empty APK response")
            if (!response.isSuccessful || (body.contentLength() >= 0 && body.contentLength() != release.size)) {
                throw IOException("Unexpected APK response")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var percent = 0
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > release.size) throw IOException("APK exceeded expected size")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val nextPercent = (100 * received / release.size).toInt()
                        if (nextPercent != percent) {
                            percent = nextPercent
                            onProgress(percent)
                        }
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (received != release.size || sha256 != release.sha256) throw IOException("APK checksum mismatch")
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, version: String) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = manager.getPackageInfo(context.packageName, flags)
        val update = manager.getPackageArchiveInfo(file.path, flags) ?: throw IOException("Invalid APK")
        val updateVersion = if (Build.VERSION.SDK_INT >= 28) update.longVersionCode else update.versionCode.toLong()
        if (update.packageName != current.packageName || update.versionName != version ||
            updateVersion <= BuildConfig.VERSION_CODE || signers(update).isEmpty() || signers(update) != signers(current)
        ) throw IOException("APK is not a signed update for this app")
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners.orEmpty().toSet()
    } else info.signatures.orEmpty().toSet()

    companion object {
        private const val Repository = "tibbar213/goutongban"
        private const val Asset = "goutongban.apk"
        private val routes = listOf("", "https://ghfast.top/", "https://ghproxy.net/", "https://gh-proxy.com/")
        private val metadataUrls = listOf(
            "https://api.github.com/repos/$Repository/releases/latest",
            "https://cdn.jsdelivr.net/gh/$Repository@main/update.json",
            "https://raw.githubusercontent.com/$Repository/main/update.json",
            "https://ghfast.top/https://raw.githubusercontent.com/$Repository/main/update.json"
        )
        private val versionTag = Regex("v(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})")
        private val digestPattern = Regex("sha256:[a-f0-9]{64}")

        internal fun parseRelease(json: String, installed: String): AppRelease? {
            val release = JSONObject(json)
            val tag = release.optString("tag_name")
            val version = versionTag.matchEntire(tag) ?: throw IOException("Unsupported release tag")
            val current = versionTag.matchEntire("v$installed") ?: throw IOException("Unsupported installed version")
            val difference = compareVersions(tag.removePrefix("v"), current.value.removePrefix("v"))
            if (release.optBoolean("draft") || release.optBoolean("prerelease") || difference <= 0) return null
            val assets = release.getJSONArray("assets")
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.optString("name") != Asset || asset.optString("state") != "uploaded") continue
                val url = asset.optString("browser_download_url")
                val digest = asset.optString("digest")
                val size = asset.optLong("size")
                if (url == "https://github.com/$Repository/releases/download/$tag/$Asset" &&
                    digestPattern.matches(digest) && size in 1..150_000_000
                ) return AppRelease(tag.removePrefix("v"), url, size, digest.removePrefix("sha256:"))
            }
            throw IOException("Release has no verifiable APK")
        }

        private fun compareVersions(first: String, second: String): Int =
            first.split('.').zip(second.split('.'))
                .map { (latest, local) -> latest.toInt() - local.toInt() }.firstOrNull { it != 0 } ?: 0
    }
}
