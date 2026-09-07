package it.palsoftware.pastiera.emoji

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.TypedValue
import android.util.Log
import android.widget.TextView
import it.palsoftware.pastiera.SettingsManager
import java.io.File

object CustomEmojiFontManager {
    private const val TAG = "CustomEmojiFont"
    private const val DIR_NAME = "custom_emoji_font"
    private const val FONT_FILE_NAME = "emoji-font.ttf"
    private const val SLOTS_DIR_NAME = "slots"

    data class EmojiFontSlot(
        val displayName: String,
        val path: String
    )

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var cachedTypeface: Typeface? = null

    fun importFont(context: Context, uri: Uri): String {
        val appContext = context.applicationContext
        migrateLegacyFontToAppleSlot(appContext)
        val displayName = resolveDisplayName(appContext, uri)
        val outDir = File(File(appContext.filesDir, DIR_NAME), SLOTS_DIR_NAME).apply { mkdirs() }
        val outFile = File(outDir, "${slotFileName(displayName)}.ttf")

        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected font" }
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Validate before saving the setting, otherwise the picker would repeatedly fail.
        Typeface.createFromFile(outFile)
        cachedPath = outFile.absolutePath
        cachedTypeface = null
        SettingsManager.setEmojiPickerCustomFont(appContext, outFile.absolutePath, displayName)
        return displayName
    }

    fun getFontSlots(context: Context): List<EmojiFontSlot> {
        val appContext = context.applicationContext
        migrateLegacyFontToAppleSlot(appContext)
        val slotsDir = File(File(appContext.filesDir, DIR_NAME), SLOTS_DIR_NAME)
        return slotsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("ttf", ignoreCase = true) }
            .sortedWith(compareBy<File> { it.name != "apple-emoji.ttf" }.thenBy { it.name })
            .map { file ->
                EmojiFontSlot(
                    displayName = if (file.name == "apple-emoji.ttf") "Apple Emoji" else displayNameForSlot(file),
                    path = file.absolutePath
                )
            }
    }

    fun selectFontSlot(context: Context, slot: EmojiFontSlot) {
        SettingsManager.setEmojiPickerCustomFont(context.applicationContext, slot.path, slot.displayName)
        clearCache()
    }

    fun getTypeface(context: Context): Typeface? {
        if (!SettingsManager.getEmojiPickerCustomFontEnabled(context)) return null
        val path = SettingsManager.getEmojiPickerCustomFontPath(context)
        if (path.isBlank()) return null
        cachedTypeface?.let { existing ->
            if (cachedPath == path) return existing
        }
        return runCatching {
            Typeface.createFromFile(File(path))
        }.onFailure { error ->
            Log.w(TAG, "Failed to load custom emoji font", error)
        }.getOrNull()?.also { loaded ->
            cachedPath = path
            cachedTypeface = loaded
        }
    }

    fun clearCache() {
        cachedPath = null
        cachedTypeface = null
    }

    fun applyToTextView(
        context: Context,
        textView: TextView,
        emoji: String,
        fallbackTypeface: Typeface,
        systemTextSizeSp: Float,
        customTextSizeSp: Float
    ) {
        val typeface = getTypeface(context)
        val displayText = typeface?.let { displayTextForCustomTypeface(emoji, it) }
        if (typeface != null && displayText != null) {
            textView.text = displayText
            textView.typeface = typeface
            textView.textSize = customTextSizeSp
        } else {
            textView.text = emoji
            textView.typeface = fallbackTypeface
            textView.textSize = systemTextSizeSp
            fitFallbackEmojiToTile(textView, emoji, systemTextSizeSp)
        }
    }

    private fun fitFallbackEmojiToTile(textView: TextView, emoji: String, textSizeSp: Float) {
        if (emoji.indexOf('\u200D') < 0) return
        textView.post {
            if (textView.text.toString() != emoji) return@post
            val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
            if (availableWidth <= 0) return@post
            val renderedWidth = textView.paint.measureText(emoji)
            if (renderedWidth <= availableWidth) return@post
            val scaledSize = (textSizeSp * availableWidth / renderedWidth).coerceAtLeast(20f)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize)
        }
    }

    /**
     * Converted Apple emoji fonts often render complex sequences as separate glyphs
     * inside Android TextView. Let Android handle those sequences instead.
     */
    fun canUseCustomTypeface(emoji: String): Boolean {
        return !emoji.contains('\u200D')
    }

    private fun displayTextForCustomTypeface(emoji: String, typeface: Typeface): String? {
        if (emoji.indexOf('\u200D') >= 0) {
            // Complex people/family emoji are single ligature glyphs only in fonts that support them.
            // Do not force a custom font when it lacks the complete sequence.
            return emoji.takeIf { Paint().apply { this.typeface = typeface }.hasGlyph(it) }
        }
        val normalized = emoji
            .replace("\uFE0E", "")
            .replace("\uFE0F", "")
        if (normalized.isBlank()) return null
        var index = 0
        var codePointCount = 0
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            if (codePoint in 0x1F3FB..0x1F3FF) return null // skin tone modifiers
            codePointCount += 1
            if (codePointCount > 1) return null // flags and other multi-codepoint sequences
            index += Character.charCount(codePoint)
        }
        return if (codePointCount == 1) normalized else null
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Custom emoji font"
    }

    private fun migrateLegacyFontToAppleSlot(context: Context) {
        val currentPath = SettingsManager.getEmojiPickerCustomFontPath(context)
        val legacyFile = File(currentPath)
        if (legacyFile.name != FONT_FILE_NAME || !legacyFile.isFile) return

        val slotsDir = File(File(context.filesDir, DIR_NAME), SLOTS_DIR_NAME).apply { mkdirs() }
        val appleSlot = File(slotsDir, "apple-emoji.ttf")
        if (!appleSlot.exists()) legacyFile.copyTo(appleSlot)
        SettingsManager.setEmojiPickerCustomFont(context, appleSlot.absolutePath, "Apple Emoji")
        clearCache()
    }

    private fun slotFileName(displayName: String): String {
        val sanitized = displayName.substringBeforeLast('.')
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return sanitized.ifBlank { "custom-emoji-font" }
    }

    private fun displayNameForSlot(file: File): String {
        return file.nameWithoutExtension
            .replace('-', ' ')
            .replaceFirstChar { it.titlecase() }
    }
}
