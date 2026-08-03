package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet

@Composable
fun SettingSlider(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    readout: (Float) -> String = { "%.1f".format(it) },
    steps: Int = 0,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = Kajet.type.label,
                color = Kajet.colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(readout(value), style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Kajet.colors.accent,
                activeTrackColor = Kajet.colors.accent,
                inactiveTrackColor = Kajet.colors.line,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.height(36.dp),
        )
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (active) Kajet.colors.accentWash else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name(option),
                    style = Kajet.type.label,
                    color = if (active) Kajet.colors.accent else Kajet.colors.text,
                )
            }
        }
    }
}

@Composable
fun IconToggle(
    icon: ImageVector,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .background(
                if (checked) Kajet.colors.accentWash else Color.Transparent,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(role = Role.Switch, onClickLabel = description) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (checked) Kajet.colors.accent else Kajet.colors.text,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun ToolPanel(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title, modifier = Modifier.weight(1f))
            if (onClose != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clickable(onClickLabel = "Zamknij panel", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        wojtoteka.ovh.kajet.core.design.icon.KajetIcons.Close,
                        contentDescription = "Zamknij panel",
                        tint = Kajet.colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        content()
    }
}
