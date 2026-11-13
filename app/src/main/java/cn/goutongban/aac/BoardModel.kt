package cn.goutongban.aac

import android.app.Application
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.goutongban.aac.audio.BoardSpeech
import cn.goutongban.aac.audio.VoiceStatus
import cn.goutongban.aac.data.BoardDatabase
import cn.goutongban.aac.data.BoardSettings
import cn.goutongban.aac.data.CustomEntryType
import cn.goutongban.aac.data.CustomPhrase
import cn.goutongban.aac.data.ModelKeyStore
import cn.goutongban.aac.data.ModelSettings
import cn.goutongban.aac.data.PhotoFiles
import cn.goutongban.aac.data.PreferencesStore
import cn.goutongban.aac.data.StoredPhrase
import cn.goutongban.aac.model.Draft
import cn.goutongban.aac.model.LibimePredictions
import cn.goutongban.aac.model.ModelPrediction
import cn.goutongban.aac.model.Notice
import cn.goutongban.aac.model.PainLevel
import cn.goutongban.aac.model.PainSpot
import cn.goutongban.aac.model.Phrase
import cn.goutongban.aac.model.PhraseAction
import cn.goutongban.aac.model.PredictionResult
import cn.goutongban.aac.model.Vocabulary
import cn.goutongban.aac.model.WordPrediction
import cn.goutongban.aac.model.WordCandidates
import cn.goutongban.aac.model.predictionContext
import cn.goutongban.aac.model.transitions
import cn.goutongban.aac.update.AppUpdates
import cn.goutongban.aac.update.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class Page {
    HOME, CUSTOM_WORDS, PHRASES, PHRASE_MANAGEMENT, PHRASE_EDITOR, CATEGORY, SECTION, PAIN, HISTORY, SETTINGS, MODEL, GUIDE
}

data class Screen(val page: Page, val id: String = "", val section: String = "")
data class PainChoice(val spot: PainSpot? = null, val level: PainLevel? = null)
data class QuickPhrases(val saved: List<Phrase> = emptyList(), val custom: List<Phrase> = emptyList())

internal data class OnlineWords(
    val draft: Draft,
    val config: ModelSettings,
    val words: List<String> = emptyList(),
    val busy: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val vocabulary: List<String> = emptyList()
)
private data class PredictionRequest(
    val draft: Draft, val config: ModelSettings, val vocabulary: List<String>, val page: Int = 0
)
internal data class DraftCandidates(val draft: Draft, val candidates: WordCandidates)

