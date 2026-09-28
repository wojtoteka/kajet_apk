package wojtoteka.ovh.kajet.core.text

import wojtoteka.ovh.kajet.core.ai.AiNoteKind

/**
 * Wszystkie napisy Kajetu w jednym miejscu.
 *
 * Interfejs z polami, a każdy język to obiekt, który je nadpisuje: dodanie
 * napisu to jedna linijka tutaj i jedna w każdym języku, a kompilator nie
 * pozwoli o żadnym zapomnieć. Nazwy pól opisują MIEJSCE, w którym napis stoi,
 * a nie jego treść - „save” zmieni się kiedyś na „zapisz zmiany”, ale dalej
 * będzie tym samym przyciskiem.
 *
 * Nie może to być klasa z napisami w konstruktorze, choć wygląda to zgrabniej.
 * Wywołanie metody w Dalviku mieści najwyżej 255 rejestrów na argumenty, a
 * napisów jest ponad czterysta. Kotlin taki kod skompiluje, ale ART odrzuca
 * klasę przy pierwszym użyciu (VerifyError) i aplikacja ginie na starcie.
 * Przy nadpisywaniu pól każde stoi osobno i limit nie ma czego dotyczyć.
 *
 * Reguła na przyszłość: nowy napis w widoku dopisujemy tu, a nie wpisujemy
 * wprost w Compose. Inaczej wraca stan, w którym połowa aplikacji nie umie
 * mówić po angielsku.
 */
interface Strings {
    /*
      Czy to zestaw angielski.

      Potrzebne przy wyliczeniach, które noszą swoje nazwy przy sobie (barwy
      folderów, ikony, narzędzia pisaka, języki programowania). Wpisywanie
      trzydziestu ośmiu nazw ikon do tej klasy nic by nie dało - stoją tam,
      gdzie stoją, a stąd biorą tylko odpowiedź na pytanie „w którym języku".
    */
    val english: Boolean

    // --- Rzeczy wspólne ---
    val appName: String
    val save: String
    val cancel: String
    val close: String
    val back: String
    val delete: String
    val rename: String
    val copy: String
    val move: String
    val create: String
    val open: String
    val send: String
    val print: String
    val settings: String
    val account: String
    val search: String
    val understood: String
    val untitled: String
    val unnamed: String

    // --- Biblioteka ---
    val sectionLibrary: String
    val folders: String
    val sectionFavorites: String
    val sectionRecent: String
    val sectionSearch: String
    val sectionTrash: String
    val newFolder: String
    val newNote: String
    val newCodeFile: String
    val uploadFile: String
    val folderName: String
    val folderColour: String
    val folderIcon: String
    val emptyFolder: String
    val emptyFolderHint: String
    val emptyTrash: String

    /** Pasek postępu przy opróżnianiu kosza, zanim policzy się pierwszy plik. */
    val emptyingTrash: String
    val restore: String
    val deleteForever: String
    val moveToTrash: String
    val addToFavorites: String
    val removeFromFavorites: String
    val rebuildIndex: String

    // --- Zaznaczanie wielu wpisów naraz ---
    val selectMany: String
    val selectAll: String
    val selectionDone: String
    val trashManyQuestion: String

    // --- Notatka ---
    val noteText: String
    val noteHandwritten: String
    val noteMindMap: String
    val writeHere: String
    val saved: String
    val saving: String
    val loading: String
    val changesWaiting: String
    val saveFailed: String
    val noteInCloud: String
    val noteWaitingForCloud: String
    val noteOpenFailed: String
    val noteSaveFailed: String
    val noteDeletedElsewhere: String
    val noteDeletedElsewhereAbout: String
    val fileDeletedElsewhereAbout: String
    val saveAsNewNote: String
    val saveAsNewFile: String
    val discardChanges: String
    val saveAsNewFailed: String
    val placeholderWord: String
    val savingPhoto: String
    val savingDrawing: String
    val photoSaveFailed: String
    val drawingSaveFailed: String
    val exportTitle: String
    val exportFormat: String
    val exportPdfAbout: String
    val exportSave: String
    val shareLink: String
    val shareLinkBusy: String

    // --- Panel udostępniania ---
    val sharePanelTitle: String
    val shareWhatMayDo: String
    val shareRightRead: String
    val shareRightEdit: String
    val shareEmailLabel: String
    val shareEmailAbout: String
    val shareValidDays: String
    val shareValidForever: String
    val shareAllowNoAccount: String
    val shareMake: String
    val shareMaking: String
    val shareLinkReady: String
    val copyLink: String
    val copiedWord: String
    val shareAlready: String
    val shareNobodyYet: String
    val shareListLoading: String
    val shareLinkAnyone: String
    val shareNeedsAccount: String
    val shareByNameAbout: String
    val shareNoDeadline: String
    val shareExpiredMark: String
    val shareNotOpenedYet: String
    val shareRevoke: String
    val shareRevokeSure: String
    val shareMailNotSent: String
    val shareTryAgain: String
    val shareEmailWrong: String
    val shareOfflineNow: String

    // --- Pisak ---
    val penInk: String
    val penBlack: String
    val penGrey: String
    val penBlue: String
    val penRed: String
    val penGreen: String
    val penBrown: String
    val penYellow: String
    val penPink: String
    val pickOwnColour: String
    val strokeWidth: String
    val colourNamed: String
    val drawingInNote: String
    val closeWithoutSaving: String

    // --- Kalkulator ---
    val calculator: String
    val calculatorClose: String
    val calculatorCopyResult: String
    val calculatorError: String
    val calculatorBackspace: String
    val calculatorClear: String
    val calculatorParens: String
    val calculatorMove: String
    val insertDrawing: String
    val clearDrawing: String
    val drawWithFingerOrStylus: String
    val yourColours: String
    val thickness: String
    val opacity: String

    // --- Ustawienia ---
    val settingsLanguage: String
    val settingsLanguageAbout: String
    val languageSystem: String
    val languagePolish: String
    val languageEnglish: String
    val settingsToolbarSide: String
    val settingsToolbarSideAbout: String
    val toolbarLeft: String
    val toolbarRight: String
    val settingsAppearance: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val settingsCode: String
    val settingsCodeAbout: String
    val codeAssistOn: String
    val codeAssistOnAbout: String
    val codeAssistOff: String
    val codeAssistOffAbout: String

    // --- Ekran biblioteki ---
    val libRebuilding: String
    val libFavoritesAbout: String
    val libFavoritesEmpty: String
    val libRecentAbout: String
    val libRecentEmpty: String
    val libSearchInNotes: String
    val libAllNotes: String
    val libFolderUp: String
    val libFolderEmpty: String
    val libFolderEmptyHint: String
    val libNothingHere: String
    val libSearchAbout: String
    val libSearchPrompt: String
    val libSearchPromptAbout: String
    val libSearchNothing: String
    val libExpand: String
    val libCollapse: String
    val libKindFolder: String
    val libKindCode: String
    val emptyNote: String
    val emptyPage: String
    val noDate: String
    val justNow: String
    val inFavorites: String
    val libSearchNothingAbout: String
    val emptyTrashQuestion: String
    val trashAbout: String
    val trashEmptyTitle: String
    val trashEmptyAbout: String
    val menuMoveToFolder: String
    val menuCopy: String

    /** Doklejane do nazwy kopii: „Wzory (kopia)". */
    val copySuffix: String

    /** Pytanie przed kasowaniem jednego wpisu z kosza, bez odwrotu. */
    val deleteForeverQuestion: String
    val deleteForeverWarning: String
    val menuLook: String
    val exportFolderPdf: String
    val exportFolderMarkdown: String
    val movePrompt: String
    val codeShort: String

    // --- Okna: nowy folder, notatka, plik ---
    val colour: String
    val icon: String
    val createFolder: String
    val createNote: String
    val createFile: String
    val titleLabel: String
    val newName: String
    val fileName: String
    val defaultFileName: String
    val noteKindLabel: String
    val kindHandwrittenAbout: String
    val kindTextAbout: String
    val kindMindMapAbout: String
    val pageLabel: String
    val pageA4About: String
    val pageScrollAbout: String
    val pageBackgroundLabel: String
    val langHtmlAbout: String
    val langNeedsAccountAbout: String

    // --- Edytor kodu ---
    val codeOutput: String
    val codeErrors: String
    val codeInput: String
    val codeRun: String
    val codeStop: String
    val codePagePreview: String
    val codeSearchInFile: String
    val codeWordWrap: String
    val codeTypeTwoLetters: String
    val codeUnsaved: String
    val codeHtmlHint: String
    val codeWontRunHere: String
    val codeRunsOnTablet: String
    val codeRunsOnServer: String
    val codeWorkingOnTablet: String
    val codeSendingToServer: String
    val codeFromServer: String
    val codeFromTablet: String
    val codeExitCode: String
    val codeInterrupted: String
    val codeOutputEmpty: String
    val codeNoErrors: String
    val codeInputLabel: String
    val codeOpenFailed: String
    val codeSaveFailed: String
    val codeRunFailed: String
    val codeFileTooLargeTitle: String

    // --- Konsola pod podglądem strony ---
    val codeConsole: String
    val codeConsoleClear: String
    val codeConsoleEmpty: String

    // --- Edytor tekstowy ---
    val insertPhotoFromGallery: String
    val takePhoto: String
    val exportNote: String
    val rawMarkdownAbout: String
    val fontFamily: String
    val fontSize: String
    val fontSizeWholeNote: String
    val wholeNoteLook: String
    val defaultSize: String
    val defaultColour: String
    val tableAddRow: String
    val tableAddColumn: String
    val tableRemoveRow: String
    val tableRemoveColumn: String
    val tableRemove: String
    fun tableSize(rows: Int, columns: Int): String
    val smallerText: String
    val largerText: String
    val colourWholeNote: String
    val colourSelection: String
    val highlightSelection: String
    val bold: String
    val italic: String
    val underline: String
    val strike: String
    val heading1: String
    val heading2: String
    val heading3: String
    val bulletList: String
    val numberedList: String
    val taskList: String
    val quote: String
    val inlineCode: String
    val codeBlock: String
    val table: String
    val formula: String
    val link: String
    val dividerLine: String
    val showRawMarkdown: String
    val backToContentView: String
    val colourWholeNoteTitle: String
    val colourSelectionTitle: String
    val tableColumn: String
    val untickTask: String
    val tickTask: String
    val photoInNote: String
    val noCaption: String
    val photoSize: String
    val photoCaption: String
    val photoUp: String
    val photoDown: String
    val photoRemove: String
    val photoCaptionAbout: String
    val photoBeside: String
    val photoOwnLine: String
    val photoChoose: String
    val photoUnchoose: String
    val photoMove: String
    val photoSmaller: String
    val photoBigger: String

    // --- Edytor odręczny ---
    val penSettings: String
    val insertTextBox: String
    val fingerDrawsSwitch: String
    val fingerScrollsSwitch: String
    val noteSettingsIcon: String
    val undo: String
    val redo: String
    val eraser: String
    val morePenSettings: String
    val deselect: String
    val penTool: String
    val highlighterTool: String
    val shapeKindLabel: String
    val shapeOutlineColourTitle: String
    val shapeFillLabel: String
    val shapeNoFill: String
    val shapeFillColourTitle: String
    val shapeSquareLock: String
    val shapeSquareAbout: String
    val shapeRotate: String
    val shapeResize: String
    val shapeRemove: String
    val eraserSize: String
    val widthLabel: String
    val highlighterColourTitle: String
    val whatYouWriteWith: String
    val inkColourTitle: String
    val penAboutPen: String
    val penAboutFineliner: String
    val penAboutPencil: String
    val penAboutDashed: String
    val textColourTitle: String
    val addBoxBackground: String
    val changeBoxBackground: String
    val boxBackgroundTitle: String
    val yourColour: String
    val noteSettings: String
    val pageKind: String
    val pageA4Long: String
    val pageScrollLong: String
    val pagesLabel: String
    val onePage: String
    val addPage: String
    val removeLastPage: String
    val fingerLabel: String
    val palmRejectionAbout: String
    val fitWidth: String
    val textBox: String
    val moveTextBox: String
    val deleteTextBox: String
    val resizeTextBox: String

    // --- Mapa myśli ---
    val newNode: String
    val addBranch: String
    val addSibling: String
    val finishConnecting: String
    val connectToOthers: String
    val inkLabel: String
    val arrangeBranches: String
    val fitWholeMap: String
    val exportMap: String
    val releaseToConnect: String
    val releaseOnEmpty: String
    val tapNodesToConnect: String
    val nodeWithoutName: String
    val disconnect: String
    val keepConnection: String
    val zoomOut: String
    val zoomIn: String
    val emptyMap: String
    val emptyMapAbout: String
    val tapTwiceToType: String
    val dragToConnect: String
    val dragToResize: String
    val expandBranch: String
    val collapseBranch: String
    val selectedNode: String
    val hideSettings: String
    val showMoreSettings: String
    val nodeText: String
    val typeNodeText: String
    val textLabel: String
    val nodeColour: String
    val connections: String
    val connectionsAbout: String
    val nodeTextColour: String
    val colourFromTheme: String
    val colourOwn: String
    val shapeLabel: String
    val nodeColourTitle: String
    val nodeTextColourTitle: String

    // --- Zapis do pliku, wybór barwy, pismo odręczne ---
    val fileReady: String
    val savingFile: String
    val exportFailed: String
    val openFile: String
    val linkFailed: String
    val exportNoHandwriting: String
    val exportNoPhotos: String
    val exportHandwrittenOnly: String
    val exportTextOnly: String
    val hue: String
    val presetColours: String
    val recentColours: String
    val done: String
    val libraryFolderName: String
    val noNotesFolderPicked: String
    val sendLink: String
    val sendFile: String
    val shareWindowFailed: String
    val shareWindowFailedFile: String
    val noSystemPrinting: String
    val printFailed: String
    val printOpenFailed: String
    val printGaveUp: String
    val unknownError: String
    val emptyNoteInExport: String
    val mapNodes: String
    val handwrittenInDocx: String
    val pageWord: String
    val noDescription: String
    val handwrittenNoTextYet: String

    // --- Konto w chmurze ---
    val cloudAccount: String
    val cloudAccountAbout: String
    val waitingForApproval: String
    val cancelWaiting: String
    val opening: String
    val signInWithGoogle: String
    val signInWithGoogleAbout: String
    val orAddressAndPassword: String
    val emailAddress: String
    val password: String
    val signIn: String
    val signingIn: String
    val tokenFromBrowser: String
    val tokenAbout: String
    val tokenFromSite: String
    val checking: String
    val connect: String
    val signedIn: String
    val spaceLabel: String
    val takenNoLimit: String
    val syncSection: String
    val syncAbout: String
    val syncing: String
    val syncNow: String
    val signOut: String
    val signOutAbout: String
    val everythingSynced: String
    val noInternet: String
    val sessionExpired: String
    val closeMessage: String
    val closePanel: String
    val deviceFallbackName: String
    val giveEmailAndPassword: String
    val serverUnreachable: String
    val approveOnSite: String
    val backFromBrowser: String
    val checkingApproval: String
    val pasteTokenFromSite: String
    val sessionExpiredServer: String
    val signedOutNotesStay: String
    val syncFailedSafe: String
    val alreadyInSync: String
    val serverCopiesKept: String
    val incompleteSignInAnswer: String
    val approvalTimedOut: String
    val notSignedIn: String
    val serverGibberish: String
    val offlineNoteQueued: String
    val cannotReachServer: String
    val serverTimedOut: String
    val connectionDropped: String
    val fileDownloadFailed: String
    val serverRefused: String
    val notOnServer: String
    val noteChangedElsewhere: String
    val outOfSpace: String
    val serverTrouble: String
    val noNotesDirToSend: String
    val noNotesDirToSave: String
    val runOnServerNeedsAccount: String
    val runOnServerNeedsInternet: String
    val codeWord: String

