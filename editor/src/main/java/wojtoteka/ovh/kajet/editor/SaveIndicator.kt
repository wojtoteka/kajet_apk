package wojtoteka.ovh.kajet.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.savedAt
import java.util.Calendar

@Composable
fun SaveIndicator(
    state: SaveState,
    lastSave: Long?,
    modifier: Modifier = Modifier,
    inCloud: Boolean? = null,
) {
    val colors = Kajet.colors
    val words = LocalStrings.current
    val (label, color) = when (state) {
        SaveState.LOADING -> words.loading to colors.muted
        SaveState.CHANGED -> words.changesWaiting to colors.muted
        SaveState.SAVING -> words.saving to colors.accent
        SaveState.SAVED -> savedLabel(lastSave, words) to colors.muted
        SaveState.ERROR -> words.saveFailed to colors.danger
    }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            when (state) {
                SaveState.SAVING, SaveState.LOADING -> SpinningRing(color)

                SaveState.SAVED -> Icon(
                    KajetIcons.Saved,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )

                SaveState.ERROR -> Icon(
                    KajetIcons.ErrorMark,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(15.dp),
                )

                SaveState.CHANGED -> BreathingDot(color)
            }
        }

        Text(label, style = Kajet.type.meta, color = color)

        AnimatedVisibility(
            visible = inCloud != null && state == SaveState.SAVED,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Icon(
                imageVector = if (inCloud == true) KajetIcons.CloudDone else KajetIcons.Offline,
                contentDescription = if (inCloud == true) {
                    words.noteInCloud
                } else {
                    words.noteWaitingForCloud
                },
                tint = if (inCloud == true) colors.accent else colors.muted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun SpinningRing(color: Color) {
    val motion = rememberInfiniteTransition(label = "zapis")
    val angle by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "obrot",
    )
    Canvas(Modifier.size(14.dp)) {
        val width = 2.dp.toPx()
        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun BreathingDot(color: Color) {
    val motion = rememberInfiniteTransition(label = "czekanie")
    val alpha by motion.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), repeatMode = RepeatMode.Reverse),
        label = "puls",
    )
    Box(
        Modifier
            .size(7.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

private fun savedLabel(lastSave: Long?, words: Strings): String {
    if (lastSave == null) return words.saved
    val calendar = Calendar.getInstance().apply { timeInMillis = lastSave }
    return words.savedAt(
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}
