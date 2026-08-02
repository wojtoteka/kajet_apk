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

/**
 * Włoskowata linia pozioma. W Kajecie linie zastępują cienie i ramki kart.
 */
@Composable
fun LiniaPozioma(
    modifier: Modifier = Modifier,
    kolor: Color = Kajet.colors.line,
    odstepFromStart: Dp = 0.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Kajet.dimens.hairline)
            .padding(start = odstepFromStart)
            .background(kolor),
    )
}

/**
 * Pionowa linia marginesu. To jest znak rozpoznawczy aplikacji.
 * Rysowana przy prawej krawędzi elementu, na który nałożysz ten modyfikator.
 */
fun Modifier.liniaMarginesu(kolor: Color, grubosc: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    val x = size.width - grubosc.toPx() / 2f
    drawLine(
        color = kolor,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = grubosc.toPx(),
    )
}

/**
 * Pasek marginesu: wąska kolumna po lewej stronie ekranu, zamknięta pionową linią.
 * Powtarza się na każdym ekranie, ale za każdym razem niesie co innego.
 */
@Composable
fun PasekMarginesu(
    modifier: Modifier = Modifier,
    szerokosc: Dp = Kajet.dimens.railWidth,
    tlo: Color = Kajet.colors.desk,
    ukladPionowy: Arrangement.Vertical = Arrangement.Top,
    zawartosc: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(szerokosc)
            .fillMaxHeight()
            .background(tlo)
            .liniaMarginesu(Kajet.colors.line),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = ukladPionowy,
        content = zawartosc,
    )
}

/**
 * Przycisk z samą ikoną. Opis jest obowiązkowy, bo czytnik ekranu
 * musi mieć co przeczytać. Cel dotykowy zawsze co najmniej 48 dp.
 */
@Composable
fun IkonaPrzycisk(
    ikona: ImageVector,
    opis: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wybrany: Boolean = false,
    wlaczony: Boolean = true,
    rozmiarIkony: Dp = 22.dp,
    celDotykowy: Dp = 48.dp,
) {
    val kolorIkony = when {
        !wlaczony -> Kajet.colors.muted.copy(alpha = 0.45f)
        wybrany -> Kajet.colors.accent
        else -> Kajet.colors.text
    }
    val zrodloInterakcji = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(celDotykowy)
            .then(
                if (wybrany) {
                    Modifier.background(Kajet.colors.accentWash, RoundedCornerShape(Kajet.dimens.corner))
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = wlaczony,
                onClick = onClick,
                role = Role.Button,
                interactionSource = zrodloInterakcji,
                indication = ripple(color = Kajet.colors.accent),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ikona,
            contentDescription = opis,
            tint = kolorIkony,
            modifier = Modifier.size(rozmiarIkony),
        )
    }
}

/** Jedno działanie główne na ekran. Wypełnione akcentem. */
@Composable
fun PrzyciskGlowny(
    tekst: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikona: ImageVector? = null,
    wlaczony: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .background(
                if (wlaczony) Kajet.colors.accent else Kajet.colors.line,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(enabled = wlaczony, onClick = onClick, role = Role.Button)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ikona != null) {
            Icon(
                imageVector = ikona,
                contentDescription = null,
                tint = if (wlaczony) Kajet.colors.onAccent else Kajet.colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = tekst,
            style = Kajet.type.label,
            color = if (wlaczony) Kajet.colors.onAccent else Kajet.colors.muted,
        )
    }
}

/** Działanie poboczne. Sama ramka, bez wypełnienia. */
@Composable
fun PrzyciskWtorny(
    tekst: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikona: ImageVector? = null,
    wlaczony: Boolean = true,
    kolor: Color = Kajet.colors.text,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .clickable(enabled = wlaczony, onClick = onClick, role = Role.Button)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ikona != null) {
            Icon(
                imageVector = ikona,
                contentDescription = null,
                tint = if (wlaczony) kolor else Kajet.colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = tekst,
            style = Kajet.type.label,
            color = if (wlaczony) kolor else Kajet.colors.muted,
        )
    }
}

/** Wersalikowa etykieta sekcji. Pojawia się rzadko, żeby nie zrobić z niej ozdoby. */
@Composable
fun EtykietaSekcji(
    tekst: String,
    modifier: Modifier = Modifier,
    kolor: Color = Kajet.colors.muted,
) {
    Text(
        text = tekst.uppercase(),
        style = Kajet.type.eyebrow,
        color = kolor,
        modifier = modifier,
    )
}

/**
 * Ekran albo panel bez treści. Mówi wprost, czego nie ma i co można zrobić.
 * Tekst jest wyrównany do lewej, bo to zdania, a nie hasła.
 */
@Composable
fun PustoTutaj(
    naglowek: String,
    opis: String,
    modifier: Modifier = Modifier,
    dzialanie: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(naglowek, style = Kajet.type.title, color = Kajet.colors.text)
        Text(
            text = opis,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(420.dp),
        )
        if (dzialanie != null) {
            Box(Modifier.padding(top = 10.dp)) { dzialanie() }
        }
    }
}

/**
 * Komunikat w treści ekranu. Ma dwa zdania: co się stało i co z tym zrobić.
 */
@Composable
fun Komunikat(
    ikona: ImageVector,
    tekst: String,
    modifier: Modifier = Modifier,
    kolor: Color = Kajet.colors.muted,
    dzialanie: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(ikona, contentDescription = null, tint = kolor, modifier = Modifier.size(20.dp))
        Text(
            text = tekst,
            style = Kajet.type.body,
            color = kolor,
            modifier = Modifier.weight(1f),
        )
        dzialanie?.invoke(this)
    }
}

/**
 * Tło w drobną kratkę pod obszarem roboczym mapy myśli i podglądów.
 * Rysowane bezpośrednio, bez obrazka, żeby skalowało się bez rozmycia.
 */
fun Modifier.tloWKratke(kolor: Color, krok: Dp = 24.dp): Modifier = drawBehind {
    val step = krok.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(kolor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(kolor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