    // --- Ustawienia i pliki na dysku ---
    val cloudAccountOn: String
    val cloudAccountOff: String
    val notesFolder: String
    val notesFolderAbout: String
    val noFolderPicked: String
    val changeFolder: String
    val rebuildIndexAbout: String
    val indexRebuilt: String
    val conflictCopyFailed: String
    val syncTrashMoveFailed: String
    val syncAttachmentFailed: String
    val retryStuckButton: String
    val stuckAbout: String
    val notUploadedTag: String
    val notUploadedAbout: String
    val stuckNothingLost: String
    val stylusAndFinger: String
    val stylusAndFingerAbout: String
    val fingerScrollsAbout: String
    val fingerDrawsAbout: String
    val appearanceAbout: String
    val newHandwrittenNote: String
    val newHandwrittenNoteAbout: String
    val autosaveSection: String
    val autosaveAbout: String
    val deviceStorage: String
    val greetingWord: String
    val photoAltText: String
    val greetingVariable: String
    val myPageTitle: String
    val emptyNoteFile: String
    val brokenContentFile: String
    val newerKajet: String
    val brokenInlineDrawing: String
    val brokenFolderDescription: String
    val noNotesFolderChosen: String
    val binEmpty: String
    val notInBinAnyMore: String
    val binEntryNoDescription: String
    val entryHasNoName: String
    val cannotMoveIntoItself: String
    val noParentFolder: String
    val handwritingLabel: String

    // --- Pierwsze uruchomienie ---
    val firstRun: String
    val whereToKeepNotes: String
    val whereToKeepNotesAbout: String
    val whereToKeepNotesWhy: String
    val pickNotesFolder: String
    val pickNotesFolderAbout: String
    val folderAccessLost: String
    val folderAccessLostAbout: String
    val couldNotKeepFolderAccess: String

    // --- Otwieranie i awarie ---
    val starting: String
    val startingSlow: String
    val errorScreenTitle: String
    val errorScreenAbout: String
    val errorRestart: String
    val errorCopyDetails: String
    val errorSendDetails: String
    val errorSendTitle: String
    val errorNoDetails: String
    val screenBrokeTitle: String
    val screenBrokeAbout: String
    val screenBrokeReload: String
    val noteNotOpened: String
    val noteNotOpenedAbout: String
    val noteOpening: String
    val noEditorYet: String
    val noEditorYetAbout: String
    val cannotEditAsText: String
    val cannotEditAsTextAbout: String
    val fileDidNotOpen: String
    val fileDidNotOpenAbout: String
    val couldNotImportShare: String
    val openInOtherApp: String
    val previousPage: String
    val nextPage: String
    val backToLibrary: String

    // --- Praca w tle ---
    val readingFolderFailed: String
    val readingFavoritesFailed: String
    val readingRecentFailed: String
    val walkingLibrary: String
    val actionFailed: String
    val pythonOnTablet: String
    val codeOnServer: String
    val pythonStartFailed: String

    // --- Informacje prawne i o aplikacji ---
    val legalSection: String
    val legalSectionAbout: String
    val termsOfService: String
    val privacyPolicy: String
    val documentNoNetwork: String
    val documentNoBrowser: String
    val aboutSection: String
    val aboutSectionAbout: String
    val appVersionWord: String

    // --- Nowa wersja aplikacji ---
    val updateTitle: String
    val updateToDownload: String
    val updateInstall: String
    val updateLater: String
    val updateWhatChanged: String
    val checkUpdates: String
    val checkingUpdates: String
    val upToDate: String
    val updateCheckFailed: String

    // --- Asystent KajetAI ---
    val aiTitle: String
    val aiOpen: String
    val aiClose: String
    val aiHint: String
    val aiAsk: String
    val aiWorking: String
    val aiUndo: String
    val aiUndone: String
    val aiUndoFailed: String
    val aiQuestionLabel: String
    val aiHistoryTitle: String
    val aiHistoryEmpty: String
    val aiForgetHistory: String
    val aiNoteChangedElsewhere: String
    val aiSaveFirst: String
    val aiOffline: String
    /* Zgoda */
    val aiConsentTitle: String
    val aiConsentWhatHappens: String
    val aiConsentTraining: String
    val aiConsentVoluntary: String
    val aiConsentReadPolicy: String
    val aiConsentAgree: String
    val aiConsentNo: String
    val aiConsentSection: String
    val aiConsentGiven: String
    val aiConsentMissing: String
    val aiConsentWithdraw: String
    val aiConsentWithdrawn: String
    val aiConsentFailed: String
    val aiPullFailed: String
}

/*
  Zdania z liczbą albo z nazwą w środku.

  Nie da się ich trzymać jako gotowych napisów, bo szyk zdania w każdym języku
  jest inny - a sklejanie kawałków w widoku daje zdania, których nikt nie
  napisałby po angielsku. Stoją więc tu, przy słowniku, całe.
*/

fun Strings.searchFound(count: Int): String =
    if (english) "$count found" else "Znalezione: $count"

fun Strings.shareValidUntil(date: String): String =
    if (english) "Valid until $date" else "Ważne do $date"

fun Strings.shareLastOpened(date: String): String =
    if (english) "Last opened $date" else "Ostatnio otwarte $date"

fun Strings.shareMailWent(address: String): String =
    if (english) "The message went to $address." else "Wiadomość poszła na $address."

/**
 * Plik jest dłuższy, niż serwer przyjmuje do uruchomienia.
 *
 * Granicę stawia route.ts i bez tego zdania odpowiadał na to „Podaj język
 * i kod" - czyli komunikatem o czymś zupełnie innym.
 */
fun Strings.codeTooLong(limit: Int): String = if (english) {
    "This file is too long to run. The server takes at most $limit characters."
} else {
    "Ten plik jest za długi, żeby go uruchomić. Serwer przyjmuje najwyżej $limit znaków."
}

fun Strings.codeInputTooLong(limit: Int): String = if (english) {
    "The input is too long. The server takes at most $limit characters."
} else {
    "Dane do wczytania są za długie. Serwer przyjmuje najwyżej $limit znaków."
}

/** Numer wiersza przy błędzie skryptu w konsoli podglądu. */
fun Strings.codeConsoleAtLine(line: Int): String =
    if (english) "line $line" else "wiersz $line"

/**
 * Konsola doszła do swojej granicy. Dalszych wierszy nie zbiera: pętla
 * z console.log wypisuje tysiące wierszy szybciej, niż da się je narysować.
 */
fun Strings.codeConsoleFull(limit: Int): String = if (english) {
    "$limit lines collected, and that is where it stops. Clear it to collect again."
} else {
    "Zebrane $limit wierszy i na tym koniec. Wyczyść, żeby zbierać od nowa."
}

fun Strings.cannotRunLanguage(language: String): String = if (english) {
    "Kajet cannot run $language. You can still write the file and save it."
} else {
    "Kajet nie umie uruchomić języka $language. Plik możesz nadal pisać i zapisywać."
}

/**
 * Ile jest w folderze.
 *
 * Polska liczba mnoga ma trzy postacie i nie da się jej złożyć z „wpis" plus
 * końcówka: 2-4 to „wpisy", ale 12-14 już „wpisów", za to 22-24 znowu „wpisy".
 * Dlatego całe zdania, a nie sklejanie.
 */
fun Strings.folderSummary(count: Int): String = if (english) {
    when (count) {
        0 -> "Empty folder"
        1 -> "1 item"
        else -> "$count items"
    }
} else {
    when {
        count == 0 -> "Pusty folder"
        count == 1 -> "1 wpis"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count wpisy"
        else -> "$count wpisów"
    }
}

fun Strings.starNote(name: String): String =
    if (english) "Add $name to favourites" else "Dodaj $name do ulubionych"

fun Strings.unstarNote(name: String): String =
    if (english) "Remove $name from favourites" else "Usuń $name z ulubionych"

fun Strings.actionsFor(name: String): String =
    if (english) "Actions for $name" else "Działania dla $name"

fun Strings.emptyTrashWarning(count: Int): String = if (english) {
    "All $count notes in the bin will be gone for good. This cannot be undone."
} else {
    "Wszystkie notatki z kosza ($count) znikną na dobre. Tego nie da się cofnąć."
}

fun Strings.trashedAt(whenText: String, where: String): String = if (english) {
    "Binned $whenText · was in: $where"
} else {
    "Wyrzucono $whenText · było w: $where"
}

/**
 * Ile zostało wpisowi w koszu, który trafił tam za serwerem. „Dzień" tylko
 * przy jedynce - po polsku każda inna liczba bierze „dni".
 */
fun Strings.disappearsIn(days: Int): String = when {
    days <= 0 -> if (english) "Disappears today" else "Zniknie dziś"
    days == 1 -> if (english) "Disappears tomorrow" else "Zniknie jutro"
    english -> "Disappears in $days days"
    else -> "Zniknie za $days dni"
}

fun Strings.deleteForeverOf(name: String): String =
    if (english) "Delete $name for good" else "Skasuj $name na dobre"

/**
 * Nazwa katalogu na dysku, gdy różni się od wpisanej. Dwukropka ani ukośnika
 * nazwa pliku nie zniesie - lepiej powiedzieć to wprost, niż zostawić dwie
 * różne nazwy bez wyjaśnienia.
 */
fun Strings.nameOnDiskAbout(nameOnDisk: String): String =
    if (english) "On the disk: $nameOnDisk" else "Na dysku: $nameOnDisk"

fun Strings.moveDialogTitle(name: String): String =
    if (english) "Move: $name" else "Przenieś: $name"

// --- Zaznaczanie wielu wpisów naraz ---
//
// Wszędzie „wpis", nie „notatka": zaznaczyć da się także folder i plik
// z kodem, a zdanie ma mówić prawdę o tym, co za chwilę zniknie.

fun Strings.selectedCount(count: Int): String = if (english) {
    if (count == 1) "1 selected" else "$count selected"
} else {
    val noun = when {
        count == 1 -> "wpis"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "wpisy"
        else -> "wpisów"
    }
    "Zaznaczono: $count $noun"
}

fun Strings.trashManyWarning(count: Int): String = if (english) {
    if (count == 1) {
        "One item goes to the bin. You can take it out of there."
    } else {
        "$count items go to the bin. You can take them out of there."
    }
} else {
    val noun = when {
        count == 1 -> "Jeden wpis trafi"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count wpisy trafią"
        else -> "$count wpisów trafi"
    }
    "$noun do kosza. Da się je stamtąd wyjąć."
}

fun Strings.moveManyTitle(count: Int): String = if (english) {
    if (count == 1) "Move 1 item" else "Move $count items"
} else {
    val noun = when {
        count == 1 -> "1 wpis"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count wpisy"
        else -> "$count wpisów"
    }
    "Przenieś $noun"
}

// Pasek postępu pokazuje to zdanie w całości, więc mówi ono, CO się dzieje,
// a nie tylko przy którym wpisie stanęło.

fun Strings.trashingProgress(done: Int, total: Int): String =
    if (english) "Moving to the bin: $done of $total" else "Wyrzucam do kosza: $done z $total"

fun Strings.movingProgress(done: Int, total: Int): String =
    if (english) "Moving: $done of $total" else "Przenoszę: $done z $total"

/*
  Kasowanie z kosza. Idzie plik po pliku i przy pełnym koszu trwa kilka albo
  kilkanaście sekund - bez tego zdania ekran stał w miejscu, a potem wszystko
  znikało naraz i wyglądało to, jakby przycisk nie zadziałał.
*/
fun Strings.deletingProgress(done: Int, total: Int): String =
    if (english) "Deleting for good: $done of $total" else "Kasuję na dobre: $done z $total"

fun Strings.deletingOne(name: String): String =
    if (english) "Deleting for good: $name" else "Kasuję na dobre: $name"

/**
 * Ile wpisów nie dało się ruszyć. Reszta poszła - dlatego zdanie mówi
 * o niepowodzeniu części, a nie o niepowodzeniu całości.
 */
fun Strings.bulkPartlyFailed(count: Int): String = if (english) {
    if (count == 1) "One item could not be done." else "$count items could not be done."
} else {
    // Po polsku przeczenie bierze dopełniacz przy każdej liczbie, więc tu
    // trzech postaci liczby mnogiej nie ma - jest „wpisów" i już.
    val noun = if (count == 1) "Jednego wpisu" else "$count wpisów"
    "$noun nie udało się ruszyć."
}

fun Strings.folderLookTitle(name: String): String =
    if (english) "Folder look: $name" else "Wygląd folderu: $name"

fun Strings.refreshingIndex(done: Int, total: Int): String = if (english) {
    "Refreshing the list of notes, $done of $total"
} else {
    "Odświeżam spis notatek, $done z $total"
}

fun Strings.checkingProgress(done: Int, total: Int): String =
    if (english) "Checking $done of $total" else "Sprawdzam $done z $total"

fun Strings.savingFolder(name: String): String =
    if (english) "Saving the folder $name" else "Zapisuję folder $name"

fun Strings.savingNote(done: Int, total: Int): String =
    if (english) "Saving note $done of $total" else "Zapisuję notatkę $done z $total"

fun Strings.savedAt(hour: Int, minute: Int): String =
    "%s %02d:%02d".format(saved, hour, minute)

fun Strings.photoNotFound(file: String): String = if (english) {
    "Kajet could not find $file. The photo may have been deleted from the note's folder."
} else {
    "Nie ma pliku $file. Zdjęcie mogło zostać skasowane z katalogu notatki."
}

fun Strings.photoOfMany(number: Int, total: Int): String = if (english) {
    "Photo $number of $total"
} else {
    "Zdjęcie $number z $total"
}

fun Strings.selectedStrokes(count: Int): String = if (english) {
    "$count strokes selected"
} else {
    val noun = when {
        count == 1 -> "kreskę"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "kreski"
        else -> "kresek"
    }
    "Zaznaczono $count $noun"
}

fun Strings.pagesCount(count: Int): String = if (english) {
    "This note has $count pages."
} else {
    "Notatka ma $count ${pageNoun(count)}."
}

/** Sam licznik stron, na pasek edytora: „3 strony", „5 stron". */
fun Strings.pagesShort(count: Int): String = if (english) {
    "$count ${if (count == 1) "page" else "pages"}"
} else if (count == 1) {
    "1 strona"
} else {
    "$count ${pageNoun(count)}"
}

fun Strings.eraserSizeOf(size: Int): String =
    if (english) "Eraser size $size" else "Wielkość gumki $size"

fun Strings.strokeWidthOf(size: String): String =
    if (english) "Width $size" else "Grubość $size"

fun Strings.disconnectFrom(name: String): String =
    if (english) "Disconnect from $name" else "Rozłącz z $name"

fun Strings.thisNode(): String = if (english) "this node" else "tym węzłem"

fun Strings.colourNamedHex(hex: String): String = "$colourNamed $hex"

fun Strings.conflictsNoted(count: Int): String = if (english) {
    "$count notes changed in two places at once. $serverCopiesKept"
} else {
    val noun = when {
        count == 1 -> "notatka zmieniła się"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "notatki zmieniły się"
        else -> "notatek zmieniło się"
    }
    "$count $noun w dwóch miejscach naraz. $serverCopiesKept"
}

fun Strings.mapTally(nodes: Int, edges: Int): String = if (english) {
    "$nodes nodes · $edges connections"
} else {
    val nodeWord = when {
        nodes == 1 -> "węzeł"
        nodes % 10 in 2..4 && nodes % 100 !in 12..14 -> "węzły"
        else -> "węzłów"
    }
    val edgeWord = when {
        edges == 1 -> "połączenie"
        edges % 10 in 2..4 && edges % 100 !in 12..14 -> "połączenia"
        else -> "połączeń"
    }
    "$nodes $nodeWord · $edges $edgeWord"
}

fun Strings.spaceUsedPercent(used: String, quota: String, percent: Int): String = if (english) {
    "$used of $quota used (${percentOf(percent)})"
} else {
    "Zajęte $used z $quota (${percentOf(percent)})"
}

fun Strings.handwritingSummary(strokes: Int, pages: Int): String = when {
    strokes == 0 -> emptyNote
    english && pages == 1 -> "Handwriting, $strokes ${if (strokes == 1) "stroke" else "strokes"}"
    english -> "Handwriting, $pages pages"
    pages == 1 -> "$handwritingLabel, $strokes ${strokeNoun(strokes)}"
    else -> "$handwritingLabel, $pages ${pageNoun(pages)}"
}

/** „1 kreska", „2 kreski", „5 kresek" - trzy postacie polskiej liczby mnogiej. */
private fun strokeNoun(count: Int): String = when {
    count == 1 -> "kreska"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "kreski"
    else -> "kresek"
}

/** To samo dla stron: „2 strony", ale „5 stron". */
private fun pageNoun(count: Int): String = when {
    count % 10 in 2..4 && count % 100 !in 12..14 -> "strony"
    else -> "stron"
}

