package online.youcd.heartrate.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import online.youcd.heartrate.data.ble.BleManager
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.ui.components.HeartRateBar
import online.youcd.heartrate.ui.components.HeartbeatECGBackground
import online.youcd.heartrate.ui.home.HomeViewModel.SessionState
import online.youcd.heartrate.ui.scan.ScanDialog
import online.youcd.heartrate.ui.theme.SuccessGreen
import java.util.Locale

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
            zoneSeconds = stats.zoneSeconds,
            activeZoneId = zone?.id,
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

@Composable
private fun BpmNumber(bpm: Int, color: Color) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bpm) {
        if (bpm > 0) {
            scale.animateTo(1.07f, animationSpec = tween(80))
            scale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
    Box(modifier = Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }) {
        Text(
            text = if (bpm > 0) bpm.toString() else "--",
            fontSize = 108.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeviceCard(
    connectionState: BleManager.ConnectionState,
    deviceName: String?,
    floatingEnabled: Boolean,
    canDrawOverlays: Boolean,
    onOpenScan: () -> Unit,
    onDisconnectRequest: () -> Unit,
    onToggleFloating: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionDot(connectionState = connectionState)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (connectionState) {
                            is BleManager.ConnectionState.Connected -> deviceName ?: "心率带"
                            is BleManager.ConnectionState.Connecting -> deviceName ?: "正在连接"
                            is BleManager.ConnectionState.Error -> deviceName ?: "连接失败"
                            else -> "未连接设备"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = when (connectionState) {
                            is BleManager.ConnectionState.Connected -> "已连接 · 蓝牙信号良好"
                            is BleManager.ConnectionState.Connecting -> "正在连接..."
                            is BleManager.ConnectionState.Error -> connectionState.message
                            else -> "点击连接心率带"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (connectionState is BleManager.ConnectionState.Connected) {
                    OutlinedButton(
                        onClick = onDisconnectRequest,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("断开")
                    }
                } else {
                    Button(
                        onClick = onOpenScan,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("连接")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = if (floatingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "心率悬浮窗",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            floatingEnabled -> "已开启，显示实时心率"
                            !canDrawOverlays -> "需要开启悬浮窗权限"
                            else -> "在其他应用上方显示"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = floatingEnabled,
                    onCheckedChange = { onToggleFloating() }
                )
            }
        }
    }
}

@Composable
private fun ConnectionDot(connectionState: BleManager.ConnectionState) {
    val isConnected = connectionState is BleManager.ConnectionState.Connected
    val color = if (isConnected) SuccessGreen else MaterialTheme.colorScheme.outline

    if (isConnected) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
            label = "alpha"
        )
        val pulseScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
            label = "scale"
        )
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.4f))
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun SessionControls(
    sessionState: SessionState,
    isConnected: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        when (sessionState) {
            SessionState.IDLE -> {
                Button(
                    onClick = onStart,
                    enabled = isConnected,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "开始训练" else "请先连接设备",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SessionState.RUNNING -> {
                FilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("暂停", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onStopRequest,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("结束", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            SessionState.PAUSED -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("继续", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStopRequest,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("结束", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ZoneListCard(
    zoneSeconds: List<Int>,
    activeZoneId: Int?,
    zoneLabel: (Int) -> String
) {
    val zones = HeartRateZone.ZONES
    val total = zoneSeconds.sum().coerceAtLeast(1)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "心率区间",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            zones.forEach { zone ->
                val seconds = zoneSeconds.getOrElse(zone.id - 1) { 0 }
                val pct = seconds * 100f / total
                val isActive = zone.id == activeZoneId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                Color(zone.color).copy(alpha = if (isActive) 1f else 0.35f)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = zoneLabel(zone.id),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Color(zone.color).copy(alpha = if (isActive) 1f else 0.5f)
                                )
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (seconds > 0) formatZoneSeconds(seconds) else "--:--",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(72.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSessionSheet(
    elapsed: Long,
    stats: HomeViewModel.SessionStats,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "确认结束训练？",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SheetStat("时长", formatElapsed(elapsed))
                SheetStat("平均", if (stats.avg > 0) "${stats.avg}" else "--")
                SheetStat("消耗", "${stats.calories} kcal")
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("确认结束", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SheetStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun zoneLabel(id: Int): String = when (id) {
    1 -> "恢复区"
    2 -> "热身区"
    3 -> "燃脂区"
    4 -> "有氧区"
    else -> "极限区"
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatZoneSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
