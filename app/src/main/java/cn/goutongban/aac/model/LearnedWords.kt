package cn.goutongban.aac.model

internal data class WordTransition(val context: String, val word: String)

internal fun Draft.transitions(): Set<WordTransition> = buildSet {
    var prefix = ""
    words.forEachIndexed { index, word ->
        val context = predictionContext(prefix)
        if (context.isNotEmpty() && WordPrediction.spokenText(word).isNotEmpty()) {
            add(WordTransition(context, word))
            if (word.length == 1 && word[0].isLetter()) {
                val letters = words.asSequence().drop(index).take(5)
                    .takeWhile { it.length == 1 && it[0].isLetter() }.toList()
                if (letters.size in 2..4) add(WordTransition(context, letters.joinToString("")))
            }
        }
        prefix += word
    }
}

internal fun predictionContext(text: String): String = text.takeLast(16)
