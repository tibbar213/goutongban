package cn.goutongban.aac.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.goutongban.aac.model.Notice
import cn.goutongban.aac.model.Phrase
import cn.goutongban.aac.model.PhraseAction
import cn.goutongban.aac.model.WordTransition
import kotlinx.coroutines.flow.Flow

data class StoredPhrase(
    val label: String,
    val speech: String,
    val categoryId: String,
    val icon: String?,
    val action: String?,
    val notice: String?
) {
    fun toPhrase(id: String, photo: String? = null) = Phrase(
        id = id, label = label, speech = speech, categoryId = categoryId,
        icon = icon, action = action?.let(PhraseAction::valueOf),
        notice = notice?.let(Notice::valueOf), photo = photo
    )

    companion object {
        fun from(phrase: Phrase) = StoredPhrase(
            phrase.label, phrase.speech, phrase.categoryId, phrase.icon,
            phrase.action?.name, phrase.notice?.name
        )
    }
}

enum class CustomEntryType { PHRASE, WORD }

@Entity(tableName = "custom_phrases")
data class CustomPhrase(
    @PrimaryKey val id: String,
    @androidx.room.Embedded val content: StoredPhrase,
    val photo: String?,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'PHRASE'") val type: CustomEntryType = CustomEntryType.PHRASE,
    @ColumnInfo(defaultValue = "0") val position: Long = 0
) {
    fun toPhrase() = content.toPhrase(id, photo)
}

@Entity(tableName = "saved_phrases")
data class SavedPhrase(
    @PrimaryKey val id: String,
    @androidx.room.Embedded val content: StoredPhrase,
    val favorite: Boolean,
    val pinned: Boolean,
    val savedAt: Long
)

@Entity(tableName = "recent_phrases")
data class RecentPhrase(
    @PrimaryKey val id: String,
    @androidx.room.Embedded val content: StoredPhrase,
    val usedAt: Long
)

@Entity(tableName = "learned_words", primaryKeys = ["context", "word"])
internal data class LearnedWord(
    val context: String,
    val word: String,
    val uses: Int,
    val lastUsed: Long
)

@Dao
abstract class BoardDao {
    @Query("SELECT * FROM custom_phrases ORDER BY position, updatedAt DESC, id")
    abstract fun custom(): Flow<List<CustomPhrase>>

    @Query("SELECT * FROM saved_phrases ORDER BY savedAt DESC")
    abstract fun saved(): Flow<List<SavedPhrase>>

    @Query("SELECT * FROM recent_phrases ORDER BY usedAt DESC, rowid DESC")
    abstract fun recent(): Flow<List<RecentPhrase>>

    @Query("SELECT * FROM custom_phrases WHERE id = :id")
    abstract suspend fun customById(id: String): CustomPhrase?

    @Upsert abstract suspend fun putCustom(row: CustomPhrase)
    @Upsert abstract suspend fun putSaved(row: SavedPhrase)
    @Upsert abstract suspend fun putRecent(row: RecentPhrase)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM custom_phrases WHERE type = :type")
    protected abstract suspend fun nextCustomPosition(type: CustomEntryType): Long

    @Query("SELECT id FROM custom_phrases WHERE type = :type ORDER BY position, updatedAt DESC, id")
    protected abstract suspend fun customOrder(type: CustomEntryType): List<String>

    @Query("UPDATE custom_phrases SET position = :position WHERE id = :id")
    protected abstract suspend fun updateCustomPosition(id: String, position: Long)

    @Query("SELECT * FROM saved_phrases WHERE id = :id")
    abstract suspend fun savedById(id: String): SavedPhrase?

    @Query("DELETE FROM custom_phrases WHERE id = :id")
    abstract suspend fun deleteCustomRow(id: String)

    @Query("DELETE FROM saved_phrases WHERE id = :id")
    abstract suspend fun deleteSaved(id: String)

    @Query("DELETE FROM recent_phrases WHERE id = :id")
    abstract suspend fun deleteRecent(id: String)

    @Query("DELETE FROM recent_phrases WHERE id NOT IN (SELECT id FROM recent_phrases ORDER BY usedAt DESC, rowid DESC LIMIT 60)")
    abstract suspend fun trimRecent()

