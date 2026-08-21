package wojtoteka.ovh.kajet.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.InlineNotice
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.notesStuck
import wojtoteka.ovh.kajet.core.text.notesWaiting
import wojtoteka.ovh.kajet.core.text.offlineWaiting
import wojtoteka.ovh.kajet.core.text.spaceUsed
import wojtoteka.ovh.kajet.core.text.spaceUsedNoLimit

@Composable
fun AccountScreen(model: AccountViewModel, onBack: () -> Unit) {
    val words = LocalStrings.current
    val state by model.accountState.collectAsStateWithLifecycle()
    val syncState by model.syncState.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val waitingForBrowser by model.waitingForBrowser.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val waiting by model.waitingInQueue.collectAsStateWithLifecycle()
    val stuck by model.stuckNotes.collectAsStateWithLifecycle()
    val authUri by DeviceAuthBridge.pending.collectAsStateWithLifecycle()

    LaunchedEffect(authUri) {
        val uri = authUri ?: return@LaunchedEffect
        model.onAuthDeepLink(uri)
        DeviceAuthBridge.clear()
    }

    // Świeży stan konta z serwera przy wejściu na ekran ORAZ po zalogowaniu -
    // sam klucz Unit odpalał się, gdy jeszcze nie było tokenu, i zajętość
    // zostawała zerowa aż do ponownego wejścia.
    LaunchedEffect(state is SignInState.SignedIn) {
        model.refreshFromServer()
    }

    val colors = Kajet.colors
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val sheetPadding = if (narrow) 16.dp else 28.dp
    val railWidth = if (narrow) 48.dp else Kajet.dimens.railWidth

    Row(Modifier.fillMaxSize().background(colors.desk)) {
        Column(
            Modifier
                .width(railWidth)
                .fillMaxSize()
                .background(colors.desk)
                .marginRule(colors.line),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconAction(KajetIcons.BackArrow, words.back, onBack)
        }

        Column(
            Modifier
                // weight, nie fillMaxSize: kolumna ma dostać dokładnie to, co
                // zostaje obok szyny - ten sam wzorzec co w bibliotece.
                .fillMaxHeight()
                .weight(1f)
                .background(colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sheetPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(words.cloudAccount, style = Kajet.type.display, color = colors.text)

            if (error != null) {
                Notice(text = error.orEmpty(), color = colors.danger, onClose = model::hideMessage)
            }
            if (message != null) {
                Notice(text = message.orEmpty(), color = colors.accent, onClose = model::hideMessage)
            }

            when (val current = state) {
                is SignInState.SignedOut -> SignIn(
                    busy = busy,
                    waitingForBrowser = waitingForBrowser,
                    onSignIn = model::signIn,
                    onSignInWithBrowser = model::signInWithBrowser,
                    onCancelBrowser = { model.cancelBrowserSignIn() },
                    onSignInWithToken = model::signInWithToken,
                )

                is SignInState.SessionExpired -> {
                    // Sesja wygasła (np. wylogowanie wszystkich sesji przez
                    // stronę): mówimy to wprost i od razu dajemy formularz.
                    Notice(
                        text = words.sessionExpired,
                        color = colors.danger,
                        onClose = null,
                    )
                    SignIn(
                        busy = busy,
                        waitingForBrowser = waitingForBrowser,
                        onSignIn = model::signIn,
                        onSignInWithBrowser = model::signInWithBrowser,
                        onCancelBrowser = { model.cancelBrowserSignIn() },
                        onSignInWithToken = model::signInWithToken,
                    )
                }

                is SignInState.SignedIn -> SignedIn(
                    state = current,
                    syncState = syncState,
                    waitingInQueue = waiting,
                    stuckNotes = stuck,
                    busy = busy,
                    onSynchronise = model::synchroniseNow,
                    onRetryStuck = model::retryStuck,
                    onSignOut = model::signOut,
                    onWithdrawAiConsent = model::withdrawAiConsent,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignIn(
    busy: Boolean,
    waitingForBrowser: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignInWithBrowser: () -> Unit,
    onCancelBrowser: () -> Unit,
    onSignInWithToken: (String) -> Unit,
) {
    val words = LocalStrings.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var viaToken by remember { mutableStateOf(false) }

    Column(
        Modifier.widthIn(max = Kajet.dimens.readingWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = words.cloudAccountAbout,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
        )

        if (waitingForBrowser) {
            Text(
                text = words.waitingForApproval,
                style = Kajet.type.body,
                color = Kajet.colors.text,
            )
            SecondaryButton(words.cancelWaiting, onCancelBrowser)
            return
        }

        if (!viaToken) {
            PrimaryButton(
                text = if (busy) words.opening else words.signInWithGoogle,
                onClick = onSignInWithBrowser,
                icon = KajetIcons.Account,
                enabled = !busy,
            )
            Text(
                text = words.signInWithGoogleAbout,
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )

            HorizontalRule()
            SectionLabel(words.orAddressAndPassword)

            Field(words.emailAddress, email, { email = it }, KeyboardType.Email)
            Field(words.password, password, { password = it }, KeyboardType.Password, hidden = true)

            // Zawijanie jak w oknie eksportu: przy dużej czcionce systemowej
            // dwa przyciski w sztywnym wierszu ściskały jeden drugiego do zera.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryButton(
                    text = if (busy) words.signingIn else words.signIn,
                    onClick = { onSignIn(email, password) },
                    icon = KajetIcons.Account,
                    enabled = !busy,
                )
                SecondaryButton(words.tokenFromBrowser, { viaToken = true })
            }
        } else {
            Text(
                text = words.tokenAbout,
                style = Kajet.type.body,
                color = Kajet.colors.muted,
            )
            Field(words.tokenFromSite, token, { token = it }, KeyboardType.Ascii)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryButton(
                    text = if (busy) words.checking else words.connect,
                    onClick = { onSignInWithToken(token) },
                    icon = KajetIcons.Confirm,
                    enabled = !busy,
                )
                SecondaryButton(words.back, { viaToken = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignedIn(
    state: SignInState.SignedIn,
    syncState: SyncState,
    waitingInQueue: Int,
    stuckNotes: Int,
    busy: Boolean,
    onSynchronise: () -> Unit,
    onRetryStuck: () -> Unit,
    onSignOut: () -> Unit,
    onWithdrawAiConsent: () -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors

    Column(
        Modifier.widthIn(max = Kajet.dimens.readingWidth),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(words.signedIn)
            Text(
                text = state.login,
                style = Kajet.type.title,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Adres zawija się w całości - w e-mailu i początek, i domena
            // mówią, czyj to adres, więc nic nie ucinamy.
            Text(state.email, style = Kajet.type.meta, color = colors.muted)

            HorizontalRule()

            SectionLabel(words.spaceLabel)
            Text(
                text = if (state.unlimited) {
                    words.spaceUsedNoLimit(humanSize(state.usedBytes))
                } else {
                    words.spaceUsed(humanSize(state.usedBytes), humanSize(state.quotaBytes))
                },
                style = Kajet.type.body,
                color = colors.text,
            )
            if (!state.unlimited) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.line, RoundedCornerShape(Kajet.dimens.corner)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(state.usedPercent / 100f)
                            .height(6.dp)
                            .background(
                                if (state.usedPercent >= 90) colors.danger else colors.accent,
                                RoundedCornerShape(Kajet.dimens.corner),
                            ),
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(words.syncSection)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = when (syncState) {
                        is SyncState.NoNetwork -> KajetIcons.Offline
                        is SyncState.Done -> KajetIcons.CloudDone
                        else -> KajetIcons.CloudMark
                    },
                    contentDescription = null,
                    tint = when (syncState) {
                        is SyncState.NoNetwork, SyncState.MustSignIn -> colors.muted
                        is SyncState.Done -> colors.accent
                        else -> colors.text
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = describeState(syncState, waitingInQueue, words),
                    style = Kajet.type.body,
                    color = colors.text,
                    // weight: tekst ma się zmieścić w tym, co zostaje po
                    // ikonie, i zawinąć - nie rozpychać wiersza.
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = words.syncAbout,
                style = Kajet.type.meta,
                color = colors.muted,
            )

            /*
              Notatki, które wyczerpały próby wysyłki. Kiedyś znikały z kolejki
              po cichu i przestawały się synchronizować na zawsze - teraz mają
              własny wiersz i przycisk, który daje im nową pulę prób.
            */
            if (stuckNotes > 0) {
                Text(
                    text = words.notesStuck(stuckNotes),
                    style = Kajet.type.body,
                    color = colors.danger,
                )
                Text(
                    text = words.stuckAbout,
                    style = Kajet.type.meta,
                    color = colors.muted,
                )
                SecondaryButton(words.retryStuckButton, onRetryStuck)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Przycisk gaśnie też, kiedy synchronizacja chodzi w tle (po
                // autozapisie), nie tylko po jego własnym kliknięciu.
                val syncBusy = busy || syncState is SyncState.InProgress
                PrimaryButton(
                    text = if (syncBusy) words.syncing else words.syncNow,
                    onClick = onSynchronise,
                    icon = KajetIcons.CloudMark,
                    enabled = !syncBusy,
                )
                SecondaryButton(words.signOut, onSignOut, color = colors.danger)
            }
        }

        /*
          Zgoda na asystenta. Pokazuje się TYLKO temu, kto ma uprawnienie -
          konto bez niego nie ma się na co zgadzać i nie ma się dowiedzieć,
          że jest czego odmawiać.

          Wycofanie to jeden przycisk, bez pytania „czy na pewno": zgoda ma
          być łatwiejsza do cofnięcia niż do udzielenia, a nie odwrotnie.
        */
        if (state.aiAvailable) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(words.aiConsentSection)
                Text(
                    text = if (state.aiConsented) words.aiConsentGiven else words.aiConsentMissing,
                    style = Kajet.type.body,
                    color = colors.muted,
                )
                if (state.aiConsented) {
                    SecondaryButton(
                        text = words.aiConsentWithdraw,
                        onClick = onWithdrawAiConsent,
                        color = colors.danger,
                    )
                }
            }
        }

        Text(
            text = words.signOutAbout,
            style = Kajet.type.meta,
            color = colors.muted,
        )
    }
}

private fun describeState(state: SyncState, waiting: Int, words: Strings): String = when (state) {
    SyncState.Idle ->
        if (waiting > 0) words.notesWaiting(waiting) else words.everythingSynced
    SyncState.InProgress -> words.syncing
    is SyncState.Done -> words.everythingSynced
    is SyncState.Waiting -> words.notesWaiting(state.count)
    is SyncState.NoNetwork ->
        if (state.waiting > 0) words.offlineWaiting(state.waiting) else words.noInternet
    SyncState.MustSignIn -> words.sessionExpired
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    kind: KeyboardType,
    hidden: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Kajet.type.body.copy(color = Kajet.colors.text),
            cursorBrush = SolidColor(Kajet.colors.accent),
            visualTransformation = if (hidden) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = kind, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color, onClose: (() -> Unit)?) {
    val words = LocalStrings.current
    InlineNotice(text = text, color = color) {
        // Bez zamykania, gdy komunikat opisuje trwały stan (wygasła sesja) -
        // zniknie sam po ponownym zalogowaniu.
        if (onClose != null) {
            IconAction(KajetIcons.Close, words.closeMessage, onClose, iconSize = 16.dp)
        }
    }
}
