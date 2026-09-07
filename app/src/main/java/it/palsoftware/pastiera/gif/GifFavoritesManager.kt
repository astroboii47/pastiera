package it.palsoftware.pastiera.gif

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GifFavoritesManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavorites(): List<KlipyGifResult> {
        val raw = prefs.getString(KEY_ITEMS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)?.toResult() ?: continue
                add(item)
            }
        }
    }

    fun isFavorite(item: KlipyGifResult): Boolean {
        val key = favoriteKey(item)
        return getFavorites().any { favoriteKey(it) == key }
    }

    fun toggleFavorite(item: KlipyGifResult): Boolean {
        val key = favoriteKey(item)
        val existing = getFavorites().filterNot { favoriteKey(it) == key }.toMutableList()
        val isAdding = existing.size == getFavorites().size
        if (isAdding) {
            existing.add(0, item)
        }
        saveFavorites(existing.take(MAX_FAVORITES))
        return isAdding
    }

    private fun saveFavorites(items: List<KlipyGifResult>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun favoriteKey(item: KlipyGifResult): String = "${item.mediaType.name}:${item.id}"

    private fun KlipyGifResult.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("mediaType", mediaType.name)
            .put("previewUrl", previewUrl)
            .put("gifUrl", gifUrl)
            .put("mimeType", mimeType)
            .put("shareUrl", shareUrl)

    private fun JSONObject.toResult(): KlipyGifResult? {
        val mediaType = runCatching {
            KlipyMediaType.valueOf(optString("mediaType"))
        }.getOrNull() ?: return null
        return KlipyGifResult(
            id = optString("id"),
            title = optString("title"),
            mediaType = mediaType,
            previewUrl = optString("previewUrl"),
            gifUrl = optString("gifUrl"),
            mimeType = optString("mimeType"),
            shareUrl = optString("shareUrl"),
            isLocal = false
        )
    }

    companion object {
        private const val PREFS_NAME = "gif_favorites"
        private const val KEY_ITEMS = "items"
        private const val MAX_FAVORITES = 200
    }
}
