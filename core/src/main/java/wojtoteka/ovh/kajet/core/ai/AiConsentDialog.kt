package wojtoteka.ovh.kajet.core.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.text.LocalStrings

/*
  Zgoda przy pierwszym użyciu asystenta.

  Nie jest to formalność do odklikania. Treść notatki wychodzi z Kajetu do
  obcej firmy, a na darmowym poziomie Gemini API trafia przy okazji do rozwoju
  produktów Google i może ją przeczytać człowiek. Dlatego okno mówi to wprost,
  osobnym akapitem, zamiast chować za odnośnikiem do polityki - a przycisk
  „Nie teraz" jest równie łatwy do trafienia jak „Zgadzam się".

  Zgoda zapisuje się PRZY KONCIE, nie na urządzeniu: po przeinstalowaniu
  aplikacji ma zostać zapamiętana, a po wycofaniu - zniknąć wszędzie.
*/
@Composable
fun AiConsentDialog(
    onAgree: () -> Unit,
    onNo: () -> Unit,
    onOpenPolicy: () -> Unit,
    error: String? = null,
    busy: Boolean = false,
) {
    val words = LocalStrings.current

    /*
      To samo okno co KajetDialog: bez usePlatformDefaultWidth=false karta
      zostaje przy sztywnych 400 dp i na telefonie wychodzi poza ekran.
      decorFitsSystemWindows=false oddaje wgląd w klawiaturę, a karta sama
      kurczy się w paddingu zamiast wystawać za pasek stanu.
    */
    Dialog(
        onDismissRequest = onNo,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onNo() } }
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxBody = (LocalConfiguration.current.screenHeightDp - 260).coerceAtLeast(96)

            Column(
                Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                    .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(words.aiConsentTitle, style = Kajet.type.title, color = Kajet.colors.text)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .heightIn(max = maxBody.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        words.aiConsentWhatHappens,
                        style = Kajet.type.body,
                        color = Kajet.colors.text,
                    )
                    // Osobne tło, bo to jest ta część, której nie wolno przeoczyć.
                    Text(
                        words.aiConsentTraining,
                        style = Kajet.type.body,
                        color = Kajet.colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Kajet.colors.desk, RoundedCornerShape(Kajet.dimens.corner))
                            .padding(12.dp),
                    )
                    Text(
                        words.aiConsentVoluntary,
                        style = Kajet.type.body,
                        color = Kajet.colors.muted,
                    )
                }

                if (error != null) {
                    Text(error, style = Kajet.type.body, color = Kajet.colors.muted)
                }

                SecondaryButton(
                    text = words.aiConsentReadPolicy,
                    onClick = onOpenPolicy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton(
                        text = words.aiConsentAgree,
                        onClick = onAgree,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    )
                    SecondaryButton(
                        text = words.aiConsentNo,
                        onClick = onNo,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    )
                }
            }
        }
    }
}
