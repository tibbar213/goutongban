package cn.goutongban.aac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.Page
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.data.CustomPhrase
import cn.goutongban.aac.model.Draft

@Composable
internal fun PhraseManagementScreen(type: CustomEntryType, model: BoardModel) {
    val entries by model.customEntries.collectAsStateWithLifecycle()
    val matchingEntries = entries.filter { it.type == type }
    val busy by model.customEntryBusy.collectAsStateWithLifecycle()
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }

    PageList {
        item {
            val title = if (type == CustomEntryType.WORD) "添加常用词" else "添加常用句"
            ActionButton(title, Modifier.fillMaxWidth(), primary = true, enabled = !busy) {
                model.openEntryEditor(type)
            }
        }
        managedEntries(matchingEntries, busy, model) { removeId = it }
    }

    entries.firstOrNull { it.id == removeId }?.let { entry ->
        BoardDialog("删除这条内容？", { removeId = null }) {
            BodyText(entry.content.label)
            ActionPair("取消", "删除", { removeId = null }, {
                model.removeCustomEntry(entry.id)
                removeId = null
            }, secondEnabled = !busy)
        }
    }
}

private fun LazyListScope.managedEntries(
    entries: List<CustomPhrase>,
    busy: Boolean,
    model: BoardModel,
    remove: (String) -> Unit
) {
    if (entries.isEmpty()) return
    itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
        Panel {
            BodyText(entry.content.label, strong = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("编辑", Modifier.weight(1f), enabled = !busy) {
                    model.openEntryEditor(entry.type, entry.id)
                }
                ActionButton("删除", Modifier.weight(1f), enabled = !busy) { remove(entry.id) }
            }
            if (entries.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("上移", Modifier.weight(1f), enabled = !busy && index > 0) {
                        model.moveCustomEntry(entry.id, -1)
                    }
                    ActionButton("下移", Modifier.weight(1f), enabled = !busy && index < entries.lastIndex) {
                        model.moveCustomEntry(entry.id, 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhraseEditorScreen(id: String, type: CustomEntryType, model: BoardModel) {
    val entries by model.customEntries.collectAsStateWithLifecycle()
    val busy by model.customEntryBusy.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == id }
    if (id.isNotEmpty() && entry == null) return

    val focus = LocalFocusManager.current
    var text by rememberSaveable(id) {
        mutableStateOf(if (entry?.type == CustomEntryType.WORD) entry.content.label else entry?.content?.speech.orEmpty())
    }
    val trimmed = text.trim()
    val wordTooLong = type == CustomEntryType.WORD && trimmed.isNotEmpty() && !Draft().canAdd(trimmed)

    PageList {
        item {
            val phrase = type == CustomEntryType.PHRASE
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(),
                label = { Text(if (phrase) "整句内容" else "词语内容") },
                placeholder = { Text(if (phrase) "请把我的老花镜拿过来" else "小王、老花镜等，每次添加一项") },
                supportingText = {
                    Text(when {
                        wordTooLong -> "内容太长，请缩短词语。"
                        phrase -> "在「常用」点一下就朗读。"
                        else -> "在「沟通 → 我的词语」选用，加入当前句子。"
                    })
                },
                isError = wordTooLong,
                enabled = !busy,
                minLines = if (phrase) 2 else 1,
                maxLines = 4
            )
        }
        item {
            ActionButton("保存", Modifier.fillMaxWidth(), primary = true, enabled = !busy && trimmed.isNotEmpty() && !wordTooLong) {
                focus.clearFocus()
                model.saveCustomEntry(entry, type, text)
            }
        }
    }
}
