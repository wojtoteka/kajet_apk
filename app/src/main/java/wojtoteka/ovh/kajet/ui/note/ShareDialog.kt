package wojtoteka.ovh.kajet.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.cloud.Cloud
import wojtoteka.ovh.kajet.cloud.CloudClient
import wojtoteka.ovh.kajet.cloud.ShareEntry
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.NoticeBar
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.shareLastOpened
import wojtoteka.ovh.kajet.core.text.shareMailWent
import wojtoteka.ovh.kajet.core.text.shareValidUntil
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.ui.library.ChoiceRow
import wojtoteka.ovh.kajet.ui.library.KajetDialog
import wojtoteka.ovh.kajet.ui.library.KajetTextField

/*
  Panel udostępniania notatki - odpowiednik panelu ze strony WWW.

  Wszystkim rządzi serwer: lista, zakładanie i cofanie to jego punkty API,
  a panel tylko je woła. Notatka świeżo napisana może być serwerowi jeszcze
  nieznana - wtedy (404) prosimy o synchronizację i pytamy drugi raz, tak samo
  jak robił to dawny przycisk „Udostępnij odnośnikiem".

  Systemowe okno udostępniania zostaje jako dodatek przy gotowym odnośniku -
  panel jest od zarządzania, nie tylko od podania linku dalej.
*/

/** Co udostępniamy: notatkę albo cały folder (z podfolderami). */
sealed interface ShareTarget {
    val id: String
    val title: String

    data class Note(val document: NoteDocument) : ShareTarget {
        override val id: String get() = document.id
        override val title: String get() = document.title
    }

    data class Folder(override val id: String, override val title: String) : ShareTarget
}

