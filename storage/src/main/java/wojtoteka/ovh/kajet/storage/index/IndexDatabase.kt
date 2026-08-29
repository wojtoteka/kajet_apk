package wojtoteka.ovh.kajet.storage.index

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries")
data class IndexEntry(
    @PrimaryKey val documentUri: String,
    val documentId: String = "",
    val path: String,
    val name: String,
    val type: String,
    val noteKind: String? = null,
    val language: String? = null,
    val colorId: String? = null,
    val iconId: String? = null,
    val favorite: Boolean = false,
    val tags: String = "",
    val updatedAt: Long = 0L,
    val openedAt: Long = 0L,
    val preview: String = "",
) {
    val parentPath: String get() = path.substringBeforeLast('/', "")
}

@Entity(tableName = "content_fts")
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class ContentFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long = 0L,
    val documentUri: String,
    val title: String,
    val content: String,
)

/** Trwały opis jednego uploadu; same bajty leżą w prywatnym pliku aplikacji. */
@Entity(tableName = "file_uploads")
data class FileUploadEntry(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val localPath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val folderPath: String,
    val folderId: String? = null,
    val status: String,
    val progress: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val retryCount: Int = 0,
    val remoteFileId: String? = null,
    val lastError: String? = null,
    val errorCode: String? = null,
    /** Pasek zamknięty krzyżykiem. Wgrywanie leci dalej, napis już nie wraca. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Dao
interface FileUploadDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: FileUploadEntry)

    @Query("SELECT * FROM file_uploads WHERE hidden = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FileUploadEntry>>

    @Query("SELECT * FROM file_uploads WHERE id = :id")
    suspend fun find(id: String): FileUploadEntry?

    @Query("UPDATE file_uploads SET hidden = 1, updatedAt = :now WHERE id = :id")
    suspend fun hide(id: String, now: Long)

    @Query("DELETE FROM file_uploads WHERE id = :id")
    suspend fun delete(id: String)

    /** Wpisy, przy których nie ma już nic do zrobienia - zamknięte i nie. */
    @Query(
        "SELECT * FROM file_uploads WHERE status IN ('SYNCED', 'FAILED_PERMANENT') " +
            "AND updatedAt < :before",
    )
    suspend fun finishedBefore(before: Long): List<FileUploadEntry>

    @Query("SELECT * FROM file_uploads WHERE status IN ('PENDING', 'FAILED_RETRYABLE') ORDER BY createdAt ASC")
    suspend fun ready(): List<FileUploadEntry>

    @Query("UPDATE file_uploads SET status = 'PENDING', progress = 0, updatedAt = :now, lastError = NULL, errorCode = NULL, hidden = 0 WHERE id = :id")
    suspend fun retry(id: String, now: Long)

    @Query("UPDATE file_uploads SET status = 'PENDING', progress = 0, updatedAt = :now WHERE status = 'UPLOADING'")
    suspend fun recoverInterrupted(now: Long): Int

    @Query("UPDATE file_uploads SET status = 'UPLOADING', progress = 0, updatedAt = :now, lastError = NULL, errorCode = NULL WHERE id = :id")
    suspend fun markUploading(id: String, now: Long)

    @Query("UPDATE file_uploads SET progress = :progress, updatedAt = :now WHERE id = :id AND status = 'UPLOADING'")
    suspend fun setProgress(id: String, progress: Int, now: Long)

    @Query("UPDATE file_uploads SET status = 'SYNCED', progress = 100, updatedAt = :now, remoteFileId = :remoteId, lastError = NULL, errorCode = NULL WHERE id = :id")
    suspend fun markSynced(id: String, remoteId: String, now: Long)

    @Query("UPDATE file_uploads SET status = :status, progress = 0, updatedAt = :now, retryCount = retryCount + 1, lastError = :message, errorCode = :code WHERE id = :id")
    suspend fun markFailed(id: String, status: String, message: String, code: String?, now: Long)
}

