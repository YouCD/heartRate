package online.youcd.heartrate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.youcd.heartrate.data.model.HeartRateZone

@Composable
fun HeartRateBar(
    bpm: Int,
    maxHr: Int,
    zoneSeconds: List<Int>,
    modifier: Modifier = Modifier,
    zoneLabel: (Int) -> String = { "Z$it" }
) {
    val zones = HeartRateZone.ZONES
    val ratio = if (maxHr <= 0) 0f else (bpm.toFloat() / maxHr).coerceIn(0f, 1f)
    val currentZone = if (bpm > 0) HeartRateZone.from(bpm, maxHr) else null
    val totalSeconds = zoneSeconds.sum().coerceAtLeast(1)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val barWidth = maxWidth
        val bubbleWidth = 84.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            // 气泡（固定高度占位，避免跳动）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                if (currentZone != null) {
                    val bubbleX = (barWidth * ratio - bubbleWidth / 2)
                        .coerceIn(0.dp, barWidth - bubbleWidth)
                    Box(
                        modifier = Modifier
                            .offset(x = bubbleX)
                            .size(width = bubbleWidth, height = 26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = Color(currentZone.color),
                                shape = RoundedCornerShape(13.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = zoneLabel(currentZone.id),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(currentZone.color)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 渐变条 + 滑块
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (currentZone != null) {
                                Brush.horizontalGradient(
                                    zones.map { Color(it.color) }
                                )
                            } else {
                                Brush.horizontalGradient(
                                    zones.map { Color(0xFF3A3A3C) }
                                )
                            }
                        )
                )
                if (currentZone != null) {
                    val sliderSize = 20.dp
                    val sliderX = (barWidth * ratio - sliderSize / 2)
                        .coerceIn(0.dp, barWidth - sliderSize)
                    Box(
                        modifier = Modifier
                            .offset(x = sliderX)
                            .size(sliderSize)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 占比信息
            if (currentZone != null) {
                val pct = zoneSeconds.getOrElse(currentZone.id - 1) { 0 } * 100 / totalSeconds
                Text(
                    text = "${zoneLabel(currentZone.id)} · $pct%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(currentZone.color),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "连接设备后显示心率区间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
