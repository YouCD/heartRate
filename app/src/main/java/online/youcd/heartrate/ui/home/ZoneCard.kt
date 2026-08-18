package online.youcd.heartrate.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.ui.theme.SuccessGreen

private val ZoneCardBg = Color(0xFF1C1C1E)
private val ZoneRowActiveBg = Color(0xFF2C2C2E)
private val ZoneMutedText = Color(0xFF8E8E93)
private val ZoneDisabledText = Color(0xFF48484A)

@Composable
internal fun ZoneListCard(
    modifier: Modifier = Modifier,
    maxHr: Int,
    zoneSeconds: List<Int>,
    activeZoneId: Int?,
    isConnected: Boolean,
    zoneLabel: (Int) -> String
) {
    val zones = HeartRateZone.ZONES
    var longPressedZone by remember { mutableStateOf<HeartRateZone?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ZoneCardBg,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 标题行：图标 + 标题 + 状态标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.MonitorHeart,
                    contentDescription = null,
                    tint = if (isConnected) SuccessGreen else ZoneMutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "心率区间",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isConnected) SuccessGreen.copy(alpha = 0.16f)
                            else ZoneMutedText.copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isConnected) "实时" else "未连接",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isConnected) SuccessGreen else ZoneMutedText
                    )
                }
            }

            if (!isConnected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "连接心率带后显示实时区间",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZoneDisabledText
                )
            }

            Spacer(Modifier.height(16.dp))

            zones.forEachIndexed { index, zone ->
                val isActive = isConnected && zone.id == activeZoneId
                ZoneRow(
                    zone = zone,
                    maxHr = maxHr,
                    zoneSeconds = zoneSeconds.getOrElse(zone.id - 1) { 0 },
                    totalSeconds = zoneSeconds.sum(),
                    isActive = isActive,
                    isConnected = isConnected,
                    index = index,
                    zoneLabel = zoneLabel,
                    onLongClick = { longPressedZone = zone }
                )
            }

            // 底部本次训练分布摘要
            Spacer(Modifier.height(10.dp))
            val mainZoneSeconds = zones.maxOf { zoneSeconds.getOrElse(it.id - 1) { 0 } }
            val mainZone = zones.firstOrNull { zoneSeconds.getOrElse(it.id - 1) { 0 } == mainZoneSeconds }
            Text(
                text = if (isConnected && mainZoneSeconds > 0) {
                    "本次训练以${zoneLabel(mainZone!!.id)}为主"
                } else {
                    "连接后显示本次训练区间分布"
                },
                style = MaterialTheme.typography.labelSmall,
                color = ZoneMutedText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    longPressedZone?.let { zone ->
        AlertDialog(
            onDismissRequest = { longPressedZone = null },
            title = { Text("${zoneLabel(zone.id)} · Z${zone.id}") },
            text = { Text(zoneDetailText(zone.id)) },
            confirmButton = {
                TextButton(onClick = { longPressedZone = null }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun ZoneSpectrumBar(
    bpm: Int,
    maxHr: Int,
    activeZoneId: Int?,
    isConnected: Boolean,
    zoneLabel: (Int) -> String
) {
    val zones = HeartRateZone.ZONES
    val ratio = if (maxHr <= 0 || !isConnected) 0f else (bpm.toFloat() / maxHr).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val barWidth = maxWidth
        val bubbleWidth = 96.dp

        Column {
            // 滑块上方气泡
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                if (isConnected && ratio > 0f) {
                    val currentZone = HeartRateZone.from(bpm, maxHr)
                    val bubbleX by animateFloatAsState(
                        targetValue = (barWidth * ratio - bubbleWidth / 2)
                            .value.coerceIn(0f, (barWidth - bubbleWidth).value),
                        animationSpec = tween(250),
                        label = "bubbleX"
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = bubbleX.dp)
                            .size(width = bubbleWidth, height = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ZoneRowActiveBg)
                            .border(1.dp, Color(currentZone.color), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$bpm · ${zoneLabel(activeZoneId ?: currentZone.id)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(currentZone.color)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 五个区间按 BPM 比例划分
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                zones.forEach { zone ->
                    Box(
                        modifier = Modifier
                            .weight(zone.maxPercent - zone.minPercent)
                            .fillMaxHeight()
                            .background(
                                Color(zone.color).copy(
                                    alpha = if (isConnected && zone.id == activeZoneId) 1f else 0.4f
                                )
                            )
                    )
                }
            }

            // 白色滑块指示器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                if (isConnected && ratio > 0f) {
                    val sliderX by animateFloatAsState(
                        targetValue = (barWidth * ratio - 6.dp)
                            .value.coerceIn(0f, (barWidth - 12.dp).value),
                        animationSpec = tween(250),
                        label = "sliderX"
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = sliderX.dp, y = (-4).dp)
                            .size(12.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(
    zone: HeartRateZone,
    maxHr: Int,
    zoneSeconds: Int,
    totalSeconds: Int,
    isActive: Boolean,
    isConnected: Boolean,
    index: Int,
    zoneLabel: (Int) -> String,
    onLongClick: () -> Unit
) {
    val rowBg by animateColorAsState(
        targetValue = if (isActive) ZoneRowActiveBg else Color.Transparent,
        animationSpec = tween(250),
        label = "rowBg"
    )
    val alpha = remember { Animatable(if (isConnected) 1f else 0f) }
    LaunchedEffect(isConnected) {
        if (isConnected) {
            delay(index * 60L)
            alpha.animateTo(1f, tween(350))
        } else {
            alpha.snapTo(0f)
        }
    }

    val nameColor = when {
        isActive -> Color.White
        isConnected -> ZoneMutedText
        else -> ZoneDisabledText
    }
    val valueColor = if (isActive) Color.White else ZoneMutedText

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选中竖线
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(zone.color))
                )
                Spacer(Modifier.width(8.dp))
            }

            // 色块
            val dotSize = if (isActive) 14.dp else 10.dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(zone.color).copy(alpha = if (isActive) 1f else 0.4f))
                    .then(
                        if (isActive) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(3.dp))
                        else Modifier
                    )
            )
            Spacer(Modifier.width(10.dp))

            // 区间中文名
            Text(
                text = zoneLabel(zone.id),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = nameColor,
                modifier = Modifier.width(56.dp)
            )

            // 本区间累计时间进度条 + 时间
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isConnected) ZoneMutedText.copy(alpha = 0.2f)
                            else ZoneDisabledText.copy(alpha = 0.2f)
                        )
                ) {
                    if (isConnected) {
                        val ratio = if (totalSeconds > 0) zoneSeconds.toFloat() / totalSeconds else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .background(Color(zone.color))
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isConnected) formatElapsed(zoneSeconds * 1000L) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = valueColor
                )
            }

            Spacer(Modifier.width(10.dp))

            // BPM 范围（右对齐）
            Text(
                text = if (isConnected) zoneRangeText(zone, maxHr) else "--",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp)
            )
            Spacer(Modifier.width(8.dp))

            // 训练效果标签 pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isConnected) Color(zone.color).copy(alpha = 0.16f)
                        else ZoneDisabledText.copy(alpha = 0.3f)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = zoneEffortLabel(zone.id),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) Color(zone.color) else ZoneDisabledText
                )
            }
        }
    }
}

