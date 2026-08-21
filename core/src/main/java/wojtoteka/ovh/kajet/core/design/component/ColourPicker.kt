package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import kotlin.math.roundToInt
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.colourNamedHex

data class HsvColour(
    val hue: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val alpha: Float = 1f,
) {
    fun toArgbInt(): Int = android.graphics.Color.HSVToColor(
        (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
        floatArrayOf(hue, saturation, brightness),
    )

    fun toComposeColor(): Color = Color(toArgbInt())

    fun toHex(): String {
        val argb = toArgbInt()
        val withoutAlpha = argb and 0x00FFFFFF
        return if (alpha >= 0.999f) {
            "#%06X".format(withoutAlpha)
        } else {
            "#%08X".format(argb)
        }
    }

    companion object {
        fun fromArgb(argb: Int): HsvColour {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            return HsvColour(
                hue = hsv[0],
                saturation = hsv[1],
                brightness = hsv[2],
                alpha = ((argb ushr 24) and 0xFF) / 255f,
            )
        }

        fun fromHex(text: String): Int? {
            val plain = text.trim().removePrefix("#")
            if (plain.length != 6 && plain.length != 8) return null
            val value = plain.toLongOrNull(16) ?: return null
            return if (plain.length == 6) {
                (0xFF000000L or value).toInt()
            } else {
                value.toInt()
            }
        }
    }
}

@Composable
fun ColourPicker(
    color: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    withAlpha: Boolean = true,
    presetColors: List<Pair<String, Color>> = emptyList(),
    recentColors: List<Int> = emptyList(),
) {
    val words = LocalStrings.current
    // Odcień pamiętamy osobno, żeby nie gubił się przy czerni i bieli.
    var state by remember(color) { mutableStateOf(HsvColour.fromArgb(color)) }
    var typedHex by remember(color) { mutableStateOf<String?>(null) }

    fun emit(updated: HsvColour) {
        state = updated
        typedHex = null
        onChange(updated.toArgbInt())
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SaturationSquare(
            hue = state.hue,
            saturation = state.saturation,
            brightness = state.brightness,
            onChange = { saturation, brightness ->
                emit(state.copy(saturation = saturation, brightness = brightness))
            },
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )

        HueSlider(
            value = state.hue / 360f,
            gradient = Brush.horizontalGradient(hueStops),
            thumb = Color(android.graphics.Color.HSVToColor(floatArrayOf(state.hue, 1f, 1f))),
            description = words.hue,
            onChange = { emit(state.copy(hue = it * 360f)) },
        )

        if (withAlpha) {
            val opaque = state.copy(alpha = 1f).toComposeColor()
            HueSlider(
                value = state.alpha,
                gradient = Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)),
                thumb = state.toComposeColor(),
                description = words.opacity,
                checkered = true,
                onChange = { emit(state.copy(alpha = it)) },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Kajet.dimens.corner))
                    .checkerBackground(Kajet.colors.line)
                    .background(state.toComposeColor(), RoundedCornerShape(Kajet.dimens.corner))
                    .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
            )
            HexField(
                text = typedHex ?: state.toHex(),
                onText = { updated ->
                    typedHex = updated
                    HsvColour.fromHex(updated)?.let { argb ->
                        state = HsvColour.fromArgb(argb).copy(
                            // Przy wpisywaniu szarości też warto zachować odcień z suwaka.
                            hue = if (HsvColour.fromArgb(argb).saturation == 0f) state.hue else HsvColour.fromArgb(argb).hue,
                        )
                        onChange(argb)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (presetColors.isNotEmpty()) {
            SectionLabel(words.presetColours)
            ColourGrid(
                colors = presetColors.map { it.second.toArgb() },
                descriptions = presetColors.map { it.first },
                selected = state.toArgbInt(),
                onSelect = { emit(HsvColour.fromArgb(it)) },
            )
        }

        if (recentColors.isNotEmpty()) {
            SectionLabel(words.recentColours)
            ColourGrid(
                colors = recentColors,
                descriptions = recentColors.map { words.colourNamedHex(HsvColour.fromArgb(it).toHex()) },
                selected = state.toArgbInt(),
                onSelect = { emit(HsvColour.fromArgb(it)) },
            )
        }
    }
}

@Composable
fun ColourPickerDialog(
    title: String,
    color: Int,
    onChange: (Int) -> Unit,
    onClose: () -> Unit,
    withAlpha: Boolean = true,
    presetColors: List<Pair<String, Color>> = emptyList(),
    recentColors: List<Int> = emptyList(),
) {
    val words = LocalStrings.current
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .width(360.dp)
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Kajet.type.title, color = Kajet.colors.text, modifier = Modifier.weight(1f))
                IconAction(KajetIcons.Close, words.close, onClose)
            }
            ColourPicker(
                color = color,
                onChange = onChange,
                withAlpha = withAlpha,
                presetColors = presetColors,
                recentColors = recentColors,
            )
            PrimaryButton(words.done, onClose, icon = KajetIcons.Confirm)
        }
    }
}

