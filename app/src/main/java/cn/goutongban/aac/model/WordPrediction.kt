package cn.goutongban.aac.model

class WordPrediction(sequences: List<List<String>>) {
    private val knownEndings = sequences.mapTo(hashSetOf()) { it.joinToString("") }

    private val continuations = buildMap<String, List<String>> {
        val wordsByPrefix = mutableMapOf<String, LinkedHashSet<String>>()
        val firstCharsByPrefix = mutableMapOf<String, LinkedHashSet<String>>()
        for (words in sequences) {
            var prefix = ""
            for (word in words) {
                wordsByPrefix.getOrPut(prefix, ::linkedSetOf).add(word)
                val text = buttonText(word)
                val punctuation = word.removeSuffix(text)
                if (punctuation.isNotEmpty()) {
                    wordsByPrefix.getOrPut(prefix + punctuation, ::linkedSetOf).add(text)
                }
                if (text.length > 1) {
                    firstCharsByPrefix.getOrPut(prefix, ::linkedSetOf).add(punctuation + text.first())
                    text.indices.drop(1).forEach { index ->
                        val partial = prefix + punctuation + text.take(index)
                        wordsByPrefix.getOrPut(partial, ::linkedSetOf).apply {
                            add(text[index].toString())
                            add(text.substring(index))
                        }
                    }
                }
                prefix += word
            }
        }
        wordsByPrefix.forEach { (prefix, words) ->
            put(prefix, (words + firstCharsByPrefix[prefix].orEmpty()).distinctBy(::buttonText))
        }
    }

    fun next(draft: Draft): List<String> = next(draft, null)

    private fun next(draft: Draft, libime: LibimePredictions?): List<String> {
        if (draft.words.isEmpty()) return starters
        if (!draft.canAppend) return emptyList()
        if (draft.complete) return starters.filter(draft::canAdd)

        val words = contextualWords(draft)
        val text = draft.text
        val context = text.substring(text.indexOfLast { it in "，、。？！" } + 1)
        val isKnownEnding = text in knownEndings && text !in starters
        val onlyClauseExtensions = words.isNotEmpty() && words.all { it.startsWith('，') }
        val predictions = if (context.isEmpty() || (isKnownEnding && onlyClauseExtensions)) emptyList()
            else libime?.next(context, allowSuffix = words.isEmpty() && !isKnownEnding).orEmpty()
        val additionalWords = predictions.asSequence().filter { word ->
            word != draft.words.last() && words.none { buttonText(it) == word }
        }.toList()
        val firstCount = if (words.isEmpty()) 8 else 6
        return (words + additionalWords.take(firstCount) + sentenceMarks.drop(1) +
            additionalWords.drop(firstCount)).distinctBy(::buttonText).filter(draft::canAdd)
    }

    internal fun contextualWords(draft: Draft): List<String> = continuations[draft.text].orEmpty()

    internal fun candidates(
        draft: Draft, useModel: Boolean, result: PredictionResult?, learned: List<String> = emptyList(),
        libime: LibimePredictions? = null
    ): WordCandidates {
        if (!draft.canPredict) return WordCandidates(next(draft, libime))
        val personal = learned.asSequence().filter(draft::canAdd).distinctBy(::buttonText).take(4).toList()
        fun merge(words: List<String>) = (personal + words).distinctBy(::buttonText)
        val builtIn = merge(next(draft, libime))
        if (!useModel) return WordCandidates(builtIn)
        if (result == null) return WordCandidates(builtIn, "正在加载联网词…")
        val modelWords = (result as? PredictionResult.Ready)?.words.orEmpty().filter(draft::canAdd)
        return WordCandidates(
            (builtIn + modelWords).distinctBy(::buttonText),
            if (result is PredictionResult.Failed) "联网词暂时无法加载" else null
        )
    }

    companion object {
        private val sentenceMarks = listOf("，", "。", "？", "！")

        val starters = listOf(
            "我", "你", "我们", "那个",
            "什么", "哪里", "谁", "为什么",
            "想", "要", "不要", "帮我",
            "疼", "不舒服", "现在", "几点",
            "这里", "他们", "怎么", "多少",
            "找", "请", "是", "不是",
            "可以", "等一下", "谢谢", "再",
            "我想", "帮", "这", "不",
            "请帮我", "我需要", "我想找", "我不想",
            "我觉得", "我有点", "请把", "请给我",
            "我想问", "什么时候", "能不能", "有没有",
            "这个", "刚才", "我还想", "请再"
        )

        fun parse(text: String) = WordPrediction(text.lineSequence()
            .filter(String::isNotBlank)
            .map { line -> line.split('|').map(String::trim).filter(String::isNotEmpty) }
            .toList())

        fun buttonText(word: String): String =
            word.trimStart('，', '、', '。', '？', '！').ifEmpty { word }

        fun spokenText(word: String): String = word.takeIf { text -> text.any(Char::isLetterOrDigit) }
            ?.let(::buttonText).orEmpty()
    }
}

internal data class WordCandidates(
    val words: List<String> = emptyList(),
    val status: String? = null,
    val canLoadMore: Boolean = false
)

data class Draft(val words: List<String> = emptyList()) {
    val text = words.joinToString("")
    val canAppend get() = text.length < MaxLength
    val complete get() = text.lastOrNull() in listOf('。', '？', '！')
    val canPredict get() = text.isNotEmpty() && canAppend
    fun canAdd(word: String) = word.isNotEmpty() && text.length + word.length <= MaxLength
    fun add(word: String) = if (canAdd(word)) Draft(words + word) else this
    fun undo() = Draft(words.dropLast(1))

    private companion object {
        const val MaxLength = 120
    }
}