@Composable
fun ShareDialog(
    document: NoteDocument,
    cloud: Cloud.Parts,
    service: ExportService,
    onClose: () -> Unit,
) = ShareDialog(ShareTarget.Note(document), cloud, service, onClose)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareDialog(
    target: ShareTarget,
    cloud: Cloud.Parts,
    service: ExportService,
    onClose: () -> Unit,
) {
    val words = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var canEdit by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("0") }
    var noAccountAllowed by remember { mutableStateOf(true) }

    var creating by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<ShareEntry?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    var shares by remember { mutableStateOf<List<ShareEntry>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var confirmRevoke by remember { mutableStateOf<String?>(null) }

    // Identyfikator wpisu, którego odnośnik właśnie poszedł do schowka -
    // przycisk mówi przez chwilę „Skopiowane" zamiast pokazywać osobny dymek.
    var copied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(2_000)
            copied = null
        }
    }

    LaunchedEffect(target.id, reload) {
        listError = null
        shares = null
        if (!cloud.client.hasNetwork()) {
            listError = words.shareOfflineNow
            return@LaunchedEffect
        }
        val listing = withSyncRetry(cloud) {
            when (target) {
                is ShareTarget.Note -> cloud.client.listShares(target.id)
                is ShareTarget.Folder -> cloud.client.listFolderShares(target.id)
            }
        }
        when (val outcome = listing) {
            is CloudClient.Result.Ok -> shares = outcome.data.shares
            is CloudClient.Result.Error -> listError = outcome.message
        }
    }

    fun copyToClipboard(url: String, mark: String) {
        clipboard.setText(AnnotatedString(url))
        copied = mark
    }

    fun makeShare() {
        val address = email.trim().ifBlank { null }
        if (address != null && !android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
            formError = words.shareEmailWrong
            return
        }
        if (!cloud.client.hasNetwork()) {
            formError = words.shareOfflineNow
            return
        }
        creating = true
        formError = null
        made = null
        scope.launch {
            val outcome = withSyncRetry(cloud) {
                when (target) {
                    is ShareTarget.Note -> cloud.client.createShare(
                        noteId = target.id,
                        canEdit = canEdit,
                        email = address,
                        anonymousAllowed = noAccountAllowed,
                        expiresInDays = days.toIntOrNull()?.takeIf { it > 0 },
                    )
                    is ShareTarget.Folder -> cloud.client.createFolderShare(
                        folderId = target.id,
                        canEdit = canEdit,
                        email = address,
                        anonymousAllowed = noAccountAllowed,
                        expiresInDays = days.toIntOrNull()?.takeIf { it > 0 },
                    )
                }
            }
            creating = false
            when (outcome) {
                is CloudClient.Result.Ok -> {
                    made = outcome.data
                    reload++
                }

                is CloudClient.Result.Error -> formError = outcome.message
            }
        }
    }

    fun revoke(entry: ShareEntry) {
        if (confirmRevoke != entry.id) {
            confirmRevoke = entry.id
            return
        }
        confirmRevoke = null
        if (!cloud.client.hasNetwork()) {
            listError = words.shareOfflineNow
            return
        }
        scope.launch {
            val revoked = when (target) {
                is ShareTarget.Note -> cloud.client.revokeShare(target.id, entry.id)
                is ShareTarget.Folder -> cloud.client.revokeFolderShare(target.id, entry.id)
            }
            when (val outcome = revoked) {
                is CloudClient.Result.Ok -> {
                    shares = shares?.filterNot { it.id == entry.id }
                    if (made?.id == entry.id) made = null
                    listError = null
                }

                is CloudClient.Result.Error -> listError = outcome.message
            }
        }
    }

    val form: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (target is ShareTarget.Folder) {
                Text(words.shareFolderAbout, style = Kajet.type.body, color = Kajet.colors.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(words.shareWhatMayDo)
                ChoiceRow(
                    text = words.shareRightRead,
                    description = null,
                    selected = !canEdit,
                    onClick = { canEdit = false },
                )
                ChoiceRow(
                    text = words.shareRightEdit,
                    description = null,
                    selected = canEdit,
                    onClick = { canEdit = true },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KajetTextField(email, { email = it }, words.shareEmailLabel)
                Text(
                    text = words.shareEmailAbout,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KajetTextField(
                    value = days,
                    onChange = { typed -> days = typed.filter { it.isDigit() }.take(4) },
                    label = words.shareValidDays,
                )
                Text(
                    text = words.shareValidForever,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )
            }

            // Imienne udostępnienie z natury wymaga konta - przełącznik nie ma
            // wtedy czego przełączać.
            if (email.isBlank()) {
                ChoiceRow(
                    text = words.shareAllowNoAccount,
                    description = null,
                    selected = noAccountAllowed,
                    onClick = { noAccountAllowed = !noAccountAllowed },
                )
            } else {
                Text(
                    text = words.shareByNameAbout,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )
            }

            if (formError != null) {
                Text(formError.orEmpty(), style = Kajet.type.body, color = Kajet.colors.danger)
            }

            PrimaryButton(
                text = if (creating) words.shareMaking else words.shareMake,
                onClick = ::makeShare,
                icon = KajetIcons.ShareArrow,
                enabled = !creating,
            )

            made?.let { fresh ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(words.shareLinkReady)
                    Text(
                        text = fresh.url,
                        style = Kajet.type.meta,
                        color = Kajet.colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    fresh.email?.let { to ->
                        Text(
                            text = if (fresh.mailSent) words.shareMailWent(to) else words.shareMailNotSent,
                            style = Kajet.type.meta,
                            color = if (fresh.mailSent) Kajet.colors.muted else Kajet.colors.danger,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SecondaryButton(
                            text = if (copied == "new") words.copiedWord else words.copyLink,
                            onClick = { copyToClipboard(fresh.url, "new") },
                            icon = KajetIcons.Copy,
                        )
                        SecondaryButton(
                            text = words.sendLink,
                            onClick = { formError = service.shareText(context, fresh.url, target.title) },
                            icon = KajetIcons.ShareArrow,
                        )
                    }
                }
            }
        }
    }

    val list: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(words.shareAlready)

            listError?.let { problem ->
                NoticeBar(
                    icon = KajetIcons.ErrorMark,
                    text = problem,
                    color = Kajet.colors.danger,
                    action = if (shares == null) {
                        { SecondaryButton(words.shareTryAgain, onClick = { reload++ }) }
                    } else {
                        null
                    },
                )
            }

            when {
                shares == null && listError == null -> Text(
                    text = words.shareListLoading,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )

                shares?.isEmpty() == true -> Text(
                    text = words.shareNobodyYet,
                    style = Kajet.type.body,
                    color = Kajet.colors.muted,
                )

                else -> shares.orEmpty().forEach { entry ->
                    ShareRow(
                        entry = entry,
                        confirming = confirmRevoke == entry.id,
                        copiedNow = copied == entry.id,
                        onCopy = { copyToClipboard(entry.url, entry.id) },
                        onRevoke = { revoke(entry) },
                    )
                }
            }
        }
    }

    // Na szerokim ekranie formularz i lista stoją obok siebie, na wąskim
    // jedno pod drugim - ten sam próg co w bibliotece.
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    KajetDialog(
        title = if (target is ShareTarget.Folder) words.shareFolderTitle else words.sharePanelTitle,
        onClose = onClose,
        width = if (wide) 780 else 520,
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) { form() }
                Column(Modifier.weight(1f)) { list() }
            }
        } else {
            form()
            HorizontalRule()
            list()
        }
    }
}

