package cn.goutongban.aac

import cn.goutongban.aac.model.Draft
import cn.goutongban.aac.model.LibimePredictions
import cn.goutongban.aac.model.ModelPrediction
import cn.goutongban.aac.model.PredictionResult
import cn.goutongban.aac.model.WordPrediction
import cn.goutongban.aac.model.WordTransition
import cn.goutongban.aac.model.transitions
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PredictionTest {
    @Test fun builtInWordsFollowTheEntirePrefix() {
        val book = RuntimeEnvironment.getApplication().assets.open("word_sequences.txt")
            .bufferedReader().use { WordPrediction.parse(it.readText()) }
        assertEquals(listOf("我", "你", "我们", "那个"), book.next(Draft()).take(4))
        assertEquals("想", book.next(Draft().add("我")).first())
        assertEquals(listOf("喝水", "喝"), book.next(Draft().add("我").add("想")).take(2))
        assertEquals(book.next(Draft().add("我").add("想")), book.next(Draft().add("我想")))
        assertEquals("水", book.next(Draft().add("我").add("想").add("喝")).first())
        assertEquals(listOf("向左边", "向右边", "向"), book.next(Draft().add("我想").add("翻身")).take(3))
        val draft = Draft().add("我").add("想").add("喝水").add("，请先").add("问医生")
        assertEquals("我想喝水，请先问医生", draft.text)
        assertEquals("我想喝水，请先", draft.undo().text)
    }

    @Test fun everyStarterHasLocalContinuationsAndModelAddsToBuiltInFollowups() {
        val book = RuntimeEnvironment.getApplication().assets.open("word_sequences.txt")
            .bufferedReader().use { WordPrediction.parse(it.readText()) }
        val start = Draft()
        val ready = PredictionResult.Ready(listOf("喝水", "去医院", "？"))
        val failed = PredictionResult.Failed("网络故障")
        val starters = book.next(start)
        assertEquals(listOf(
            "我", "你", "我们", "那个",
            "什么", "哪里", "谁", "为什么",
            "想", "要", "不要", "帮我",
            "疼", "不舒服", "现在", "几点",
            "这里", "他们", "怎么", "多少",
            "找", "请", "是", "不是",
            "可以", "等一下", "谢谢", "再",
            "我想", "帮", "这", "不"
        ), starters.take(32))
        assertEquals(48, starters.size)
        assertEquals(starters, book.candidates(start, true, ready).words)
        starters.forEach { word ->
            assertTrue(word, book.contextualWords(start.add(word)).count { it.any(Char::isLetter) } >= 3)
        }
        val next = start.add("我").add("想")
        assertEquals(book.next(next), book.candidates(next, false, ready).words)
        assertEquals(book.next(next), book.candidates(next, true, null).words)
        assertEquals(
            (book.next(next) + ready.words).distinctBy(WordPrediction::buttonText),
            book.candidates(next, true, ready).words
        )
        assertEquals(book.next(next), book.candidates(next, true, failed).words)
        assertEquals(book.next(next), book.candidates(next, true, PredictionResult.Ready(emptyList())).words)
        assertTrue(book.next(next.add("。")).contains("再"))
        assertEquals(WordPrediction.starters, book.candidates(next.add("。"), true, null).words)
    }

    @Test fun offlineInputMethodExpandsContextWithoutAddingUnrelatedCharactersToKnownEndings() {
        val assets = RuntimeEnvironment.getApplication().assets
        val book = assets.open("word_sequences.txt").bufferedReader().use { WordPrediction.parse(it.readText()) }
        val libime = assets.open("third-party/libime-next-words.tsv").use { LibimePredictions(it.readBytes()) }
        val draft = Draft().add("我").add("想")
        val afterWant = book.candidates(draft, false, null, libime = libime).words
        assertTrue("去" in afterWant)
        val afterGo = book.candidates(draft.add("去"), true, PredictionResult.Failed("网络故障"), libime = libime).words
        assertTrue("哪里" in afterGo)
        val afterWater = book.candidates(draft.add("喝水"), false, null, libime = libime).words
        assertEquals(listOf("，请先", "，请帮我", "，只要", "，请", "，只", "。", "？", "！"), afterWater)
    }

    @Test fun endpointAcceptsBaseOrFullPathAndRequiresHttps() {
        assertEquals("https://example.org/v1/chat/completions", ModelPrediction.endpoint(" https://example.org/ ").toString())
        assertEquals("https://example.org/openai/v1/chat/completions",
            ModelPrediction.endpoint("https://example.org/openai/v1/").toString())
        assertEquals("https://example.org/v1/chat/completions",
            ModelPrediction.endpoint("https://example.org/v1/chat/completions").toString())
        assertNull(ModelPrediction.endpoint("http://example.org/v1"))
        assertNull(ModelPrediction.endpoint("https://user:secret@example.org/v1"))
        assertNull(ModelPrediction.endpoint("https://example.org/v1?key=secret"))
    }

    @Test fun personalWordsAppearInContextWithOrWithoutModel() {
        val book = RuntimeEnvironment.getApplication().assets.open("word_sequences.txt")
            .bufferedReader().use { WordPrediction.parse(it.readText()) }
        val draft = Draft().add("我").add("想").add("听").add("戏")
        assertTrue(WordTransition("我想", "听戏") in draft.transitions())
        val prefix = Draft().add("我想")
        val learned = listOf("听戏", "喝水")
        assertEquals(learned, book.candidates(prefix, false, null, learned).words.take(2))
        assertEquals(book.candidates(prefix, false, null, learned).words, book.candidates(prefix, true, null, learned).words)
        val model = PredictionResult.Ready(listOf("喝水", "出门"))
        val assisted = book.candidates(prefix, true, model, learned).words
        assertEquals(learned, assisted.take(2))
        assertEquals("出门", assisted.last())
        assertTrue(assisted.containsAll(book.next(prefix)))
        assertEquals(WordPrediction.starters, book.candidates(Draft(), true, model, learned).words)
    }

    @Test fun compatibleApiSendsOnlyCurrentWordsAndReturnsSanitizedCandidates() = runBlocking {
        MockWebServer().use { server ->
            val choices = JSONArray(listOf("喝水", "休息", "，休息", "我想", "A123", "开灯。马上", "？", 123))
            val content = "```json\n$choices\n```"
            server.enqueue(MockResponse().setBody(JSONObject().put("choices", JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", content))
            )).toString()))
            val result = ModelPrediction().suggest(
                server.url("/v1/chat/completions"), "demo", "test-key", listOf("我想"), listOf("休息")
            )
            assertEquals(PredictionResult.Ready(listOf("喝水", "？")), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer test-key", request.getHeader("Authorization"))
            val raw = request.body.readUtf8()
            val json = JSONObject(raw)
            assertEquals("demo", json.getString("model"))
            assertFalse(json.getBoolean("stream"))
            val messages = json.getJSONArray("messages")
            assertEquals(2, messages.length())
            assertEquals("我想", messages.getJSONObject(1).getString("content"))
            assertFalse(raw.contains("test-key"))
        }
    }

    @Test fun noCredentialsFollowARedirectAndErrorMessagesOmitServerContent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/other"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("must never show this"))
            val predictor = ModelPrediction()
            val url = server.url("/v1/chat/completions")
            assertEquals(PredictionResult.Failed("模型服务返回错误（302）"),
                predictor.suggest(url, "demo", "test-key", listOf("我想")))
            assertEquals(PredictionResult.Failed("模型服务拒绝授权，请检查 API Key"),
                predictor.suggest(url, "demo", "test-key", listOf("我想")))
            assertEquals(2, server.requestCount)
            assertEquals("/v1/chat/completions", server.takeRequest().path)
            assertEquals("/v1/chat/completions", server.takeRequest().path)
        }
    }
}
