package wojtoteka.ovh.kajet.core.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
) {
    val words = LocalStrings.current

    Dialog(onDismissRequest = onNo) {
        Column(
            Modifier
                .width(400.dp)
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(words.aiConsentTitle, style = Kajet.type.title, color = Kajet.colors.text)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 380.dp)
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

            SecondaryButton(
                text = words.aiConsentReadPolicy,
                onClick = onOpenPolicy,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(text = words.aiConsentAgree, onClick = onAgree)
                SecondaryButton(text = words.aiConsentNo, onClick = onNo)
            }
        }
    }
}
