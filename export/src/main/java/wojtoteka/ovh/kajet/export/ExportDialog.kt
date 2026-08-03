package wojtoteka.ovh.kajet.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

@Composable
fun ExportDialog(
    document: NoteDocument,
    path: String,
    service: ExportService,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var ready by remember { mutableStateOf<File?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .width(520.dp)
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            Text(
                text = "Zapisz notatkę do pliku",
                style = Kajet.type.title,
                color = Kajet.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            )
            HorizontalRule()

            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("Format")
                ExportFormat.entries.forEach { variant ->
                    val warning = warning(document, variant)
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
                            Text(variant.labelPl, style = Kajet.type.body, color = Kajet.colors.text)
                            Text(variant.descriptionPl, style = Kajet.type.meta, color = Kajet.colors.muted)
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
                        text = "Plik jest gotowy: ${ready?.name}",
                        style = Kajet.type.body,
                        color = Kajet.colors.text,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val file = ready
                    if (file == null) {
                        PrimaryButton(
                            text = if (working) "Zapisuję..." else "Zapisz plik",
                            onClick = {
                                working = true
                                error = null
                                scope.launch {
                                    try {
                                        ready = service.export(path, document, format)
                                    } catch (e: Exception) {
                                        error = e.message ?: "Zapis się nie udał. Spróbuj innego formatu."
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
                            text = "Wyślij",
                            onClick = { service.share(file, format.mime, document.title) },
                            icon = KajetIcons.ShareArrow,
                        )
                        SecondaryButton(
                            text = "Otwórz",
                            onClick = { service.open(file, format.mime) },
                        )
                    }
                    SecondaryButton(
                        text = "Drukuj",
                        onClick = { service.print(document, path) },
                        icon = KajetIcons.Printer,
                    )
                    Box(Modifier.weight(1f))
                    SecondaryButton("Zamknij", onClose)
                }
            }
        }
    }
}

private fun warning(document: NoteDocument, format: ExportFormat): String? = when {
    format == ExportFormat.DOCX && document.kind == NoteKind.HANDWRITTEN ->
        "Pismo odręczne nie wejdzie do tego pliku."

    format == ExportFormat.DOCX && document.text?.markdown?.contains("![") == true ->
        "Zdjęcia nie wejdą do tego pliku."

    format == ExportFormat.PNG && document.kind != NoteKind.HANDWRITTEN ->
        "Ten format zapisuje tylko notatki odręczne."

    format == ExportFormat.MARKDOWN && document.kind == NoteKind.HANDWRITTEN ->
        "Zapisze się tylko tekst, bez pisma."

    else -> null
}