/** Ile węzłów ma mapa myśli. */
fun Strings.nodesCount(count: Int): String = if (english) {
    "$count ${if (count == 1) "node" else "nodes"}"
} else {
    val noun = when {
        count == 1 -> "węzeł"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "węzły"
        else -> "węzłów"
    }
    "$count $noun"
}

fun Strings.cannotOpenFile(name: String?): String =
    if (english) "Kajet cannot open $name." else "Nie można otworzyć pliku $name."

fun Strings.cannotSaveFile(name: String?): String =
    if (english) "Kajet cannot save $name." else "Nie można zapisać pliku $name."

fun Strings.cannotCreateFile(name: String, folder: String?): String = if (english) {
    "Kajet cannot create $name in the folder $folder."
} else {
    "Nie można utworzyć pliku $name w katalogu $folder."
}

fun Strings.diskRenamedFile(saved: String?, wanted: String): String = if (english) {
    "The disk saved the file as $saved instead of $wanted. Pick another folder for your notes."
} else {
    "Dysk zapisał plik pod nazwą $saved zamiast $wanted. Wybierz inny katalog na notatki."
}

fun Strings.cannotCreateFolder(name: String?): String =
    if (english) "Kajet cannot create the folder $name." else "Nie można utworzyć katalogu $name."

fun Strings.cannotReadFile(name: String?): String =
    if (english) "Kajet cannot read $name." else "Nie można odczytać pliku $name."

fun Strings.fileVanishedWhileCopying(name: String?): String = if (english) {
    "$name disappeared while it was being copied."
} else {
    "Plik $name zniknął w trakcie kopiowania."
}

fun Strings.folderGone(path: String): String = if (english) {
    "The folder $path is gone. Someone may have moved it outside Kajet."
} else {
    "Nie ma już folderu $path. Ktoś mógł go przenieść poza aplikacją."
}

fun Strings.folderCreateFailed(name: String): String = if (english) {
    "The folder $name could not be created. Check that there is space on the disk."
} else {
    "Nie udało się utworzyć folderu $name. Sprawdź, czy jest miejsce na dysku."
}

fun Strings.noteGone(path: String): String =
    if (english) "The note $path is gone." else "Nie ma już notatki $path."

fun Strings.noteGoneUnsaved(path: String): String = if (english) {
    "The note $path is gone. Your changes were not saved."
} else {
    "Nie ma już notatki $path. Zmiany nie zostały zapisane."
}

fun Strings.noteUnreadable(name: String?): String = if (english) {
    "The note $name cannot be read - its file is damaged."
} else {
    "Nie da się odczytać notatki $name - jej plik jest uszkodzony."
}

fun Strings.fileGone(path: String): String =
    if (english) "The file $path is gone." else "Nie ma już pliku $path."

fun Strings.fileGoneUnsaved(path: String): String = if (english) {
    "The file $path is gone. Your changes were not saved."
} else {
    "Nie ma już pliku $path. Zmiany nie zostały zapisane."
}

fun Strings.entryGone(path: String): String =
    if (english) "The entry $path is gone." else "Nie ma już wpisu $path."

fun Strings.renameFailed(name: String): String =
    if (english) "Renaming to $name did not work." else "Nie udało się zmienić nazwy na $name."

fun Strings.fileGoneFromBin(name: String): String = if (english) {
    "The file $name is no longer in the bin."
} else {
    "W koszu nie ma już pliku $name."
}

fun Strings.pythonInternalError(message: String?): String = if (english) {
    "Python stopped: $message"
} else {
    "Python się zatrzymał: $message"
}

fun Strings.spaceUsed(used: String, quota: String): String =
    if (english) "$used of $quota" else "$used z $quota"

fun Strings.spaceUsedNoLimit(used: String): String = "$used $takenNoLimit"

fun Strings.notesStuck(count: Int): String = if (english) {
    if (count == 1) "1 note could not be uploaded." else "$count notes could not be uploaded."
} else {
    val noun = when {
        count == 1 -> "notatki nie udało się"
        else -> "notatek nie udało się"
    }
    "$count $noun wysłać."
}

fun Strings.notesWaiting(count: Int): String = if (english) {
    "$count notes waiting to upload"
} else {
    val noun = when {
        count == 1 -> "notatka czeka"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "notatki czekają"
        else -> "notatek czeka"
    }
    "$count $noun na wysłanie"
}

fun Strings.offlineWaiting(count: Int): String = if (english) {
    "No internet. $count notes are waiting and will upload when the network is back."
} else {
    val noun = when {
        count == 1 -> "notatka czeka i pójdzie"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "notatki czekają i pójdą"
        else -> "notatek czeka i pójdzie"
    }
    "Brak internetu. $count $noun, gdy sieć wróci."
}

fun Strings.signedInAs(login: String): String =
    if (english) "Signed in as $login." else "Zalogowano jako $login."

fun Strings.syncSummary(sent: Int, fetched: Int): String = buildString {
    if (english) {
        if (sent > 0) append("$sent sent. ")
        if (fetched > 0) append("$fetched fetched. ")
    } else {
        if (sent > 0) append("Wysłano $sent. ")
        if (fetched > 0) append("Pobrano $fetched. ")
    }
}

/**
 * Odpowiedź serwera, której Kajet nie umówił sobie z nim wcześniej. Numer stanu
 * zostaje w dzienniku - na ekranie nie powiedziałby nikomu nic.
 */
@Suppress("UNUSED_PARAMETER")
fun Strings.serverError(status: Int): String = if (english) {
    "The server is not answering properly right now. Try again in a moment."
} else {
    "Serwer nie odpowiada tak, jak powinien. Spróbuj za chwilę."
}

fun Strings.noteSaveOnDeviceFailed(title: String): String = if (english) {
    "The note \u201c$title\u201d would not save on this device."
} else {
    "Nie udało się zapisać notatki \u201e$title\u201d na urządzeniu."
}

fun Strings.codeNoteUnknownShape(title: String): String = if (english) {
    "The code note \u201c$title\u201d is saved in a way Kajet does not know."
} else {
    "Notatka z kodem \u201e$title\u201d ma nieznany format."
}

fun Strings.nothingOpensFile(name: String): String = if (english) {
    "No app on this device can open $name. Use \u201cSend\u201d and pick a program yourself."
} else {
    "Żadna aplikacja na tym urządzeniu nie umie otworzyć pliku $name. " +
        "Użyj \u201eWyślij\u201d i wybierz program samodzielnie."
}

fun Strings.codeFileTooLargeAbout(sizeBytes: Long, limitBytes: Int): String {
    val sizeKb = (sizeBytes.coerceAtLeast(1L) + 1023L) / 1024L
    val limitKb = limitBytes / 1024
    return if (english) {
        "The file is about $sizeKb KB. Kajet edits files up to $limitKb KB, so this one " +
            "was not loaded into the editor. You can open it in another app instead."
    } else {
        "Plik ma około $sizeKb KB. Kajet edytuje pliki do $limitKb KB, dlatego ten nie " +
            "został wczytany do edytora. Możesz otworzyć go w innej aplikacji."
    }
}

/**
 * Ileś megabajtów zamiast siedmiu cyfr. Bajty są dokładne, ale žeby
 * zrozumieć „41 943 040 B", trzeba je najpierw policzyć na palcach.
 *
 * Zaokrąglenie idzie w górę do jednej dziesiątej: plik odrobinę większy od
 * limitu nie może się w napisie zrównać z limitem, bo wyszedłby z tego
 * komunikat o pliku, który się mieści, a jednak nie przeszedł.
 */
fun Strings.megabytes(bytes: Long): String {
    val tenths = (bytes.coerceAtLeast(0L) * 10 + MEGABYTE - 1) / MEGABYTE
    val whole = tenths / 10
    val rest = tenths % 10
    return if (rest == 0L) "$whole" else "$whole${if (english) "." else ","}$rest"
}

private const val MEGABYTE = 1_048_576L

fun Strings.uploadTooLargeAbout(sizeBytes: Long, limitBytes: Long): String = if (english) {
    "The file is about ${megabytes(sizeBytes)} MB. Kajet uploads files up to " +
        "${megabytes(limitBytes)} MB, so this one stayed on the device."
} else {
    "Plik ma około ${megabytes(sizeBytes)} MB. Kajet wgrywa pliki do " +
        "${megabytes(limitBytes)} MB, dlatego ten został na urządzeniu."
}

/**
 * Plik, którego Kajet nie wgra do chmury. Wybieraczka plików systemu pokazuje
 * wszystko, co jest na urządzeniu, więc zdjęcie albo film da się w niej
 * wskazać — lepiej powiedzieć to od razu, niż po nieudanym wysyłaniu.
 */
fun Strings.uploadNotTextAbout(name: String): String = if (english) {
    "“$name” is not a text or source-code file."
} else {
    "„$name” to nie plik tekstowy ani z kodem."
}

/**
 * Napis paska wgrywania.
 *
 * Odmowy z serwera bywają rozpisane na dwa pełne zdania i na telefonie taki
 * pasek zajmował pół ekranu. Znane powody mają więc tutaj własne, krótkie
 * zakończenie zdania, a nieznane skracają się do pierwszego zdania.
 */
fun Strings.uploadStatusText(
    name: String,
    status: String,
    progress: Int,
    error: String?,
    errorCode: String? = null,
): String = when (status) {
    "PENDING" -> if (english) "$name — waiting for a connection" else "$name — oczekuje na połączenie"
    "UPLOADING" -> if (english) "$name — uploading ($progress%)" else "$name — wysyłanie ($progress%)"
    "SYNCED" -> if (english) "$name — uploaded successfully" else "$name — wgrany pomyślnie"
    "FAILED_RETRYABLE" -> "$name — " + uploadProblem(error, errorCode, temporary = true)
    else -> "$name — " + uploadProblem(error, errorCode, temporary = false)
}

/** Krótki powód odmowy. Kody są te same, którymi odpowiada serwer. */
fun Strings.uploadProblem(error: String?, errorCode: String?, temporary: Boolean): String = when (errorCode) {
    "unsupported-extension", "mime-mismatch" ->
        if (english) "not a text or source-code file" else "to nie plik tekstowy ani z kodem"

    "not-utf8", "unreadable-file" ->
        if (english) "the contents are not plain text" else "treść nie jest zwykłym tekstem"

    "file-too-large" -> if (english) "the file is too big" else "plik jest za duży"

    "no-folder" ->
        if (english) "this folder is not in the cloud yet" else "tego folderu nie ma jeszcze w chmurze"

    else -> firstSentence(error) ?: if (temporary) {
        if (english) "sending did not work, Kajet will try again" else "wysyłanie się nie udało, Kajet spróbuje jeszcze raz"
    } else {
        if (english) "sending did not work" else "wysyłanie się nie udało"
    }
}

/**
 * Pierwsze zdanie komunikatu, najwyżej sto dwadzieścia znaków. Pasek ma
 * mieścić się w dwóch, trzech wierszach także wtedy, gdy serwer przyśle
 * powiadomienie, którego ta wersja aplikacji jeszcze nie zna.
 */
private fun firstSentence(text: String?): String? {
    val whole = text?.trim().orEmpty()
    if (whole.isEmpty()) return null
    val stop = whole.indexOf(". ")
    val one = if (stop > 0) whole.take(stop + 1) else whole
    return if (one.length <= 120) one else one.take(119).trimEnd() + "…"
}

fun Strings.pdfPage(current: Int, total: Int): String =
    if (english) "Page $current of $total" else "Strona $current z $total"

fun Strings.minutesAgo(minutes: Long): String =
    if (english) "$minutes min ago" else "$minutes min temu"

fun Strings.hoursAgo(hours: Long): String =
    if (english) "$hours h ago" else "$hours godz. temu"

/** Co stoi na urządzeniu - pod wyróżnionym numerem wersji do pobrania. */
fun Strings.updateOnThisDevice(installedVersion: String): String = if (english) {
    "This device has $installedVersion."
} else {
    "Na tym urządzeniu jest $installedVersion."
}

/**
 * Wiersz o pliku pod numerem wersji: system, wielkość i data wystawienia.
 * Puste człony wypadają, bo starszy serwer nie podaje daty.
 */
fun Strings.releaseFacts(size: String, date: String): String =
    listOf("Android", size, date).filter { it.isNotBlank() }.joinToString(" · ")

/** Odpowiedź na ręczne sprawdzenie w ustawieniach, gdy nowsza wersja jednak jest. */
fun Strings.newVersionFound(version: String): String = if (english) {
    "A newer version is available: $version."
} else {
    "Jest nowsza wersja: $version."
}

/** Jednostka przy suwakach wielkości pisma, grubości kreski i gumki. */
fun Strings.pointsOf(size: String): String = if (english) "$size pt" else "$size pkt"

/**
 * Procent na ekranie. Polska typografia stawia przed znakiem spację,
 * angielska nie - a oba napisy trafiają czasem na ten sam pasek.
 */
fun Strings.percentOf(value: Int): String = if (english) "$value%" else "$value %"

/** Miejsce po zdjęciu w pliku DOCX - samego obrazu ten zapis tu nie niesie. */
fun Strings.docxImageHere(caption: String): String = if (english) {
    "[A picture was here: $caption]"
} else {
    "[Tu było zdjęcie: $caption]"
}

/**
 * Nazwa drugiej wersji notatki, odłożonej obok po zmianie w dwóch miejscach
 * naraz. Widać ją na liście notatek, więc mówi po ludzku.
 */
fun Strings.cloudCopyOf(name: String, whenChanged: String): String = if (english) {
    "$name (copy from the cloud, $whenChanged)"
} else {
    "$name (kopia z chmury, $whenChanged)"
}

/** Pierwsza notatka, która się nie udała, gdy takich samych było więcej. */
fun Strings.andMoreNotes(first: String, count: Int): String = if (english) {
    "$first This affects $count notes."
} else {
    "$first Dotyczy to $count notatek."
}

fun Strings.aiHint(kind: AiNoteKind): String {
    val forNote = aiHint
    return when (kind) {
        AiNoteKind.TEXT -> forNote
        AiNoteKind.CODE -> if (english) "What should change in this file?" else "Co zmienić w tym pliku?"
        AiNoteKind.MINDMAP -> if (english) "What should change on this map?" else "Co zmienić na tej mapie?"
    }
}

fun Strings.aiWorking(kind: AiNoteKind): String {
    val forNote = aiWorking
    return when (kind) {
        AiNoteKind.TEXT -> forNote
        AiNoteKind.CODE -> if (english) "KajetAI is working on the file…" else "KajetAI pracuje nad plikiem…"
        AiNoteKind.MINDMAP -> if (english) "KajetAI is working on the map…" else "KajetAI pracuje nad mapą…"
    }
}

fun Strings.aiUndoFailed(kind: AiNoteKind): String {
    val forNote = aiUndoFailed
    return when (kind) {
        AiNoteKind.TEXT -> forNote
        AiNoteKind.CODE -> if (english) {
            "Could not undo it. The file stayed as KajetAI left it."
        } else {
            "Nie udało się cofnąć. Plik został taki, jak go zmienił KajetAI."
        }
        AiNoteKind.MINDMAP -> if (english) {
            "Could not undo it. The map stayed as KajetAI left it."
        } else {
            "Nie udało się cofnąć. Mapa została taka, jak ją zmienił KajetAI."
        }
    }
}

fun Strings.aiHistoryEmpty(kind: AiNoteKind): String {
    val forNote = aiHistoryEmpty
    return when (kind) {
        AiNoteKind.TEXT -> forNote
        AiNoteKind.CODE -> if (english) {
            "Nothing has been asked about this file yet."
        } else {
            "Przy tym pliku jeszcze o nic nie proszono."
        }
        AiNoteKind.MINDMAP -> if (english) {
            "Nothing has been asked about this map yet."
        } else {
            "Przy tej mapie jeszcze o nic nie proszono."
        }
    }
}

object PolishStrings : Strings {
    override val english = false
    override val appName = "Kajet"
    override val save = "Zapisz"
    override val cancel = "Anuluj"
    override val close = "Zamknij"
    override val back = "Wróć"
    override val delete = "Usuń"
    override val rename = "Zmień nazwę"
    override val copy = "Kopiuj"
    override val move = "Przenieś"
    override val create = "Utwórz"
    override val open = "Otwórz"
    override val send = "Wyślij"
    override val print = "Drukuj"
    override val settings = "Ustawienia"
    override val account = "Konto"
    override val search = "Szukaj"
    override val understood = "Rozumiem"
    override val untitled = "Bez tytułu"
    override val unnamed = "Bez nazwy"

