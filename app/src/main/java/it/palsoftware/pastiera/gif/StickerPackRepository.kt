package it.palsoftware.pastiera.gif

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray

class StickerPackRepository(private val context: Context) {
    data class StickerPack(
        val uri: Uri,
        val name: String
    )

    private val prefs = context.getSharedPreferences("sticker_packs", Context.MODE_PRIVATE)

    fun addPack(uri: Uri) {
        val current = getPackUris().toMutableList()
        val value = uri.toString()
        if (value !in current) {
            current.add(value)
            prefs.edit().putString(KEY_PACK_URIS, JSONArray(current).toString()).apply()
        }
    }

    fun getPacks(): List<StickerPack> {
        return getPackUris().mapNotNull { value ->
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@mapNotNull null
            val root = DocumentFile.fromTreeUri(context, uri) ?: return@mapNotNull null
            StickerPack(uri, root.name ?: "Sticker pack")
        }
    }

    fun getItems(pack: StickerPack, query: String = "", limit: Int = 96): List<KlipyGifResult> {
        val root = DocumentFile.fromTreeUri(context, pack.uri) ?: return emptyList()
        val normalizedQuery = query.trim()
        return root.listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter { file -> isSupportedImage(file.name, file.type) }
            .filter { file ->
                normalizedQuery.isBlank() ||
                    (file.name ?: "").contains(normalizedQuery, ignoreCase = true)
            }
            .sortedBy { it.name ?: "" }
            .take(limit)
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val uri = file.uri.toString()
                val mimeType = mimeTypeForName(name, file.type)
                val mediaType = if (mimeType.equals("image/gif", ignoreCase = true)) {
                    KlipyMediaType.GIF
                } else {
                    KlipyMediaType.LOCAL
                }
                KlipyGifResult(
                    id = uri,
                    title = name.substringBeforeLast('.').replace('_', ' ').replace('-', ' '),
                    mediaType = mediaType,
                    previewUrl = uri,
                    gifUrl = uri,
                    mimeType = mimeType,
                    shareUrl = "",
                    isLocal = true
                )
            }
            .toList()
    }

    private fun getPackUris(): List<String> {
        val raw = prefs.getString(KEY_PACK_URIS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun isSupportedImage(name: String?, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/") == true) return true
        val lowerName = name?.lowercase().orEmpty()
        return lowerName.endsWith(".png") ||
            lowerName.endsWith(".gif") ||
            lowerName.endsWith(".webp") ||
            lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg")
    }

    private fun mimeTypeForName(name: String, fallback: String? = null): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> fallback ?: "image/png"
        }
    }

    companion object {
        private const val KEY_PACK_URIS = "pack_uris"
    }
}
