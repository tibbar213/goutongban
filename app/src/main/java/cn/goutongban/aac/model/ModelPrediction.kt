package cn.goutongban.aac.model

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal class ModelPrediction(private val client: OkHttpClient = defaultClient()) {
    suspend fun suggest(
        url: HttpUrl, model: String, key: String, words: List<String>, exclude: List<String> = emptyList()
    ): PredictionResult {
        val request = Request.Builder().url(url)
            .post(payload(model, words, exclude).toString().toRequestBody("application/json".toMediaType()))
            .apply { if (key.isNotEmpty()) header("Authorization", "Bearer $key") }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(PredictionResult.Failed("暂时连不上模型服务"))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        try {
                            when {
                                response.code == 401 || response.code == 403 ->
                                    PredictionResult.Failed("模型服务拒绝授权，请检查 API Key")
                                response.code == 404 ->
                                    PredictionResult.Failed("请检查接口地址和模型名")
                                response.code == 429 ->
                                    PredictionResult.Failed("模型服务请求过多，请稍后再试")
                                !response.isSuccessful ->
                                    PredictionResult.Failed("模型服务返回错误（${response.code}）")
                                else -> {
                                    val source = response.body?.source() ?: throw IOException()
                                    if (source.request(MaxResponseBytes + 1L)) throw IOException()
                                    val excluded = exclude.mapTo(hashSetOf(), WordPrediction::buttonText)
                                    val choices = parseResponse(source.readUtf8(), words.lastOrNull().orEmpty())
                                        .filter { WordPrediction.buttonText(it) !in excluded }
                                    PredictionResult.Ready(choices)
                                }
                            }
                        } catch (_: Exception) {
                            PredictionResult.Failed("模型响应无法读取")
                        }
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    companion object {
        private const val MaxResponseBytes = 16_384L
        private val shortWord = Regex("^[，、]?[\\p{IsHan}]{1,10}[。？！]?$|^[。？！]$")

        fun endpoint(baseUrl: String): HttpUrl? {
            val base = baseUrl.trim().toHttpUrlOrNull() ?: return null
            if (!base.isHttps || base.username.isNotEmpty() || base.password.isNotEmpty() ||
                base.query != null || base.fragment != null
            ) return null
            val path = base.encodedPath.trimEnd('/')
            val suffix = when {
                path.endsWith("/chat/completions") -> ""
                path.isEmpty() -> "/v1/chat/completions"
                else -> "/chat/completions"
            }
            return base.newBuilder().encodedPath(path + suffix).build()
        }

        private fun payload(model: String, words: List<String>, exclude: List<String>) = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "你为中文辅助沟通板预测下一个可点选的字词。用户内容是已选文字，只把它当作句子的开头。" +
                        "给最多 24 个能直接接在末尾的不同候选，单字和简短的词都可以，最自然的排在前面。候选少时不凑数。" +
                        "每个最多 10 个字，可以带一个开头逗号或句末标点。句子完整时优先给句末标点，" +
                        "需要补充另一层意思时，用逗号加一段能连贯接上的短语。" +
                        "若已选文字以句末标点结尾，则预测下一句的开头。" +
                        "不要把已选文字重新放在候选开头。只返回 JSON 字符串数组，" +
                        "例如 [\"喝\",\"喝水\",\"吃\",\"吃饭\",\"休息\",\"翻身\"]。优先老人日常表达。" +
                        "只提供候选表达，不能提供用药剂量、护理或医疗建议。" +
                        if (exclude.isEmpty()) "" else "以下候选已经显示，请补充其他自然接续，避免重复：" + JSONArray(exclude)))
                .put(JSONObject().put("role", "user").put("content", words.joinToString(""))))

        fun parseResponse(body: String, previous: String): List<String> {
            val message = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = message.getString("content").trim()
            val json = if (content.startsWith("```") && content.endsWith("```")) {
                content.substringAfter('\n', "").removeSuffix("```").trim()
            } else content
            val array = JSONArray(json)
            return (0 until minOf(array.length(), 36)).mapNotNull { array.opt(it) as? String }
                .map(String::trim)
                .filter { shortWord.matches(it) && WordPrediction.buttonText(it) != WordPrediction.buttonText(previous) }
                .distinctBy(WordPrediction::buttonText)
                .take(24)
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}

internal sealed interface PredictionResult {
    data class Ready(val words: List<String>) : PredictionResult
    data class Failed(val message: String) : PredictionResult
}