class BoardModel(private val app: Application) : AndroidViewModel(app) {
    private val appUpdates = AppUpdates(app)
    private val mutableUpdate = MutableStateFlow(UpdateState())
    internal val update = mutableUpdate.asStateFlow()
    private var lastUpdateCheck = 0L
    val vocabulary = app.assets.open("phrases.json").bufferedReader().use { Vocabulary.parse(it.readText()) }
    private val predictions = app.assets.open("word_sequences.txt").bufferedReader().use { WordPrediction.parse(it.readText()) }
    private val libime = viewModelScope.async(Dispatchers.IO) {
        app.assets.open("third-party/libime-next-words.tsv").use { LibimePredictions(it.readBytes()) }
    }
    private val database = BoardDatabase.create(app)
    private val dao = database.board()
    private val prefs = PreferencesStore(app)
    private val keyStore = ModelKeyStore()
    private val predictor = ModelPrediction()
    val photos = PhotoFiles(app)
    val speech = BoardSpeech(app)
    val settings = prefs.values.stateIn(viewModelScope, SharingStarted.Eagerly, BoardSettings())
    val modelSettings = prefs.model.stateIn(viewModelScope, SharingStarted.Eagerly, ModelSettings())
    private val expressionWrites = Mutex()
    val history = dao.recent().map { rows ->
        rows.map { it.content.toPhrase(it.id) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val learnedWords = dao.learnedWords().map { rows ->
        rows.groupBy({ it.context }, { it.word })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val customEntries = dao.custom().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val customWordVocabulary = customEntries.map { entries ->
        entries.filter { it.type == CustomEntryType.WORD }.map { it.content.label }.distinct()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val savedPhrases = dao.saved().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites = savedPhrases.map { saved ->
        saved.filter { it.favorite }.map { it.content.toPhrase(it.id) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val quickPhrases = combine(customEntries, savedPhrases) { entries, saved ->
        val custom = entries.filter { it.type == CustomEntryType.PHRASE }.map(CustomPhrase::toPhrase)
        val wordIds = entries.filter { it.type == CustomEntryType.WORD }.mapTo(mutableSetOf()) { it.id }
        val current = vocabulary.byId + custom.associateBy { it.id }
        val shortcuts = saved.filter { it.pinned && it.id !in wordIds }
            .map { current[it.id] ?: it.content.toPhrase(it.id) }
        QuickPhrases(shortcuts, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QuickPhrases())
    private val mutableCustomEntryBusy = MutableStateFlow(false)
    val customEntryBusy = mutableCustomEntryBusy.asStateFlow()

    private val mutablePath = MutableStateFlow(listOf(Screen(Page.HOME)))
    val path = mutablePath.asStateFlow()
    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val notice = mutableNotice.asStateFlow()
    private val mutablePain = MutableStateFlow(PainChoice())
    val pain = mutablePain.asStateFlow()
    private val mutableDraft = MutableStateFlow(Draft())
    val draft = mutableDraft.asStateFlow()
    private val predictionActive = MutableStateFlow(false)
    private val predictionPage = MutableStateFlow(0)
    private val mutableOnline = MutableStateFlow<OnlineWords?>(null)
    private val online = mutableOnline.asStateFlow()
    private val mutableModelBusy = MutableStateFlow(false)
    val modelBusy = mutableModelBusy.asStateFlow()
    private val mutableModelMessage = MutableStateFlow("")
    val modelMessage = mutableModelMessage.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = mutableMessages.asSharedFlow()

    private var lastTapId: String? = null
    private var lastTapTime: Long = 0
    private var lastWord: String? = null
    private var lastWordTime: Long = 0
    private var pendingSpeech: Pair<String, Long>? = null
    private var recordedDrafts = mutableSetOf<String>()
    internal val candidates = combine(draft, modelSettings, online, settings, learnedWords) { draft, model, online, settings, learned ->
        DraftCandidates(draft, resolveCandidates(draft, model, online, settings, learned))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DraftCandidates(Draft(), predictions.candidates(Draft(), false, null)))

    private suspend fun resolveCandidates(
        draft: Draft, config: ModelSettings, online: OnlineWords?, settings: BoardSettings,
        learned: Map<String, List<String>>
    ): WordCandidates {
        val current = online?.takeIf {
            it.draft == draft && it.config == config && it.vocabulary == customWordVocabulary.value
        }
        val result = when {
            current == null || current.busy && current.words.isEmpty() -> null
            current.words.isNotEmpty() -> PredictionResult.Ready(current.words)
            current.error != null -> PredictionResult.Failed(current.error)
            else -> PredictionResult.Ready(current.words)
        }
        val personal = if (settings.learnWords) learned[predictionContext(draft.text)].orEmpty() else emptyList()
        val offline = if (draft.canPredict && !draft.complete) libime.await() else null
        val candidates = predictions.candidates(draft, config.enabled, result, personal, offline)
        val onlineWords = if (config.enabled) current?.words.orEmpty() else emptyList()
        return candidates.copy(
            words = (candidates.words + onlineWords).distinctBy(WordPrediction::buttonText),
            status = when {
                !config.enabled -> candidates.status
                current?.busy == true -> "正在加载联网词…"
                current?.error != null -> current.error
                else -> candidates.status
            },
            canLoadMore = config.enabled && current != null && !current.busy && current.hasMore && current.error == null
        )
    }

    init {
        viewModelScope.launch {
            speech.state.collect { state ->
                if (state.status == VoiceStatus.READY) {
                    val pending = pendingSpeech
                    pendingSpeech = null
                    if (pending != null && SystemClock.elapsedRealtime() - pending.second <= 6_000) {
                        speak(pending.first)
                    }
                }
                if (state.status == VoiceStatus.MISSING || state.status == VoiceStatus.ERROR) pendingSpeech = null
            }
        }
        viewModelScope.launch {
            val inputs = combine(draft, modelSettings, customWordVocabulary) { draft, config, vocabulary ->
                PredictionRequest(draft, config, vocabulary)
            }
            combine(inputs, path, predictionActive, predictionPage) { input, path, active, page ->
                if (active && path.last().page == Page.HOME && input.config.enabled && input.draft.canPredict) {
                    input.copy(page = page)
                } else null
            }.distinctUntilChanged().collectLatest { request ->
                if (request != null) {
                    val (draft, config, vocabulary) = request
                    val previous = mutableOnline.value?.takeIf {
                        it.draft == draft && it.config == config && it.vocabulary == vocabulary
                    }
                    mutableOnline.value = (previous ?: OnlineWords(draft, config, vocabulary = vocabulary))
                        .copy(busy = true, error = null)
                    delay(300)
                    val displayed = previous?.words.orEmpty()
                    val excluded = if (displayed.isEmpty()) emptyList() else {
                        val offline = predictions.candidates(draft, false, null, libime = libime.await()).words
                        (displayed + offline).distinctBy(WordPrediction::buttonText)
                    }
                    val result = requestWords(config, draft.words, excluded, vocabulary)
                    if (draft == mutableDraft.value && config == modelSettings.value &&
                        vocabulary == customWordVocabulary.value
                    ) {
                        val additions = (result as? PredictionResult.Ready)?.words.orEmpty()
                            .filter(draft::canAdd)
                            .filter { word -> displayed.none { WordPrediction.buttonText(it) == WordPrediction.buttonText(word) } }
                        mutableOnline.value = OnlineWords(
                            draft, config, previous?.words.orEmpty() + additions,
                            hasMore = additions.isNotEmpty(),
                            error = (result as? PredictionResult.Failed)?.message,
                            vocabulary = vocabulary
                        )
                    }
                } else {
                    mutableOnline.value = mutableOnline.value?.copy(busy = false)
                }
            }
        }
    }

    fun loadMorePredictions(expected: Draft) {
        val current = mutableOnline.value ?: return
        if (current.draft != expected || expected != draft.value || current.busy || !current.hasMore || current.error != null) return
        mutableOnline.value = current.copy(busy = true)
        predictionPage.value += 1
    }

    fun open(page: Page, id: String = "", section: String = "") {
        val destination = Screen(page, id, section)
        if (mutablePath.value.last() != destination) mutablePath.value = mutablePath.value + destination
    }

    fun tab(page: Page) {
        val home = listOf(Screen(Page.HOME))
        mutablePath.value = if (page == Page.HOME) home else home + Screen(page)
    }

    fun home() = tab(Page.HOME)

    fun back() {
        val path = mutablePath.value
        if (path.size > 1) mutablePath.value = path.dropLast(1)
    }

    fun addWord(expected: Draft, word: String) {
        val displayed = candidates.value
        if (displayed.draft != expected || word !in displayed.candidates.words) return
        appendWord(expected, word)
    }

    fun addCustomWord(expected: Draft, id: String) {
        val entry = customEntries.value.firstOrNull { it.id == id && it.type == CustomEntryType.WORD } ?: return
        if (appendWord(expected, entry.content.label)) back()
    }

    private fun appendWord(expected: Draft, word: String): Boolean {
        if (expected != draft.value || !expected.canAdd(word)) return false
        val now = SystemClock.elapsedRealtime()
        if (settings.value.guardTaps && word == lastWord && now - lastWordTime < 450) return false
        lastWord = word
        lastWordTime = now
        mutableOnline.value = null
        predictionPage.value = 0
        mutableDraft.value = expected.add(word)
        val spoken = WordPrediction.spokenText(word)
        if (spoken.isNotEmpty() && settings.value.speakEachWord) speak(spoken, enqueue = true)
        return true
    }

    fun undoWord() {
        stopSpeech()
        lastWord = null
        mutableOnline.value = null
        predictionPage.value = 0
        mutableDraft.value = draft.value.undo()
        if (draft.value.words.isEmpty()) recordedDrafts = mutableSetOf()
    }

    fun clearWords() {
        stopSpeech()
        lastWord = null
        mutableOnline.value = null
        predictionPage.value = 0
        mutableDraft.value = Draft()
        recordedDrafts = mutableSetOf()
    }

    fun readDraft() {
        val draft = draft.value
        if (draft.words.isEmpty()) return
        val text = draft.text
        val notice = when {
            listOf("胸口疼", "胸痛", "胸闷", "呼吸困难", "喘不上气", "突然一侧没力气")
                .any(text::contains) -> Notice.URGENT
            listOf("喝水", "吞咽", "吃饭").any(text::contains) -> Notice.SWALLOW
            else -> null
        }
        speakExpression(text)
        mutableNotice.value = notice
        val recorded = recordedDrafts
        if (recorded.add(text)) {
            val id = "draft:" + UUID.nameUUIDFromBytes(text.toByteArray(Charsets.UTF_8))
            val phrase = Phrase(id = id, label = text, speech = text, categoryId = "other", notice = notice)
            val transitions = if (settings.value.learnWords) draft.transitions() else emptySet()
            perform("历史保存失败") {
                try {
                    expressionWrites.withLock { dao.recordExpression(phrase, transitions) }
                } catch (error: Exception) {
                    recorded.remove(text)
                    throw error
                }
            }
        }
    }

    fun choose(phrase: Phrase) {
        val now = SystemClock.elapsedRealtime()
        if (settings.value.guardTaps && phrase.id == lastTapId && now - lastTapTime < 700) return
        lastTapId = phrase.id
        lastTapTime = now
        speakExpression(phrase.speech)
        perform("历史保存失败") { expressionWrites.withLock { dao.record(phrase) } }
        if (phrase.notice == Notice.URGENT) mutableNotice.value = phrase.notice
        when (phrase.action) {
            PhraseAction.PAIN -> openPain()
            PhraseAction.MORE -> home()
            else -> Unit
        }
    }

    fun dismissNotice() { mutableNotice.value = null }
    fun openEntryEditor(type: CustomEntryType, id: String = "") {
        open(Page.PHRASE_EDITOR, id, type.name)
    }

    fun manageEntries(type: CustomEntryType) {
        open(Page.PHRASE_MANAGEMENT, section = type.name)
    }

    fun saveCustomEntry(entry: CustomPhrase?, type: CustomEntryType, value: String) {
        val text = value.trim()
        if (text.isEmpty() || (type == CustomEntryType.WORD && !Draft().canAdd(text))) return
        val editorPath = path.value
        updateCustomEntry("保存短语失败") {
            val id = entry?.id ?: "custom:${UUID.randomUUID()}"
            val previous = entry?.toPhrase()
            val label = if (type == CustomEntryType.PHRASE && previous?.speech == text) previous.label else text
            val phrase = previous?.copy(label = label, speech = text)
                ?: Phrase(id = id, label = label, speech = text, categoryId = "other")
            dao.saveCustom(CustomPhrase(id, StoredPhrase.from(phrase), entry?.photo, System.currentTimeMillis(), type))
            if (path.value === editorPath) back()
        }
    }

    fun moveCustomEntry(id: String, offset: Int) = updateCustomEntry("调整顺序失败") {
        dao.moveCustom(id, offset)
    }

    fun removeCustomEntry(id: String) = updateCustomEntry("删除短语失败") {
        val photo = expressionWrites.withLock { dao.removeCustom(id) }
        withContext(Dispatchers.IO) { photos.delete(photo) }
    }

    private fun updateCustomEntry(errorMessage: String, block: suspend () -> Unit) {
        if (!mutableCustomEntryBusy.compareAndSet(expect = false, update = true)) return
        perform(errorMessage) {
            try { block() }
            finally { mutableCustomEntryBusy.value = false }
        }
    }

    fun replayHistory(phrase: Phrase) {
        speakExpression(phrase.speech)
        mutableNotice.value = phrase.notice
    }

    fun setFavorite(phrase: Phrase, favorite: Boolean) {
        perform("收藏保存失败，请重试") {
            expressionWrites.withLock { dao.changeSaved(phrase, favorite = favorite) }
        }
    }

    fun clearHistory() {
        perform("清空历史失败") { expressionWrites.withLock { dao.clearRecent() } }
    }

    fun clearLearning() {
        perform("清除学习记录失败") {
            expressionWrites.withLock { dao.clearLearnedWords() }
        }
    }

    private fun speakExpression(text: String) {
        when (speech.state.value.status) {
            VoiceStatus.MISSING -> mutableMessages.tryEmit("请在设置中安装普通话离线语音")
            VoiceStatus.ERROR -> mutableMessages.tryEmit("语音播报失败，请在设置中重新检测")
            else -> speak(text)
        }
    }

    fun speak(text: String, enqueue: Boolean = false) {
        val config = settings.value
        if (speech.state.value.status == VoiceStatus.CHECKING) {
            pendingSpeech = text to SystemClock.elapsedRealtime()
        } else {
            speech.speak(text, config.speed, config.volume, enqueue)
        }
    }

    fun stopSpeech() {
        pendingSpeech = null
        speech.stop()
    }

    fun openPain() {
        mutablePain.value = PainChoice()
        open(Page.PAIN)
    }

    fun chooseSpot(spot: PainSpot) {
        val level = mutablePain.value.takeIf { it.spot == spot }?.level
        mutablePain.value = PainChoice(spot, level)
        choose(spot.phrase(level))
    }

    fun chooseLevel(level: PainLevel) {
        val spot = mutablePain.value.spot ?: return
        mutablePain.value = PainChoice(spot, level)
        choose(spot.phrase(level))
    }

    fun <T> setting(key: Preferences.Key<T>, value: T) {
        perform("设置保存失败") { prefs.set(key, value) }
    }

    fun toggleAiPrediction() {
        val current = modelSettings.value
        if (!current.enabled && (ModelPrediction.endpoint(current.baseUrl) == null || current.model.isBlank())) {
            open(Page.MODEL)
            return
        }
        updateModel(onFailure = { mutableMessages.tryEmit("AI 联想开关保存失败，请重试") }) {
            prefs.saveModel(current.copy(enabled = !current.enabled))
        }
    }

    fun saveModel(baseUrl: String, model: String, key: String, clearKey: Boolean) {
        val url = baseUrl.trim()
        val name = model.trim()
        val token = key.trim()
        if (ModelPrediction.endpoint(url) == null || name.isEmpty()) {
            mutableModelMessage.value = "请填写 HTTPS 接口地址和模型名"
            return
        }
        if (token.any { it !in '!'..'~' }) {
            mutableModelMessage.value = "API Key 含有无效字符"
            return
        }
        updateModel {
            val encrypted = when {
                token.isNotEmpty() -> withContext(Dispatchers.IO) { keyStore.encrypt(token) }
                clearKey || url != modelSettings.value.baseUrl -> ""
                else -> modelSettings.value.encryptedKey
            }
            val enabled = modelSettings.value.enabled && url == modelSettings.value.baseUrl &&
                name == modelSettings.value.model
            prefs.saveModel(ModelSettings(enabled, url, name, encrypted))
            mutableModelMessage.value = "模型设置已保存"
        }
    }

    fun testModel() {
        val config = modelSettings.value
        updateModel {
            mutableModelMessage.value = "正在用示例「我想」测试…"
            mutableModelMessage.value = when (val result = requestWords(config, listOf("我想"))) {
                is PredictionResult.Ready -> "连接成功 · ${result.words.joinToString("、")}"
                is PredictionResult.Failed -> result.message
            }
        }
    }

    private fun updateModel(
        onFailure: () -> Unit = { mutableModelMessage.value = "无法保存模型设置或 API Key" },
        block: suspend () -> Unit
    ) {
        if (mutableModelBusy.value) return
        mutableModelBusy.value = true
        mutableModelMessage.value = ""
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure() }
            finally { mutableModelBusy.value = false }
        }
    }

    private suspend fun requestWords(
        config: ModelSettings, words: List<String>, exclude: List<String> = emptyList(),
        vocabulary: List<String> = customWordVocabulary.value
    ): PredictionResult {
        val url = ModelPrediction.endpoint(config.baseUrl)
            ?: return PredictionResult.Failed("请先保存 HTTPS 接口地址")
        val token = try {
            withContext(Dispatchers.IO) { keyStore.decrypt(config.encryptedKey) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return PredictionResult.Failed("API Key 无法读取，请重新录入")
        }
        return predictor.suggest(url, config.model, token, words, exclude, vocabulary)
    }

    fun resumed(value: Boolean) {
        predictionActive.value = value
        if (value) checkForUpdate(manual = false)
    }

    fun checkForUpdate(manual: Boolean = true) {
        if (mutableUpdate.value.busy) return
        val now = SystemClock.elapsedRealtime()
        if (!manual && lastUpdateCheck != 0L && now - lastUpdateCheck < 6 * 60 * 60 * 1000L) return
        lastUpdateCheck = now
        mutableUpdate.value = mutableUpdate.value.copy(busy = true, message = "正在检查更新…")
        viewModelScope.launch {
            try {
                val release = appUpdates.latest()
                val previous = mutableUpdate.value
                mutableUpdate.value = UpdateState(
                    release = release,
                    file = previous.file.takeIf { previous.release == release && it?.isFile == true },
                    message = if (release == null) "已是最新版本" else "发现新版本 ${release.version}"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                lastUpdateCheck = 0L
                mutableUpdate.value = mutableUpdate.value.copy(busy = false, message = "暂时无法检查更新，请稍后重试")
            }
        }
    }

    fun downloadUpdate() {
        val current = mutableUpdate.value
        val release = current.release ?: return
        if (current.busy) return
        mutableUpdate.value = current.copy(busy = true, file = null, message = "正在选择下载线路…")
        viewModelScope.launch {
            try {
                val file = appUpdates.download(release) { percent ->
                    mutableUpdate.value = mutableUpdate.value.copy(message = "正在下载 · $percent%")
                }
                mutableUpdate.value = UpdateState(release = release, file = file, message = "已下载，可以安装")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUpdate.value = UpdateState(release = release, message = "下载或校验失败，请重试")
            }
        }
    }

    private fun perform(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableMessages.tryEmit(errorMessage) }
        }
    }

    override fun onCleared() {
        speech.close()
        database.close()
        super.onCleared()
    }
}
