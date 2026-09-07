package it.palsoftware.pastiera.gif

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps recently sent media independently from user-pinned favourites. */
class GifRecentsManager(context: Context) {
    private val prefs = context.getSharedPreferences("gif_recents", Context.MODE_PRIVATE)

    fun getRecents(): List<KlipyGifResult> {
        val array = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type = runCatching { KlipyMediaType.valueOf(item.optString("type")) }.getOrNull() ?: continue
                add(KlipyGifResult(
                    id = item.optString("id"), title = item.optString("title"), mediaType = type,
                    previewUrl = item.optString("preview"), gifUrl = item.optString("url"),
                    mimeType = item.optString("mime"), shareUrl = item.optString("share"),
                    isLocal = item.optBoolean("local")
                ))
            }
        }
    }

    fun add(item: KlipyGifResult) {
        val items = getRecents().filterNot { it.favoriteKey() == item.favoriteKey() }
        val array = JSONArray()
        (listOf(item) + items).take(80).forEach { result ->
            array.put(JSONObject()
                .put("id", result.id).put("title", result.title).put("type", result.mediaType.name)
                .put("preview", result.previewUrl).put("url", result.gifUrl).put("mime", result.mimeType)
                .put("share", result.shareUrl).put("local", result.isLocal))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}

private fun KlipyGifResult.favoriteKey(): String = "${mediaType.name}:$id"
