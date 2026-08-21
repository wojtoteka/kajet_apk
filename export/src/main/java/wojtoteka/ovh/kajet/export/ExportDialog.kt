package wojtoteka.ovh.kajet.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import java.io.File
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(
    document: NoteDocument,
    path: String,
    service: ExportService,
    onClose: () -> Unit,
    /**
     * Otwiera panel udostępniania. Null, gdy nie ma konta w chmurze - wtedy
     * nie ma czym zarządzać. Okno zapisu nie zna chmury i nie ma jej znać
     * (moduł `export` nie zależy od `cloud`), więc dostaje gotowe przejście,
     * a panel montuje ekran notatki, który zna jedno i drugie.
     */
    onShareLink: (() -> Unit)? = null,
) {
    val words = LocalStrings.current
    val scope = rememberCoroutineScope()
    // Kontekst ekranu, nie aplikacji - systemowy druk wymaga Activity.
    val context = androidx.compose.ui.platform.LocalContext.current
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var ready by remember { mutableStateOf<File?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                // Na telefonie okno ma zmieścić się w ekranie, na tablecie nie
                // rozciągać się na całą szerokość. Granica idzie PRZED
                // wypełnieniem - po nim byłaby martwa, bo `fillMaxWidth` ustala
                // szerokość sztywno i `widthIn` nie ma już czego przyciąć.
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            Text(
                text = words.exportTitle,
                style = Kajet.type.title,
                color = Kajet.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            )
            HorizontalRule()

            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel(words.exportFormat)
                ExportFormat.entries.forEach { variant ->
                    val warning = warning(document, variant, words)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(
                                if (format == variant) Kajet.colors.accentWash else Kajet.colors.sheet,
                                RoundedCornerShape(Kajet.dimens.corner),
                            )
                            .clickable { format = variant; ready = null }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = KajetIcons.Export,
                            contentDescription = null,
                            tint = if (format == variant) Kajet.colors.accent else Kajet.colors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(variant.label(words), style = Kajet.type.body, color = Kajet.colors.text)
                            Text(variant.description(words), style = Kajet.type.meta, color = Kajet.colors.muted)
                            if (warning != null) {
                                Text(warning, style = Kajet.type.meta, color = Kajet.colors.danger)
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(error.orEmpty(), style = Kajet.type.body, color = Kajet.colors.danger)
                }

                if (ready != null) {
                    Text(
                        text = "${words.fileReady}: ${ready?.name}",
                        style = Kajet.type.body,
                        color = Kajet.colors.text,
                    )
                }

                // Na telefonie wszystkie przyciski nie mieszczą się w jednym
                // wierszu - bez zawijania ostatni był ściskany do zera.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val file = ready
                    if (file == null) {
                        PrimaryButton(
                            text = if (working) words.savingFile else words.exportSave,
                            onClick = {
                                working = true
                                error = null
                                scope.launch {
                                    try {
                                        ready = service.export(path, document, format)
                                    } catch (e: Exception) {
                                        error = e.message ?: words.exportFailed
                                    } finally {
                                        working = false
                                    }
                                }
                            },
                            icon = KajetIcons.Export,
                            enabled = !working,
                        )
                    } else {
                        PrimaryButton(
                            text = words.sendFile,
                            onClick = { error = service.share(context, file, format.mime, document.title) },
                            icon = KajetIcons.ShareArrow,
                        )
                        SecondaryButton(
                            text = words.openFile,
                            onClick = { error = service.open(context, file, format.mime) },
                        )
                    }
                    if (onShareLink != null) {
                        SecondaryButton(
                            text = words.shareLink,
                            onClick = onShareLink,
                            icon = KajetIcons.ShareArrow,
                        )
                    }
                    SecondaryButton(
                        text = words.print,
                        onClick = { error = service.print(context, document, path) },
                        icon = KajetIcons.Printer,
                    )
                    SecondaryButton(words.close, onClose)
                }
            }
        }
    }
}

private fun warning(
    document: NoteDocument,
    format: ExportFormat,
    words: Strings,
): String? = when {
    format == ExportFormat.DOCX && document.kind == NoteKind.HANDWRITTEN ->
        words.exportNoHandwriting

    format == ExportFormat.DOCX && document.text?.markdown?.contains("![") == true ->
        words.exportNoPhotos

    format == ExportFormat.PNG && document.kind != NoteKind.HANDWRITTEN ->
        words.exportHandwrittenOnly

    format == ExportFormat.MARKDOWN && document.kind == NoteKind.HANDWRITTEN ->
        words.exportTextOnly

    else -> null
}
