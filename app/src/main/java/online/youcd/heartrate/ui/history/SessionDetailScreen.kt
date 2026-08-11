package online.youcd.heartrate.ui.history

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.ui.components.AnimatedNumber
import online.youcd.heartrate.ui.components.DonutChart
import online.youcd.heartrate.ui.components.HeartRateChart
import online.youcd.heartrate.ui.components.XAxisMode

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val heartRates by viewModel.heartRates.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var xAxisMode by remember { mutableStateOf(XAxisMode.TRAINING_TIME) }
    val shareLayer = rememberGraphicsLayer()
    val shareBg = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "训练回顾",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                session?.let {
                    Text(
                        text = formatDate(it.startTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            session?.let { s ->
                IconButton(onClick = {
                    scope.launch {
                        shareSessionScreenshot(context, shareLayer)
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "分享")
                }
            }
        }

        val s = session ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .drawWithContent {
                    val contentScope = this
                    shareLayer.record {
                        drawRect(color = shareBg)
                        contentScope.drawContent()
                    }
                    contentScope.drawContent()
                }
        ) {            // Hero 消耗热量
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "消耗热量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumber(
                            target = s.calories,
                            fontSize = 52.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "千卡",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 次级数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailPill("时长", formatDuration(s.durationMillis), Modifier.weight(1f))
                DetailPill("平均", "${s.avgBpm} BPM", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailPill("最高", "${s.maxBpm} BPM", Modifier.weight(1f))
                DetailPill("最低", "${s.minBpm} BPM", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // 心率变化趋势
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "心率变化趋势",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.FilterChip(
                    selected = xAxisMode == XAxisMode.TRAINING_TIME,
                    onClick = { xAxisMode = XAxisMode.TRAINING_TIME },
                    label = { Text("训练时间", style = MaterialTheme.typography.bodySmall) }
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.FilterChip(
                    selected = xAxisMode == XAxisMode.CLOCK_TIME,
                    onClick = { xAxisMode = XAxisMode.CLOCK_TIME },
                    label = { Text("自然时间", style = MaterialTheme.typography.bodySmall) }
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                if (heartRates.size >= 2) {
                    val maxHr = if (s.maxHr > 0) s.maxHr else (s.maxBpm * 1.2).toInt()
                    HeartRateChart(
                        samples = heartRates.map { it.bpm },
                        maxHr = maxHr,
                        lineColor = Color(s.maxBpmColor(s.maxHr)),
                        timestamps = heartRates.map { it.timestamp },
                        xAxisMode = xAxisMode,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("数据不足", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 训练总结
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("❤", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本次训练总结",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = buildSummary(s),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 强度区间分布
            Text(
                text = "强度区间分布",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                val zoneMap = parseZoneSeconds(s.zoneSeconds)
                val totalSeconds = zoneMap.values.sum()
                val mainZone = zoneMap.maxByOrNull { it.value }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DonutChart(
                        zoneSeconds = HeartRateZone.ZONES.map {
                            zoneMap[it.displayName] ?: 0
                        },
                        centerLabel = mainZone?.let { zoneLabelFromName(it.key) } ?: "无数据",
                        centerValue = if (totalSeconds > 0) {
                            val top = mainZone?.value ?: 0
                            "${top * 100 / totalSeconds}%"
                        } else {
                            "--"
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                    HeartRateZone.ZONES.forEach { zone ->
                        val seconds = zoneMap[zone.displayName] ?: 0
                        val pct = if (totalSeconds > 0) seconds * 100f / totalSeconds else 0f
                        val hasData = seconds > 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color(zone.color).copy(alpha = if (hasData) 1f else 0.3f)
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = zoneLabelFromName(zone.displayName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.width(72.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                            ) {
                                if (hasData) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(pct / 100f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(zone.color))
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (hasData) formatSeconds(seconds) else "--",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (hasData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 底部操作
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Home, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("返回首页", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("删除记录", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条训练记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSession()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DetailPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(64.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
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
}

private suspend fun shareSessionScreenshot(
    context: Context,
    layer: GraphicsLayer
) {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, "分享训练回顾")
    )
}

private fun buildSummary(s: online.youcd.heartrate.data.db.SessionEntity): String {
    val zoneMap = parseZoneSeconds(s.zoneSeconds)
    val total = zoneMap.values.sum()
    if (total <= 0) return "本次训练数据不足，未能生成总结。"
    val mainZone = zoneMap.maxByOrNull { it.value }?.key ?: return ""
    val mainPct = zoneMap[mainZone]!! * 100 / total
    return when (mainZone) {
        "HR Z5" -> "本次训练达到了极限区强度，${mainPct}% 时间处于高强度，表现非常出色，注意充分恢复！"
        "HR Z4" -> "本次训练以高强度有氧为主，${mainPct}% 时间处于有氧区，对心肺提升效果显著。"
        "HR Z3" -> "本次训练以燃脂区为主，${mainPct}% 时间处于燃脂区间，燃脂效率出色。"
        "HR Z2" -> "本次训练以热身区为主，${mainPct}% 时间处于低强度区间，适合作为恢复日训练。"
        else -> "本次训练强度较低，心率保持稳定，适合作为恢复日训练。"
    }
}

private fun zoneLabelFromName(name: String): String = when (name) {
    "HR Z1" -> "Z1 · 恢复区"
    "HR Z2" -> "Z2 · 热身区"
    "HR Z3" -> "Z3 · 燃脂区"
    "HR Z4" -> "Z4 · 有氧区"
    "HR Z5" -> "Z5 · 极限区"
    else -> name
}

private fun parseZoneSeconds(raw: String): Map<String, Int> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(",")
        .mapNotNull { part ->
            val split = part.split(":")
            if (split.size == 2) split[0] to (split[1].toIntOrNull() ?: 0) else null
        }
        .toMap()
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun online.youcd.heartrate.data.db.SessionEntity.maxBpmColor(maxHr: Int): Long {
    if (maxHr <= 0) return 0xFFFF3B30
    return HeartRateZone.zoneColor(maxBpm, maxHr)
}
