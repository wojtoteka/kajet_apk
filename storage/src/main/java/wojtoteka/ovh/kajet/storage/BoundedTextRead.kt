package wojtoteka.ovh.kajet.storage

/**
 * Wynik bezpiecznego odczytu pliku tekstowego do edytora.
 *
 * Zwykła synchronizacja nadal może przeczytać cały plik. Ekran edycji używa
 * tej drogi, żeby nie wciągnąć do Compose pliku, którego nie da się płynnie
 * wyświetlić.
 */
sealed interface BoundedTextRead {
    data class Content(val text: String) : BoundedTextRead

    data class TooLarge(
        /** Rozmiar zgłoszony przez dostawcę pliku albo co najmniej limit + 1. */
        val sizeBytes: Long,
        /** URI pozwala przekazać plik zewnętrznej aplikacji bez czytania treści. */
        val documentUri: String,
    ) : BoundedTextRead
}