/**
 * Jedno udostępnienie na liście: komu, prawa, ważność, ostatnie otwarcie
 * i przyciski. „Cofnij" pyta drugim stuknięciem zamiast osobnym oknem -
 * okno w oknie na tablecie z rysikiem to za dużo zachodu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareRow(
    entry: ShareEntry,
    confirming: Boolean,
    copiedNow: Boolean,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    val words = LocalStrings.current
    val context = LocalContext.current
    // Lokalna kopia: właściwość z innego modułu nie podda się smart castowi.
    val expiresAt = entry.expiresAt
    val expired = expiresAt != null && expiresAt < System.currentTimeMillis()

    val rights = if (entry.permission == "edit") words.shareRightEdit else words.shareRightRead
    val validity = when {
        expiresAt == null -> words.shareNoDeadline
        expired -> "${words.shareValidUntil(formatDay(context, expiresAt))} · ${words.shareExpiredMark}"
        else -> words.shareValidUntil(formatDay(context, expiresAt))
    }
    val accountMark =
        if (entry.email == null && !entry.anonymousAllowed) " · ${words.shareNeedsAccount}" else ""

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = entry.email ?: words.shareLinkAnyone,
            style = Kajet.type.body,
            color = Kajet.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$rights · $validity$accountMark",
            style = Kajet.type.meta,
            color = if (expired) Kajet.colors.danger else Kajet.colors.muted,
        )
        Text(
            text = entry.lastUsedAt
                ?.let { words.shareLastOpened(formatDay(context, it)) }
                ?: words.shareNotOpenedYet,
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!expired) {
                SecondaryButton(
                    text = if (copiedNow) words.copiedWord else words.copyLink,
                    onClick = onCopy,
                    icon = KajetIcons.Copy,
                )
            }
            SecondaryButton(
                text = if (confirming) words.shareRevokeSure else words.shareRevoke,
                onClick = onRevoke,
                color = Kajet.colors.danger,
            )
        }
        HorizontalRule(color = Kajet.colors.line.copy(alpha = 0.6f))
    }
}

/**
 * Serwer może jeszcze nie znać świeżo napisanej notatki (404) - wtedy
 * wysyłamy ją synchronizacją i pytamy drugi raz. Ten sam obyczaj co przy
 * dawnym przycisku „Udostępnij odnośnikiem".
 */
private suspend fun <T> withSyncRetry(
    cloud: Cloud.Parts,
    ask: suspend () -> CloudClient.Result<T>,
): CloudClient.Result<T> {
    val first = ask()
    if (first !is CloudClient.Result.Error || !first.notFound) return first
    cloud.sync.synchroniseInBackground().await()
    return ask()
}

private fun formatDay(context: android.content.Context, ms: Long): String =
    android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(ms))