    override val sectionLibrary = "Biblioteka"
    override val folders = "Foldery"
    override val sectionFavorites = "Ulubione"
    override val sectionRecent = "Ostatnio otwarte"
    override val sectionSearch = "Szukaj"
    override val sectionTrash = "Kosz"
    override val newFolder = "Nowy folder"
    override val newNote = "Nowa notatka"
    override val newCodeFile = "Nowy plik z kodem"
    override val uploadFile = "Wgraj plik"
    override val folderName = "Nazwa folderu"
    override val folderColour = "Kolor folderu"
    override val folderIcon = "Ikona folderu"
    override val emptyFolder = "Tu jeszcze nic nie ma"
    override val emptyFolderHint = "Zacznij od nowej notatki albo folderu."
    override val emptyTrash = "Opróżnij kosz"
    override val emptyingTrash = "Opróżniam kosz"
    override val restore = "Przywróć"
    override val deleteForever = "Skasuj na dobre"
    override val moveToTrash = "Wyrzuć do kosza"
    override val addToFavorites = "Dodaj do ulubionych"
    override val removeFromFavorites = "Usuń z ulubionych"
    override val rebuildIndex = "Odbuduj spis notatek"

    override val selectMany = "Zaznacz"
    override val selectAll = "Zaznacz wszystko"
    override val selectionDone = "Zakończ zaznaczanie"
    override val trashManyQuestion = "Wyrzucić zaznaczone do kosza?"

    override val noteText = "Tekstowa"
    override val noteHandwritten = "Odręczna"
    override val noteMindMap = "Mapa myśli"
    override val writeHere = "Zacznij pisać. Formatowanie widać od razu w treści."
    override val saved = "Zapisane"
    override val saving = "Zapisuję…"
    override val loading = "Wczytuję"
    override val changesWaiting = "Zmiany czekają"
    override val saveFailed = "Zapis się nie udał"
    override val noteInCloud = "Notatka jest w chmurze"
    override val noteWaitingForCloud = "Notatka czeka na wysłanie do chmury"
    override val noteOpenFailed = "Nie udało się otworzyć notatki."
    override val noteSaveFailed = "Nie udało się zapisać notatki."
    override val noteDeletedElsewhere = "Usunięta na innym urządzeniu"
    override val noteDeletedElsewhereAbout = "Ta notatka została usunięta na innym urządzeniu. " +
        "Twoja wersja jest wciąż tutaj - możesz zapisać ją jako nową notatkę " +
        "albo odrzucić zmiany."
    override val fileDeletedElsewhereAbout = "Ten plik został usunięty na innym urządzeniu. " +
        "Twoja wersja jest wciąż tutaj - możesz zapisać ją jako nowy plik " +
        "albo odrzucić zmiany."
    override val saveAsNewNote = "Zapisz jako nową"
    override val saveAsNewFile = "Zapisz jako nowy plik"
    override val discardChanges = "Odrzuć zmiany"
    override val saveAsNewFailed = "Nie udało się zapisać nowej kopii. Treść zostaje na ekranie - " +
        "spróbuj jeszcze raz."
    override val placeholderWord = "tekst"
    override val savingPhoto = "Zapisuję zdjęcie…"
    override val savingDrawing = "Zapisuję rysunek…"
    override val photoSaveFailed = "Nie udało się zapisać zdjęcia w notatce."
    override val drawingSaveFailed = "Nie udało się zapisać rysunku w notatce."
    override val exportTitle = "Zapisz notatkę do pliku"
    override val exportFormat = "Format"
    override val exportPdfAbout = "Kartka do druku i do wysłania. Pismo odręczne wychodzi jak na " +
        "ekranie; w tekście i na mapie myśli zostaje formatowanie i zdjęcia."
    override val exportSave = "Zapisz plik"
    override val shareLink = "Udostępnij odnośnikiem"
    override val shareLinkBusy = "Robię odnośnik…"

    override val sharePanelTitle = "Udostępnianie"
    override val shareWhatMayDo = "Co wolno z odnośnikiem"
    override val shareRightRead = "Tylko czytać"
    override val shareRightEdit = "Czytać i poprawiać"
    override val shareEmailLabel = "Adres e-mail odbiorcy - możesz zostawić puste"
    override val shareEmailAbout =
        "Puste pole daje zwykły odnośnik. Z adresem udostępnienie jest imienne: " +
        "otworzy je tylko osoba zalogowana tym adresem, a wiadomość wysyła serwer."
    override val shareValidDays = "Na ile dni"
    override val shareValidForever = "Zostaw zero, żeby odnośnik nie tracił ważności."
    override val shareAllowNoAccount = "Pozwól otworzyć bez zakładania konta"
    override val shareMake = "Udostępnij"
    override val shareMaking = "Udostępniam…"
    override val shareLinkReady = "Odnośnik gotowy"
    override val copyLink = "Skopiuj odnośnik"
    override val copiedWord = "Skopiowane"
    override val shareAlready = "Już udostępnione"
    override val shareNobodyYet = "Ta notatka nie jest jeszcze nikomu udostępniona."
    override val shareListLoading = "Sprawdzam udostępnienia…"
    override val shareLinkAnyone = "Odnośnik dla każdego"
    override val shareNeedsAccount = "tylko z kontem"
    override val shareByNameAbout = "Otworzy tylko osoba zalogowana tym adresem."
    override val shareNoDeadline = "Bezterminowo"
    override val shareExpiredMark = "wygasłe"
    override val shareNotOpenedYet = "Jeszcze nie otwarte"
    override val shareRevoke = "Odbierz dostęp"
    override val shareRevokeSure = "Na pewno?"
    override val shareMailNotSent =
        "Wiadomość nie wyszła z serwera. Skopiuj odnośnik i podaj go inną drogą."
    override val shareTryAgain = "Spróbuj jeszcze raz"
    override val shareEmailWrong = "Ten adres e-mail wygląda na niepełny."
    override val shareOfflineNow =
        "Nie ma połączenia z internetem. Udostępnieniami zarządza serwer, więc bez " +
        "połączenia nie da się ich obejrzeć ani zmienić."

    override val penInk = "Atrament"
    override val penBlack = "Czarny"
    override val penGrey = "Szary"
    override val penBlue = "Niebieski"
    override val penRed = "Czerwony"
    override val penGreen = "Zielony"
    override val penBrown = "Brązowy"
    override val penYellow = "Żółty"
    override val penPink = "Różowy"
    override val pickOwnColour = "Dobierz własny kolor"
    override val strokeWidth = "Grubość kreski"
    override val colourNamed = "Kolor"
    override val drawingInNote = "Rysunek w notatce"
    override val closeWithoutSaving = "Zamknij bez zapisywania"
    override val calculator = "Kalkulator"
    override val calculatorClose = "Zamknij kalkulator"
    override val calculatorCopyResult = "Kopiuj wynik"
    override val calculatorError = "Nie da się policzyć"
    override val calculatorBackspace = "Usuń ostatni znak"
    override val calculatorClear = "Wyczyść"
    override val calculatorParens = "Nawias"
    override val calculatorMove = "Przesuń kalkulator"
    override val insertDrawing = "Wstaw rysunek"
    override val clearDrawing = "Wyczyść"
    override val drawWithFingerOrStylus = "Rysuj palcem albo rysikiem"
    override val yourColours = "Twoje kolory"
    override val thickness = "Grubość"
    override val opacity = "Krycie"

    override val settingsLanguage = "Język"
    override val settingsLanguageAbout = "Kajet mówi w języku systemu: po polsku, gdy system " +
        "jest po polsku, i po angielsku w pozostałych przypadkach. Poniżej możesz to zmienić."
    override val languageSystem = "Taki jak w systemie"
    override val languagePolish = "Polski"
    // Nazwy języków stoją zawsze w swoim języku: kto szuka polskiego, widzi
    // „Polski", kto angielskiego - „English", niezależnie od bieżącej mowy.
    override val languageEnglish = "English"
    override val settingsToolbarSide = "Pasek narzędzi"
    override val settingsToolbarSideAbout = "Po której stronie ekranu stoi pasek z narzędziami " +
        "w edytorach. Leworęczni zwykle wolą prawą - dłoń nie zasłania wtedy przycisków " +
        "i nic nie klika się samo."
    override val toolbarLeft = "Po lewej"
    override val toolbarRight = "Po prawej"
    override val settingsAppearance = "Wygląd"
    override val themeSystem = "Taki jak w systemie"
    override val themeLight = "Jasny"
    override val themeDark = "Ciemny"
    override val settingsCode = "Pisanie kodu"
    override val settingsCodeAbout = "Edytor kodu może domykać za Ciebie nawiasy, cudzysłowy " +
        "i znaczniki HTML. Podpowiada, ale nie pisze za Ciebie: żadnych gotowych " +
        "pętli ani szkieletów."
    override val codeAssistOn = "Domykaj nawiasy i znaczniki"
    override val codeAssistOnAbout = "Po wpisaniu „(” dostajesz „()” z kursorem w środku, " +
        "a po „<p>” dopisuje się „</p>”. Enter przepisuje wcięcie."
    override val codeAssistOff = "Bez podpowiedzi"
    override val codeAssistOffAbout = "Edytor wpisuje dokładnie to, co naciśniesz."

    override val libRebuilding = "Odbudowuję spis notatek…"
    override val libFavoritesAbout = "Notatki i pliki oznaczone gwiazdką."
    override val libFavoritesEmpty = "Nie masz jeszcze nic w ulubionych. Naciśnij gwiazdkę przy " +
        "notatce albo pliku w spisie."
    override val libRecentAbout = "Dwadzieścia ostatnio otwieranych notatek."
    override val libRecentEmpty = "Tu pojawią się notatki, które otworzysz."
    override val libSearchInNotes = "Szukaj w notatkach"
    override val libAllNotes = "Wszystkie notatki"
    override val libFolderUp = "Folder wyżej"
    override val libFolderEmpty = "Ten folder jest pusty"
    override val libFolderEmptyHint = "Utwórz notatkę albo folder na przedmiot. Wszystko zapisze " +
        "się w katalogu wybranym na tym urządzeniu."
    override val libNothingHere = "Pusto"
    override val libSearchAbout = "Szukam w tytułach i w treści. Pismo odręczne znajdę wtedy, kiedy " +
        "zamienisz je na tekst."
    override val libSearchPrompt = "Wpisz, czego szukasz"
    override val libSearchPromptAbout = "Wystarczą dwie litery. Szukanie działa bez internetu, bo spis notatek " +
        "leży na urządzeniu."
    override val libSearchNothing = "Nic nie znalazłem"
    override val libExpand = "Rozwiń"
    override val libCollapse = "Zwiń"
    override val libKindFolder = "Folder"
    override val libKindCode = "Plik z kodem"
    override val emptyNote = "Pusta notatka"
    override val emptyPage = "Pusta strona"
    override val noDate = "Bez daty"
    override val justNow = "Przed chwilą"
    override val inFavorites = "W ulubionych"
    override val libSearchNothingAbout = "Sprawdź pisownię albo odbuduj spis notatek w ustawieniach, " +
        "jeśli notatki trafiły tu spoza Kajetu."
    override val emptyTrashQuestion = "Opróżnić kosz?"
    override val trashAbout = "Wyrzucone notatki leżą w ukrytym katalogu obok biblioteki. Nic nie ginie, " +
        "dopóki nie opróżnisz kosza."
    override val trashEmptyTitle = "Kosz jest pusty"
    override val trashEmptyAbout = "Wyrzucone notatki znajdziesz tutaj i przywrócisz je dokładnie " +
        "tam, skąd zniknęły."
    override val menuMoveToFolder = "Przenieś do innego folderu"
    override val menuCopy = "Zrób kopię"
    override val copySuffix = "(kopia)"
    override val deleteForeverQuestion = "Skasować na dobre?"
    override val deleteForeverWarning = "Tego się nie cofnie. Wpis zniknie z kosza, z dysku i z konta w chmurze."
    override val menuLook = "Zmień kolor i ikonę"
    override val exportFolderPdf = "Zapisz cały folder jako PDF"
    override val exportFolderMarkdown = "Zapisz cały folder jako Markdown"
    override val movePrompt = "Wybierz folder, do którego ma trafić ten wpis."
    override val codeShort = "Kod"

    override val colour = "Kolor"
    override val icon = "Ikona"
    override val createFolder = "Utwórz folder"
    override val createNote = "Utwórz notatkę"
    override val createFile = "Utwórz plik"
    override val titleLabel = "Tytuł"
    override val newName = "Nowa nazwa"
    override val fileName = "Nazwa pliku"
    override val defaultFileName = "program"
    override val noteKindLabel = "Rodzaj"
    override val kindHandwrittenAbout = "Piszesz rysikiem, możesz też wstawić pole z tekstem."
    override val kindTextAbout = "Piszesz z klawiatury, możesz wstawić zdjęcie i mały rysunek."
    override val kindMindMapAbout = "Węzły połączone liniami, podpisy z klawiatury albo rysikiem."
    override val pageLabel = "Strona"
    override val pageA4About = "Tak jak w zeszycie. Wydruk wychodzi bez przycinania."
    override val pageScrollAbout = "Strona rośnie w dół, kiedy piszesz przy dolnej krawędzi."
    override val pageBackgroundLabel = "Tło strony"
    override val langHtmlAbout = "Strona WWW. Obejrzysz ją w podglądzie, bez internetu."
    override val langNeedsAccountAbout = "Uruchamia się na serwerze, potrzebne konto i internet."

    override val codeOutput = "Wynik"
    override val codeErrors = "Błędy"
    override val codeInput = "Wejście"
    override val codeRun = "Uruchom program"
    override val codeStop = "Zatrzymaj"
    override val codePagePreview = "Podgląd strony"
    override val codeSearchInFile = "Szukaj w pliku"
    override val codeWordWrap = "Zawijanie wierszy"
    override val codeTypeTwoLetters = "Wpisz co najmniej dwie litery"
    override val codeUnsaved = "Zmiany czekają"
    override val codeHtmlHint = "HTML. Stronę obejrzysz pod ikoną globusa po lewej."
    override val codeWontRunHere = "Tego języka Kajet nie uruchomi."
    override val codeRunsOnTablet = "Uruchamia się na tablecie, bez internetu."
    override val codeRunsOnServer = "Uruchamia się na serwerze, potrzebny internet."
    override val codeWorkingOnTablet = "Uruchamiam na tablecie…"
    override val codeSendingToServer = "Wysyłam na serwer…"
    override val codeFromServer = "Serwer"
    override val codeFromTablet = "Tablet"
    override val codeExitCode = "kod wyjścia"
    override val codeInterrupted = "przerwane"
    override val codeOutputEmpty = "Naciśnij przycisk uruchomienia po lewej stronie. Tu pojawi się to, " +
        "co program wypisze."
    override val codeNoErrors = "Nie ma błędów."
    override val codeInputLabel = "Dane, które program przeczyta"
    override val codeOpenFailed = "Nie udało się otworzyć pliku."
    override val codeSaveFailed = "Nie udało się zapisać pliku."
    override val codeRunFailed = "Uruchomienie się nie udało."
    override val codeFileTooLargeTitle = "Plik jest za duży do edycji"

    override val codeConsole = "Konsola"
    override val codeConsoleClear = "Wyczyść"
    override val codeConsoleEmpty = "Tu staje to, co strona wypisze przez console.log, " +
        "razem z błędami skryptów."

