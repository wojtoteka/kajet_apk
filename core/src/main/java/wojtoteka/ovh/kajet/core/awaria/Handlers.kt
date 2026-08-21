package wojtoteka.ovh.kajet.core.awaria

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Wyjątek, który wyleci z korutyny bez własnego `try`, idzie do domyślnego
 * handlera wątku - czyli ubija cały proces. Model widoku nie ma o tym pojęcia,
 * a na ekranie zostaje samo tło.
 *
 * Ten handler zatrzymuje wyjątek na granicy zadania w tle: zapisuje go do
 * dziennika i, jeśli ekran ma gdzie o tym powiedzieć, oddaje go dalej przez
 * [onFailure]. Nic nie połyka po cichu - wpis w dzienniku jest zawsze.
 *
 * @param where nazwa miejsca, żeby wpis w dzienniku dało się przypisać.
 */
fun failureHandler(
    where: String,
    onFailure: ((Throwable) -> Unit)? = null,
): CoroutineExceptionHandler = CoroutineExceptionHandler { _, failure ->
    Log.e(TAG, "Zadanie w tle przewróciło się: $where", failure)
    runCatching { onFailure?.invoke(failure) }
}

private const val TAG = "Kajet"
