package cn.goutongban.aac

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.goutongban.aac.data.BoardDatabase
import cn.goutongban.aac.data.BoardDao
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.data.CustomPhrase
import cn.goutongban.aac.data.PreferencesStore
import cn.goutongban.aac.data.StoredPhrase
import cn.goutongban.aac.model.Phrase
import cn.goutongban.aac.model.WordTransition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun favoritePinnedHistoryAndCustomSurviveDatabaseReopen() = runBlocking {
        val name = "test_board.db"
        context.deleteDatabase(name)
        val phrase = Phrase("custom:test", "找小王", "请帮我找小王。", "find")
        try {
            withDatabase(name) { dao ->
                dao.putCustom(CustomPhrase(phrase.id, StoredPhrase.from(phrase), "example.jpg", 1L))
                dao.changeSaved(phrase, favorite = true)
                dao.changeSaved(phrase, pinned = true)
                dao.record(phrase)
            }
            withDatabase(name) { dao ->
                val saved = dao.saved().first().single()
                assertTrue(saved.favorite)
                assertTrue(saved.pinned)
                assertEquals(phrase.speech, dao.recent().first().single().content.speech)
                assertEquals("example.jpg", dao.custom().first().single().photo)
                assertEquals("example.jpg", dao.removeCustom(phrase.id))
                assertTrue(dao.saved().first().isEmpty())
                assertTrue(dao.recent().first().isEmpty())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun withDatabase(name: String, block: suspend (BoardDao) -> Unit) {
        val database = Room.databaseBuilder(context, BoardDatabase::class.java, name)
            .addMigrations(BoardDatabase.AddLearnedWords, BoardDatabase.AddCustomEntryTypes).build()
        try {
            block(database.board())
        } finally {
            database.close()
        }
    }

    @Test fun learnedWordsNeedRepeatedUseAndPersistSeparatelyFromHistory() = runBlocking {
        val name = "test_learning.db"
        context.deleteDatabase(name)
        val phrase = Phrase("draft:learning", "我想听戏", "我想听戏", "other")
        val transitions = setOf(WordTransition("我想", "听戏"))
        try {
            withDatabase(name) { dao ->
                dao.recordExpression(phrase, transitions)
                assertTrue(dao.learnedWords().first().isEmpty())
                dao.recordExpression(phrase, transitions)
            }
            withDatabase(name) { dao ->
                assertEquals("听戏", dao.learnedWords().first().single().word)
                assertEquals(2, dao.learnedWords().first().single().uses)
                assertEquals("我想听戏", dao.recent().first().single().content.label)
                dao.clearRecent()
                assertTrue(dao.recent().first().isEmpty())
                assertEquals(2, dao.learnedWords().first().single().uses)
                dao.clearLearnedWords()
                assertTrue(dao.learnedWords().first().isEmpty())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun customOrderSurvivesAnEditAndDatabaseReopen() = runBlocking {
        val name = "test_custom_order.db"
        context.deleteDatabase(name)
        val glasses = Phrase("custom:glasses", "眼镜", "眼镜", "other")
        val keys = Phrase("custom:keys", "钥匙", "钥匙", "other")
        val phrase = Phrase("custom:phrase", "请帮我翻身", "请帮我翻身", "care")
        try {
            withDatabase(name) { dao ->
                dao.saveCustom(CustomPhrase(glasses.id, StoredPhrase.from(glasses), null, 1L, CustomEntryType.WORD))
                dao.saveCustom(CustomPhrase(keys.id, StoredPhrase.from(keys), null, 2L, CustomEntryType.WORD))
                dao.saveCustom(CustomPhrase(phrase.id, StoredPhrase.from(phrase), null, 3L))
                dao.moveCustom(keys.id, -1)
                val entry = requireNotNull(dao.customById(glasses.id))
                dao.saveCustom(entry.copy(content = entry.content.copy(label = "老花镜"), updatedAt = 4L))
            }
            withDatabase(name) { dao ->
                val entries = dao.custom().first().groupBy { it.type }
                assertEquals(listOf("钥匙", "老花镜"), entries.getValue(CustomEntryType.WORD).map { it.content.label })
                assertEquals(phrase.label, entries.getValue(CustomEntryType.PHRASE).single().content.label)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationKeepsPriorHistoryAndSavedPhrases() = runBlocking {
        val name = "test_board_migration.db"
        context.deleteDatabase(name)
        val phrase = Phrase("test:upgrade", "请把窗打开", "请把窗打开。", "other")
        try {
            withDatabase(name) { dao ->
                dao.changeSaved(phrase, pinned = true)
                dao.record(phrase)
                dao.putCustom(CustomPhrase(phrase.id, StoredPhrase.from(phrase), null, 100L))
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { legacy ->
                    legacy.execSQL("DROP TABLE learned_words")
                    legacy.execSQL("ALTER TABLE custom_phrases RENAME TO custom_phrases_v3")
                    legacy.execSQL(
                        "CREATE TABLE custom_phrases (id TEXT NOT NULL PRIMARY KEY, photo TEXT, " +
                            "updatedAt INTEGER NOT NULL, label TEXT NOT NULL, speech TEXT NOT NULL, " +
                            "categoryId TEXT NOT NULL, icon TEXT, action TEXT, notice TEXT)"
                    )
                    legacy.execSQL(
                        "INSERT INTO custom_phrases SELECT id, photo, updatedAt, label, speech, " +
                            "categoryId, icon, action, notice FROM custom_phrases_v3"
                    )
                    legacy.execSQL("DROP TABLE custom_phrases_v3")
                    legacy.version = 1
                }
            withDatabase(name) { dao ->
                assertTrue(dao.saved().first().single().pinned)
                assertEquals(phrase.speech, dao.recent().first().single().content.speech)
                assertTrue(dao.learnedWords().first().isEmpty())
                assertEquals(phrase.label, dao.custom().first().single().content.label)
                assertEquals(-100L, dao.custom().first().single().position)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun displayAndSpeechSettingsRemainReadable() = runBlocking {
        val store = PreferencesStore(context)
        val previous = store.values.first()
        try {
            store.set(PreferencesStore.Font, 3)
            store.set(PreferencesStore.Contrast, true)
            store.set(PreferencesStore.Images, false)
            store.set(PreferencesStore.Speed, 0.75f)
            val reloaded = PreferencesStore(context).values.first()
            assertEquals(3, reloaded.font)
            assertTrue(reloaded.highContrast)
            assertFalse(reloaded.showImages)
            assertEquals(0.75f, reloaded.speed, 0f)
        } finally {
            store.set(PreferencesStore.Font, previous.font)
            store.set(PreferencesStore.Contrast, previous.highContrast)
            store.set(PreferencesStore.Images, previous.showImages)
            store.set(PreferencesStore.Speed, previous.speed)
        }
    }
}
