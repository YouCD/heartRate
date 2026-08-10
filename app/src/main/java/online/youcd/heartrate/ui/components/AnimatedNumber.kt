package online.youcd.heartrate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

@Composable
fun AnimatedNumber(
    target: Int,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Bold,
    color: androidx.compose.ui.graphics.Color
) {
    val displayed = remember { Animatable(0f) }
    LaunchedEffect(target) {
        displayed.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }
    Text(
        text = displayed.value.roundToInt().toString(),
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        ),
        modifier = modifier
    )
}