@Composable
fun ColourDot(
    color: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    diameter: Dp = 24.dp,
) {
    Box(
        modifier
            .size(44.dp)
            // Kropka koloru nie przejmuje skupienia - patrz IconAction:
            // inaczej zwijałaby zaznaczenie w polu tekstu.
            .focusProperties { canFocus = false }
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) diameter + 4.dp else diameter)
                .clip(CircleShape)
                .checkerBackground(Kajet.colors.line)
                .background(Color(color), CircleShape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Kajet.colors.accent else Kajet.colors.line,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun ColourGrid(
    colors: List<Int>,
    descriptions: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val words = LocalStrings.current
    // Osiem w rzędzie mieści się w panelu i nie wymaga przewijania w bok.
    colors.chunked(8).forEachIndexed { row, chunk ->
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            chunk.forEachIndexed { i, color ->
                val index = row * 8 + i
                ColourDot(
                    color = color,
                    description = descriptions.getOrElse(index) { words.colourNamed },
                    onClick = { onSelect(color) },
                    selected = color == selected,
                    diameter = 22.dp,
                )
            }
        }
    }
}

@Composable
private fun SaturationSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val plain = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val density = LocalDensity.current

    val fromPoint: (Offset) -> Unit = { point ->
        if (size.width > 0 && size.height > 0) {
            onChange(
                (point.x / size.width).coerceIn(0f, 1f),
                1f - (point.y / size.height).coerceIn(0f, 1f),
            )
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .background(Brush.horizontalGradient(listOf(Color.White, plain)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { fromPoint(it) }, onTap = { fromPoint(it) })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> fromPoint(change.position) }
            },
    ) {
        val x = with(density) { (size.width * saturation).toDp() } - THUMB / 2
        val y = with(density) { (size.height * (1f - brightness)).toDp() } - THUMB / 2
        Handle(
            x = x,
            y = y,
            color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))),
        )
    }
}

@Composable
private fun HueSlider(
    value: Float,
    gradient: Brush,
    thumb: Color,
    description: String,
    onChange: (Float) -> Unit,
    checkered: Boolean = false,
) {
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val fromPoint: (Offset) -> Unit = { point ->
        if (width > 0) onChange((point.x / width).coerceIn(0f, 1f))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(Kajet.dimens.corner))
            .then(if (checkered) Modifier.checkerBackground(Kajet.colors.line) else Modifier)
            .background(gradient)
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { fromPoint(it) }, onTap = { fromPoint(it) })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> fromPoint(change.position) }
            },
    ) {
        val x = with(density) { (width * value).toDp() } - THUMB / 2
        Handle(x = x, y = 4.dp, color = thumb)
    }
}

@Composable
private fun Handle(x: Dp, y: Dp, color: Color) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(THUMB)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .border(3.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
    )
}

private val THUMB = 20.dp

@Composable
private fun HexField(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = text,
        onValueChange = { onText(it.take(9)) },
        textStyle = Kajet.type.code.copy(color = Kajet.colors.text),
        singleLine = true,
        cursorBrush = SolidColor(Kajet.colors.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(),
        modifier = modifier
            .height(40.dp)
            .background(Kajet.colors.desk, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

fun Modifier.checkerBackground(color: Color, step: Dp = 6.dp): Modifier = drawBehind {
    val side = step.toPx()
    drawRect(Color.White)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else side
        while (x < size.width) {
            drawRect(
                color = color.copy(alpha = 0.5f),
                topLeft = Offset(x, y),
                size = Size(
                    width = minOf(side, size.width - x),
                    height = minOf(side, size.height - y),
                ),
            )
            x += side * 2
        }
        y += side
        row++
    }
}

private val hueStops = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)