    override val insertPhotoFromGallery = "Wstaw zdjęcie z galerii"
    override val takePhoto = "Zrób zdjęcie"
    override val exportNote = "Zapisz notatkę do pliku"
    override val rawMarkdownAbout = "Surowy zapis notatki. Wróć do widoku treści, żeby zobaczyć formatowanie."
    override val fontFamily = "Krój pisma"
    override val fontSize = "Wielkość pisma"
    override val fontSizeWholeNote = "Wielkość pisma całej notatki"
    override val wholeNoteLook = "Wygląd całej notatki"
    override val defaultSize = "Domyślna"
    override val defaultColour = "Domyślny kolor"
    override val tableAddRow = "Dodaj wiersz"
    override val tableAddColumn = "Dodaj kolumnę"
    override val tableRemoveRow = "Usuń wiersz z kursorem"
    override val tableRemoveColumn = "Usuń kolumnę z kursorem"
    override val tableRemove = "Usuń tabelę"
    override fun tableSize(rows: Int, columns: Int) = "Tabela $rows na $columns"
    override val smallerText = "Mniejsze pismo"
    override val largerText = "Większe pismo"
    override val colourWholeNote = "Kolor pisma całej notatki"
    override val colourSelection = "Kolor zaznaczonego fragmentu"
    override val highlightSelection = "Wyróżnij zaznaczony fragment"
    override val bold = "Pogrubienie"
    override val italic = "Kursywa"
    override val underline = "Podkreślenie"
    override val strike = "Przekreślenie"
    override val heading1 = "Nagłówek największy"
    override val heading2 = "Nagłówek średni"
    override val heading3 = "Nagłówek mały"
    override val bulletList = "Lista"
    override val numberedList = "Lista numerowana"
    override val taskList = "Lista zadań"
    override val quote = "Cytat"
    override val inlineCode = "Kod w tekście"
    override val codeBlock = "Blok kodu"
    override val table = "Tabela"
    override val formula = "Wzór matematyczny"
    override val link = "Odnośnik"
    override val dividerLine = "Linia oddzielająca"
    override val showRawMarkdown = "Pokaż surowy zapis Markdown"
    override val backToContentView = "Wróć do widoku treści"
    override val colourWholeNoteTitle = "Kolor pisma w całej notatce"
    override val colourSelectionTitle = "Kolor zaznaczonego fragmentu"
    override val tableColumn = "Kolumna"
    override val untickTask = "Zaznacz zadanie jako niezrobione"
    override val tickTask = "Zaznacz zadanie jako zrobione"
    override val photoInNote = "Zdjęcie w notatce"
    override val noCaption = "Bez podpisu"
    override val photoSize = "Wielkość zdjęcia"
    override val photoCaption = "Podpis zdjęcia"
    override val photoUp = "Przesuń zdjęcie wyżej"
    override val photoDown = "Przesuń zdjęcie niżej"
    override val photoRemove = "Usuń zdjęcie z notatki"
    override val photoCaptionAbout = "Podpis czyta czytnik ekranu i trafia do wydruku."
    override val photoBeside = "Obok poprzedniego"
    override val photoOwnLine = "Od nowego wiersza"
    override val photoChoose = "Wybierz zdjęcie"
    override val photoUnchoose = "Odznacz zdjęcie"
    override val photoMove = "Przeciągnij, żeby przesunąć zdjęcie"
    override val photoSmaller = "Zmniejsz zdjęcie"
    override val photoBigger = "Powiększ zdjęcie"

    override val penSettings = "Ustawienia pisaka"
    override val insertTextBox = "Wstaw pole tekstowe"
    override val fingerDrawsSwitch = "Palec rysuje. Dotknij, żeby palcem przewijać stronę."
    override val fingerScrollsSwitch = "Palec przewija stronę. Dotknij, żeby palcem rysować."
    override val noteSettingsIcon = "Ustawienia notatki: tło, strony i sposób pisania"
    override val undo = "Cofnij"
    override val redo = "Ponów"
    override val eraser = "Gumka"
    override val morePenSettings = "Więcej ustawień pisaka"
    override val deselect = "Odznacz"
    override val penTool = "Pisak"
    override val highlighterTool = "Zakreślacz"
    override val shapeKindLabel = "Jaki kształt"
    override val shapeOutlineColourTitle = "Kolor obrysu"
    override val shapeFillLabel = "Wypełnienie"
    override val shapeNoFill = "Bez wypełnienia"
    override val shapeFillColourTitle = "Kolor wypełnienia"
    override val shapeSquareLock = "Proporcje 1:1"
    override val shapeSquareAbout =
        "Przy włączonej blokadzie wychodzi koło i kwadrat, a linia trzyma się kąta co 45 stopni. " +
            "To samo daje Shift albo drugi palec na ekranie w trakcie rysowania."
    override val shapeRotate = "Obróć kształt"
    override val shapeResize = "Zmień rozmiar kształtu"
    override val shapeRemove = "Usuń kształt"
    override val eraserSize = "Wielkość gumki"
    override val widthLabel = "Szerokość"
    override val highlighterColourTitle = "Kolor zakreślacza"
    override val whatYouWriteWith = "Czym piszesz"
    override val inkColourTitle = "Kolor atramentu"
    override val penAboutPen = "Kreska grubieje tam, gdzie mocniej naciskasz rysikiem."
    override val penAboutFineliner = "Równa kreska o stałej szerokości."
    override val penAboutPencil = "Kreska z ziarnem, jak ołówek na papierze."
    override val penAboutDashed = "Linia przerywana, do podziałów i szkiców."
    override val textColourTitle = "Kolor pisma"
    override val addBoxBackground = "Dodaj tło pola"
    override val changeBoxBackground = "Zmień tło pola"
    override val boxBackgroundTitle = "Tło pola tekstowego"
    override val yourColour = "Twój kolor"
    override val noteSettings = "Ustawienia notatki"
    override val pageKind = "Rodzaj strony"
    override val pageA4Long = "Osobne kartki A4. Tak samo wyjdzie na drukarce."
    override val pageScrollLong = "Jedna strona, która rośnie w dół, kiedy dopiszesz przy dolnej krawędzi."
    override val pagesLabel = "Strony"
    override val onePage = "Notatka ma jedną stronę."
    override val addPage = "Dodaj"
    override val removeLastPage = "Usuń ostatnią"
    override val fingerLabel = "Palec"
    override val palmRejectionAbout = "Kiedy rysik dotyka ekranu, dłoń nie rysuje. Dzieje się tak " +
        "niezależnie od ustawienia obok."
    override val fitWidth = "Dopasuj szerokość"
    override val textBox = "Pole tekstowe"
    override val moveTextBox = "Przesuń pole tekstowe"
    override val deleteTextBox = "Usuń pole tekstowe"
    override val resizeTextBox = "Zmień wielkość pola"

    override val newNode = "Nowy węzeł"
    override val addBranch = "Dodaj gałąź do wybranego węzła"
    override val addSibling = "Dodaj węzeł obok wybranego"
    override val finishConnecting = "Skończ łączenie"
    override val connectToOthers = "Połącz ten węzeł z innymi"
    override val inkLabel = "Podpis rysikiem"
    override val arrangeBranches = "Rozłóż gałęzie automatycznie"
    override val fitWholeMap = "Zmieść całą mapę w oknie"
    override val exportMap = "Zapisz mapę do pliku"
    override val releaseToConnect = "Puść, żeby połączyć."
    override val releaseOnEmpty = "Puść na pustym miejscu, żeby dołożyć tam nowy węzeł."
    override val tapNodesToConnect = "Dotykaj kolejnych węzłów, żeby połączyć je z wybranym. " +
        "Skończysz tym samym przyciskiem."
    override val nodeWithoutName = "węzeł bez nazwy"
    override val disconnect = "Rozłącz"
    override val keepConnection = "Zostaw połączenie"
    override val zoomOut = "Oddal"
    override val zoomIn = "Przybliż"
    override val emptyMap = "Pusta mapa"
    override val emptyMapAbout = "Dodaj pierwszy węzeł przyciskiem po lewej stronie albo dotknij planszę " +
        "dwa razy, a potem doczepiaj gałęzie."
    override val tapTwiceToType = "Dotknij dwa razy, aby wpisać"
    override val dragToConnect = "Pociągnij, żeby połączyć z innym węzłem"
    override val dragToResize = "Pociągnij, żeby zmienić rozmiar węzła"
    override val expandBranch = "Rozwiń gałąź"
    override val collapseBranch = "Zwiń gałąź"
    override val selectedNode = "Wybrany węzeł"
    override val hideSettings = "Schowaj ustawienia"
    override val showMoreSettings = "Pokaż więcej ustawień"
    override val nodeText = "Tekst węzła"
    override val typeNodeText = "Wpisz treść węzła"
    override val textLabel = "Pismo"
    override val nodeColour = "Kolor węzła"
    override val connections = "Połączenia"
    override val connectionsAbout = "Możesz też dotknąć linii na planszy i rozłączyć ją tam."
    override val nodeTextColour = "Kolor pisma"
    override val colourFromTheme = "dobierany do motywu"
    override val colourOwn = "własny"
    override val shapeLabel = "Kształt"
    override val nodeColourTitle = "Kolor węzła"
    override val nodeTextColourTitle = "Kolor pisma w węźle"

    override val fileReady = "Plik jest gotowy"
    override val savingFile = "Zapisuję…"
    override val exportFailed = "Zapis się nie udał. Spróbuj innego formatu."
    override val openFile = "Otwórz"
    override val linkFailed = "Nie udało się zrobić odnośnika."
    override val exportNoHandwriting = "Pismo odręczne nie wejdzie do tego pliku."
    override val exportNoPhotos = "Zdjęcia nie wejdą do tego pliku."
    override val exportHandwrittenOnly = "Ten format zapisuje tylko notatki odręczne."
    override val exportTextOnly = "Zapisze się tylko tekst, bez pisma."
    override val hue = "Odcień"
    override val presetColours = "Gotowe"
    override val recentColours = "Ostatnio używane"
    override val done = "Gotowe"
    override val libraryFolderName = "Biblioteka"
    override val noNotesFolderPicked = "Nie wybrano katalogu na notatki."
    override val sendLink = "Wyślij odnośnik"
    override val sendFile = "Wyślij"
    override val shareWindowFailed = "Nie udało się otworzyć okna udostępniania. Skopiuj odnośnik ręcznie:"
    override val shareWindowFailedFile = "Nie udało się otworzyć okna udostępniania. Plik leży w pamięci " +
        "aplikacji."
    override val noSystemPrinting = "To urządzenie nie udostępnia systemowego drukowania."
    override val printFailed = "Nie udało się uruchomić drukowania. Spróbuj jeszcze raz " +
        "albo zapisz notatkę do pliku PDF."
    override val printOpenFailed = "Nie udało się otworzyć pliku do wydruku."
    override val printGaveUp = "Wydruk się nie udał."
    override val unknownError = "nieznany błąd"
    override val emptyNoteInExport = "Ta notatka jest jeszcze pusta."
    override val mapNodes = "Węzły mapy"
    override val handwrittenInDocx = "Do pliku DOCX wejdzie tylko tekst. Żeby zachować pismo " +
        "odręczne, wybierz PDF."
    override val pageWord = "Strona"
    override val noDescription = "bez opisu"
    override val handwrittenNoTextYet = "Ta notatka jest pisana odręcznie i nie ma w niej jeszcze tekstu. " +
        "Zaznacz pismo narzędziem zaznaczania i wybierz zamianę na tekst, a potem zapisz plik " +
        "jeszcze raz."

    override val cloudAccount = "Konto w chmurze"
    override val cloudAccountAbout = "Kajet działa bez konta. Notatki leżą wtedy tylko w katalogu " +
        "wybranym na tym urządzeniu. Konto przydaje się, żeby otworzyć je na komputerze " +
        "i odzyskać po zmianie telefonu albo tabletu."
    override val waitingForApproval = "Czekam, aż zatwierdzisz logowanie na stronie. Możesz wrócić do " +
        "aplikacji sam albo przyciskiem „Otwórz aplikację” po zatwierdzeniu."
    override val cancelWaiting = "Anuluj oczekiwanie"
    override val opening = "Otwieram…"
    override val signInWithGoogle = "Zaloguj przez Google"
    override val signInWithGoogleAbout = "Otworzy stronę logowania w aplikacji. Wybierzesz tam " +
        "swoje konto Google."
    override val orAddressAndPassword = "Albo adres i hasło"
    override val emailAddress = "Adres e-mail"
    override val password = "Hasło"
    override val signIn = "Zaloguj się"
    override val signingIn = "Loguję…"
    override val tokenFromBrowser = "Kod ze strony"
    override val tokenAbout = "Zamiast hasła możesz przepisać kod ze strony Kajetu. Na stronie konta " +
        "wybierz to urządzenie, a dostaniesz kod do wklejenia tutaj. Przydaje się wtedy, gdy " +
        "logowanie przez stronę nie zadziała."
    override val tokenFromSite = "Wklej kod"
    override val checking = "Sprawdzam…"
    override val connect = "Połącz"
    override val signedIn = "Zalogowany"
    override val spaceLabel = "Miejsce"
    override val takenNoLimit = "zajęte, bez limitu"
    override val syncSection = "Synchronizacja z chmurą"
    override val syncAbout = "Kajet wysyła zmiany z urządzenia i pobiera zmiany ze strony. " +
        "Uzgadnia też foldery oraz ostatnie położenie plików. Ręczna synchronizacja ponawia " +
        "również zadania, które wcześniej utknęły."
    override val syncing = "Synchronizuję…"
    override val syncNow = "Synchronizuj teraz"
    override val signOut = "Wyloguj się"
    override val signOutAbout = "Wylogowanie odcina chmurę, ale nie kasuje niczego z urządzenia. " +
        "Notatki zostają w wybranym katalogu."
    override val everythingSynced = "Wszystko zsynchronizowane"
    override val noInternet = "Brak internetu"
    override val sessionExpired = "Logowanie straciło ważność. Zaloguj się jeszcze raz."
    override val closeMessage = "Zamknij komunikat"
    override val closePanel = "Zamknij panel"
    override val deviceFallbackName = "Urządzenie"
    override val giveEmailAndPassword = "Podaj adres e-mail i hasło."
    override val serverUnreachable = "Nie udało się połączyć z serwerem."
    override val approveOnSite = "Zaloguj się na stronie (Google albo hasło) i zatwierdź to urządzenie. " +
        "Kajet czeka w tle."
    override val backFromBrowser = "Powrót z przeglądarki. Sprawdzam zatwierdzenie…"
    override val checkingApproval = "Sprawdzam zatwierdzenie logowania…"
    override val pasteTokenFromSite = "Wklej kod ze strony konta."
    override val sessionExpiredServer = "To logowanie już nie działa. Zaloguj się jeszcze raz."
    override val signedOutNotesStay = "Wylogowano. Notatki zostały na urządzeniu."
    override val syncFailedSafe = "Nie udało się zsynchronizować notatek. Są bezpieczne na urządzeniu."
    override val alreadyInSync = "Urządzenie i chmura są już zsynchronizowane."
    override val serverCopiesKept = "Obie wersje leżą obok siebie, żeby nic nie przepadło."
    override val incompleteSignInAnswer = "Logowanie się nie dokończyło. Spróbuj jeszcze raz."
    override val approvalTimedOut = "Czas na zatwierdzenie minął. Spróbuj jeszcze raz."
    override val notSignedIn = "Nie jesteś zalogowany."
    override val serverGibberish = "Coś poszło nie tak po drugiej stronie. " +
        "Spróbuj jeszcze raz za chwilę."
    override val offlineNoteQueued = "Nie ma połączenia z internetem. Notatka jest zapisana na urządzeniu " +
        "i wyślemy ją, gdy sieć wróci."
    override val cannotReachServer = "Nie udało się połączyć z serwerem. Sprawdź połączenie z internetem."
    override val serverTimedOut = "Serwer nie odpowiedział na czas. Kajet spróbuje jeszcze raz później."
    override val connectionDropped = "Połączenie z serwerem się urwało. Kajet spróbuje jeszcze raz później."
    override val fileDownloadFailed = "Nie udało się pobrać pliku. Kajet spróbuje później."
    override val serverRefused = "Serwer odmówił dostępu."
    override val notOnServer = "Nie ma tego na serwerze."
    override val noteChangedElsewhere = "Ta notatka zmieniła się także gdzie indziej."
    override val outOfSpace = "Brakuje miejsca na koncie."
    override val serverTrouble = "Serwer ma kłopot. Kajet spróbuje jeszcze raz później."
    override val noNotesDirToSend = "Nie ma katalogu z notatkami, więc nie można wykonać synchronizacji. " +
        "Otwórz ustawienia i wskaż folder na urządzeniu."
    override val noNotesDirToSave = "Nie wskazano katalogu na notatki, więc nie ma dokąd ich zapisać. " +
        "Otwórz ustawienia i wybierz folder na urządzeniu."
    override val runOnServerNeedsAccount = "Uruchamianie na serwerze wymaga konta. Zaloguj się " +
        "w ustawieniach, w sekcji „Konto w chmurze”."
    override val runOnServerNeedsInternet = "Nie ma internetu, a ten język uruchamia się przez sieć. " +
        "Kod jest zapisany - uruchomisz go, gdy sieć wróci."
    override val codeWord = "kod"

