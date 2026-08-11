package online.youcd.heartrate.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * 后台下载 APK 更新包，下载完成后弹出系统安装界面。
 * 使用系统 DownloadManager 下载（支持后台、通知栏进度），
 * 完成后通过 FileProvider 唤起系统安装器。
 */
object UpdateDownloader {

    private const val DOWNLOAD_DIR = "downloads"
    private const val FILE_NAME = "HeartRate-latest.apk"

    /**
     * 开始后台下载。
     * @param downloadUrl 完整下载地址（已含 gh-proxy.com 代理）
     * @param title 通知栏标题
     */
    fun download(context: Context, downloadUrl: String, title: String = "HeartRate 更新") {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(title)
            .setDescription("正在下载最新版本，完成后点击安装")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, DOWNLOAD_DIR, FILE_NAME)
            .setMimeType("application/vnd.android.package-archive")
        try {
            val id = dm.enqueue(request)
            // 注册广播监听下载完成
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (downloadId == id) {
                        ctx.unregisterReceiver(this)
                        val file = File(ctx.getExternalFilesDir(DOWNLOAD_DIR), FILE_NAME)
                        if (file.exists()) {
                            installApk(ctx, file)
                        }
                    }
                }
            }
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        } catch (_: Exception) {
            // 下载失败静默处理
        }
    }

    /**
     * 通过 FileProvider 唤起系统安装器安装 APK。
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
