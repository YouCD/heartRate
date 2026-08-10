package online.youcd.heartrate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.youcd.heartrate.MainActivity
import online.youcd.heartrate.R
import online.youcd.heartrate.data.ble.BleManager
import online.youcd.heartrate.data.ble.BleManager.ConnectionState
import javax.inject.Inject

@AndroidEntryPoint
class HeartRateService : LifecycleService() {

    @Inject
    lateinit var bleManager: BleManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0, isConnected = false))

        scope.launch {
            bleManager.heartRate.collectLatest { bpm ->
                NotificationManagerCompat.from(this@HeartRateService)
                    .notify(NOTIFICATION_ID, buildNotification(bpm, isConnected = true))
            }
        }
        scope.launch {
            bleManager.connectionState
                .collectLatest { state ->
                    NotificationManagerCompat.from(this@HeartRateService)
                        .notify(
                            NOTIFICATION_ID,
                            buildNotification(0, isConnected = state is ConnectionState.Connected)
                        )
                }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(bpm: Int, isConnected: Boolean): Notification {
        val contentText = if (bpm > 0) {
            "实时心率：$bpm bpm"
        } else if (isConnected) {
            "等待心率数据..."
        } else {
            "未连接心率带"
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "heart_rate_monitor"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, HeartRateService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeartRateService::class.java))
        }
    }
}
