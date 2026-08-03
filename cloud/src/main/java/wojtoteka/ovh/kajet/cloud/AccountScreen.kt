package wojtoteka.ovh.kajet.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons

@Composable
fun AccountScreen(model: AccountViewModel, onBack: () -> Unit) {
    val state by model.accountState.collectAsStateWithLifecycle()
    val syncState by model.syncState.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val waitingForBrowser by model.waitingForBrowser.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val waiting by model.waitingInQueue.collectAsStateWithLifecycle()
    val authUri by DeviceAuthBridge.pending.collectAsStateWithLifecycle()

    LaunchedEffect(authUri) {
        val uri = authUri ?: return@LaunchedEffect
        model.onAuthDeepLink(uri)
        DeviceAuthBridge.clear()
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
            IconAction(KajetIcons.BackArrow, "Wróć", onBack)
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sheetPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Konto w chmurze", style = Kajet.type.display, color = colors.text)

            if (error != null) {
                Notice(text = error.orEmpty(), color = colors.danger, onClose = model::hideMessage)
            }
            if (message != null) {
                Notice(text = message.orEmpty(), color = colors.accent, onClose = model::hideMessage)
            }

            when (val current = state) {
                is SignInState.SignedOut -> SignIn(
                    serverUrl = current.serverUrl,
                    busy = busy,
                    waitingForBrowser = waitingForBrowser,
                    onSignIn = model::signIn,
                    onSignInWithBrowser = model::signInWithBrowser,
                    onCancelBrowser = { model.cancelBrowserSignIn() },
                    onSignInWithToken = model::signInWithToken,
                )

                is SignInState.SignedIn -> SignedIn(
                    state = current,
                    syncState = syncState,
                    waitingInQueue = waiting,
                    busy = busy,
                    onSynchronise = model::synchroniseNow,
                    onSignOut = model::signOut,
                )
            }
        }
    }
}

@Composable
private fun SignIn(
    serverUrl: String,
    busy: Boolean,
    waitingForBrowser: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignInWithBrowser: () -> Unit,
    onCancelBrowser: () -> Unit,
    onSignInWithToken: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var viaToken by remember { mutableStateOf(false) }

    Column(
        Modifier.widthIn(max = Kajet.dimens.readingWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Kajet działa bez konta. Notatki leżą wtedy tylko na tym urządzeniu, " +
                "w katalogu, który sam wskazałeś. Konto przydaje się, żeby otworzyć " +
                "je na komputerze i odzyskać po zmianie telefonu albo tabletu.",
            style = Kajet.type.body,
            color = Kajet.colors.muted,
        )

        if (waitingForBrowser) {
            Text(
                text = "Czekam, aż zatwierdzisz logowanie na stronie. Możesz wrócić do aplikacji " +
                    "sam albo przyciskiem „Otwórz aplikację” po zatwierdzeniu.",
                style = Kajet.type.body,
                color = Kajet.colors.text,
            )
            SecondaryButton("Anuluj oczekiwanie", onCancelBrowser)
            return
        }

        if (!viaToken) {
            PrimaryButton(
                text = if (busy) "Otwieram..." else "Zaloguj przez Google",
                onClick = onSignInWithBrowser,
                icon = KajetIcons.Account,
                enabled = !busy,
            )
            Text(
                text = "Otworzy stronę $serverUrl w aplikacji. Tam wybierzesz konto Google " +
                    "(albo hasło) i zatwierdzisz to urządzenie — zwykle wystarczą trzy tapnięcia.",
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )

            HorizontalRule()
            SectionLabel("Albo adres i hasło")

            Field("Adres e-mail", email, { email = it }, KeyboardType.Email)
            Field("Hasło", password, { password = it }, KeyboardType.Password, hidden = true)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (busy) "Loguję..." else "Zaloguj się",
                    onClick = { onSignIn(email, password) },
                    icon = KajetIcons.Account,
                    enabled = !busy,
                )
                SecondaryButton("Token z przeglądarki", { viaToken = true })
            }
        } else {
            Text(
                text = "Zamiast hasła możesz wkleić token urządzenia. Otwórz w przeglądarce " +
                    "stronę $serverUrl/account, wydaj token dla tego urządzenia i przepisz go tutaj. " +
                    "Przydaje się jako awaryjne wejście, gdy logowanie przez stronę nie zadziała.",
                style = Kajet.type.body,
                color = Kajet.colors.muted,
            )
            Field("Token ze strony", token, { token = it }, KeyboardType.Ascii)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (busy) "Sprawdzam..." else "Połącz",
                    onClick = { onSignInWithToken(token) },
                    icon = KajetIcons.Confirm,
                    enabled = !busy,
                )
                SecondaryButton("Wróć", { viaToken = false })
            }
        }
    }
}

@Composable
private fun SignedIn(
    state: SignInState.SignedIn,
    syncState: SyncState,
    waitingInQueue: Int,
    busy: Boolean,
    onSynchronise: () -> Unit,
    onSignOut: () -> Unit,
) {
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
            SectionLabel("Zalogowany")
            Text(state.login, style = Kajet.type.title, color = colors.text)
            Text(state.email, style = Kajet.type.meta, color = colors.muted)

            HorizontalRule()

            SectionLabel("Miejsce")
            Text(
                text = if (state.unlimited) {
                    "${humanSize(state.usedBytes)} zajęte, bez limitu"
                } else {
                    "${humanSize(state.usedBytes)} z ${humanSize(state.quotaBytes)}"
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
            SectionLabel("Synchronizacja")

            Row(
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
                    text = describeState(syncState, waitingInQueue),
                    style = Kajet.type.body,
                    color = colors.text,
                )
            }

            Text(
                text = "Notatki wysyłają się same po każdym zapisie. Kiedy nie ma internetu, " +
                    "czekają na urządzeniu i idą, gdy sieć wróci.",
                style = Kajet.type.meta,
                color = colors.muted,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (busy) "Synchronizuję..." else "Synchronizuj teraz",
                    onClick = onSynchronise,
                    icon = KajetIcons.CloudMark,
                    enabled = !busy,
                )
                SecondaryButton("Wyloguj", onSignOut, color = colors.danger)
            }
        }

        Text(
            text = "Wylogowanie odcina chmurę, ale nie kasuje niczego z urządzenia. " +
                "Notatki zostają w katalogu, który wskazałeś.",
            style = Kajet.type.meta,
            color = colors.muted,
        )
    }
}

private fun describeState(state: SyncState, waiting: Int): String = when (state) {
    SyncState.Idle ->
        if (waiting > 0) "$waiting notatek czeka na wysłanie" else "Wszystko wysłane"
    SyncState.InProgress -> "Synchronizuję..."
    is SyncState.Done -> "Wszystko wysłane"
    is SyncState.Waiting -> "${state.count} notatek czeka na wysłanie"
    is SyncState.NoNetwork ->
        if (state.waiting > 0) {
            "Brak internetu. ${state.waiting} notatek czeka i pójdzie, gdy sieć wróci."
        } else {
            "Brak internetu"
        }
    SyncState.MustSignIn -> "Sesja wygasła. Zaloguj się jeszcze raz."
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
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, color, RoundedCornerShape(Kajet.dimens.corner))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = Kajet.type.body, color = color, modifier = Modifier.weight(1f))
        IconAction(KajetIcons.Close, "Zamknij komunikat", onClose, iconSize = 16.dp)
    }
}
