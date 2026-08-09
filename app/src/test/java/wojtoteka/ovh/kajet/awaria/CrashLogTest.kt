package wojtoteka.ovh.kajet.awaria

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `rotacja zostawia piec najnowszych raportow`() {
        val dir = folder.newFolder("awarie")
        for (i in 1..8) {
            File(dir, "awaria-$i.txt").writeText("raport $i")
        }

        CrashLog.rotate(dir)

        val names = dir.listFiles()!!.map { it.name }.sorted()
        assertThat(names).containsExactly(
            "awaria-4.txt",
            "awaria-5.txt",
            "awaria-6.txt",
            "awaria-7.txt",
            "awaria-8.txt",
        )
    }

    @Test
    fun `opis awarii niesie caly slad wywolan`() {
        val trace = CrashLog.stackTrace(IllegalStateException("coś pękło"))

        assertThat(trace).contains("IllegalStateException")
        assertThat(trace).contains("coś pękło")
        assertThat(trace).contains("CrashLogTest")
    }

    @Test
    fun `naglowek raportu daje sie rozebrac na pola`() {
        val report = listOf(
            "Kajet 1.0 (2)",
            "Czas: 2026-08-06 14:33:14",
            "Urządzenie: LENOVO TB520FU, Android 16",
            "Wątek: main",
            "",
            "java.lang.IllegalStateException: coś pękło",
        ).joinToString("\n")

        val facts = CrashLog.facts(report)

        assertThat(facts.appVersion).isEqualTo("1.0")
        assertThat(facts.versionCode).isEqualTo(2)
        assertThat(facts.device).isEqualTo("LENOVO TB520FU")
        assertThat(facts.android).isEqualTo("16")
        assertThat(facts.thread).isEqualTo("main")
    }

    @Test
    fun `starszy raport bez numeru wydania tez sie czyta`() {
        val report = "Kajet 1.0\nUrządzenie: samsung SM-X710\nWątek: DefaultDispatcher-worker-1\n"

        val facts = CrashLog.facts(report)

        assertThat(facts.appVersion).isEqualTo("1.0")
        assertThat(facts.versionCode).isNull()
        assertThat(facts.device).isEqualTo("samsung SM-X710")
        assertThat(facts.android).isNull()
        assertThat(facts.thread).isEqualTo("DefaultDispatcher-worker-1")
    }

    @Test
    fun `raport bez naglowka nie wywraca odczytu`() {
        val facts = CrashLog.facts("java.lang.OutOfMemoryError")

        assertThat(facts.appVersion).isNull()
        assertThat(facts.device).isNull()
        assertThat(facts.thread).isNull()
    }

    @Test
    fun `odhaczony raport nie wraca do wysylki`() {
        val dir = folder.newFolder("awarie2")
        val file = File(dir, "awaria-1.txt").apply { writeText("raport") }

        assertThat(CrashLog.markSent(file)).isTrue()

        val names = dir.listFiles()!!.map { it.name }
        assertThat(names).containsExactly("awaria-1.txt" + CrashLog.SENT_SUFFIX)
    }

    @Test
    fun `obciecie do intentu trzyma limit`() {
        val long = "x".repeat(CrashLog.LONGEST_EXTRA + 5_000)
        assertThat(CrashLog.clip(long).length).isEqualTo(CrashLog.LONGEST_EXTRA)
        assertThat(CrashLog.clip("krótki")).isEqualTo("krótki")
    }
}
