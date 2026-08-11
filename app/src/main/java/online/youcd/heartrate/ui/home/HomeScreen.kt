package online.youcd.heartrate.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import online.youcd.heartrate.data.ble.BleManager
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.data.session.SessionManager.SessionState
import online.youcd.heartrate.ui.components.HeartRateBar
import online.youcd.heartrate.ui.components.HeartbeatECGBackground
import online.youcd.heartrate.ui.scan.ScanDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val currentBpm by viewModel.currentBpm.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val elapsedMillis by viewModel.elapsedMillis.collectAsState()
    val stats by viewModel.sessionStats.collectAsState()
    val maxHr by viewModel.maxHr.collectAsState()
    val floatingEnabled by viewModel.floatingEnabled.collectAsState()
    val heartRateTick by viewModel.heartRateTick.collectAsState()

    var showScan by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showStopSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val zone = if (currentBpm > 0) HeartRateZone.from(currentBpm, maxHr) else null
    val zoneColor = zone?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurface
    val isConnected = connectionState is BleManager.ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        DeviceCard(
            connectionState = connectionState,
            deviceName = connectedDeviceName,
            floatingEnabled = floatingEnabled,
            canDrawOverlays = viewModel.isOverlayPermissionGranted(),
            onOpenScan = { showScan = true },
            onDisconnectRequest = { showDisconnectConfirm = true },
            onToggleFloating = {
                if (!viewModel.isOverlayPermissionGranted()) {
                    overlayPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } else {
                    viewModel.toggleFloatingWindow()
                }
            }
        )

        Spacer(Modifier.height(14.dp))

        // 当前心率
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = zoneColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "当前心率",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 心电图背景 + 心率数字
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HeartbeatECGBackground(
                        bpm = currentBpm,
                        tick = heartRateTick,
                        color = zoneColor,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BpmNumber(bpm = currentBpm, color = zoneColor)
                        Text(
                            text = "BPM",
                            fontSize = 16.sp,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HeartRateBar(
                    bpm = currentBpm,
                    maxHr = maxHr,
                    zoneSeconds = stats.zoneSeconds,
                    zoneLabel = { zoneLabel(it) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 训练时长
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "训练时长",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatElapsed(elapsedMillis),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (sessionState == SessionState.PAUSED) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SessionControls(
            sessionState = sessionState,
            isConnected = isConnected,
            onStart = { viewModel.startSession() },
            onPause = { viewModel.pauseSession() },
            onResume = { viewModel.resumeSession() },
            onStopRequest = { showStopSheet = true }
        )

        Spacer(Modifier.height(14.dp))

        // 心率区间列表
        ZoneListCard(
            maxHr = maxHr,
            zoneSeconds = stats.zoneSeconds,
            activeZoneId = zone?.id,
            isConnected = isConnected,
            zoneLabel = ::zoneLabel
        )

        Spacer(Modifier.height(14.dp))

        // 卡路里 / 平均 / 最大
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatPill(
                label = "消耗热量",
                value = "${stats.calories}",
                unit = "kcal",
                modifier = Modifier.weight(1.2f)
            )
            StatPill(
                label = "平均",
                value = if (stats.avg > 0) stats.avg.toString() else "--",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "最大",
                value = if (stats.max > 0) stats.max.toString() else "--",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showScan) {
        ScanDialog(
            onDismiss = { showScan = false },
            onConnected = { showScan = false }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("断开连接？") },
            text = { Text("断开后将停止接收心率数据。") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    viewModel.disconnect()
                }) {
                    Text("断开", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showStopSheet) {
        StopSessionSheet(
            elapsed = elapsedMillis,
            stats = stats,
            onDismiss = { showStopSheet = false },
            onConfirm = {
                showStopSheet = false
                viewModel.stopSession()
            }
        )
    }
}
