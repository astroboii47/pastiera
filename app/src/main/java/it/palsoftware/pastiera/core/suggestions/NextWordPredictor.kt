package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import org.json.JSONObject
import java.util.Locale

class NextWordPredictor(
    context: Context,
    private val locale: Locale = Locale.getDefault(),
    private val isValidWord: ((String) -> Boolean)? = null
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NEXT_WORD_HISTORY = "next_word_history"
        private const val MAX_HEAD_WORDS = 200
        private const val MAX_FOLLOWERS_PER_HEAD = 12
        private val normalizeRegex = Regex("[^\\p{L}']")
    }

    fun recordTransition(previousWord: String, nextWord: String) {
        val previous = normalize(previousWord) ?: return
        val next = normalize(nextWord) ?: return
        if (previous == next) return
        if (!isPlausibleWord(previous) || !isPlausibleWord(next)) return

        val history = loadHistory()
        val followers = history.optJSONObject(previous) ?: JSONObject()
        val nextCount = followers.optInt(next, 0) + 1
        followers.put(next, nextCount)

        trimFollowers(followers)
        history.put(previous, followers)
        trimHeads(history)
        saveHistory(history)
    }

    fun predictNextWords(previousWord: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val previous = normalize(previousWord) ?: return emptyList()
        val followers = loadHistory().optJSONObject(previous) ?: return emptyList()
        return followers.keys().asSequence()
            .map { candidate -> candidate to followers.optInt(candidate, 0) }
            .filter { (candidate, _) -> isPlausibleWord(candidate) }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun clear() {
        prefs.edit().remove(KEY_NEXT_WORD_HISTORY).apply()
    }

    private fun loadHistory(): JSONObject {
        val raw = prefs.getString(KEY_NEXT_WORD_HISTORY, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun saveHistory(history: JSONObject) {
        prefs.edit().putString(KEY_NEXT_WORD_HISTORY, history.toString()).apply()
    }

    private fun normalize(word: String): String? {
        val normalized = word
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('ʼ', '\'')
            .lowercase(locale)
            .replace(normalizeRegex, "")
            .trim('\'')
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun isPlausibleWord(word: String): Boolean {
        if (word.length < 2) return false
        return isValidWord?.invoke(word) ?: true
    }

    private fun trimFollowers(followers: JSONObject) {
        val ranked = followers.keys().asSequence()
            .map { key -> key to followers.optInt(key, 0) }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(MAX_FOLLOWERS_PER_HEAD)
            .toList()

        val trimmed = JSONObject()
        ranked.forEach { (key, value) -> trimmed.put(key, value) }

        followers.keys().asSequence().toList().forEach { followers.remove(it) }
        trimmed.keys().asSequence().forEach { key -> followers.put(key, trimmed.getInt(key)) }
    }

    private fun trimHeads(history: JSONObject) {
        val heads = history.keys().asSequence().toList()
        if (heads.size <= MAX_HEAD_WORDS) return

        val rankedHeads = heads.mapNotNull { head ->
            val followers = history.optJSONObject(head) ?: return@mapNotNull null
            val score = followers.keys().asSequence().sumOf { followers.optInt(it, 0) }
            head to score
        }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(MAX_HEAD_WORDS)
            .map { it.first }
            .toSet()

        heads.filter { it !in rankedHeads }.forEach { history.remove(it) }
    }
}