    override val cloudAccountOn = "Notatki trafiają też na serwer i otworzysz je na komputerze."
    override val cloudAccountOff = "Bez konta Kajet działa normalnie, a notatki leżą tylko na tym " +
        "urządzeniu. Z kontem trafiają też na serwer i otworzysz je na komputerze."
    override val notesFolder = "Katalog na notatki"
    override val notesFolderAbout = "Tu leżą wszystkie Twoje pliki. Po zmianie katalogu Kajet przeczyta " +
        "go od nowa."
    override val noFolderPicked = "Jeszcze nie wybrano folderu."
    override val changeFolder = "Zmień folder"
    override val rebuildIndexAbout = "Odbuduj spis wtedy, gdy notatki trafiły tu z komputera albo " +
        "gdy wyszukiwanie nie znajduje czegoś, co na pewno masz."
    override val indexRebuilt = "Spis notatek odbudowany."
    override val conflictCopyFailed = "Nie udało się odłożyć drugiej wersji notatki. Kajet spróbuje jeszcze raz."
    override val syncTrashMoveFailed = "Nie udało się przenieść do kosza. Kajet spróbuje jeszcze raz."
    override val syncAttachmentFailed = "Nie udało się przenieść załącznika. Kajet spróbuje jeszcze raz."
    override val retryStuckButton = "Spróbuj jeszcze raz"
    override val stuckAbout = "Te notatki zostały na urządzeniu. Zajrzyj do nich albo spróbuj wysłać jeszcze raz."
    override val notUploadedTag = "nie wysłano"
    override val notUploadedAbout = "Nie udało się wysłać do chmury."
    override val stuckNothingLost = "Nic nie ginie - zmiany czekają na urządzeniu."
    override val stylusAndFinger = "Rysik i palec"
    override val stylusAndFingerAbout = "Kiedy rysik dotyka ekranu, dłoń nie rysuje. Dzieje się tak " +
        "niezależnie od ustawienia poniżej."
    override val fingerScrollsAbout = "Palcem przesuwasz stronę, rysikiem piszesz. Tak jest najwygodniej."
    override val fingerDrawsAbout = "Palcem też rysujesz. Przydaje się, kiedy nie masz przy sobie rysika."
    override val appearanceAbout = "Wybierz, czy Kajet ma być jasny, czy ciemny."
    override val newHandwrittenNote = "Nowa notatka odręczna"
    override val newHandwrittenNoteAbout = "Te ustawienia podpowiadają się przy tworzeniu notatki. " +
        "Zawsze możesz je zmienić."
    override val autosaveSection = "Zapis automatyczny"
    override val autosaveAbout = "Notatka zapisuje się sama po każdej zmianie. Nie ma przycisku zapisz " +
        "i nie musisz o tym myśleć."
    override val deviceStorage = "Pamięć urządzenia"
    override val greetingWord = "Cześć"
    override val photoAltText = "zdjęcie"
    override val greetingVariable = "powitanie"
    override val myPageTitle = "Moja strona"
    override val emptyNoteFile = "Plik notatki jest pusty. Otwórz kopię z kosza albo utwórz notatkę na nowo."
    override val brokenContentFile = "Ten plik jest uszkodzony albo nie pochodzi z Kajetu."
    override val newerKajet = "Ta notatka pochodzi z nowszej wersji Kajetu. Zaktualizuj aplikację, żeby " +
        "ją otworzyć."
    override val brokenInlineDrawing = "Nie da się odczytać rysunku wstawionego w tekst."
    override val brokenFolderDescription = "Nie da się odczytać opisu folderu."
    override val noNotesFolderChosen = "Nie wybrano katalogu na notatki. Otwórz ustawienia i wskaż folder " +
        "na urządzeniu."
    override val binEmpty = "Kosz jest pusty."
    override val notInBinAnyMore = "Tego wpisu nie ma już w koszu."
    override val binEntryNoDescription = "Wpis w koszu nie ma opisu, więc nie wiadomo, gdzie go odłożyć."
    override val entryHasNoName = "Wpis nie ma nazwy."
    override val cannotMoveIntoItself = "Nie można przenieść folderu do jego własnego wnętrza."
    override val noParentFolder = "Nie ma już folderu nadrzędnego."
    override val handwritingLabel = "Pismo odręczne"

    override val firstRun = "Pierwsze uruchomienie"
    override val whereToKeepNotes = "Gdzie mam trzymać Twoje notatki?"
    override val whereToKeepNotesAbout = "Wskaż folder na urządzeniu. Kajet będzie w nim zapisywał " +
        "wszystko, co napiszesz."
    override val whereToKeepNotesWhy = "Dzięki temu notatki zostaną na urządzeniu nawet wtedy, gdy " +
        "odinstalujesz Kajet. Możesz je też skopiować na komputer albo otworzyć w innej " +
        "aplikacji. Najlepszym miejscem jest folder Dokumenty."
    override val pickNotesFolder = "Wskaż folder na notatki"
    override val pickNotesFolderAbout = "Otworzy się okno systemu Android. Wybierz folder i naciśnij " +
        "przycisk potwierdzenia. Możesz zmienić to później w ustawieniach."
    override val folderAccessLost = "Wskaż folder z notatkami jeszcze raz"
    override val folderAccessLostAbout = "Notatki leżą tam, gdzie były. Po reinstalacji albo kopii " +
        "zapasowej Android nie oddaje dostępu do folderu sam - trzeba go wskazać ponownie. " +
        "Plików to nie kasuje."
    override val couldNotKeepFolderAccess = "Android nie zapisał dostępu do folderu. Wskaż go jeszcze raz."

    override val starting = "Kajet się otwiera…"
    override val startingSlow = "Trwa to dłużej niż zwykle. Notatki leżą bezpiecznie na " +
        "urządzeniu. Jeśli nic się nie zmieni, zamknij Kajet i otwórz go jeszcze raz."
    override val errorScreenTitle = "Kajet się zatrzymał"
    override val errorScreenAbout = "Powodem jest błąd opisany niżej. Notatki są zapisane na dysku " +
        "i nic im nie grozi. Uruchom aplikację jeszcze raz przyciskiem poniżej."
    override val errorRestart = "Uruchom ponownie"
    override val errorCopyDetails = "Skopiuj szczegóły"
    override val errorSendDetails = "Wyślij opis błędu"
    override val errorSendTitle = "Zgłoszenie błędu Kajetu"
    override val errorNoDetails = "Brak zapisanych szczegółów błędu."
    override val screenBrokeTitle = "Ten widok się nie otworzył"
    override val screenBrokeAbout = "Kajet potknął się przy otwieraniu tego widoku. Reszta aplikacji " +
        "działa, a notatki są bezpieczne na dysku. Spróbuj otworzyć go jeszcze raz."
    override val screenBrokeReload = "Spróbuj jeszcze raz"
    override val noteNotOpened = "Notatka się nie otworzyła"
    override val noteNotOpenedAbout = "Nie udało się otworzyć notatki. Sprawdź, czy plik nadal jest " +
        "w folderze."
    override val noteOpening = "Otwieram notatkę"
    override val noEditorYet = "Kajet nie umie otworzyć tej notatki"
    override val noEditorYetAbout = "Notatka jest bezpieczna na dysku. Zaktualizuj Kajet - nowsza " +
        "wersja może już ją znać."
    override val cannotEditAsText = "Tego pliku nie da się edytować jako tekst"
    override val cannotEditAsTextAbout =
        "Kajet otwiera tu notatki i pliki z kodem. Ten plik zostaje nietknięty w bibliotece."
    override val fileDidNotOpen = "Plik się nie otworzył"
    override val fileDidNotOpenAbout =
        "Nie udało się odczytać tego pliku. Sprawdź, czy nadal jest w folderze."
    override val couldNotImportShare = "Nie udało się wziąć tej treści do Kajetu."
    override val openInOtherApp = "Otwórz w innej aplikacji"
    override val previousPage = "Poprzednia"
    override val nextPage = "Następna"
    override val backToLibrary = "Wróć do biblioteki"

    override val readingFolderFailed = "Nie udało się odczytać folderu."
    override val readingFavoritesFailed = "Nie udało się odczytać ulubionych."
    override val readingRecentFailed = "Nie udało się odczytać ostatnio otwartych."
    override val walkingLibrary = "Przeglądam bibliotekę…"
    override val actionFailed = "Nie udało się wykonać tej czynności."
    override val pythonOnTablet = "na tablecie"
    override val codeOnServer = "na serwerze Kajetu"
    override val pythonStartFailed = "Nie udało się uruchomić Pythona na tablecie. Zamknij aplikację " +
        "i otwórz ją jeszcze raz."

    override val legalSection = "Informacje prawne"
    override val legalSectionAbout = "Regulamin i polityka prywatności otwierają się na stronie " +
        "Kajetu, w przeglądarce."
    override val termsOfService = "Regulamin"
    override val privacyPolicy = "Polityka prywatności"
    override val documentNoNetwork = "Bez internetu nie da się otworzyć tego dokumentu. Połącz się " +
        "z siecią i spróbuj jeszcze raz."
    override val documentNoBrowser = "Na tym urządzeniu nie ma przeglądarki, która otworzyłaby tę " +
        "stronę."
    override val aboutSection = "O aplikacji"
    override val aboutSectionAbout = "Kajet - notatnik na pismo odręczne, tekst, mapy myśli i kod."
    override val appVersionWord = "Wersja"

    override val updateTitle = "Nowa wersja"
    override val updateToDownload = "Do pobrania"
    override val updateInstall = "Zainstaluj"
    override val updateLater = "Później"
    override val updateWhatChanged = "Co się zmieniło"
    override val checkUpdates = "Sprawdź aktualizacje"
    override val checkingUpdates = "Sprawdzam…"
    override val upToDate = "Zainstalowana wersja jest najnowsza."
    override val updateCheckFailed = "Nie udało się zapytać serwera. Sprawdź połączenie " +
        "z internetem i spróbuj jeszcze raz."

    // --- Asystent KajetAI ---
    override val aiTitle = "KajetAI"
    override val aiOpen = "Poproś KajetAI o zmianę"
    override val aiClose = "Zamknij KajetAI"
    override val aiHint = "Co zmienić w tej notatce?"
    override val aiAsk = "Poproś"
    override val aiWorking = "KajetAI pracuje nad notatką…"
    override val aiUndo = "Cofnij zmianę"
    override val aiUndone = "Zmiana cofnięta."
    override val aiUndoFailed = "Nie udało się cofnąć. Notatka została taka, jak ją zmienił KajetAI."
    override val aiQuestionLabel = "KajetAI pyta"
    override val aiHistoryTitle = "Wcześniejsze polecenia"
    override val aiHistoryEmpty = "Przy tej notatce jeszcze o nic nie proszono."
    override val aiForgetHistory = "Wyczyść rozmowę"
    override val aiNoteChangedElsewhere =
        "Treść zmieniła się w międzyczasie. Odśwież ją i poproś jeszcze raz."
    override val aiSaveFirst = "Treść nie doszła jeszcze do chmury, a KajetAI pracuje na tym, " +
        "co tam jest. Sprawdź internet i poproś jeszcze raz."
    override val aiOffline = "KajetAI potrzebuje internetu."
    override val aiConsentTitle = "Zanim poprosisz KajetAI"
    override val aiConsentWhatHappens =
        "Treść tej notatki - cały tekst, kod albo napisy w węzłach mapy - zostanie wysłana " +
            "do Google, bo to jego model wprowadza zmiany. Pismo odręczne i zdjęcia nie są wysyłane."
    override val aiConsentTraining =
        "Model jest darmowy, więc Google może wykorzystać wysłaną treść do uczenia swoich " +
            "modeli, a jego pracownik może ją przeczytać. Nie wysyłaj notatek, które mają " +
            "zostać prywatne."
    override val aiConsentVoluntary =
        "Zgoda jest dobrowolna i możesz ją wycofać w każdej chwili w ustawieniach konta. " +
            "Bez niej KajetAI nie działa, a cała reszta Kajetu działa tak samo jak dotąd."
    override val aiConsentReadPolicy = "Przeczytaj politykę prywatności"
    override val aiConsentAgree = "Zgadzam się"
    override val aiConsentNo = "Nie teraz"
    override val aiConsentSection = "Asystent KajetAI"
    override val aiConsentGiven = "Zgoda na wysyłanie treści notatek do Google jest udzielona."
    override val aiConsentMissing = "Bez zgody KajetAI nie działa. Poprosi o nią, gdy pierwszy raz " +
        "go użyjesz."
    override val aiConsentWithdraw = "Wycofaj zgodę"
    override val aiConsentWithdrawn = "Zgoda wycofana. Rozmowy z KajetAI zostały skasowane."
    override val aiConsentFailed =
        "Nie udało się zapisać zgody. Sprawdź internet i spróbuj jeszcze raz."
    override val aiPullFailed =
        "KajetAI zmienił treść w chmurze, ale nie udało się jej ściągnąć. " +
            "Sprawdź internet i poproś jeszcze raz."
}

object EnglishStrings : Strings {
    override val english = true
    override val appName = "Kajet"
    override val save = "Save"
    override val cancel = "Cancel"
    override val close = "Close"
    override val back = "Back"
    override val delete = "Delete"
    override val rename = "Rename"
    override val copy = "Copy"
    override val move = "Move"
    override val create = "Create"
    override val open = "Open"
    override val send = "Send"
    override val print = "Print"
    override val settings = "Settings"
    override val account = "Account"
    override val search = "Search"
    override val understood = "Got it"
    override val untitled = "Untitled"
    override val unnamed = "Unnamed"

    override val sectionLibrary = "Library"
    override val folders = "Folders"
    override val sectionFavorites = "Favourites"
    override val sectionRecent = "Recently opened"
    override val sectionSearch = "Search"
    override val sectionTrash = "Bin"
    override val newFolder = "New folder"
    override val newNote = "New note"
    override val newCodeFile = "New code file"
    override val uploadFile = "Upload file"
    override val folderName = "Folder name"
    override val folderColour = "Folder colour"
    override val folderIcon = "Folder icon"
    override val emptyFolder = "Nothing here yet"
    override val emptyFolderHint = "Start with a new note or folder."
    override val emptyTrash = "Empty the bin"
    override val emptyingTrash = "Emptying the bin"
    override val restore = "Restore"
    override val deleteForever = "Delete for good"
    override val moveToTrash = "Move to bin"
    override val addToFavorites = "Add to favourites"
    override val removeFromFavorites = "Remove from favourites"
    override val rebuildIndex = "Rebuild the list of notes"

    override val selectMany = "Select"
    override val selectAll = "Select all"
    override val selectionDone = "Stop selecting"
    override val trashManyQuestion = "Move the selected items to the bin?"

    override val noteText = "Text"
    override val noteHandwritten = "Handwritten"
    override val noteMindMap = "Mind map"
    override val writeHere = "Start writing. Formatting shows up straight away."
    override val saved = "Saved"
    override val saving = "Saving…"
    override val loading = "Loading"
    override val changesWaiting = "Changes waiting"
    override val saveFailed = "Saving did not work"
    override val noteInCloud = "The note is in the cloud"
    override val noteWaitingForCloud = "The note is waiting to go to the cloud"
    override val noteOpenFailed = "Kajet could not open the note."
    override val noteSaveFailed = "Kajet could not save the note."
    override val noteDeletedElsewhere = "Deleted on another device"
    override val noteDeletedElsewhereAbout = "This note has been deleted on another device. " +
        "Your version is still here - you can save it as a new note " +
        "or discard the changes."
    override val fileDeletedElsewhereAbout = "This file has been deleted on another device. " +
        "Your version is still here - you can save it as a new file " +
        "or discard the changes."
    override val saveAsNewNote = "Save as a new note"
    override val saveAsNewFile = "Save as a new file"
    override val discardChanges = "Discard the changes"
    override val saveAsNewFailed = "Kajet could not save the new copy. Your content stays on " +
        "screen - try again."
    override val placeholderWord = "text"
    override val savingPhoto = "Saving the photo…"
    override val savingDrawing = "Saving the drawing…"
    override val photoSaveFailed = "The photo would not save into the note."
    override val drawingSaveFailed = "The drawing would not save into the note."
    override val exportTitle = "Save the note to a file"
    override val exportFormat = "Format"
    override val exportPdfAbout = "A page for printing and sending. Handwriting matches the screen; " +
        "text notes and mind maps keep formatting and pictures."
    override val exportSave = "Save file"
    override val shareLink = "Share a link"
    override val shareLinkBusy = "Making a link…"