@Dao
interface IndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IndexEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<IndexEntry>)

    @Query("DELETE FROM entries WHERE documentUri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM entries WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun deleteBranch(path: String)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM entries WHERE documentUri = :uri")
    suspend fun find(uri: String): IndexEntry?

    @Query("SELECT * FROM entries WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): IndexEntry?

    @Query("SELECT * FROM entries WHERE documentId = :id LIMIT 1")
    suspend fun findById(id: String): IndexEntry?

    /**
     * Ulubione: notatki i pliki, bo gwiazdkę da się postawić i na jednym,
     * i na drugim. Foldery zostają poza spisem - one gwiazdki nie noszą.
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE favorite = 1 AND type != 'FOLDER'
        ORDER BY updatedAt DESC
        """,
    )
    fun favorites(): Flow<List<IndexEntry>>

    @Query(
        """
        SELECT * FROM entries
        WHERE openedAt > 0
        ORDER BY openedAt DESC
        LIMIT :count
        """,
    )
    fun recent(count: Int = 20): Flow<List<IndexEntry>>

    /** Zwraca liczbę zmienionych wierszy, więc widać, czy wpis w ogóle był w spisie. */
    @Query("UPDATE entries SET openedAt = :at WHERE documentUri = :uri")
    suspend fun rememberOpened(uri: String, at: Long): Int

    @Query("UPDATE entries SET openedAt = :at WHERE path = :path")
    suspend fun rememberOpenedByPath(path: String, at: Long): Int

    @Query("SELECT openedAt FROM entries WHERE documentUri = :uri")
    suspend fun openedAt(uri: String): Long?

    @Transaction
    suspend fun upsertKeepingOpened(entry: IndexEntry) {
        val previous = openedAt(entry.documentUri) ?: 0L
        upsert(entry.copy(openedAt = maxOf(previous, entry.openedAt)))
    }

    @Query("SELECT DISTINCT tags FROM entries WHERE tags != ''")
    fun allTags(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    /** Wszystkie notatki ze spisu - do uzgadniania biblioteki z chmurą. */
    @Query("SELECT * FROM entries WHERE type = 'NOTE' AND documentId != ''")
    suspend fun allNotes(): List<IndexEntry>

    /** Cały spis - do wypatrywania wierszy po plikach, których już nie ma. */
    @Query("SELECT * FROM entries")
    suspend fun allEntries(): List<IndexEntry>

    /** Adresy plików w gałęzi - po nich kasuje się treść z wyszukiwarki. */
    @Query("SELECT documentUri FROM entries WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun urisUnder(path: String): List<String>

    /** Wszystkie pliki z kodem - one też jeżdżą do chmury. */
    @Query("SELECT path FROM entries WHERE type = 'CODE_FILE'")
    suspend fun allCodeFilePaths(): List<String>

    /** Wszystkie foldery - do synchronizacji struktury katalogów z chmurą. */
    @Query("SELECT path FROM entries WHERE type = 'FOLDER'")
    suspend fun allFolderPaths(): List<String>

    /** Notatki w gałęzi (wpis i wszystko pod nim) - do zgłaszania kasowań. */
    @Query(
        """
        SELECT * FROM entries
        WHERE (path = :path OR path LIKE :path || '/%')
          AND type = 'NOTE' AND documentId != ''
        """,
    )
    suspend fun notesUnder(path: String): List<IndexEntry>

    /** Foldery w gałęzi (wpis i wszystko pod nim) - do zgłaszania kasowań. */
    @Query(
        """
        SELECT path FROM entries
        WHERE (path = :path OR path LIKE :path || '/%')
          AND type = 'FOLDER'
        """,
    )
    suspend fun folderPathsUnder(path: String): List<String>

    /** Pliki z kodem w gałęzi - do zgłaszania kasowań. */
    @Query(
        """
        SELECT path FROM entries
        WHERE (path = :path OR path LIKE :path || '/%')
          AND type = 'CODE_FILE'
        """,
    )
    suspend fun codeFilePathsUnder(path: String): List<String>

    // Searching

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(content: ContentFts)

    @Query("DELETE FROM content_fts WHERE documentUri = :uri")
    suspend fun deleteContent(uri: String)

    @Query("DELETE FROM content_fts")
    suspend fun deleteAllContent()

    @Query(
        """
        SELECT e.* FROM entries e
        JOIN content_fts f ON f.documentUri = e.documentUri
        WHERE content_fts MATCH :query
        ORDER BY e.updatedAt DESC
        LIMIT 100
        """,
    )
    suspend fun searchContent(query: String): List<IndexEntry>

    @Query(
        """
        SELECT * FROM entries
        WHERE name LIKE '%' || :fragment || '%'
        ORDER BY updatedAt DESC
        LIMIT 100
        """,
    )
    suspend fun searchNames(fragment: String): List<IndexEntry>

    @Transaction
    suspend fun save(entry: IndexEntry, title: String, content: String) {
        upsertKeepingOpened(entry)
        deleteContent(entry.documentUri)
        if (content.isNotBlank() || title.isNotBlank()) {
            upsertContent(ContentFts(documentUri = entry.documentUri, title = title, content = content))
        }
    }

    /**
     * Kasuje gałąź razem z treścią do wyszukiwania.
     *
     * Samo [deleteBranch] zostawiało wiersze w `content_fts` - szukanie po
     * treści oddawało wtedy notatki, których dawno nie ma (złączenie z
     * `entries` je gubi, ale wiersze rosną w nieskończoność). Kasowanie ma po
     * sobie nie zostawiać niczego, więc idą razem i jednym zapisem.
     */
    @Transaction
    suspend fun deleteBranchWithContent(path: String) {
        for (uri in urisUnder(path)) deleteContent(uri)
        deleteBranch(path)
    }

    @Transaction
    suspend fun clear() {
        deleteAll()
        deleteAllContent()
    }
}

@Database(
    entities = [IndexEntry::class, ContentFts::class, FileUploadEntry::class],
    // Version 3 renames the tables and columns to English. The index is thrown
    // away and rebuilt, so there is nothing to migrate.
    //
    // Wersja 4 nie zmienia kształtu tabel - wymusza odbudowę, bo podglądy
    // notatek zapisane starą wersją IndexText mają w treści gołe `**`.
    // Podgląd powstaje przy zapisie do spisu, więc bez odbudowy poprawka
    // objęłaby wyłącznie notatki tknięte po aktualizacji.
    // Wersja 5 dodaje trwałą kolejkę uploadów. W przeciwieństwie do samego
    // indeksu nie wolno jej skasować przy aktualizacji, bo zawiera pracę
    // oczekującą na sieć.
    // Wersja 6 zapamiętuje zamknięcie paska wgrywania. Bez tego napis wracał
    // po każdym uruchomieniu aplikacji, choć ktoś go wcześniej zamknął.
    version = 6,
    exportSchema = true,
)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun index(): IndexDao
    abstract fun uploads(): FileUploadDao

    companion object {
        @Volatile
        private var instance: IndexDatabase? = null

        fun get(context: Context): IndexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IndexDatabase::class.java,
                    "kajet-index.db",
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    // Stare wersje 1-3 zawierały wyłącznie odbudowywalny indeks.
                    // Od wersji 5 baza niesie też kolejkę uploadów, więc każda
                    // przyszła zmiana MUSI dostać migrację zamiast kasowania.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `file_uploads` (
                        `id` TEXT NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `originalName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `folderPath` TEXT NOT NULL,
                        `folderId` TEXT,
                        `status` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `remoteFileId` TEXT,
                        `lastError` TEXT,
                        `errorCode` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `file_uploads` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
