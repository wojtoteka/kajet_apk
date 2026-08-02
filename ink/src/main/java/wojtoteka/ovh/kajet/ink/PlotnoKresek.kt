package wojtoteka.ovh.kajet.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.PageBackground
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Jedna strona gotowa do narysowania. Współrzędne kresek liczone od lewego górnego rogu strony. */
class StronaNaEkranie(
    val indeks: Int,
    val szerokosc: Float,
    val wysokosc: Float,
    val tlo: PageBackground,
    var kreski: List<InkStroke>,
)

/** Co zaszło na płótnie. Edytor zamienia to na zmianę w dokumencie. */
interface SluchaczPlotna {
    fun kreskaSkonczona(strona: Int, kreska: InkStroke)
    fun gumkaPrzeszla(strona: Int, x: Float, y: Float, promien: Float, calaKreska: Boolean)
    fun lassoSkonczone(strona: Int, wielokat: List<Float>)
    fun zaznaczeniePrzesuniete(dx: Float, dy: Float, koniec: Boolean)
    fun widokZmieniony(przesuniecieX: Float, przesuniecieY: Float, powiekszenie: Float)
    fun dotknietoPustego()
}

/**
 * Płótno notatki odręcznej.
 *
 * Kreska, którą właśnie piszesz, idzie przez InProgressStrokesView, czyli przez
 * silnik androidx.ink. Na Androidzie 10 i nowszym rysuje on po froncie bufora,
 * z pominięciem zwykłej kolejki rysowania, i to jest jedyny sposób,
 * żeby kreska nadążała za rysikiem.
 *
 * Kreski już zapisane rysujemy sami, w osobnej warstwie pod spodem.
 * Dzięki temu pisanie nie przerysowuje całej strony.
 */
@SuppressLint("ViewConstructor")
class PlotnoKresek(context: Context) : FrameLayout(context), InProgressStrokesFinishedListener {