    override val sharePanelTitle = "Sharing"
    override val shareWhatMayDo = "What the link allows"
    override val shareRightRead = "Read only"
    override val shareRightEdit = "Read and edit"
    override val shareEmailLabel = "Recipient’s e-mail - you can leave it empty"
    override val shareEmailAbout =
        "Leave it empty for a plain link. With an address the share is personal: " +
        "only the person signed in with it can open the note, and the server sends the message."
    override val shareValidDays = "For how many days"
    override val shareValidForever = "Leave it at zero for a link that never expires."
    override val shareAllowNoAccount = "Allow opening without an account"
    override val shareMake = "Share"
    override val shareMaking = "Sharing…"
    override val shareLinkReady = "The link is ready"
    override val copyLink = "Copy the link"
    override val copiedWord = "Copied"
    override val shareAlready = "Already shared"
    override val shareNobodyYet = "This note has not been shared with anyone yet."
    override val shareListLoading = "Checking the shares…"
    override val shareLinkAnyone = "Link for anyone"
    override val shareNeedsAccount = "account required"
    override val shareByNameAbout = "Opens only for the person signed in with this address."
    override val shareNoDeadline = "No time limit"
    override val shareExpiredMark = "expired"
    override val shareNotOpenedYet = "Not opened yet"
    override val shareRevoke = "Revoke"
    override val shareRevokeSure = "Are you sure?"
    override val shareMailNotSent =
        "The message did not leave the server. Copy the link and hand it over another way."
    override val shareTryAgain = "Try again"
    override val shareEmailWrong = "This e-mail address looks incomplete."
    override val shareOfflineNow =
        "There is no internet connection. Shares live on the server, so without a " +
        "connection they cannot be seen or changed."

    override val penInk = "Ink"
    override val penBlack = "Black"
    override val penGrey = "Grey"
    override val penBlue = "Blue"
    override val penRed = "Red"
    override val penGreen = "Green"
    override val penBrown = "Brown"
    override val penYellow = "Yellow"
    override val penPink = "Pink"
    override val pickOwnColour = "Pick your own colour"
    override val strokeWidth = "Stroke width"
    override val colourNamed = "Colour"
    override val drawingInNote = "Drawing in the note"
    override val closeWithoutSaving = "Close without saving"
    override val calculator = "Calculator"
    override val calculatorClose = "Close calculator"
    override val calculatorCopyResult = "Copy result"
    override val calculatorError = "Can't calculate that"
    override val calculatorBackspace = "Delete last character"
    override val calculatorClear = "Clear"
    override val calculatorParens = "Bracket"
    override val calculatorMove = "Move calculator"
    override val insertDrawing = "Insert the drawing"
    override val clearDrawing = "Clear"
    override val drawWithFingerOrStylus = "Draw with a finger or a stylus"
    override val yourColours = "Your colours"
    override val thickness = "Thickness"
    override val opacity = "Opacity"

    override val settingsLanguage = "Language"
    override val settingsLanguageAbout = "Without a choice of your own, Kajet speaks the language " +
        "your system is set to: Polish when the system is Polish, English otherwise. " +
        "You can change that below."
    override val languageSystem = "Same as the system"
    // Language names always speak their own language - see the Polish strings.
    override val languagePolish = "Polski"
    override val languageEnglish = "English"
    override val settingsToolbarSide = "Toolbar"
    override val settingsToolbarSideAbout = "Which side of the screen the editor toolbar " +
        "sits on. Left-handed people usually prefer the right - the hand no longer covers " +
        "the buttons or taps them by accident."
    override val toolbarLeft = "On the left"
    override val toolbarRight = "On the right"
    override val settingsAppearance = "Appearance"
    override val themeSystem = "Same as the system"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val settingsCode = "Writing code"
    override val settingsCodeAbout = "The code editor can close brackets, quotes and HTML tags " +
        "for you. It helps, but it does not write for you: no ready-made loops, " +
        "no scaffolding."
    override val codeAssistOn = "Close brackets and tags"
    override val codeAssistOnAbout = "Typing “(” gives you “()” with the cursor inside, and “<p>” " +
        "adds “</p>”. Enter keeps the indent."
    override val codeAssistOff = "No help"
    override val codeAssistOffAbout = "The editor types exactly what you press."

    override val libRebuilding = "Rebuilding the list of notes…"
    override val libFavoritesAbout = "Notes and files you starred."
    override val libFavoritesEmpty = "Nothing in favourites yet. Press the star next to a note or " +
        "a file in the list."
    override val libRecentAbout = "The twenty notes you opened most recently."
    override val libRecentEmpty = "Notes you open will show up here."
    override val libSearchInNotes = "Search your notes"
    override val libAllNotes = "All notes"
    override val libFolderUp = "Folder above"
    override val libFolderEmpty = "This folder is empty"
    override val libFolderEmptyHint = "Create a note or a folder for a subject. Everything is saved in the " +
        "directory you picked on this device."
    override val libNothingHere = "Nothing here"
    override val libSearchAbout = "Kajet searches titles and content. Handwriting turns up once you " +
        "convert it to text."
    override val libSearchPrompt = "Type what you are looking for"
    override val libSearchPromptAbout = "Two letters are enough. Search works offline, because the list " +
        "of notes lives on this device."
    override val libSearchNothing = "Nothing found"
    override val libExpand = "Expand"
    override val libCollapse = "Collapse"
    override val libKindFolder = "Folder"
    override val libKindCode = "Code file"
    override val emptyNote = "Empty note"
    override val emptyPage = "Empty page"
    override val noDate = "No date"
    override val justNow = "Just now"
    override val inFavorites = "In favourites"
    override val libSearchNothingAbout = "Check the spelling, or rebuild the list of notes in settings " +
        "if you copied files in from outside Kajet."
    override val emptyTrashQuestion = "Empty the bin?"
    override val trashAbout = "Binned notes sit in a hidden folder next to your library. Nothing is lost " +
        "until you empty the bin."
    override val trashEmptyTitle = "The bin is empty"
    override val trashEmptyAbout = "Binned notes turn up here, and you can put them back exactly where they " +
        "came from."
    override val menuMoveToFolder = "Move to another folder"
    override val menuCopy = "Make a copy"
    override val copySuffix = "(copy)"
    override val deleteForeverQuestion = "Delete for good?"
    override val deleteForeverWarning = "There is no way back. It goes from the bin, from the disk and from the cloud account."
    override val menuLook = "Change the colour and icon"
    override val exportFolderPdf = "Save the whole folder as PDF"
    override val exportFolderMarkdown = "Save the whole folder as Markdown"
    override val movePrompt = "Pick the folder this should go into."
    override val codeShort = "Code"

    override val colour = "Colour"
    override val icon = "Icon"
    override val createFolder = "Create the folder"
    override val createNote = "Create the note"
    override val createFile = "Create the file"
    override val titleLabel = "Title"
    override val newName = "New name"
    override val fileName = "File name"
    override val defaultFileName = "program"
    override val noteKindLabel = "Kind"
    override val kindHandwrittenAbout = "You write with the stylus, and can drop in a text box too."
    override val kindTextAbout = "You type it, and can add a picture or a small drawing."
    override val kindMindMapAbout = "Nodes joined by lines, labelled by keyboard or by stylus."
    override val pageLabel = "Page"
    override val pageA4About = "Just like an exercise book. Prints without anything cut off."
    override val pageScrollAbout = "The page grows downwards as you write near the bottom."
    override val pageBackgroundLabel = "Page background"
    override val langHtmlAbout = "A web page. You see it in the preview, no internet needed."
    override val langNeedsAccountAbout = "Runs on the server, so it needs an account and internet."

    override val codeOutput = "Output"
    override val codeErrors = "Errors"
    override val codeInput = "Input"
    override val codeRun = "Run the program"
    override val codeStop = "Stop"
    override val codePagePreview = "Page preview"
    override val codeSearchInFile = "Search in this file"
    override val codeWordWrap = "Wrap long lines"
    override val codeTypeTwoLetters = "Type at least two letters"
    override val codeUnsaved = "Changes waiting"
    override val codeHtmlHint = "HTML. The globe on the left shows you the page."
    override val codeWontRunHere = "Kajet cannot run this language."
    override val codeRunsOnTablet = "Runs on the tablet, no internet needed."
    override val codeRunsOnServer = "Runs on the server, so it needs internet."
    override val codeWorkingOnTablet = "Running on the tablet…"
    override val codeSendingToServer = "Sending to the server…"
    override val codeFromServer = "Server"
    override val codeFromTablet = "Tablet"
    override val codeExitCode = "exit code"
    override val codeInterrupted = "interrupted"
    override val codeOutputEmpty = "Press the run button on the left. Whatever the program prints " +
        "shows up here."
    override val codeNoErrors = "No errors."
    override val codeInputLabel = "What the program will read"
    override val codeOpenFailed = "The file would not open."
    override val codeSaveFailed = "The file would not save."
    override val codeRunFailed = "Running it did not work."
    override val codeFileTooLargeTitle = "This file is too large to edit"

    override val codeConsole = "Console"
    override val codeConsoleClear = "Clear"
    override val codeConsoleEmpty = "Whatever the page prints with console.log stands here, " +
        "along with script errors."

    override val insertPhotoFromGallery = "Insert a photo from the gallery"
    override val takePhoto = "Take a photo"
    override val exportNote = "Save the note to a file"
    override val rawMarkdownAbout = "The raw note. Go back to the content view to see the formatting."
    override val fontFamily = "Typeface"
    override val fontSize = "Text size"
    override val fontSizeWholeNote = "Text size for the whole note"
    override val wholeNoteLook = "Look of the whole note"
    override val defaultSize = "Default"
    override val defaultColour = "Default colour"
    override val tableAddRow = "Add row"
    override val tableAddColumn = "Add column"
    override val tableRemoveRow = "Remove the row with the cursor"
    override val tableRemoveColumn = "Remove the column with the cursor"
    override val tableRemove = "Remove table"
    override fun tableSize(rows: Int, columns: Int) = "Table $rows by $columns"
    override val smallerText = "Smaller text"
    override val largerText = "Larger text"
    override val colourWholeNote = "Text colour for the whole note"
    override val colourSelection = "Colour of the selected piece"
    override val highlightSelection = "Highlight the selected piece"
    override val bold = "Bold"
    override val italic = "Italic"
    override val underline = "Underline"
    override val strike = "Strikethrough"
    override val heading1 = "Biggest heading"
    override val heading2 = "Medium heading"
    override val heading3 = "Small heading"
    override val bulletList = "List"
    override val numberedList = "Numbered list"
    override val taskList = "Tick list"
    override val quote = "Quote"
    override val inlineCode = "Code inside the text"
    override val codeBlock = "Code block"
    override val table = "Table"
    override val formula = "Maths formula"
    override val link = "Link"
    override val dividerLine = "Dividing line"
    override val showRawMarkdown = "Show the raw Markdown"
    override val backToContentView = "Back to the content view"
    override val colourWholeNoteTitle = "Text colour for the whole note"
    override val colourSelectionTitle = "Colour of the selected piece"
    override val tableColumn = "Column"
    override val untickTask = "Untick the task"
    override val tickTask = "Tick the task"
    override val photoInNote = "Photo in the note"
    override val noCaption = "No caption"
    override val photoSize = "Photo size"
    override val photoCaption = "Photo caption"
    override val photoUp = "Move the photo up"
    override val photoDown = "Move the photo down"
    override val photoRemove = "Remove the photo from the note"
    override val photoCaptionAbout = "The caption is read by screen readers and goes into the print-out."
    override val photoBeside = "Next to the one above"
    override val photoOwnLine = "On a new line"
    override val photoChoose = "Choose the photo"
    override val photoUnchoose = "Deselect the photo"
    override val photoMove = "Drag to move the photo"
    override val photoSmaller = "Make the photo smaller"
    override val photoBigger = "Make the photo bigger"

    override val penSettings = "Pen settings"
    override val insertTextBox = "Insert a text box"
    override val fingerDrawsSwitch = "Your finger draws. Tap to scroll with it instead."
    override val fingerScrollsSwitch = "Your finger scrolls. Tap to draw with it instead."
    override val noteSettingsIcon = "Note settings: background, pages and how you write"
    override val undo = "Undo"
    override val redo = "Redo"
    override val eraser = "Eraser"
    override val morePenSettings = "More pen settings"
    override val deselect = "Deselect"
    override val penTool = "Pen"
    override val highlighterTool = "Highlighter"
    override val shapeKindLabel = "Which shape"
    override val shapeOutlineColourTitle = "Outline colour"
    override val shapeFillLabel = "Fill"
    override val shapeNoFill = "No fill"
    override val shapeFillColourTitle = "Fill colour"
    override val shapeSquareLock = "Equal sides"
    override val shapeSquareAbout =
        "With the lock on you get a circle and a square, and a line keeps to 45 degree steps. " +
            "Shift, or a second finger on the screen while drawing, does the same."
    override val shapeRotate = "Rotate the shape"
    override val shapeResize = "Resize the shape"
    override val shapeRemove = "Remove the shape"
    override val eraserSize = "Eraser size"
    override val widthLabel = "Width"
    override val highlighterColourTitle = "Highlighter colour"
    override val whatYouWriteWith = "What you write with"
    override val inkColourTitle = "Ink colour"
    override val penAboutPen = "The stroke thickens where you press harder with the stylus."
    override val penAboutFineliner = "An even stroke of constant width."
    override val penAboutPencil = "A grainy stroke, like a pencil on paper."
    override val penAboutDashed = "A dashed line, for dividers and sketches."
    override val textColourTitle = "Text colour"
    override val addBoxBackground = "Add a background to the box"
    override val changeBoxBackground = "Change the box background"
    override val boxBackgroundTitle = "Text box background"
    override val yourColour = "Your colour"
    override val noteSettings = "Note settings"
    override val pageKind = "Page kind"
    override val pageA4Long = "Separate A4 sheets. Prints exactly the same way."
    override val pageScrollLong = "One page that grows downwards as you write near the bottom."
    override val pagesLabel = "Pages"
    override val onePage = "This note has one page."
    override val addPage = "Add one"
    override val removeLastPage = "Delete the last one"
    override val fingerLabel = "Finger"
    override val palmRejectionAbout = "While the stylus is on the screen, your palm never draws. That " +
        "always holds."
    override val fitWidth = "Fit to width"
    override val textBox = "Text box"
    override val moveTextBox = "Move the text box"
    override val deleteTextBox = "Delete the text box"
    override val resizeTextBox = "Resize the box"

    override val newNode = "New node"
    override val addBranch = "Add a branch to the selected node"
    override val addSibling = "Add a node next to the selected one"
    override val finishConnecting = "Finish connecting"
    override val connectToOthers = "Connect this node to others"
    override val inkLabel = "Label with the stylus"
    override val arrangeBranches = "Lay the branches out automatically"
    override val fitWholeMap = "Fit the whole map in the window"
    override val exportMap = "Save the map to a file"
    override val releaseToConnect = "Let go to connect."
    override val releaseOnEmpty = "Let go on empty space to put a new node there."
    override val tapNodesToConnect = "Tap the nodes you want joined to the selected one. The same " +
        "button finishes."
    override val nodeWithoutName = "unnamed node"
    override val disconnect = "Disconnect"
    override val keepConnection = "Keep the connection"
    override val zoomOut = "Zoom out"
    override val zoomIn = "Zoom in"
    override val emptyMap = "Empty map"
    override val emptyMapAbout = "Add the first node with the button on the left, or double-tap the " +
        "board, then hang branches off it."
    override val tapTwiceToType = "Double-tap to type"
    override val dragToConnect = "Drag to connect to another node"
    override val dragToResize = "Drag to resize the node"
    override val expandBranch = "Expand the branch"
    override val collapseBranch = "Collapse the branch"
    override val selectedNode = "Selected node"
    override val hideSettings = "Hide the settings"
    override val showMoreSettings = "Show more settings"
    override val nodeText = "Node text"
    override val typeNodeText = "Type what goes in the node"
    override val textLabel = "Text"
    override val nodeColour = "Node colour"
    override val connections = "Connections"
    override val connectionsAbout = "You can also tap a line on the board and disconnect it there."
    override val nodeTextColour = "Text colour"
    override val colourFromTheme = "follows the theme"
    override val colourOwn = "your own"
    override val shapeLabel = "Shape"
    override val nodeColourTitle = "Node colour"
    override val nodeTextColourTitle = "Text colour in the node"

