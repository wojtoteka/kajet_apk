package wojtoteka.ovh.kajet.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageLinesTest {

    @Test
    fun `wiersz z kilkoma zdjeciami czyta sie w calosci`() {
        val photos = ImageLines.read("![a|25%](assets/a.png) ![b|50%](assets/b.png)")

        assertThat(photos).isNotNull()
        assertThat(photos!!.map { it.url }).containsExactly("assets/a.png", "assets/b.png").inOrder()
        assertThat(photos[0].width).isWithin(0.001f).of(0.25f)
        assertThat(photos[1].width).isWithin(0.001f).of(0.5f)
        assertThat(photos.map { it.alt }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `zdjecie w srodku zdania to nie wiersz ze zdjeciami`() {
        assertThat(ImageLines.read("Zobacz ![to](assets/a.png) tutaj")).isNull()
        assertThat(ImageLines.read("![to](assets/a.png) i jeszcze slowo")).isNull()
        assertThat(ImageLines.read("zwykly wiersz")).isNull()
    }

    @Test
    fun `nazwa z nawiasem przezywa sasiedztwo drugiego zdjecia`() {
        val photos = ImageLines.read("![a|25%](assets/zdjecie (2).png) ![b|25%](assets/b.png)")

        assertThat(photos).isNotNull()
        assertThat(photos!!.map { it.url })
            .containsExactly("assets/zdjecie (2).png", "assets/b.png").inOrder()
    }

    @Test
    fun `ulozenie ma caly wiersz, nie pojedyncze zdjecie`() {
        val photos = ImageLines.read("![a](assets/a.png \"srodek\") ![b](assets/b.png)")

        assertThat(photos!!.map { it.align })
            .containsExactly(NoteAlign.CENTER, NoteAlign.CENTER)
    }

    @Test
    fun `zapis wraca do tego samego wiersza`() {
        val line = "![a|25%](assets/a.png \"prawo\") ![b|40%](assets/b.png \"prawo\")"

        assertThat(ImageLines.write(ImageLines.read(line)!!)).isEqualTo(line)
    }

    @Test
    fun `dawny zapis szerokosci w tytule nadal sie czyta`() {
        val photos = ImageLines.read("![z](assets/z.png \"50%\")")

        assertThat(photos!!.single().width).isWithin(0.001f).of(0.5f)
        assertThat(photos.single().align).isEqualTo(NoteAlign.LEFT)
    }

    @Test
    fun `zdjecia szersze niz kartka scieraja sie do jej szerokosci`() {
        // Dwa razy 75% i odstęp między nimi nie mieszczą się obok siebie, więc
        // oba schodzą po równo - a nie wyjeżdżają poza notatkę.
        val room = ImageLines.sideBySide(listOf(0.75f, 0.75f), gapShare = 0.02f)

        assertThat(room[0]).isWithin(0.001f).of(room[1])
        assertThat(room.sum()).isWithin(0.001f).of(0.98f)
    }

    @Test
    fun `zdjecia mieszczace sie obok siebie zostaja bez zmian`() {
        val room = ImageLines.sideBySide(listOf(0.25f, 0.25f), gapShare = 0.02f)

        assertThat(room).containsExactly(0.25f, 0.25f).inOrder()
    }
}
