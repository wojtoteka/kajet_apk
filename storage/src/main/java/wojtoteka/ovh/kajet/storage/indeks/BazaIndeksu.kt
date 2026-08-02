package wojtoteka.ovh.kajet.storage.indeks

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Wpis w indeksie. Indeks jest tylko po to, żeby lista i wyszukiwanie działały szybko.
 *
 * Prawdą są pliki. Jeśli skasujesz bazę, aplikacja odbuduje ją z katalogu biblioteki
 * i nic nie zginie.
 */
@Entity(tableName = "wpisy")
data class WpisIndeksu(
    @PrimaryKey val documentUri: String,
    val path: String,
    val name: String,
    val type: String,
    val noteKind: String? = null,
    val language: String? = null,
    val colorId: String? = null,
    val iconId: String? = null,
    val favorite: Boolean = false,
    /** Tagi sklejone znakiem pionowej kreski. */
    val tags: String = "",
    val updatedAt: Long = 0L,
    /** Kiedy użytkownik ostatnio otworzył ten wpis. Zero oznacza, że nigdy. */
    val openedAt: Long = 0L,
    val preview: String = "",
) {
    val parentPath: String get() = path.substringBeforeLast('/', "")
}

/**
 * Tabela do wyszukiwania pełnotekstowego. Trzyma tekst notatek,
 * w tym pismo odręczne zamienione na tekst.
 */
@Entity(tableName = "tresc_fts")
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class TrescFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long = 0L,
    val documentUri: String,
    val tytul: String,
    val tresc: String,
)

@Dao
interface IndeksDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun wstaw(wpis: WpisIndeksu)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun wstawWszystkie(wpisy: List<WpisIndeksu>)

    @Query("DELETE FROM wpisy WHERE documentUri = :uri")
    suspend fun usun(uri: String)

    @Query("DELETE FROM wpisy WHERE path = :sciezka OR path LIKE :sciezka || '/%'")
    suspend fun usunGalaz(sciezka: String)

    @Query("DELETE FROM wpisy")
    suspend fun usunWszystkie()

    @Query("SELECT * FROM wpisy WHERE documentUri = :uri")
    suspend fun znajdz(uri: String): WpisIndeksu?

    @Query("SELECT * FROM wpisy WHERE path = :sciezka LIMIT 1")
    suspend fun znajdzPoSciezce(sciezka: String): WpisIndeksu?

    @Query(
        """
        SELECT * FROM wpisy
        WHERE favorite = 1 AND type = 'NOTATKA'
        ORDER BY updatedAt DESC
        """,
    )
    fun ulubione(): Flow<List<WpisIndeksu>>

    @Query(
        """
        SELECT * FROM wpisy
        WHERE openedAt > 0
        ORDER BY openedAt DESC
        LIMIT :ile
        """,
    )
    fun ostatnie(ile: Int = 20): Flow<List<WpisIndeksu>>

    @Query("UPDATE wpisy SET openedAt = :kiedy WHERE documentUri = :uri")
    suspend fun zapamietajOtwarcie(uri: String, kiedy: Long)

    @Query("SELECT DISTINCT tags FROM wpisy WHERE tags != ''")
    fun wszystkieTagi(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM wpisy")
    suspend fun ile(): Int

    // Wyszukiwanie

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun wstawTresc(tresc: TrescFts)

    @Query("DELETE FROM tresc_fts WHERE documentUri = :uri")
    suspend fun usunTresc(uri: String)

    @Query("DELETE FROM tresc_fts")
    suspend fun usunCalaTresc()

    @Query(
        """
        SELECT w.* FROM wpisy w
        JOIN tresc_fts f ON f.documentUri = w.documentUri
        WHERE tresc_fts MATCH :zapytanie
        ORDER BY w.updatedAt DESC
        LIMIT 100
        """,
    )
    suspend fun szukajWTresci(zapytanie: String): List<WpisIndeksu>

    @Query(
        """
        SELECT * FROM wpisy
        WHERE name LIKE '%' || :fragment || '%'
        ORDER BY updatedAt DESC
        LIMIT 100
        """,
    )
    suspend fun szukajWNazwach(fragment: String): List<WpisIndeksu>

    @Transaction
    suspend fun zapisz(wpis: WpisIndeksu, tytul: String, tresc: String) {
        wstaw(wpis)
        usunTresc(wpis.documentUri)
        if (tresc.isNotBlank() || tytul.isNotBlank()) {
            wstawTresc(TrescFts(documentUri = wpis.documentUri, tytul = tytul, tresc = tresc))
        }
    }

    @Transaction
    suspend fun wyczysc() {
        usunWszystkie()
        usunCalaTresc()
    }
}

@Database(
    entities = [WpisIndeksu::class, TrescFts::class],
    version = 1,
    exportSchema = true,
)
abstract class BazaIndeksu : RoomDatabase() {
    abstract fun indeks(): IndeksDao

    companion object {
        @Volatile
        private var instancja: BazaIndeksu? = null

        fun pobierz(context: Context): BazaIndeksu =
            instancja ?: synchronized(this) {
                instancja ?: Room.databaseBuilder(
                    context.applicationContext,
                    BazaIndeksu::class.java,
                    "kajet-indeks.db",
                )
                    // Indeks da się odbudować z plików, więc przy zmianie wersji
                    // kasujemy go i budujemy od nowa zamiast pisać migracje.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instancja = it }
            }
    }
}