    override val fileReady = "The file is ready"
    override val savingFile = "Saving…"
    override val exportFailed = "Saving did not work. Try another format."
    override val openFile = "Open"
    override val linkFailed = "Kajet could not make a link."
    override val exportNoHandwriting = "Handwriting will not go into this file."
    override val exportNoPhotos = "Photos will not go into this file."
    override val exportHandwrittenOnly = "This format only saves handwritten notes."
    override val exportTextOnly = "Only the text will be saved, not the handwriting."
    override val hue = "Hue"
    override val presetColours = "Ready-made"
    override val recentColours = "Recently used"
    override val done = "Done"
    override val libraryFolderName = "Library"
    override val noNotesFolderPicked = "No folder has been picked for your notes."
    override val sendLink = "Send the link"
    override val sendFile = "Send"
    override val shareWindowFailed = "The sharing window would not open. Copy the link by hand:"
    override val shareWindowFailedFile = "The sharing window would not open. The file is in Kajet’s own " +
        "storage."
    override val noSystemPrinting = "This device does not offer system printing."
    override val printFailed = "Printing would not start. Try again, or save the note to a PDF file."
    override val printOpenFailed = "The file for printing would not open."
    override val printGaveUp = "Printing did not work."
    override val unknownError = "unknown error"
    override val emptyNoteInExport = "This note is still empty."
    override val mapNodes = "Map nodes"
    override val handwrittenInDocx = "Only the text goes into a DOCX file. To keep the handwriting, " +
        "choose PDF."
    override val pageWord = "Page"
    override val noDescription = "no description"
    override val handwrittenNoTextYet = "This note is handwritten and has no text in it yet. Select the " +
        "handwriting with the select tool, turn it into text, then save the file again."

    override val cloudAccount = "Cloud account"
    override val cloudAccountAbout = "Kajet works without an account. Your notes then live only on this " +
        "device, in the folder you picked. An account is for opening them on a computer and " +
        "getting them back after you change phone or tablet."
    override val waitingForApproval = "Waiting for you to approve the sign-in on the website. Come back " +
        "yourself, or use the “Open the app” button once you have approved it."
    override val cancelWaiting = "Stop waiting"
    override val opening = "Opening…"
    override val signInWithGoogle = "Sign in with Google"
    override val signInWithGoogleAbout = "Opens the sign-in page inside the app, where you pick " +
        "your Google account."
    override val orAddressAndPassword = "Or an address and password"
    override val emailAddress = "E-mail address"
    override val password = "Password"
    override val signIn = "Sign in"
    override val signingIn = "Signing in…"
    override val tokenFromBrowser = "Code from the website"
    override val tokenAbout = "Instead of a password you can copy a code from the Kajet website. On " +
        "your account page pick this device and you get a code to paste here. It is the way " +
        "in when signing in through the website does not work."
    override val tokenFromSite = "Paste the code"
    override val checking = "Checking…"
    override val connect = "Connect"
    override val signedIn = "Signed in"
    override val spaceLabel = "Space"
    override val takenNoLimit = "used, no limit"
    override val syncSection = "Cloud synchronisation"
    override val syncAbout = "Kajet sends changes from this device and fetches changes from the website. " +
        "It also reconciles folders and the latest file locations. A manual sync retries work " +
        "that got stuck earlier."
    override val syncing = "Synchronising…"
    override val syncNow = "Synchronise now"
    override val signOut = "Sign out"
    override val signOutAbout = "Signing out cuts off the cloud but deletes nothing from the device. " +
        "Your notes stay in the folder you picked."
    override val everythingSynced = "Everything is synchronised"
    override val noInternet = "No internet"
    override val sessionExpired = "Your sign-in is no longer valid. Sign in again."
    override val closeMessage = "Close this message"
    override val closePanel = "Close the panel"
    override val deviceFallbackName = "Device"
    override val giveEmailAndPassword = "Enter an e-mail address and a password."
    override val serverUnreachable = "Kajet could not reach the server."
    override val approveOnSite = "Sign in on the website (Google or password) and approve this device. " +
        "Kajet is waiting in the background."
    override val backFromBrowser = "Back from the browser. Checking the approval…"
    override val checkingApproval = "Checking the sign-in approval…"
    override val pasteTokenFromSite = "Paste the code from your account page."
    override val sessionExpiredServer = "This sign-in no longer works. Sign in again."
    override val signedOutNotesStay = "Signed out. Your notes stayed on the device."
    override val syncFailedSafe = "Synchronisation did not work. Your notes are safe on the device."
    override val alreadyInSync = "This device and the cloud are already synchronised."
    override val serverCopiesKept = "Both versions are saved side by side, so nothing is lost."
    override val incompleteSignInAnswer = "The sign-in did not finish. Try again."
    override val approvalTimedOut = "The time to approve ran out. Try again."
    override val notSignedIn = "You are not signed in."
    override val serverGibberish = "Something went wrong at the other end. Try again in a moment."
    override val offlineNoteQueued = "There is no internet. The note is saved on the device and will go " +
        "up when the network is back."
    override val cannotReachServer = "Kajet cannot reach the server. Check your internet connection."
    override val serverTimedOut = "The server did not answer in time. Kajet will try again later."
    override val connectionDropped = "The connection to the server dropped. Kajet will try again later."
    override val fileDownloadFailed = "The file would not download. Kajet will try later."
    override val serverRefused = "The server refused access."
    override val notOnServer = "That is not on the server."
    override val noteChangedElsewhere = "This note has also changed somewhere else."
    override val outOfSpace = "Your account is out of space."
    override val serverTrouble = "The server is having trouble. Kajet will try again later."
    override val noNotesDirToSend = "Kajet cannot see the notes folder, so it cannot synchronise. " +
        "Open settings and point it at a folder on this device."
    override val noNotesDirToSave = "No notes folder has been picked, so there is nowhere to save them. " +
        "Open settings and choose a folder on this device."
    override val runOnServerNeedsAccount = "Running on the server needs an account. Sign in under " +
        "settings, in the “Cloud account” section."
    override val runOnServerNeedsInternet = "There is no internet, and this language runs over the " +
        "network. Your code is saved - you can run it once the network is back."
    override val codeWord = "code"

    override val cloudAccountOn = "Your notes also go to the server, so you can open them on a computer."
    override val cloudAccountOff = "Without an account Kajet works fine and your notes live only on this " +
        "device. With one they also go to the server, so you can open them on a computer."
    override val notesFolder = "Notes folder"
    override val notesFolderAbout = "All your files live here. After you change the folder, Kajet reads " +
        "it from scratch."
    override val noFolderPicked = "No folder picked yet."
    override val changeFolder = "Change the folder"
    override val rebuildIndexAbout = "Rebuild the list when you have copied notes in from a computer, " +
        "or when search cannot find something you know is there."
    override val indexRebuilt = "The list of notes has been rebuilt."
    override val conflictCopyFailed = "Kajet could not put the other version of the note aside. " +
        "It will try again."
    override val syncTrashMoveFailed = "Could not move it to the bin. Kajet will try again."
    override val syncAttachmentFailed = "Could not carry an attachment over. Kajet will try again."
    override val retryStuckButton = "Try again"
    override val stuckAbout = "These notes stayed on the device. Have a look at them or try uploading again."
    override val notUploadedTag = "not uploaded"
    override val notUploadedAbout = "Could not be uploaded to the cloud."
    override val stuckNothingLost = "Nothing is lost - the changes wait on the device."
    override val stylusAndFinger = "Stylus and finger"
    override val stylusAndFingerAbout = "While the stylus is on the screen, your palm never draws. That " +
        "always holds."
    override val fingerScrollsAbout = "Your finger moves the page, the stylus writes. That is the " +
        "comfortable way round."
    override val fingerDrawsAbout = "Your finger draws too. Handy when you have no stylus with you."
    override val appearanceAbout = "Choose whether Kajet is light or dark."
    override val newHandwrittenNote = "New handwritten note"
    override val newHandwrittenNoteAbout = "These settings are suggested when you create a note. You can " +
        "always change them."
    override val autosaveSection = "Autosave"
    override val autosaveAbout = "The note saves itself after every change. There is no save button and " +
        "nothing to remember."
    override val deviceStorage = "Device storage"
    override val greetingWord = "Hello"
    override val photoAltText = "photo"
    override val greetingVariable = "greeting"
    override val myPageTitle = "My page"
    override val emptyNoteFile = "The note file is empty. Open a copy from the bin, or create the note again."
    override val brokenContentFile = "This file is damaged, or it did not come from Kajet."
    override val newerKajet = "This note comes from a newer version of Kajet. Update the app to open it."
    override val brokenInlineDrawing = "Kajet cannot read the drawing placed inside the text."
    override val brokenFolderDescription = "Kajet cannot read the folder description."
    override val noNotesFolderChosen = "No notes folder has been picked. Open settings and point Kajet at " +
        "a folder on this device."
    override val binEmpty = "The bin is empty."
    override val notInBinAnyMore = "That entry is no longer in the bin."
    override val binEntryNoDescription = "The bin entry has no description, so there is no telling where " +
        "to put it back."
    override val entryHasNoName = "The entry has no name."
    override val cannotMoveIntoItself = "A folder cannot be moved inside itself."
    override val noParentFolder = "The parent folder is gone."
    override val handwritingLabel = "Handwriting"

    override val firstRun = "First run"
    override val whereToKeepNotes = "Where should Kajet keep your notes?"
    override val whereToKeepNotesAbout = "Pick a folder on this device. Kajet will save everything you " +
        "write there."
    override val whereToKeepNotesWhy = "That way your notes stay on the device even if you uninstall " +
        "Kajet. You can copy them to a computer or open them in another app. Documents is " +
        "usually the best place."
    override val pickNotesFolder = "Pick a folder for your notes"
    override val pickNotesFolderAbout = "An Android window will open. Choose a folder and press the " +
        "confirm button. You can change this later in settings."
    override val folderAccessLost = "Pick the notes folder again"
    override val folderAccessLostAbout = "Your notes are still where they were. After a reinstall or " +
        "a backup restore, Android does not give folder access back on its own - you have to " +
        "point at the folder again. That does not delete any files."
    override val couldNotKeepFolderAccess = "Android did not keep access to the folder. Pick it again."

    override val starting = "Kajet is opening…"
    override val startingSlow = "This is taking longer than usual. Your notes are safe on the " +
        "device. If nothing changes, close Kajet and open it again."
    override val errorScreenTitle = "Kajet stopped"
    override val errorScreenAbout = "The error below is why. Your notes are saved on disk and are " +
        "safe. Use the button to start the app again."
    override val errorRestart = "Restart"
    override val errorCopyDetails = "Copy details"
    override val errorSendDetails = "Send the error report"
    override val errorSendTitle = "Kajet error report"
    override val errorNoDetails = "No saved error details."
    override val screenBrokeTitle = "This view did not open"
    override val screenBrokeAbout = "Kajet tripped up while opening this view. The rest of the app " +
        "works and your notes are safe on disk. Try opening it again."
    override val screenBrokeReload = "Try again"
    override val noteNotOpened = "The note did not open"
    override val noteNotOpenedAbout = "Kajet could not open the note. Check that the file is still in " +
        "its folder."
    override val noteOpening = "Opening the note"
    override val noEditorYet = "Kajet cannot open this note"
    override val noEditorYetAbout = "The note is safe on disk. Update Kajet - a newer version may " +
        "know it."
    override val cannotEditAsText = "This file cannot be edited as text"
    override val cannotEditAsTextAbout =
        "Kajet opens notes and code files here. This file stays untouched in the library."
    override val fileDidNotOpen = "The file did not open"
    override val fileDidNotOpenAbout =
        "Kajet could not read this file. Check that it is still in its folder."
    override val couldNotImportShare = "Kajet could not take this content into the library."
    override val openInOtherApp = "Open in another app"
    override val previousPage = "Previous"
    override val nextPage = "Next"
    override val backToLibrary = "Back to the library"

    override val readingFolderFailed = "Kajet could not read the folder."
    override val readingFavoritesFailed = "Kajet could not read your favourites."
    override val readingRecentFailed = "Kajet could not read the recently opened notes."
    override val walkingLibrary = "Going through the library…"
    override val actionFailed = "That did not work out."
    override val pythonOnTablet = "on the tablet"
    override val codeOnServer = "on the Kajet server"
    override val pythonStartFailed = "Python would not start on the tablet. Close Kajet and open it " +
        "again."

    override val legalSection = "Legal"
    override val legalSectionAbout = "The terms of service and the privacy policy open on the Kajet " +
        "website, in your browser."
    override val termsOfService = "Terms of service"
    override val privacyPolicy = "Privacy policy"
    override val documentNoNetwork = "Without an internet connection Kajet cannot open this " +
        "document. Connect to a network and try again."
    override val documentNoBrowser = "There is no browser on this device that could open the page."
    override val aboutSection = "About"
    override val aboutSectionAbout = "Kajet - a notebook for handwriting, text, mind maps and code."
    override val appVersionWord = "Version"

    override val updateTitle = "New version"
    override val updateToDownload = "To download"
    override val updateInstall = "Install"
    override val updateLater = "Later"
    override val updateWhatChanged = "What changed"
    override val checkUpdates = "Check for updates"
    override val checkingUpdates = "Checking…"
    override val upToDate = "The installed version is the newest one."
    override val updateCheckFailed = "The server could not be reached. Check the internet " +
        "connection and try again."

    // --- Asystent KajetAI ---
    override val aiTitle = "KajetAI"
    override val aiOpen = "Ask KajetAI for a change"
    override val aiClose = "Close KajetAI"
    override val aiHint = "What should change in this note?"
    override val aiAsk = "Ask"
    override val aiWorking = "KajetAI is working on the note…"
    override val aiUndo = "Undo the change"
    override val aiUndone = "Change undone."
    override val aiUndoFailed = "Could not undo it. The note stayed as KajetAI left it."
    override val aiQuestionLabel = "KajetAI asks"
    override val aiHistoryTitle = "Earlier instructions"
    override val aiHistoryEmpty = "Nothing has been asked about this note yet."
    override val aiForgetHistory = "Clear the conversation"
    override val aiNoteChangedElsewhere =
        "The content changed in the meantime. Refresh it and ask again."
    override val aiSaveFirst = "The content has not reached the cloud yet, and KajetAI works on " +
        "what is there. Check your connection and ask again."
    override val aiOffline = "KajetAI needs an internet connection."
    override val aiConsentTitle = "Before you ask KajetAI"
    override val aiConsentWhatHappens =
        "The content of this note - all the text, the code, or the labels in the map nodes - " +
            "will be sent to Google, because it is their model that makes the change. " +
            "Handwriting and photographs are not sent."
    override val aiConsentTraining =
        "The model is free, so Google may use what is sent to train its models, and a Google " +
            "employee may read it. Do not send notes that are meant to stay private."
    override val aiConsentVoluntary =
        "The consent is voluntary and you can withdraw it at any time in account settings. " +
            "Without it KajetAI does not work, and the rest of Kajet works exactly as before."
    override val aiConsentReadPolicy = "Read the privacy policy"
    override val aiConsentAgree = "I agree"
    override val aiConsentNo = "Not now"
    override val aiConsentSection = "KajetAI assistant"
    override val aiConsentGiven = "Consent to sending note content to Google has been given."
    override val aiConsentMissing = "Without consent KajetAI does not work. It will ask the first " +
        "time you use it."
    override val aiConsentWithdraw = "Withdraw consent"
    override val aiConsentWithdrawn = "Consent withdrawn. Your conversations with KajetAI have been deleted."
    override val aiConsentFailed =
        "Consent could not be saved. Check your connection and try again."
    override val aiPullFailed =
        "KajetAI changed the content in the cloud, but it could not be downloaded. " +
            "Check your connection and ask again."
}
