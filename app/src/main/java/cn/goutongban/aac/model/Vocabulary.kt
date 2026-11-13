package cn.goutongban.aac.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class PhraseAction { PAIN, MORE, ASSIST }
enum class Notice { URGENT, SWALLOW }

data class Phrase(
    val id: String,
    val label: String,
    val speech: String,
    val categoryId: String,
    val icon: String? = null,
    val keywords: String = "",
    val action: PhraseAction? = null,
    val notice: Notice? = null,
    val photo: String? = null
) {
    fun matches(query: String): Boolean =
        query.isBlank() || listOf(label, speech, keywords).any { it.contains(query, ignoreCase = true) }
}

data class Section(val id: String, val label: String, val phrases: List<Phrase>)
data class Category(val id: String, val label: String, val icon: String, val sections: List<Section>) {
    val phrases = sections.flatMap { it.phrases }
}

class Vocabulary(val categories: List<Category>) {
    val phrases = categories.flatMap { it.phrases }
    val byId = phrases.associateBy { it.id }
    val byCategory = categories.associateBy { it.id }

    init {
        require(phrases.size == byId.size) { "预置词库的短语编号必须唯一" }
        require(categories.size == byCategory.size) { "预置词库的分类编号必须唯一" }
    }

    companion object {
        fun parse(json: String): Vocabulary = Vocabulary(JSONArray(json).objects { category ->
            val categoryId = category.getString("id")
            Category(
                categoryId, category.getString("label"), category.getString("icon"),
                category.getJSONArray("sections").objects { section ->
                    Section(
                        section.getString("id"), section.getString("label"),
                        section.getJSONArray("phrases").objects { entry ->
                            Phrase(
                                id = entry.getString("id"),
                                label = entry.getString("label"),
                                speech = entry.getString("speech"),
                                categoryId = categoryId,
                                icon = entry.optString("icon").ifBlank { null },
                                keywords = entry.optString("keywords"),
                                action = entry.optString("action").ifBlank { null }?.let {
                                    PhraseAction.valueOf(it.uppercase(Locale.ROOT))
                                },
                                notice = entry.optString("notice").ifBlank { null }?.let {
                                    Notice.valueOf(it.uppercase(Locale.ROOT))
                                }
                            )
                        }
                    )
                }
            )
        })

        private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
            List(length()) { transform(getJSONObject(it)) }
    }
}