    @Query("DELETE FROM recent_phrases")
    abstract suspend fun clearRecent()

    @Query("SELECT * FROM learned_words WHERE uses >= 2 ORDER BY uses DESC, lastUsed DESC, length(word) DESC, word")
    internal abstract fun learnedWords(): Flow<List<LearnedWord>>

    @Query("UPDATE learned_words SET uses = min(uses + 1, 10000), lastUsed = :now WHERE context = :context AND word = :word")
    internal abstract suspend fun countWord(context: String, word: String, now: Long): Int

    @Upsert internal abstract suspend fun putWord(word: LearnedWord)

    @Query("DELETE FROM learned_words WHERE rowid NOT IN (SELECT rowid FROM learned_words ORDER BY lastUsed DESC, uses DESC, rowid DESC LIMIT 2000)")
    internal abstract suspend fun trimLearnedWords()

    @Query("DELETE FROM learned_words")
    internal abstract suspend fun clearLearnedWords()

    @Transaction
    internal open suspend fun recordExpression(phrase: Phrase, transitions: Set<WordTransition>) {
        record(phrase)
        if (transitions.isEmpty()) return
        val now = System.currentTimeMillis()
        transitions.forEach { (context, word) ->
            if (countWord(context, word, now) == 0) putWord(LearnedWord(context, word, 1, now))
        }
        trimLearnedWords()
    }

    @Transaction
    open suspend fun record(phrase: Phrase) {
        putRecent(RecentPhrase(phrase.id, StoredPhrase.from(phrase), System.currentTimeMillis()))
        trimRecent()
    }

    @Transaction
    open suspend fun changeSaved(phrase: Phrase, favorite: Boolean? = null, pinned: Boolean? = null) {
        val previous = savedById(phrase.id)
        val isFavorite = favorite ?: previous?.favorite ?: false
        val isPinned = pinned ?: previous?.pinned ?: false
        if (isFavorite || isPinned) {
            putSaved(SavedPhrase(
                phrase.id, StoredPhrase.from(phrase), isFavorite, isPinned,
                previous?.savedAt ?: System.currentTimeMillis()
            ))
        } else {
            deleteSaved(phrase.id)
        }
    }

    @Transaction
    open suspend fun saveCustom(row: CustomPhrase) {
        val previous = customById(row.id)
        val position = previous?.takeIf { it.type == row.type }?.position ?: nextCustomPosition(row.type)
        putCustom(row.copy(position = position))
    }

    @Transaction
    open suspend fun moveCustom(id: String, offset: Int) {
        val row = customById(id) ?: return
        val order = customOrder(row.type).toMutableList()
        val index = order.indexOf(id)
        val target = index + offset
        if (index < 0 || target !in order.indices) return
        order.removeAt(index)
        order.add(target, id)
        order.forEachIndexed { position, entry -> updateCustomPosition(entry, position.toLong()) }
    }

    @Transaction
    open suspend fun removeCustom(id: String): String? {
        val photo = customById(id)?.photo
        deleteSaved(id)
        deleteRecent(id)
        deleteCustomRow(id)
        return photo
    }
}

@Database(
    entities = [CustomPhrase::class, SavedPhrase::class, RecentPhrase::class, LearnedWord::class],
    version = 3,
    exportSchema = false
)
abstract class BoardDatabase : RoomDatabase() {
    abstract fun board(): BoardDao

    companion object {
        internal val AddLearnedWords = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE learned_words (context TEXT NOT NULL, word TEXT NOT NULL, " +
                        "uses INTEGER NOT NULL, lastUsed INTEGER NOT NULL, PRIMARY KEY(context, word))"
                )
            }
        }

        internal val AddCustomEntryTypes = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_phrases ADD COLUMN type TEXT NOT NULL DEFAULT 'PHRASE'")
                db.execSQL("ALTER TABLE custom_phrases ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE custom_phrases SET position = -updatedAt")
            }
        }

        fun create(context: Context): BoardDatabase =
            Room.databaseBuilder(context, BoardDatabase::class.java, "communication_board.db")
                .addMigrations(AddLearnedWords, AddCustomEntryTypes)
                .build()
    }
}
