package online.youcd.heartrate.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * 自定义启动页：渐变圆底 + 白色心电图 + 光点沿波形流动。
 * 通过 [onFinish] 通知外层淡出进入主界面。
 */
@Composable
fun SplashScreen(
    onFinish: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1500)
        onFinish()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) * 0.18f

            // 渐变圆底
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF6B6B), Color(0xFFEE0979)),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius,
                center = center
            )

            // 心电图路径（相对中心缩放到圆内）
            val scale = radius / 256f
            fun px(x: Float) = center.x + (x - 256f) * scale
            fun py(y: Float) = center.y + (y - 256f) * scale

            // 装饰心形
            val heart = Path().apply {
                moveTo(px(256f), py(380f))
                cubicTo(px(256f), py(380f), px(120f), py(290f), px(120f), py(190f))
                cubicTo(px(120f), py(130f), px(170f), py(90f), px(220f), py(90f))
                cubicTo(px(245f), py(90f), px(256f), py(110f), px(256f), py(110f))
                cubicTo(px(256f), py(110f), px(267f), py(90f), px(292f), py(90f))
                cubicTo(px(342f), py(90f), px(392f), py(130f), px(392f), py(190f))
                cubicTo(px(392f), py(290f), px(256f), py(380f), px(256f), py(380f))
                close()
            }
            drawPath(
                path = heart,
                color = Color.White.copy(alpha = 0.15f)
            )

            // 心电图主线
            val ecgPoints = listOf(
                Offset(px(60f), py(256f)),
                Offset(px(130f), py(256f)),
                Offset(px(160f), py(220f)),
                Offset(px(190f), py(290f)),
                Offset(px(230f), py(140f)),
                Offset(px(270f), py(360f)),
                Offset(px(310f), py(230f)),
                Offset(px(340f), py(256f)),
                Offset(px(452f), py(256f))
            )
            val ecgPath = Path().apply {
                moveTo(ecgPoints[0].x, ecgPoints[0].y)
                ecgPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = ecgPath,
                color = Color.White.copy(alpha = 0.9f),
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )

            // 底部数据记录指示器（三条柱）
            val barColor = Color.White.copy(alpha = 0.8f)
            drawRect(
                color = barColor,
                topLeft = Offset(px(191f), py(420f)),
                size = androidx.compose.ui.geometry.Size(30f * scale, 30f * scale)
            )
            drawRect(
                color = barColor,
                topLeft = Offset(px(241f), py(400f)),
                size = androidx.compose.ui.geometry.Size(30f * scale, 50f * scale)
            )
            drawRect(
                color = barColor,
                topLeft = Offset(px(291f), py(410f)),
                size = androidx.compose.ui.geometry.Size(30f * scale, 40f * scale)
            )

            // 光点沿波形流动（按总路径长度比例插值）
            // 计算分段累计长度
            val segLengths = ecgPoints.zipWithNext { a, b ->
                kotlin.math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
            }
            val totalLen = segLengths.sum()
            val targetLen = progress * totalLen
            var acc = 0f
            var dotPos = ecgPoints.last()
            for (i in segLengths.indices) {
                if (acc + segLengths[i] >= targetLen) {
                    val t = (targetLen - acc) / segLengths[i]
                    dotPos = Offset(
                        ecgPoints[i].x + (ecgPoints[i + 1].x - ecgPoints[i].x) * t,
                        ecgPoints[i].y + (ecgPoints[i + 1].y - ecgPoints[i].y) * t
                    )
                    break
                }
                acc += segLengths[i]
            }

            // 光点光晕 + 光点
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = 20f,
                center = dotPos
            )
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = dotPos
            )
        }
    }
}