    private val widokKreski = InProgressStrokesView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        it.eagerInit()
        it.addFinishedStrokesListener(this)
    }

    private val rysownik: CanvasStrokeRenderer = CanvasStrokeRenderer.create()

    var sluchacz: SluchaczPlotna? = null

    var strony: List<StronaNaEkranie> = emptyList()
        set(wartosc) {
            field = wartosc
            przeliczKreski()
            invalidate()
        }

    var narzedzie: Narzedzie = Narzedzie.PIORO
    var ustawienia: UstawieniaPisaka = UstawieniaPisaka(
        kolorPiora = Color.BLACK,
        kolorZakreslacza = Color.YELLOW,
    )

    /** Kiedy fałsz, palec przewija stronę zamiast rysować. */
    var palecRysuje: Boolean = false

    var kolorPapieru: Int = Color.WHITE
    var kolorLinii: Int = Color.LTGRAY
    var kolorBiurka: Int = Color.GRAY
    var kolorZaznaczenia: Int = Color.BLUE

    /** Kreski zaznaczone lassem, w układzie strony. */
    var zaznaczone: List<InkStroke> = emptyList()
        set(wartosc) {
            field = wartosc
            invalidate()
        }
    var stronaZaznaczenia: Int = -1

    var przesuniecieX: Float = 0f
        private set
    var przesuniecieY: Float = 0f
        private set
    var powiekszenie: Float = 1f
        private set

    private val kreskiSilnika = HashMap<Int, MutableList<Stroke>>()

    /** Policzone siatki kresek, po identyfikatorze kreski z pliku. */
    private val podreczne = HashMap<String, Stroke>()

    private val pedzelPapieru = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pedzelLinii = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pedzelZaznaczenia = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val pedzelGumki = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val dokumentDoWidoku = Matrix()
    private val widokDoDokumentu = Matrix()
    private val macierzStrony = Matrix()
    private val bufor = FloatArray(2)

    // Stan dotyku

    private var idRysujacego = -1
    private var kreskaWTrakcie: InProgressStrokeId? = null
    private var stronaKreski = -1

    private var czasOstatniegoRysika = 0L
    private var rysikNaEkranie = false

    private val lasso = ArrayList<Float>()
    private var przesuwanieZaznaczenia = false
    private var poprzedniX = 0f
    private var poprzedniY = 0f

    private var gumkaX = Float.NaN
    private var gumkaY = Float.NaN

    private var palceId = intArrayOf(-1, -1)
    private var poprzedniSrodekX = 0f
    private var poprzedniSrodekY = 0f
    private var poprzedniRozstaw = 0f

    private val prog = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
        addView(widokKreski)
    }

    // Ustawianie widoku

    fun ustawWidok(x: Float, y: Float, skala: Float) {
        przesuniecieX = x
        przesuniecieY = y
        powiekszenie = skala.coerceIn(MIN_POWIEKSZENIE, MAKS_POWIEKSZENIE)
        odswiezMacierze()
        invalidate()
    }

    /** Dopasowuje szerokość pierwszej strony do szerokości ekranu. */
    fun dopasujSzerokosc() {
        val strona = strony.firstOrNull() ?: return
        if (width == 0) return
        val skala = (width - 2 * MARGINES_BIURKA) / strona.szerokosc
        ustawWidok(-MARGINES_BIURKA / skala, 0f, skala)
        sluchacz?.widokZmieniony(przesuniecieX, przesuniecieY, powiekszenie)
    }

    private fun odswiezMacierze() {
        dokumentDoWidoku.reset()
        dokumentDoWidoku.postTranslate(-przesuniecieX, -przesuniecieY)
        dokumentDoWidoku.postScale(powiekszenie, powiekszenie)

        widokDoDokumentu.reset()
        widokDoDokumentu.postScale(1f / powiekszenie, 1f / powiekszenie)
        widokDoDokumentu.postTranslate(przesuniecieX, przesuniecieY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && w > 0 && powiekszenie == 1f && przesuniecieX == 0f) {
            dopasujSzerokosc()
        }
        odswiezMacierze()
    }

    // Układ stron w jednym układzie współrzędnych

    private fun gornaKrawedz(indeks: Int): Float {
        var y = 0f
        for (i in 0 until indeks) {
            y += strony[i].wysokosc + ODSTEP_STRON
        }
        return y
    }

    /** Numer strony pod punktem w układzie dokumentu. Zwraca -1 poza stronami. */
    fun stronaPod(docX: Float, docY: Float): Int {
        var y = 0f
        for (strona in strony) {
            if (docY >= y && docY <= y + strona.wysokosc && docX >= 0f && docX <= strona.szerokosc) {
                return strona.indeks
            }
            y += strona.wysokosc + ODSTEP_STRON
        }
        return -1
    }

    /** Najbliższa strona, także wtedy, gdy punkt wypadł na marginesie obok kartki. */
    private fun najblizszaStrona(docY: Float): Int {
        if (strony.isEmpty()) return -1
        var y = 0f
        for (strona in strony) {
            if (docY <= y + strona.wysokosc + ODSTEP_STRON / 2f) return strona.indeks
            y += strona.wysokosc + ODSTEP_STRON
        }
        return strony.last().indeks
    }

    private fun doDokumentu(x: Float, y: Float): FloatArray {
        bufor[0] = x
        bufor[1] = y
        widokDoDokumentu.mapPoints(bufor)
        return bufor
    }

    // Rysowanie

    /**
     * Buduje siatki dla kresek, których jeszcze nie policzyliśmy.
     *
     * Policzone kreski trzymamy w podręcznej mapie po identyfikatorze.
     * Bez tego dopisanie jednej kreski do zapisanej strony kazałoby
     * przeliczyć wszystkie od nowa i pisanie zwalniałoby z każdą linijką.
     */
    private fun przeliczKreski() {
        val zywe = HashSet<String>()
        kreskiSilnika.clear()
        for (strona in strony) {
            val lista = ArrayList<Stroke>(strona.kreski.size)
            for (kreska in strona.kreski) {
                zywe += kreska.id
                val gotowa = podreczne[kreska.id] ?: runCatching { Kresy.doSilnika(kreska) }
                    .getOrNull()
                    ?.also { podreczne[kreska.id] = it }
                if (gotowa != null) lista += gotowa
            }
            kreskiSilnika[strona.indeks] = lista
        }
        if (podreczne.size > zywe.size) {
            podreczne.keys.retainAll(zywe)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(kolorBiurka)
        if (strony.isEmpty()) return

        pedzelPapieru.color = kolorPapieru
        pedzelLinii.color = kolorLinii

        var gora = 0f
        for (strona in strony) {
            val doleWidoku = (gora + strona.wysokosc - przesuniecieY) * powiekszenie
            val goraWidoku = (gora - przesuniecieY) * powiekszenie
            if (doleWidoku >= -32f && goraWidoku <= height + 32f) {
                rysujStrone(canvas, strona, gora)
            }
            gora += strona.wysokosc + ODSTEP_STRON
        }

        if (lasso.size >= 4) rysujLasso(canvas)
        if (zaznaczone.isNotEmpty()) rysujRamkeZaznaczenia(canvas)
        if (!gumkaX.isNaN()) rysujKoloGumki(canvas)
    }

    private fun rysujStrone(canvas: Canvas, strona: StronaNaEkranie, gora: Float) {
        val lewo = -przesuniecieX * powiekszenie
        val gornaY = (gora - przesuniecieY) * powiekszenie
        val prawo = lewo + strona.szerokosc * powiekszenie
        val dol = gornaY + strona.wysokosc * powiekszenie

        canvas.drawRect(lewo, gornaY, prawo, dol, pedzelPapieru)

        canvas.save()
        canvas.clipRect(lewo, gornaY, prawo, dol)
        rysujTlo(canvas, strona, lewo, gornaY)

        macierzStrony.set(dokumentDoWidoku)
        macierzStrony.preTranslate(0f, gora)
        val doNarysowania = kreskiSilnika[strona.indeks].orEmpty()
        for (kreska in doNarysowania) {
            rysownik.draw(canvas, kreska, macierzStrony)
        }
        canvas.restore()

        pedzelLinii.strokeWidth = 1f
        canvas.drawRect(lewo, gornaY, prawo, dol, pedzelLinii)
    }

    /**
     * Linie, kratka, kropki i pięciolinia. Rysowane w skali widoku,
     * więc przy powiększeniu nie rozmywają się jak obrazek.
     */
    private fun rysujTlo(canvas: Canvas, strona: StronaNaEkranie, lewo: Float, gora: Float) {
        val s = powiekszenie
        pedzelLinii.strokeWidth = max(1f, 0.6f * s)
        val szerokosc = strona.szerokosc * s
        val wysokosc = strona.wysokosc * s

        when (strona.tlo) {
            PageBackground.GLADKIE -> Unit

            PageBackground.LINIE -> {
                var y = ODSTEP_LINII
                while (y < strona.wysokosc) {
                    canvas.drawLine(lewo, gora + y * s, lewo + szerokosc, gora + y * s, pedzelLinii)
                    y += ODSTEP_LINII
                }
            }

            PageBackground.KRATKA -> {
                var y = ODSTEP_KRATKI
                while (y < strona.wysokosc) {
                    canvas.drawLine(lewo, gora + y * s, lewo + szerokosc, gora + y * s, pedzelLinii)
                    y += ODSTEP_KRATKI
                }
                var x = ODSTEP_KRATKI
                while (x < strona.szerokosc) {
                    canvas.drawLine(lewo + x * s, gora, lewo + x * s, gora + wysokosc, pedzelLinii)
                    x += ODSTEP_KRATKI
                }
            }

            PageBackground.KROPKI -> {
                val promien = max(1f, 0.9f * s)
                pedzelLinii.style = Paint.Style.FILL
                var y = ODSTEP_KRATKI
                while (y < strona.wysokosc) {
                    var x = ODSTEP_KRATKI
                    while (x < strona.szerokosc) {
                        canvas.drawCircle(lewo + x * s, gora + y * s, promien, pedzelLinii)
                        x += ODSTEP_KRATKI
                    }
                    y += ODSTEP_KRATKI
                }
                pedzelLinii.style = Paint.Style.STROKE
            }

            PageBackground.PIECIOLINIA -> {
                var y = ODSTEP_PIECIOLINII
                while (y + 4 * ODSTEP_PIECIOLINII < strona.wysokosc) {
                    for (i in 0 until 5) {
                        val linia = gora + (y + i * ODSTEP_PIECIOLINII) * s
                        canvas.drawLine(
                            lewo + MARGINES_PIECIOLINII * s,
                            linia,
                            lewo + szerokosc - MARGINES_PIECIOLINII * s,
                            linia,
                            pedzelLinii,
                        )
                    }
                    y += 5 * ODSTEP_PIECIOLINII + ODSTEP_MIEDZY_PIECIOLINIAMI
                }
            }
        }

        // Linia marginesu, ta sama, która przechodzi przez całą aplikację.
        if (strona.tlo != PageBackground.GLADKIE) {
            pedzelLinii.strokeWidth = max(1f, 0.9f * s)
            canvas.drawLine(
                lewo + MARGINES_STRONY * s,
                gora,
                lewo + MARGINES_STRONY * s,
                gora + wysokosc,
                pedzelLinii,
            )
        }
    }

    private fun rysujLasso(canvas: Canvas) {
        val sciezka = Path()
        val punkt = FloatArray(2)
        for (i in 0 until lasso.size / 2) {
            punkt[0] = lasso[i * 2]
            punkt[1] = lasso[i * 2 + 1]
            dokumentDoWidoku.mapPoints(punkt)
            if (i == 0) sciezka.moveTo(punkt[0], punkt[1]) else sciezka.lineTo(punkt[0], punkt[1])
        }
        pedzelZaznaczenia.color = kolorZaznaczenia
        canvas.drawPath(sciezka, pedzelZaznaczenia)
    }

    private fun rysujRamkeZaznaczenia(canvas: Canvas) {
        val obszar = Kresy.obszar(zaznaczone) ?: return
        val gora = gornaKrawedz(stronaZaznaczenia.coerceAtLeast(0))
        val lewoWidok = (obszar.left - przesuniecieX) * powiekszenie
        val goraWidok = (obszar.top + gora - przesuniecieY) * powiekszenie
        val prawoWidok = (obszar.right - przesuniecieX) * powiekszenie
        val dolWidok = (obszar.bottom + gora - przesuniecieY) * powiekszenie
        pedzelZaznaczenia.color = kolorZaznaczenia
        canvas.drawRect(lewoWidok - 8f, goraWidok - 8f, prawoWidok + 8f, dolWidok + 8f, pedzelZaznaczenia)
    }

    private fun rysujKoloGumki(canvas: Canvas) {
        pedzelGumki.color = kolorZaznaczenia
        pedzelGumki.strokeWidth = 2f
        val punkt = floatArrayOf(gumkaX, gumkaY)
        dokumentDoWidoku.mapPoints(punkt)
        canvas.drawCircle(punkt[0], punkt[1], ustawienia.promienGumki * powiekszenie, pedzelGumki)
    }

    // Dotyk

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val indeks = event.actionIndex
        val id = event.getPointerId(indeks)
        val typ = event.getToolType(indeks)

        if (typ == MotionEvent.TOOL_TYPE_STYLUS || typ == MotionEvent.TOOL_TYPE_ERASER) {
            czasOstatniegoRysika = event.eventTime
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> obsluzDol(event, indeks, id, typ)
            MotionEvent.ACTION_MOVE -> obsluzRuch(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> obsluzGore(event, indeks, id)
            MotionEvent.ACTION_CANCEL -> {
                anuluj()
                true
            }
            else -> false
        }
    }

    private fun obsluzDol(event: MotionEvent, indeks: Int, id: Int, typ: Int): Boolean {
        val rysik = typ == MotionEvent.TOOL_TYPE_STYLUS || typ == MotionEvent.TOOL_TYPE_ERASER

        if (rysik) {
            rysikNaEkranie = true
            // Rysik ma pierwszeństwo. Dłoń, która już leży na ekranie, przestaje działać.
            porzucPalce()
        } else if (odrzucicDlon(event)) {
            return true
        }

        val docPunkt = doDokumentu(event.getX(indeks), event.getY(indeks))
        val docX = docPunkt[0]
        val docY = docPunkt[1]

        val gumkaRysika = typ == MotionEvent.TOOL_TYPE_ERASER
        val rysujemy = rysik || palecRysuje

        if (!rysujemy) {
            zapamietajPalec(id, event.getX(indeks), event.getY(indeks), event)
            return true
        }

        when {
            gumkaRysika || narzedzie.gumka -> {
                idRysujacego = id
                gumkaX = docX
                gumkaY = docY
                wytrzyj(docX, docY, narzedzie == Narzedzie.GUMKA_KRESKA && !gumkaRysika)
                invalidate()
            }

            narzedzie == Narzedzie.LASSO -> {
                idRysujacego = id
                if (zaznaczone.isNotEmpty() && wZaznaczeniu(docX, docY)) {
                    przesuwanieZaznaczenia = true
                    poprzedniX = docX
                    poprzedniY = docY
                } else {
                    lasso.clear()
                    lasso += docX
                    lasso += docY
                    zaznaczone = emptyList()
                }
            }

            narzedzie.pisze -> {
                val strona = najblizszaStrona(docY)
                if (strona < 0) return true
                idRysujacego = id
                stronaKreski = strona
                val pedzel = when (narzedzie) {
                    Narzedzie.ZAKRESLACZ -> Pedzle.zakreslacz(
                        ustawienia.kolorZakreslacza,
                        ustawienia.gruboscZakreslacza,
                    )
                    else -> Pedzle.pioro(ustawienia.kolorPiora, ustawienia.gruboscPiora)
                }
                kreskaWTrakcie = widokKreski.startStroke(
                    event,
                    id,
                    pedzel,
                    Matrix(widokDoDokumentu),
                    Matrix(dokumentDoWidoku),
                )
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun obsluzRuch(event: MotionEvent): Boolean {
        if (idRysujacego >= 0) {
            val indeks = event.findPointerIndex(idRysujacego)
            if (indeks < 0) return true
            val docPunkt = doDokumentu(event.getX(indeks), event.getY(indeks))
            val docX = docPunkt[0]
            val docY = docPunkt[1]

            when {
                kreskaWTrakcie != null -> widokKreski.addToStroke(event, idRysujacego, kreskaWTrakcie!!, null)

                przesuwanieZaznaczenia -> {
                    sluchacz?.zaznaczeniePrzesuniete(docX - poprzedniX, docY - poprzedniY, koniec = false)
                    poprzedniX = docX
                    poprzedniY = docY
                }

                narzedzie == Narzedzie.LASSO -> {
                    lasso += docX
                    lasso += docY
                    invalidate()
                }

                else -> {
                    // Gumka. Wycieramy po drodze, a nie tylko w miejscu ostatniego palca.
                    val kroki = max(
                        1,
                        (hypot(docX - gumkaX, docY - gumkaY) / (ustawienia.promienGumki / 2f)).toInt(),
                    )
                    for (i in 1..kroki) {
                        val t = i / kroki.toFloat()
                        wytrzyj(
                            gumkaX + (docX - gumkaX) * t,
                            gumkaY + (docY - gumkaY) * t,
                            narzedzie == Narzedzie.GUMKA_KRESKA,
                        )
                    }
                    gumkaX = docX
                    gumkaY = docY
                    invalidate()
                }
            }
            return true
        }

        if (palceId[0] >= 0) {
            przesunIPowieksz(event)
            return true
        }
        return true
    }

    private fun obsluzGore(event: MotionEvent, indeks: Int, id: Int): Boolean {
        if (id == idRysujacego) {
            val kreska = kreskaWTrakcie
            if (kreska != null) {
                widokKreski.finishStroke(event, id, kreska)
            } else if (przesuwanieZaznaczenia) {
                sluchacz?.zaznaczeniePrzesuniete(0f, 0f, koniec = true)
                przesuwanieZaznaczenia = false
            } else if (narzedzie == Narzedzie.LASSO && lasso.size >= 6) {
                val strona = najblizszaStrona(lasso[1])
                if (strona >= 0) {
                    val gora = gornaKrawedz(strona)
                    val lokalny = ArrayList<Float>(lasso.size)
                    for (i in lasso.indices) {
                        lokalny += if (i % 2 == 0) lasso[i] else lasso[i] - gora
                    }
                    sluchacz?.lassoSkonczone(strona, lokalny)
                }
                lasso.clear()
                invalidate()
            } else if (narzedzie.gumka) {
                gumkaX = Float.NaN
                gumkaY = Float.NaN
                invalidate()
            }
            idRysujacego = -1
            kreskaWTrakcie = null
        }

        zapomnijPalec(id)

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            rysikNaEkranie = false
            porzucPalce()
        }
        return true
    }

    private fun anuluj() {
        kreskaWTrakcie?.let { widokKreski.cancelStroke(it, null) }
        kreskaWTrakcie = null
        idRysujacego = -1
        przesuwanieZaznaczenia = false
        lasso.clear()
        porzucPalce()
        gumkaX = Float.NaN
        gumkaY = Float.NaN
        invalidate()
    }

    /**
     * Odrzucanie dłoni. Kiedy rysik jest na ekranie albo był tam przed chwilą,
     * dotyk palca nie robi nic. Ręka oparta o tablet przestaje przeszkadzać.
     */
    private fun odrzucicDlon(event: MotionEvent): Boolean {
        if (rysikNaEkranie) return true
        return event.eventTime - czasOstatniegoRysika < CZAS_ODRZUCANIA_DLONI
    }

    private fun zapamietajPalec(id: Int, x: Float, y: Float, event: MotionEvent) {
        if (palceId[0] < 0) {
            palceId[0] = id
            poprzedniSrodekX = x
            poprzedniSrodekY = y
            poprzedniRozstaw = 0f
        } else if (palceId[1] < 0 && palceId[0] != id) {
            palceId[1] = id
            odswiezSrodekPalcow(event)
        }
    }

    private fun zapomnijPalec(id: Int) {
        if (palceId[0] == id) palceId[0] = palceId[1].also { palceId[1] = -1 }
        if (palceId[1] == id) palceId[1] = -1
        poprzedniRozstaw = 0f
    }

    private fun porzucPalce() {
        palceId[0] = -1
        palceId[1] = -1
        poprzedniRozstaw = 0f
    }

    private fun odswiezSrodekPalcow(event: MotionEvent) {
        val i1 = event.findPointerIndex(palceId[0])
        val i2 = event.findPointerIndex(palceId[1])
        if (i1 < 0 || i2 < 0) return
        poprzedniSrodekX = (event.getX(i1) + event.getX(i2)) / 2f
        poprzedniSrodekY = (event.getY(i1) + event.getY(i2)) / 2f
        poprzedniRozstaw = hypot(event.getX(i1) - event.getX(i2), event.getY(i1) - event.getY(i2))
    }

    private fun przesunIPowieksz(event: MotionEvent) {
        val i1 = event.findPointerIndex(palceId[0])
        if (i1 < 0) return
        val i2 = if (palceId[1] >= 0) event.findPointerIndex(palceId[1]) else -1

        if (i2 < 0) {
            val dx = event.getX(i1) - poprzedniSrodekX
            val dy = event.getY(i1) - poprzedniSrodekY
            if (abs(dx) < prog && abs(dy) < prog && poprzedniRozstaw == 0f) return
            przesuniecieX -= dx / powiekszenie
            przesuniecieY -= dy / powiekszenie
            poprzedniSrodekX = event.getX(i1)
            poprzedniSrodekY = event.getY(i1)
        } else {
            val srodekX = (event.getX(i1) + event.getX(i2)) / 2f
            val srodekY = (event.getY(i1) + event.getY(i2)) / 2f
            val rozstaw = hypot(event.getX(i1) - event.getX(i2), event.getY(i1) - event.getY(i2))

            if (poprzedniRozstaw > 0f && rozstaw > 0f) {
                val zmiana = rozstaw / poprzedniRozstaw
                val noweP = (powiekszenie * zmiana).coerceIn(MIN_POWIEKSZENIE, MAKS_POWIEKSZENIE)
                // Powiększamy wokół punktu między palcami, żeby nie uciekała strona.
                val przed = doDokumentu(srodekX, srodekY)
                val docX = przed[0]
                val docY = przed[1]
                powiekszenie = noweP
                odswiezMacierze()
                val po = doDokumentu(srodekX, srodekY)
                przesuniecieX += docX - po[0]
                przesuniecieY += docY - po[1]
            }

            przesuniecieX -= (srodekX - poprzedniSrodekX) / powiekszenie
            przesuniecieY -= (srodekY - poprzedniSrodekY) / powiekszenie
            poprzedniSrodekX = srodekX
            poprzedniSrodekY = srodekY
            poprzedniRozstaw = rozstaw
        }

        ograniczWidok()
        odswiezMacierze()
        invalidate()
        sluchacz?.widokZmieniony(przesuniecieX, przesuniecieY, powiekszenie)
    }

    private fun ograniczWidok() {
        val ostatnia = strony.lastOrNull() ?: return
        val calaWysokosc = gornaKrawedz(ostatnia.indeks) + ostatnia.wysokosc
        val zapasX = ostatnia.szerokosc * 0.5f
        val zapasY = height / powiekszenie * 0.5f
        przesuniecieX = przesuniecieX.coerceIn(-zapasX, ostatnia.szerokosc + zapasX)
        przesuniecieY = przesuniecieY.coerceIn(-zapasY, calaWysokosc + zapasY)
    }

    private fun wZaznaczeniu(docX: Float, docY: Float): Boolean {
        val obszar = Kresy.obszar(zaznaczone) ?: return false
        val gora = gornaKrawedz(stronaZaznaczenia.coerceAtLeast(0))
        return obszar.expanded(10f).contains(docX, docY - gora)
    }

    private fun wytrzyj(docX: Float, docY: Float, calaKreska: Boolean) {
        val strona = najblizszaStrona(docY)
        if (strona < 0) return
        val gora = gornaKrawedz(strona)
        sluchacz?.gumkaPrzeszla(strona, docX, docY - gora, ustawienia.promienGumki, calaKreska)
    }

    // Odbiór gotowych kresek z silnika

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        val strona = stronaKreski
        val gora = if (strona >= 0) gornaKrawedz(strona) else 0f
        for ((_, kreska) in strokes) {
            var model = Kresy.doModelu(kreska, narzedzie.doModelu())
            if (narzedzie == Narzedzie.LINIJKA) model = Kresy.wyprostuj(model)
            // Kreska przyszła w układzie dokumentu, a zapisujemy ją w układzie strony.
            val wStronie = model.translated(0f, -gora)
            if (strona >= 0) sluchacz?.kreskaSkonczona(strona, wStronie)
        }
        widokKreski.removeFinishedStrokes(strokes.keys)
        stronaKreski = -1
    }

    override fun onDetachedFromWindow() {
        widokKreski.removeFinishedStrokesListener(this)
        super.onDetachedFromWindow()
    }

    companion object {
        const val ODSTEP_STRON = 24f
        const val MARGINES_BIURKA = 24f
        const val ODSTEP_LINII = 28f
        const val ODSTEP_KRATKI = 20f
        const val ODSTEP_PIECIOLINII = 9f
        const val ODSTEP_MIEDZY_PIECIOLINIAMI = 46f
        const val MARGINES_PIECIOLINII = 30f
        const val MARGINES_STRONY = 60f
        const val MIN_POWIEKSZENIE = 0.25f
        const val MAKS_POWIEKSZENIE = 8f

        /** Ile milisekund po oderwaniu rysika dotyk palca jest nadal odrzucany. */
        const val CZAS_ODRZUCANIA_DLONI = 400L
    }
}