private fun zoneEffortLabel(id: Int): String = when (id) {
    1 -> "轻量"
    2 -> "热身"
    3 -> "燃脂"
    4 -> "耐力"
    else -> "极限"
}

private fun zoneDetailText(id: Int): String = when (id) {
    1 -> "恢复区（Z1）：强度极低，以恢复和放松为主，适合热身与放松阶段。"
    2 -> "热身区（Z2）：低强度有氧，提升心率和血流量，为正式训练做准备。"
    3 -> "燃脂区（Z3）：脂肪供能比例最高，建议保持 30 分钟以上以高效燃脂。"
    4 -> "有氧区（Z4）：中高强度有氧，有效提升心肺功能与耐力。"
    else -> "极限区（Z5）：高强度无氧区间，短时冲刺训练，注意控制时长避免过度疲劳。"
}

internal fun zoneLabel(id: Int): String = when (id) {
    1 -> "恢复"
    2 -> "热身"
    3 -> "燃脂"
    4 -> "有氧"
    else -> "极限"
}

private fun zoneRangeText(zone: HeartRateZone, maxHr: Int): String {
    val min = (zone.minPercent * maxHr).toInt()
    val max = (zone.maxPercent * maxHr).toInt()
    return when {
        min <= 0 -> "≤${max}bpm"
        zone.maxPercent >= 1f -> ">${min}bpm"
        else -> "$min-${max}bpm"
    }
}
