package cn.goutongban.aac.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.localPreferences by preferencesDataStore("board_settings")

data class BoardSettings(
    val font: Int = 1,
    val tile: Int = 0,
    val showImages: Boolean = true,
    val highContrast: Boolean = false,
    val fullScreen: Boolean = false,
    val guardTaps: Boolean = true,
    val speakEachWord: Boolean = true,
    val learnWords: Boolean = true,
    val speed: Float = 0.9f,
    val volume: Float = 1f
)

class PreferencesStore(context: Context) {
    private val store = context.applicationContext.localPreferences

    private val data = store.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    val values = data.map { values ->
        BoardSettings(
            font = (values[Font] ?: 1).coerceIn(0, 3),
            tile = (values[Tile] ?: 0).coerceIn(0, 2),
            showImages = values[Images] ?: true,
            highContrast = values[Contrast] ?: false,
            fullScreen = values[FullScreen] ?: false,
            guardTaps = values[GuardTaps] ?: true,
            speakEachWord = values[SpeakEachWord] ?: true,
            learnWords = values[LearnWords] ?: true,
            speed = (values[Speed] ?: 0.9f).coerceIn(0.55f, 1.35f),
            volume = (values[Volume] ?: 1f).coerceIn(0.1f, 1f)
        )
    }

    val model = data.map { values ->
        ModelSettings(
            enabled = values[ModelEnabled] ?: false,
            baseUrl = values[ModelUrl].orEmpty(),
            model = values[ModelName].orEmpty(),
            encryptedKey = values[ModelKey].orEmpty()
        )
    }

    suspend fun saveModel(settings: ModelSettings) {
        store.edit {
            it[ModelEnabled] = settings.enabled
            it[ModelUrl] = settings.baseUrl
            it[ModelName] = settings.model
            it[ModelKey] = settings.encryptedKey
        }
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    companion object {
        val Font = intPreferencesKey("font")
        val Tile = intPreferencesKey("tile")
        val Images = booleanPreferencesKey("show_images")
        val Contrast = booleanPreferencesKey("contrast")
        val FullScreen = booleanPreferencesKey("full_screen")
        val GuardTaps = booleanPreferencesKey("guard_taps")
        val SpeakEachWord = booleanPreferencesKey("speak_each_word")
        val LearnWords = booleanPreferencesKey("learn_words")
        val Speed = floatPreferencesKey("speed")
        val Volume = floatPreferencesKey("volume")
        private val ModelEnabled = booleanPreferencesKey("model_enabled")
        private val ModelUrl = stringPreferencesKey("model_url")
        private val ModelName = stringPreferencesKey("model_name")
        private val ModelKey = stringPreferencesKey("model_key")
    }
}
