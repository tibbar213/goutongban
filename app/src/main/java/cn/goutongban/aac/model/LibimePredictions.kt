package cn.goutongban.aac.model

internal class LibimePredictions(private val rows: ByteArray) {
    private val rowOffsets = IntArray(rows.count { it == Newline }).apply {
        var row = 1
        for (index in rows.indices) {
            if (rows[index] == Newline && row < size) this[row++] = index + 1
        }
    }

    fun next(context: String, allowSuffix: Boolean): List<String> {
        val suffix = context.takeLast(MaxContextLength)
        if (!allowSuffix) return if (suffix == context) lookup(context) else emptyList()
        val shortest = suffix.length.coerceAtMost(2)
        for (start in 0..suffix.length - shortest) {
            val words = lookup(suffix.substring(start))
            if (words.isNotEmpty()) return words
        }
        return emptyList()
    }

    private fun lookup(context: String): List<String> {
        var low = 0
        var high = rowOffsets.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val start = rowOffsets[middle]
            var separator = start
            while (rows[separator] != Tab) separator++
            val key = String(rows, start, separator - start, Charsets.UTF_8)
            val comparison = key.compareTo(context)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> {
                    val end = rowOffsets.getOrElse(middle + 1) { rows.size } - 1
                    return String(rows, separator + 1, end - separator - 1, Charsets.UTF_8).split('\t')
                }
            }
        }
        return emptyList()
    }

    companion object {
        private const val MaxContextLength = 8
        private const val Newline: Byte = 10
        private const val Tab: Byte = 9
    }
}
