package cn.goutongban.aac.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.model.Phrase

@Composable
internal fun HistoryScreen(model: BoardModel) {
    val history by model.history.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val favoriteIds = favorites.mapTo(hashSetOf()) { it.id }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    PageList {
        if (history.isEmpty()) {
            item { BodyText("朗读过的话会显示在这里。") }
        } else {
            items(history, key = { it.id }) { phrase ->
                val saved = phrase.id in favoriteIds
                SavedExpression(phrase, saved, { model.replayHistory(phrase) }) {
                    model.setFavorite(phrase, !saved)
                }
            }
            item {
                ActionButton("清空历史", Modifier.fillMaxWidth()) { confirmClear = true }
            }
        }
    }
    if (confirmClear) {
        BoardDialog("清空历史？", { confirmClear = false }) {
            BodyText("将删除此页的表达记录。学习记录可在设置里清除。")
            ActionPair("清空", "取消", {
                model.clearHistory()
                confirmClear = false
            }, { confirmClear = false })
        }
    }
}

@Composable
internal fun SavedExpression(phrase: Phrase, saved: Boolean, replay: () -> Unit, toggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = Shape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = replay, modifier = Modifier.weight(1f).heightIn(min = 60.dp), shape = Shape) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.primary)
                    BodyText(phrase.label, Modifier.weight(1f), strong = true)
                }
            }
            IconButton(onClick = toggle, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (saved) "取消收藏：${phrase.label}" else "收藏：${phrase.label}",
                    tint = if (saved) colors.primary else colors.onSurfaceVariant
                )
            }
        }
    }
}
