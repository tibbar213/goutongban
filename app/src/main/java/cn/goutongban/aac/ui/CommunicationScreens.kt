package cn.goutongban.aac.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.Page
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.model.Phrase

@Composable
internal fun PhraseScreen(model: BoardModel) {
    val library by model.quickPhrases.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val favoriteIds = favorites.mapTo(hashSetOf()) { it.id }
    PageList { columns ->
        item {
            ActionButton("添加常用句", Modifier.fillMaxWidth()) { model.openEntryEditor(CustomEntryType.PHRASE) }
        }
        if (library.custom.isNotEmpty()) item {
            ActionButton("管理常用句", Modifier.fillMaxWidth()) { model.manageEntries(CustomEntryType.PHRASE) }
        }
        val saved = (library.custom + library.saved + favorites).distinctBy(Phrase::id)
        if (saved.isNotEmpty()) {
            item { Heading("我的常用句") }
            saved.forEach { phrase ->
                item(key = "saved:${phrase.id}") {
                    if (phrase.id in favoriteIds) {
                        SavedExpression(phrase, true, { model.replayHistory(phrase) }) {
                            model.setFavorite(phrase, false)
                        }
                    } else {
                        Tile(phrase.label, Modifier.fillMaxWidth(), group = phrase.categoryId,
                            photo = phrase.photo?.let(model.photos::resolve)) { model.choose(phrase) }
                    }
                }
            }
        }
        item {
            ActionButton("疼痛部位", Modifier.fillMaxWidth(), primary = true, click = model::openPain)
        }
        grid(model.vocabulary.categories, columns, { "category:${it.id}" }) { category, tile ->
            Tile(category.label, tile, icon = category.icon, group = category.id, large = true) {
                model.open(Page.CATEGORY, category.id)
            }
        }
    }
}

@Composable
internal fun CategoryScreen(id: String, model: BoardModel) {
    val category = model.vocabulary.byCategory.getValue(id)
    PageList { columns ->
        if (id == "food") item { SwallowNote() }
        if (id == "body") item {
            ActionButton("疼痛部位", Modifier.fillMaxWidth(), primary = true, click = model::openPain)
        }
        phrases(category.sections.first().phrases.take(4), columns, model)
        item { Heading("更多短语") }
        grid(category.sections, columns, { "section:${it.id}" }) { section, tile ->
            Tile(section.label, tile, group = id) { model.open(Page.SECTION, id, section.id) }
        }
    }
}

@Composable
internal fun SectionScreen(id: String, sectionId: String, model: BoardModel) {
    val section = model.vocabulary.byCategory.getValue(id).sections.first { it.id == sectionId }
    PageList { columns ->
        if (id == "food") item { SwallowNote() }
        phrases(section.phrases, columns, model)
    }
}

private fun LazyListScope.phrases(values: List<Phrase>, columns: Int, model: BoardModel) {
    grid(values, columns, { "phrase:${it.id}" }) { phrase, tile ->
        Tile(
            phrase.label, tile, icon = phrase.icon, group = phrase.categoryId,
            photo = phrase.photo?.let(model.photos::resolve)
        ) { model.choose(phrase) }
    }
}
