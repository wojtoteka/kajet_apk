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

    @Query(
        """
        SELECT * FROM entries
        WHERE favorite = 1 AND type = 'NOTE'
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

    /** Wszystkie notatki ze spisu — do uzgadniania biblioteki z chmurą. */
    @Query("SELECT * FROM entries WHERE type = 'NOTE' AND documentId != ''")
    suspend fun allNotes(): List<IndexEntry>

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

    @Transaction
    suspend fun clear() {
        deleteAll()
        deleteAllContent()
    }
}

@Database(
    entities = [IndexEntry::class, ContentFts::class],
    // Version 3 renames the tables and columns to English. The index is thrown
    // away and rebuilt, so there is nothing to migrate.
    version = 3,
    exportSchema = true,
)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun index(): IndexDao

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
                    // The index can be rebuilt from the files, so on a version
                    // change we drop it and build it again instead of writing
                    // migrations.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
