package com.pulse.player.lyrics

data class LrcLine(val time: Long, val text: String)

object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /** @return satırlar ve senkron olup olmadığı */
    fun parse(text: String): Pair<List<LrcLine>, Boolean> {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LrcLine>()
        for (raw in text.lines()) {
            val matches = stamp.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val body = raw.substring(matches.last().range.last + 1).replace(wordTag, "").trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                out += LrcLine((min * 60_000 + sec * 1000 + frac - offset).coerceAtLeast(0), body)
            }
        }
        if (out.isNotEmpty()) {
            out.sortBy { it.time }
            return out to true
        }
        val plain = text.lines().map { it.trim() }.filter { !it.startsWith("[") || !it.endsWith("]") }
            .dropWhile { it.isEmpty() }
        return plain.map { LrcLine(-1, it) } to false
    }
}
