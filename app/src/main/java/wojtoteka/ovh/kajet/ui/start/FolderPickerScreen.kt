package wojtoteka.ovh.kajet.ui.start

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.KajetMark
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings

@Composable
fun FolderPickerScreen(
    onPicked: (Uri) -> Unit,
) {
    val words = LocalStrings.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) onPicked(uri) }

    Row(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk),
    ) {
        Box(
            Modifier
                .width(Kajet.dimens.railWidth)
                .fillMaxHeight()
                .marginRule(Kajet.colors.line),
            contentAlignment = Alignment.TopCenter,
        ) {
            KajetMark(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(28.dp),
                color = Kajet.colors.accent,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, end = 32.dp, top = 56.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionLabel(words.firstRun)

            Text(
                text = words.whereToKeepNotes,
                style = Kajet.type.display,
                color = Kajet.colors.text,
                modifier = Modifier.widthIn(max = 560.dp),
            )

            Text(
                text = words.whereToKeepNotesAbout,
                style = Kajet.type.bodyLarge,
                color = Kajet.colors.text,
                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
            )

            Text(
                text = words.whereToKeepNotesWhy,
                style = Kajet.type.body,
                color = Kajet.colors.muted,
                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
            )

            Box(Modifier.height(8.dp))

            PrimaryButton(
                text = words.pickNotesFolder,
                onClick = { picker.launch(documentsHint()) },
                icon = KajetIcons.Folder,
            )

            Text(
                text = words.pickNotesFolderAbout,
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
            )
        }
    }
}

fun documentsHint(): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val id = "primary:" + Environment.DIRECTORY_DOCUMENTS
    return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", id)
}

fun folderPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    )
}
