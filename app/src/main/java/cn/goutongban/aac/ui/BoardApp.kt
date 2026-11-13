package cn.goutongban.aac.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.Page
import cn.goutongban.aac.Screen
import cn.goutongban.aac.R
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.model.Notice

@Composable
fun BoardApp(model: BoardModel, fullScreen: (Boolean) -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val path by model.path.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val update by model.update.collectAsStateWithLifecycle()
    val screen = path.last()
    val root = path.getOrNull(1)?.page ?: Page.HOME
    val activeTab = when (screen.page) {
        Page.HOME, Page.CUSTOM_WORDS -> Page.HOME
        Page.PHRASES, Page.CATEGORY, Page.SECTION, Page.PAIN -> Page.PHRASES
        Page.PHRASE_MANAGEMENT, Page.PHRASE_EDITOR -> if (screen.entryType() == CustomEntryType.WORD) Page.HOME else Page.PHRASES
        Page.HISTORY -> Page.HISTORY
        Page.GUIDE -> root
        Page.SETTINGS, Page.MODEL -> Page.SETTINGS
    }
    val snack = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()

    LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(settings.fullScreen, screen.page) {
        val eligible = screen.page !in setOf(Page.SETTINGS, Page.MODEL, Page.GUIDE, Page.PHRASE_MANAGEMENT, Page.PHRASE_EDITOR)
        fullScreen(settings.fullScreen && eligible)
    }
    BackHandler(enabled = path.size > 1, onBack = model::back)

    BoardTheme(settings) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = { if (!keyboardOpen) BottomNavigation(activeTab, update.release != null, model::tab) },
            snackbarHost = { SnackbarHost(snack) }
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                BoardHeader(
                    title = screen.title(model),
                    back = if (screen.page != activeTab) model::back else null,
                    action = { if (screen.page == Page.HOME) AiPredictionButton(model) }
                )
                Box(Modifier.weight(1f)) {
                    val content: @Composable () -> Unit = {
                        when (screen.page) {
                            Page.HOME -> WordScreen(model)
                            Page.CUSTOM_WORDS -> CustomWordsScreen(model)
                            Page.PHRASES -> PhraseScreen(model)
                            Page.PHRASE_MANAGEMENT -> PhraseManagementScreen(screen.entryType(), model)
                            Page.PHRASE_EDITOR -> PhraseEditorScreen(screen.id, screen.entryType(), model)
                            Page.CATEGORY -> CategoryScreen(screen.id, model)
                            Page.SECTION -> SectionScreen(screen.id, screen.section, model)
                            Page.PAIN -> PainScreen(model)
                            Page.HISTORY -> HistoryScreen(model)
                            Page.SETTINGS -> SettingsScreen(model)
                            Page.MODEL -> ModelSettingsScreen(model)
                            Page.GUIDE -> GuideScreen()
                        }
                    }
                    if (screen.page == Page.PHRASE_EDITOR) content()
                    else holder.SaveableStateProvider("${screen.page}/${screen.id}/${screen.section}", content)
                }
            }
        }
        notice?.let {
            BoardDialog(if (it == Notice.URGENT) "紧急就医提示" else "饮食提醒", model::dismissNotice) {
                if (it == Notice.URGENT) UrgentNote() else SwallowNote()
                ActionButton("关闭", Modifier.fillMaxWidth(), primary = true, click = model::dismissNotice)
            }
        }
    }
}

private fun Screen.title(model: BoardModel): String = when (page) {
    Page.HOME -> "沟通板"
    Page.CUSTOM_WORDS -> "我的词语"
    Page.PHRASES -> "常用句"
    Page.PHRASE_MANAGEMENT -> if (entryType() == CustomEntryType.WORD) "管理常用词" else "管理常用句"
    Page.PHRASE_EDITOR -> {
        val name = if (entryType() == CustomEntryType.WORD) "常用词" else "常用句"
        if (id.isEmpty()) "添加$name" else "编辑$name"
    }
    Page.CATEGORY -> model.vocabulary.byCategory.getValue(id).label
    Page.SECTION -> model.vocabulary.byCategory.getValue(id).sections.first { it.id == section }.label
    Page.PAIN -> "哪里疼？"
    Page.HISTORY -> "历史"
    Page.SETTINGS -> "设置"
    Page.MODEL -> "模型设置"
    Page.GUIDE -> "紧急就医与吞咽指引"
}

private fun Screen.entryType() = CustomEntryType.valueOf(section)

@Composable
private fun BoardHeader(title: String, back: (() -> Unit)?, action: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .padding(start = if (back == null) 16.dp else 4.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (back != null) IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Heading(title, Modifier.weight(1f).padding(vertical = 8.dp))
                action()
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AiPredictionButton(model: BoardModel) {
    val config by model.modelSettings.collectAsStateWithLifecycle()
    val busy by model.modelBusy.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({ model.openEntryEditor(CustomEntryType.WORD) }, Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "添加常用词")
        }
        IconToggleButton(
            checked = config.enabled,
            onCheckedChange = { model.toggleAiPrediction() },
            modifier = Modifier.size(48.dp).testTag("ai-prediction").semantics {
                stateDescription = if (config.enabled) "AI 联想已开启" else "AI 联想已关闭"
            },
            enabled = !busy,
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContainerColor = colors.surfaceVariant,
                checkedContentColor = colors.primary
            )
        ) {
            Icon(painterResource(R.drawable.ic_cloud), contentDescription = "AI 联想")
        }
    }
}

private data class Destination(val page: Page, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination(Page.HOME, "沟通", Icons.Filled.Email),
    Destination(Page.PHRASES, "常用", Icons.AutoMirrored.Filled.List),
    Destination(Page.HISTORY, "历史", Icons.Filled.DateRange),
    Destination(Page.SETTINGS, "设置", Icons.Filled.Settings)
)

@Composable
private fun BottomNavigation(active: Page, updateAvailable: Boolean, navigate: (Page) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider()
        NavigationBar(
            modifier = Modifier.heightIn(min = 60.dp),
            containerColor = colors.surface
        ) {
            destinations.forEach { item ->
                NavigationBarItem(
                    modifier = Modifier.testTag("tab-${item.page.name.lowercase()}"),
                    selected = active == item.page,
                    onClick = { navigate(item.page) },
                    icon = {
                        if (item.page == Page.SETTINGS && updateAvailable) {
                            BadgedBox(badge = { Badge() }) {
                                Icon(item.icon, contentDescription = "有更新", Modifier.size(24.dp))
                            }
                        } else {
                            Icon(item.icon, contentDescription = null, Modifier.size(24.dp))
                        }
                    },
                    label = {
                        val labelSize = if (LocalDensity.current.fontScale >= 1.5f) 13.sp else 14.sp
                        Text(item.label, fontSize = labelSize, fontWeight = FontWeight.Medium, maxLines = 1)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = colors.surfaceVariant,
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
internal fun PageList(content: LazyListScope.(Int) -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val font = LocalBoardSettings.current.font
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth.value - 28f >= (136f + 12f * (font - 1).coerceAtLeast(0)) * 2 * scale + 10f) 2 else 1
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("board-page"),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content(columns) }
    }
}
