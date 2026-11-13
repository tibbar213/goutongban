package cn.goutongban.aac.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.Page
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.model.Draft
import cn.goutongban.aac.model.WordPrediction

@Composable
internal fun WordScreen(model: BoardModel) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val entries by model.customEntries.collectAsStateWithLifecycle()
    val hasCustomWords = entries.any { it.type == CustomEntryType.WORD }
    val openCustomWords = { model.open(Page.CUSTOM_WORDS) }
    val candidateState by model.candidates.collectAsStateWithLifecycle()
    val candidates = candidateState.candidates.takeIf { candidateState.draft == draft }
    val settings = LocalBoardSettings.current
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val font = listOf(20, 22, 26, 30)[settings.font]
    val cardHeight = listOf(48, 62, 76)[settings.tile].dp

    Column(Modifier.fillMaxSize()) {
        SentenceBox(draft, model::readDraft, model::undoWord, model::clearWords)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton("添加词语", Modifier.weight(1f)) {
                model.openEntryEditor(CustomEntryType.WORD)
            }
            if (hasCustomWords) {
                ActionButton("我的词语", Modifier.weight(1f), enabled = draft.canAppend, click = openCustomWords)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableWidth = maxWidth.value - 24f
            val columns = ((availableWidth + 6f) / (font * 3 * scale + 18f)).toInt().coerceIn(1, 4)
            key(draft) {
                val words = candidates?.words.orEmpty()
                val listState = rememberLazyListState()
                val paginate = draft.canPredict && !draft.complete
                val pageSize = columns * 6
                var visibleCount by remember(columns) { mutableIntStateOf(pageSize) }
                val hasMore = paginate && words.size > visibleCount
                LaunchedEffect(hasMore, visibleCount, columns) {
                    if (hasMore) snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    }.collect { lastVisible ->
                        if (lastVisible >= visibleCount / columns - 2) visibleCount += pageSize
                    }
                }
                LaunchedEffect(candidates?.canLoadMore, words.size, hasMore, columns) {
                    if (candidates?.canLoadMore == true && !hasMore) snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    }.collect { lastVisible ->
                        val rows = (words.size + columns - 1) / columns
                        if (lastVisible >= rows - 2) model.loadMorePredictions(draft)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("board-page"),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grid(if (paginate) words.take(visibleCount) else words, columns, { it }, spacing = 6.dp) { word, modifier ->
                        WordCard(
                            word, modifier.heightIn(min = cardHeight), font,
                            cellWidth = (availableWidth.dp - 6.dp * (columns - 1)) / columns,
                            featured = draft.words.isEmpty() && word in FeaturedStarters
                        ) { model.addWord(draft, word) }
                    }
                    candidates?.status?.let { status ->
                        item(key = "status") {
                            Text(
                                status,
                                Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomWordsScreen(model: BoardModel) {
    val entries by model.customEntries.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val words = entries.filter { it.type == CustomEntryType.WORD }
    PageList { columns ->
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("添加词语", Modifier.weight(1f)) { model.openEntryEditor(CustomEntryType.WORD) }
                if (words.isNotEmpty()) {
                    ActionButton("管理词语", Modifier.weight(1f)) { model.manageEntries(CustomEntryType.WORD) }
                }
            }
        }
        grid(words, columns, { it.id }) { word, modifier ->
            Tile(
                word.content.label, modifier, photo = word.photo?.let(model.photos::resolve),
                enabled = draft.canAdd(word.content.label)
            ) {
                model.addCustomWord(draft, word.id)
            }
        }
    }
}

private val FeaturedStarters = WordPrediction.starters.take(8).toSet()

@Composable
private fun WordCard(
    word: String, modifier: Modifier, font: Int, cellWidth: Dp, featured: Boolean, onClick: () -> Unit
) {
    val settings = LocalBoardSettings.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val label = WordPrediction.buttonText(word)
    val size = if (label.length == 4) {
        with(density) { ((cellWidth - 10.dp) / 4).toSp().value }.coerceIn(17f, font.toFloat())
    } else font.toFloat()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (featured && !settings.highContrast) colors.surfaceVariant else colors.surface,
        contentColor = if (featured) colors.primary else colors.onSurface,
        border = BorderStroke(1.dp, if (settings.highContrast) colors.onSurface else colors.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label, Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                fontSize = size.sp, lineHeight = (size + 4).sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SentenceBox(draft: Draft, read: () -> Unit, undo: () -> Unit, clear: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(draft.text, scroll.maxValue) {
        if (scroll.maxValue in 1 until Int.MAX_VALUE) scroll.scrollTo(scroll.maxValue)
    }
    val colors = MaterialTheme.colorScheme
    val empty = draft.words.isEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .testTag("sentence-box"),
        shape = Shape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column {
            Surface(
                onClick = read,
                enabled = !empty,
                modifier = Modifier.fillMaxWidth().testTag("sentence-bar")
                    .heightIn(min = 52.dp, max = 96.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.surface,
                contentColor = if (empty) colors.onSurfaceVariant else colors.onSurface
            ) {
                Text(
                    draft.text.ifEmpty { "点下方字词" },
                    Modifier.verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 9.dp),
                    fontSize = if (empty) 18.sp else 26.sp,
                    lineHeight = if (empty) 26.sp else 32.sp,
                    fontWeight = if (empty) FontWeight.Normal else FontWeight.Bold
                )
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionButton("读整句", Modifier.weight(1.3f), primary = true, enabled = !empty, click = read)
                ActionButton("撤销", Modifier.weight(1f), enabled = !empty, click = undo)
                ActionButton("重来", Modifier.weight(1f), enabled = !empty, click = clear)
            }
        }
    }
}
