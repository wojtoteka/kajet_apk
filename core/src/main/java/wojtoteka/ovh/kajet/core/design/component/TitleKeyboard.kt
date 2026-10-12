package wojtoteka.ovh.kajet.core.design.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

/*
  Pole tytułu w pasku notatki - jednowierszowe, bez własnej ramki.

  Ptaszek na klawiaturze chował ją, ale pole zostawało w skupieniu i kursor
  mrugał dalej w tytule, jakby czekał na kolejne litery. Najgorzej w notatce
  odręcznej: płótno nie przejmuje skupienia, więc kreska wisiała tam do
  wyjścia z notatki. Teraz „Gotowe" oddaje skupienie, a chowanie klawiatury
  w inny sposób (przycisk „wstecz", gest) robi to samo.
*/

/** „Gotowe" zamiast „Enter" - tytuł ma jeden wiersz. */
val TitleKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)

/** Ptaszek na klawiaturze kończy pisanie tytułu - kursor znika razem z nią. */
@Composable
fun titleKeyboardActions(): KeyboardActions {
    val focus = LocalFocusManager.current
    return KeyboardActions(onDone = { focus.clearFocus() })
}

/**
 * Oddaje skupienie, gdy klawiatura ekranowa schowa się w trakcie pisania
 * tytułu. Przy klawiaturze fizycznej (ekranowej w ogóle nie widać) nic nie
 * robi - pole zostaje w skupieniu, dopóki ktoś pisze.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.releaseFocusWithKeyboard(): Modifier {
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.isImeVisible
    var focused by remember { mutableStateOf(false) }
    var keyboardSeen by remember { mutableStateOf(false) }

    LaunchedEffect(focused, keyboardVisible) {
        when {
            !focused -> keyboardSeen = false
            keyboardVisible -> keyboardSeen = true
            keyboardSeen -> {
                keyboardSeen = false
                focus.clearFocus()
            }
        }
    }

    return this.onFocusChanged { focused = it.isFocused }
}
