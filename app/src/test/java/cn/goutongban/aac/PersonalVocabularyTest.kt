package cn.goutongban.aac

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.data.PreferencesStore
import cn.goutongban.aac.model.Draft
import cn.goutongban.aac.model.ModelPrediction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PersonalVocabularyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun await(condition: () -> Boolean) = ui.waitUntil(8_000) {
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        ui.waitForIdle()
        condition()
    }

    @Test fun addingAndRemovingAWordRefreshesTheCurrentAiPredictionImmediately() {
        val name = "林小满"
        val requests = ConcurrentLinkedQueue<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer()
            requireNotNull(chain.request().body).writeTo(buffer)
            val body = buffer.readUtf8()
            requests.add(body)
            val choice = if (body.contains(name)) name else "家人"
            val json = JSONObject().put("choices", JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", JSONArray(listOf(choice)).toString()))
            )).toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(json.toResponseBody()).build()
        }.build()
        val prefs = PreferencesStore(ui.activity.application)
        val previous = runBlocking { prefs.model.first() }
        lateinit var model: BoardModel
        ui.runOnIdle {
            model = ViewModelProvider(ui.activity)[BoardModel::class.java]
            BoardModel::class.java.getDeclaredField("predictor").apply { isAccessible = true }
                .set(model, ModelPrediction(client))
        }
        try {
            ui.runOnIdle { model.saveModel("https://example.invalid/v1", "demo", "", clearKey = true) }
            await { model.modelMessage.value == "模型设置已保存" }
            ui.runOnIdle { model.toggleAiPrediction() }
            await { model.modelSettings.value.enabled }
            ui.runOnIdle { model.addWord(Draft(), "我想找") }
            assertEquals("我想找", model.draft.value.text)
            await { requests.isNotEmpty() }
            assertFalse(requests.last().contains(name))

            ui.runOnIdle {
                model.openEntryEditor(CustomEntryType.WORD)
                model.saveCustomEntry(null, CustomEntryType.WORD, name)
            }
            await { model.candidates.value.candidates.words.firstOrNull() == name }
            val count = requests.size
            ui.runOnIdle { model.loadMorePredictions(model.draft.value) }
            await { requests.size > count && !model.candidates.value.candidates.canLoadMore }
            assertTrue(requests.last().contains("儿子"))
            val entry = model.customEntries.value.first { it.content.label == name }
            ui.runOnIdle { model.removeCustomEntry(entry.id) }
            await {
                !requests.last().contains(name) && name !in model.candidates.value.candidates.words
            }
        } finally {
            runBlocking { prefs.saveModel(previous) }
        }
    }
}
