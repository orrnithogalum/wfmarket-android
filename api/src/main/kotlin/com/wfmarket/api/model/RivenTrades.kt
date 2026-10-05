package com.wfmarket.api.model

import kotlinx.serialization.Serializable

/**
 * One line of Warframe's weekly riven trade figures: the platinum paid for one weapon's rivens in
 * trades between players. Veiled rivens have no [compatibility]. [pop] is how often it traded,
 * scaled so the week's most traded riven is 100.
 */
@Serializable
data class RivenTrade(
    val itemType: String = "",
    val compatibility: String? = null,
    val rerolled: Boolean = false,
    val avg: Double = 0.0,
    val stddev: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val pop: Double = 0.0,
    val median: Double = 0.0,
) {
    val rivenClass: RivenClass? get() = RivenClass.ofModName(itemType)
}

/** A week of riven trades on one platform. [publishedMillis] is when Warframe put the week out. */
@Serializable
data class RivenTrades(
    val platform: String,
    val publishedMillis: Long,
    val trades: List<RivenTrade> = emptyList(),
)

/**
 * Turns a JavaScript object literal, which is what Warframe publishes the riven figures as, into
 * JSON: keys get quotes, single-quoted strings become double-quoted, and trailing commas go.
 */
fun relaxedJson(text: String): String {
    val out = StringBuilder(text.length + text.length / 4)
    var i = 0
    fun isWordStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    while (i < text.length) {
        val c = text[i]
        when {
            c == '"' || c == '\'' -> {
                out.append('"')
                i++
                while (i < text.length && text[i] != c) {
                    val ch = text[i]
                    when {
                        ch == '\\' && i + 1 < text.length -> {
                            val next = text[i + 1]
                            // JSON has no \' escape, and needs none.
                            if (next == '\'') out.append('\'') else out.append(ch).append(next)
                            i += 2
                            continue
                        }
                        ch == '"' -> out.append("\\\"")
                        else -> out.append(ch)
                    }
                    i++
                }
                out.append('"')
                i++
            }
            isWordStart(c) -> {
                val start = i
                while (i < text.length && (isWordStart(text[i]) || text[i].isDigit())) i++
                val word = text.substring(start, i)
                var next = i
                while (next < text.length && text[next].isWhitespace()) next++
                if (next < text.length && text[next] == ':') out.append('"').append(word).append('"') else out.append(word)
            }
            c == ',' -> {
                var next = i + 1
                while (next < text.length && text[next].isWhitespace()) next++
                if (next >= text.length || (text[next] != ']' && text[next] != '}')) out.append(c)
                i++
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}
