package online.youcd.heartrate.data.update

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub 仓库最新版本检查与下载地址生成。
 * - 通过 GitHub API 获取最新 release
 * - 使用 gh-proxy.com 代理拼接 APK 下载地址（解决国内网络访问 GitHub 慢的问题）
 */
data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {

    private const val REPO_API = "https://api.github.com/repos/YouCD/heartRate/releases/latest"
    private const val PROXY_BASE = "https://gh-proxy.com/"

    /**
     * 检查是否有新版本。网络请求在 IO 线程执行。
     * @return 返回最新版本信息；无网络或解析失败返回 null
     */
    suspend fun checkForUpdate(): UpdateInfo? = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.IO
    ) {
        runCatching {
            val conn = URL(REPO_API).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "HeartRate-Android")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode != 200) return@runCatching null

                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v")
                val name = json.optString("name", tag)
                val bodyText = json.optString("body", "")

                // 从 assets 中找 APK 下载链接
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val aName = asset.optString("name", "")
                        if (aName.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                if (apkUrl == null) return@runCatching null

                UpdateInfo(
                    versionName = tag.ifBlank { name },
                    releaseUrl = json.optString("html_url", ""),
                    downloadUrl = proxyUrl(apkUrl),
                    releaseNotes = bodyText.ifBlank { "发现新版本，点击下载更新。" }
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * 版本号比较：支持 "1.0.0"、"1.2.3" 形式。
     * @return true 表示 [newVersion] 比 [currentVersion] 新
     */
    fun isNewer(newVersion: String, currentVersion: String): Boolean {
        fun parse(v: String): List<Int> =
            v.split(".").mapNotNull { it.toIntOrNull() }
        val a = parse(newVersion)
        val b = parse(currentVersion)
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * 将 GitHub 下载地址包装为 gh-proxy.com 代理地址。
     * 例如：
     * https://github.com/YouCD/heartRate/releases/download/v1.0.0/HeartRate-v1.0.0.apk
     * ->
     * https://gh-proxy.com/https://github.com/YouCD/heartRate/releases/download/v1.0.0/HeartRate-v1.0.0.apk
     */
    fun proxyUrl(originalUrl: String): String =
        "$PROXY_BASE$originalUrl"
}
