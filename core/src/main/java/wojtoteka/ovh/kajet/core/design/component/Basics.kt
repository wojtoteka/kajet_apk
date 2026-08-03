package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet

@Composable
fun HorizontalRule(
    modifier: Modifier = Modifier,
    color: Color = Kajet.colors.line,
    insetFromStart: Dp = 0.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Kajet.dimens.hairline)
            .padding(start = insetFromStart)
            .background(color),
    )
}

fun Modifier.marginRule(color: Color, width: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    val x = size.width - width.toPx() / 2f
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = width.toPx(),
    )
}

@Composable
fun MarginRail(
    modifier: Modifier = Modifier,
    width: Dp = Kajet.dimens.railWidth,
    background: Color = Kajet.colors.desk,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(background)
            .marginRule(Kajet.colors.line),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    touchTarget: Dp = 48.dp,
) {
    val iconColor = when {
        !enabled -> Kajet.colors.muted.copy(alpha = 0.45f)
        selected -> Kajet.colors.accent
        else -> Kajet.colors.text
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(touchTarget)
            .then(
                if (selected) {
                    Modifier.background(Kajet.colors.accentWash, RoundedCornerShape(Kajet.dimens.corner))
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(color = Kajet.colors.accent),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .background(
                if (enabled) Kajet.colors.accent else Kajet.colors.line,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Kajet.colors.onAccent else Kajet.colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            style = Kajet.type.label,
            color = if (enabled) Kajet.colors.onAccent else Kajet.colors.muted,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Kajet.colors.text,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else Kajet.colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            style = Kajet.type.label,
            color = if (enabled) color else Kajet.colors.muted,
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Kajet.colors.muted,
) {
    Text(
        text = text.uppercase(),
        style = Kajet.type.eyebrow,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = Kajet.type.title, color = Kajet.colors.text)
        Text(
            text = description,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(420.dp),
        )
        if (action != null) {
            Box(Modifier.padding(top = 10.dp)) { action() }
        }
    }
}

@Composable
fun NoticeBar(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Kajet.colors.muted,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = Kajet.type.body,
            color = color,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

fun Modifier.gridBackground(color: Color, step: Dp = 24.dp): Modifier = drawBehind {
    val step = step.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
