package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.variation.VariationRepository
import it.palsoftware.pastiera.core.AutoSpaceTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Alt/SYM mappings, long press handling and special character insertion.
 */
class AltSymManager(
    private val assets: AssetManager,
    private val prefs: SharedPreferences,
    private val context: Context? = null,
    private val activeLayoutNameProvider: (() -> String?)? = null
) {
    // Callback invoked when an Alt character is inserted after a long press
    var onAltCharInserted: ((Char) -> Unit)? = null
    // Callback invoked when a normal character is confirmed (short press in Alt mode)
    var onNormalCharCommitted: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "AltSymManager"
    }

    private fun autoSpacePunctuation(): String {
        return context?.let { SettingsManager.getAutoSpacePunctuation(it) }
            ?: it.palsoftware.pastiera.core.Punctuation.DEFAULT_AUTO_SPACE
    }

    private val handler = Handler(Looper.getMainLooper())

    private val altKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap2 = mutableMapOf<Int, String>()
    private val symKeyMapUppercase = mutableMapOf<Int, String>()
    private val symKeyMap2Uppercase = mutableMapOf<Int, String>()

    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    private val longPressRunnables = ConcurrentHashMap<Int, Runnable>()
    private val longPressActivated = ConcurrentHashMap<Int, Boolean>()
    private val insertedNormalChars = ConcurrentHashMap<Int, String>()
    private val keyPressWasShifted = ConcurrentHashMap<Int, Boolean>()

    private var longPressThreshold: Long = 500L

    init {
        altKeyMap.putAll(KeyMappingLoader.loadAltKeyMappings(assets, context))
        symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
        symKeyMap2.putAll(KeyMappingLoader.loadSymKeyMappingsPage2(assets))
        symKeyMapUppercase.putAll(KeyMappingLoader.loadSymKeyMappingsUppercase(assets))
        symKeyMap2Uppercase.putAll(KeyMappingLoader.loadSymKeyMappingsPage2Uppercase(assets))
        reloadLongPressThreshold()
    }

    fun reloadLongPressThreshold() {
        longPressThreshold = prefs.getLong("long_press_threshold", 500L).coerceIn(50L, 1000L)
    }

    fun reloadAltMappings() {
        altKeyMap.clear()
        altKeyMap.putAll(KeyMappingLoader.loadAltKeyMappings(assets, context))
    }

    fun getAltMappings(): Map<Int, String> = altKeyMap

    fun getSymMappings(): Map<Int, String> = symKeyMap
    
    fun getSymMappings2(): Map<Int, String> = symKeyMap2

    fun getSymMappingsUppercase(): Map<Int, String> = symKeyMapUppercase

    fun getSymMappings2Uppercase(): Map<Int, String> = symKeyMap2Uppercase
    
    /**
     * Ricarica le mappature SYM, controllando prima le personalizzazioni.
     */
    fun reloadSymMappings() {
        if (context != null) {
            val customMappings = it.palsoftware.pastiera.SettingsManager.getSymMappings(context)
            if (customMappings.isNotEmpty()) {
                symKeyMap.clear()
                symKeyMap.putAll(customMappings)
                symKeyMapUppercase.clear()
                Log.d(TAG, "Loaded custom SYM mappings: ${customMappings.size} entries")
            } else {
                // Use default mappings from JSON
                symKeyMap.clear()
                symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
                symKeyMapUppercase.clear()
                symKeyMapUppercase.putAll(KeyMappingLoader.loadSymKeyMappingsUppercase(assets))
                Log.d(TAG, "Loaded default SYM mappings")
            }
        }
    }
    
    /**
     * Reloads SYM mappings for page 2, checking for custom mappings first.
     */
    fun reloadSymMappings2() {
        if (context != null) {
            val customMappings = it.palsoftware.pastiera.SettingsManager.getSymMappingsPage2(context)
            if (customMappings.isNotEmpty()) {
                symKeyMap2.clear()
                symKeyMap2.putAll(customMappings)
                symKeyMap2Uppercase.clear()
                Log.d(TAG, "Loaded custom SYM page 2 mappings: ${customMappings.size} entries")
            } else {
                // Use default mappings from JSON
                symKeyMap2.clear()
                symKeyMap2.putAll(KeyMappingLoader.loadSymKeyMappingsPage2(assets))
                symKeyMap2Uppercase.clear()
                symKeyMap2Uppercase.putAll(KeyMappingLoader.loadSymKeyMappingsPage2Uppercase(assets))
                Log.d(TAG, "Loaded default SYM page 2 mappings")
            }
        }
    }

    fun hasAltMapping(keyCode: Int): Boolean = altKeyMap.containsKey(keyCode)

    fun getAltMapping(keyCode: Int): String? = altKeyMap[keyCode]

    fun hasSymLongPressMapping(keyCode: Int, shiftPressed: Boolean): Boolean {
        val useEmojiFirst = context?.let {
            SettingsManager.getSymPagesConfig(it).prefersEmojiLongPressLayer()
        } ?: true
        return if (useEmojiFirst) {
            if (shiftPressed && symKeyMapUppercase.containsKey(keyCode)) {
                true
            } else {
                symKeyMap.containsKey(keyCode)
            }
        } else {
            if (shiftPressed && symKeyMap2Uppercase.containsKey(keyCode)) {
                true
            } else {
                symKeyMap2.containsKey(keyCode)
            }
        }
    }

    fun hasPendingPress(keyCode: Int): Boolean = pressedKeys.containsKey(keyCode)

    fun addAltKeyMapping(keyCode: Int, character: String) {
        altKeyMap[keyCode] = character
    }

    fun removeAltKeyMapping(keyCode: Int) {
        altKeyMap.remove(keyCode)
    }

    fun resetTransientState() {
        longPressRunnables.values.forEach { handler.removeCallbacks(it) }
        longPressRunnables.clear()
        pressedKeys.clear()
        longPressActivated.clear()
        insertedNormalChars.clear()
        keyPressWasShifted.clear()
    }

    fun buildEmojiMapText(): String {
        val keyLabels = mapOf(
            KeyEvent.KEYCODE_Q to "Q", KeyEvent.KEYCODE_W to "W", KeyEvent.KEYCODE_E to "E",
            KeyEvent.KEYCODE_R to "R", KeyEvent.KEYCODE_T to "T", KeyEvent.KEYCODE_Y to "Y",
            KeyEvent.KEYCODE_U to "U", KeyEvent.KEYCODE_I to "I", KeyEvent.KEYCODE_O to "O",
            KeyEvent.KEYCODE_P to "P", KeyEvent.KEYCODE_A to "A", KeyEvent.KEYCODE_S to "S",
            KeyEvent.KEYCODE_D to "D", KeyEvent.KEYCODE_F to "F", KeyEvent.KEYCODE_G to "G",
            KeyEvent.KEYCODE_H to "H", KeyEvent.KEYCODE_J to "J", KeyEvent.KEYCODE_K to "K",
            KeyEvent.KEYCODE_L to "L", KeyEvent.KEYCODE_Z to "Z", KeyEvent.KEYCODE_X to "X",
            KeyEvent.KEYCODE_C to "C", KeyEvent.KEYCODE_V to "V", KeyEvent.KEYCODE_B to "B",
            KeyEvent.KEYCODE_N to "N", KeyEvent.KEYCODE_M to "M"
        )

        val rows = mutableListOf<String>()
        val keys = listOf(
            listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P),
            listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L),
            listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M)
        )

        for (row in keys) {
            val rowText = row.joinToString("  ") { keyCode ->
                val label = keyLabels[keyCode] ?: ""
                val emoji = symKeyMap[keyCode] ?: ""
                "$label:$emoji"
            }
            rows.add(rowText)
        }

        return rows.joinToString("\n")
    }

    fun handleKeyWithAltMapping(
        keyCode: Int,
        event: KeyEvent?,
        capsLockEnabled: Boolean,
        inputConnection: InputConnection,
        shiftOneShot: Boolean = false,
        layoutChar: Char? = null // Optional character from keyboard layout
    ): Boolean {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false

        // Use centralized character retrieval from layout manager when key is mapped
        var normalChar = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
        } else {
            // Fallback: use layout character if provided, otherwise fall back to event's unicode character
            if (layoutChar != null) {
                layoutChar.toString()
            } else if (event != null && event.unicodeChar != 0) {
                event.unicodeChar.toChar().toString()
            } else {
                ""
            }
        }

        // For unmapped keys, apply case conversion if needed (fallback only)
        if (normalChar.isNotEmpty() && !LayoutMappingRepository.isMapped(keyCode)) {
            // Gestisci shiftOneShot: se è attivo e il carattere è una lettera, rendilo maiuscolo
            if (shiftOneShot && normalChar.isNotEmpty() && normalChar[0].isLetter()) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed != true) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed == true) {
                normalChar = normalChar.lowercase()
            }
        }

        if (normalChar.isNotEmpty()) {
            inputConnection.commitText(normalChar, 1)
            insertedNormalChars[keyCode] = normalChar
            keyPressWasShifted[keyCode] = shiftOneShot || event?.isShiftPressed == true
        }

        // Check if this key should support long press
        val longPressMode = context?.let {
            SettingsManager.getLongPressModifier(it)
        } ?: "alt"

        val shouldScheduleLongPress = when (longPressMode) {
            "variations" -> {
                if (normalChar.isEmpty()) {
                    false
                } else {
                    val variations = context?.let { ctx ->
                        VariationRepository.loadVariations(
                            assets = ctx.assets,
                            context = ctx,
                            activeLayoutName = activeLayoutNameProvider?.invoke()
                        )
                    } ?: emptyMap()
                    variations[normalChar.firstOrNull()]?.isNotEmpty() == true
                }
            }
            "sym" -> hasSymLongPressMapping(
                keyCode = keyCode,
                shiftPressed = keyPressWasShifted[keyCode] == true
            )
            "shift" -> LayoutMappingRepository.isMapped(keyCode) && normalChar.isNotEmpty()
            else -> altKeyMap.containsKey(keyCode)
        }
        
        if (shouldScheduleLongPress) {
            scheduleLongPress(keyCode, inputConnection)
        }
        
        return true
    }

    fun handleAltCombination(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        defaultHandler: (Int, KeyEvent?) -> Boolean
    ): Boolean {
        val altChar = altKeyMap[keyCode]
        return if (altChar != null) {
            val frenchSpacedPunctuation = altChar.length == 1 &&
                context?.let { SettingsManager.shouldApplyFrenchPunctuationSpacing(it) } == true &&
                it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, altChar[0])
            if (frenchSpacedPunctuation) {
                onAltCharInserted?.invoke(altChar[0])
                return true
            }
            val punctuationSet = autoSpacePunctuation()
            if (altChar.isNotEmpty() && altChar[0] in punctuationSet) {
                val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, altChar)
                if (applied) {
                    Log.d(TAG, "Alt mapping applied with auto-space replacement for '$altChar'")
                    onAltCharInserted?.invoke(altChar[0])
                    return true
                }
            }
            AutoSpaceTracker.clear()
            inputConnection.commitText(altChar, 1)
            if (altChar.isNotEmpty()) {
                onAltCharInserted?.invoke(altChar[0])
            }
            true
        } else {
            defaultHandler(keyCode, event)
        }
    }

    fun handleKeyUp(keyCode: Int, symKeyActive: Boolean, shiftPressed: Boolean = false): Boolean {
        val pressStartTime = pressedKeys.remove(keyCode)
        val wasLongPressActivated = longPressActivated.remove(keyCode) ?: false
        val insertedChar = insertedNormalChars.remove(keyCode)
        keyPressWasShifted.remove(keyCode)
        
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }

        // If the long press did NOT trigger and we had inserted a normal char, notify tracking
        if (!wasLongPressActivated && insertedChar != null) {
            onNormalCharCommitted?.invoke(insertedChar)
        }

        return pressStartTime != null && !symKeyActive
    }

    fun cancelPendingLongPress(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    /**
     * Schedules a long-press without committing a new character, reusing the
     * same runnable logic used by handleKeyWithAltMapping. This is used so
     * multi-tap commits can still trigger Alt/Shift long-press behaviour.
     */
    fun scheduleLongPressOnly(
        keyCode: Int,
        inputConnection: InputConnection,
        insertedChar: String
    ) {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false
        insertedNormalChars[keyCode] = insertedChar
        keyPressWasShifted[keyCode] = insertedChar.firstOrNull()?.isUpperCase() == true
        scheduleLongPress(keyCode, inputConnection)
    }

    private fun scheduleLongPress(
        keyCode: Int,
        inputConnection: InputConnection,
        modeOverride: String? = null,
        allowSecondLongPress: Boolean = true,
        collapseDuplicatePreviousText: Boolean = false,
        thresholdOverride: Long? = null
    ) {
        reloadLongPressThreshold()
        val activeLongPressThreshold = thresholdOverride ?: longPressThreshold

        val longPressMode = modeOverride ?: context?.let {
            SettingsManager.getLongPressModifier(it)
        } ?: "alt"

        val runnable = Runnable {
            if (!pressedKeys.containsKey(keyCode)) return@Runnable

            val insertedChar = insertedNormalChars[keyCode].orEmpty()
            val output = resolveLongPressOutput(longPressMode, keyCode, insertedChar) ?: return@Runnable

            longPressActivated[keyCode] = true
            if (commitLongPressOutput(inputConnection, insertedChar, output, longPressMode, collapseDuplicatePreviousText)) {
                insertedNormalChars[keyCode] = output
                Log.d(TAG, "Long press $longPressMode per keyCode $keyCode -> $output")
                if (allowSecondLongPress) {
                    scheduleSecondLongPressIfNeeded(keyCode, inputConnection, output)
                } else {
                    clearLongPressTracking(keyCode)
                }
            }
        }

        longPressRunnables[keyCode] = runnable
        handler.postDelayed(runnable, activeLongPressThreshold)
    }

    fun schedulePhysicalAltShiftLongPress(
        keyCode: Int,
        inputConnection: InputConnection,
        insertedChar: String,
        shiftedFromAltChar: Boolean
    ) {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false
        insertedNormalChars[keyCode] = insertedChar
        keyPressWasShifted[keyCode] = shiftedFromAltChar
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
        val threshold = context?.let { SettingsManager.getPhysicalAltShiftLongPressThreshold(it) }
        scheduleLongPress(
            keyCode = keyCode,
            inputConnection = inputConnection,
            modeOverride = "shift",
            allowSecondLongPress = false,
            collapseDuplicatePreviousText = true,
            thresholdOverride = threshold
        )
    }

    private fun scheduleSecondLongPressIfNeeded(
        keyCode: Int,
        inputConnection: InputConnection,
        currentText: String
    ) {
        val ctx = context
        if (ctx == null) {
            clearLongPressTracking(keyCode)
            return
        }

        val secondMode = SettingsManager.getSecondLongPressModifier(ctx)
        if (secondMode == "off") {
            clearLongPressTracking(keyCode)
            return
        }

        val secondThreshold = SettingsManager.getSecondLongPressThreshold(ctx)
            .coerceAtLeast(longPressThreshold + 50L)
        val secondDelay = (secondThreshold - longPressThreshold).coerceAtLeast(50L)
        val secondRunnable = Runnable {
            if (!pressedKeys.containsKey(keyCode)) return@Runnable

            val previousText = insertedNormalChars[keyCode] ?: currentText
            val output = resolveLongPressOutput(secondMode, keyCode, previousText) ?: return@Runnable
            if (commitLongPressOutput(inputConnection, previousText, output, secondMode)) {
                longPressActivated[keyCode] = true
                Log.d(TAG, "Second long press $secondMode per keyCode $keyCode -> $output")
                clearLongPressTracking(keyCode)
            }
        }

        longPressRunnables[keyCode] = secondRunnable
        handler.postDelayed(secondRunnable, secondDelay)
    }

    private fun resolveLongPressOutput(
        mode: String,
        keyCode: Int,
        insertedChar: String
    ): String? {
        return when (mode) {
            "variations" -> resolveVariationLongPressOutput(keyCode, insertedChar)
            "sym" -> resolveSymLongPressOutput(keyCode)
            "shift" -> resolveShiftLongPressOutput(keyCode, insertedChar)
            "alt" -> altKeyMap[keyCode]
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }

    private fun resolveVariationLongPressOutput(keyCode: Int, insertedChar: String): String? {
        if (insertedChar.isEmpty()) return null
        val wasShifted = keyPressWasShifted[keyCode] ?: false
        val baseChar = insertedChar[0]
        val lookupChar = if (wasShifted && baseChar.isLowerCase()) {
            baseChar.uppercaseChar()
        } else if (!wasShifted && baseChar.isUpperCase()) {
            baseChar.lowercaseChar()
        } else {
            baseChar
        }
        return context?.let { ctx ->
            VariationRepository.loadVariations(
                assets = ctx.assets,
                context = ctx,
                activeLayoutName = activeLayoutNameProvider?.invoke()
            )[lookupChar]
        }?.firstOrNull()
    }

    private fun resolveSymLongPressOutput(keyCode: Int): String? {
        val useEmojiFirst = context?.let { ctx ->
            SettingsManager.getSymPagesConfig(ctx).prefersEmojiLongPressLayer()
        } ?: true
        val wasShifted = keyPressWasShifted[keyCode] ?: false
        return if (useEmojiFirst) {
            if (wasShifted && symKeyMapUppercase.containsKey(keyCode)) {
                symKeyMapUppercase[keyCode]
            } else {
                symKeyMap[keyCode]
            }
        } else {
            if (wasShifted && symKeyMap2Uppercase.containsKey(keyCode)) {
                symKeyMap2Uppercase[keyCode]
            } else {
                symKeyMap2[keyCode]
            }
        }
    }

    private fun resolveShiftLongPressOutput(keyCode: Int, insertedChar: String): String? {
        if (LayoutMappingRepository.isMapped(keyCode)) {
            return LayoutMappingRepository.getUppercase(keyCode)
        }
        return insertedChar.takeIf { it.firstOrNull()?.isLetter() == true }?.uppercase()
    }

    private fun commitLongPressOutput(
        inputConnection: InputConnection,
        previousText: String,
        output: String,
        mode: String,
        collapseDuplicatePreviousText: Boolean = false
    ): Boolean {
        inputConnection.finishComposingText()

        val punctuationAware = mode == "alt" || mode == "sym"
        if (punctuationAware && output.length == 1) {
            val frenchSpacedPunctuation =
                context?.let { SettingsManager.shouldApplyFrenchPunctuationSpacing(it) } == true
            if (frenchSpacedPunctuation) {
                deletePreviousLongPressText(inputConnection, previousText)
                it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, output[0])
                onAltCharInserted?.invoke(output[0])
                return true
            }

            if (output[0] in autoSpacePunctuation()) {
                deletePreviousLongPressText(inputConnection, previousText)
                val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, output)
                if (applied) {
                    onAltCharInserted?.invoke(output[0])
                    return true
                }
                AutoSpaceTracker.clear()
                inputConnection.commitText(output, 1)
                onAltCharInserted?.invoke(output[0])
                return true
            }
        }

        AutoSpaceTracker.clear()
        replacePreviousLongPressText(inputConnection, previousText, output, collapseDuplicatePreviousText)
        output.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
        return true
    }

    private fun deletePreviousLongPressText(
        inputConnection: InputConnection,
        previousText: String
    ) {
        if (previousText.isEmpty()) return
        replacePreviousLongPressText(inputConnection, previousText, "")
    }

    private fun replacePreviousLongPressText(
        inputConnection: InputConnection,
        previousText: String,
        output: String,
        collapseDuplicatePreviousText: Boolean = false
    ) {
        if (previousText.isEmpty()) {
            inputConnection.commitText(output, 1)
            return
        }

        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        val selectionStart = extracted?.selectionStart ?: -1
        val selectionEnd = extracted?.selectionEnd ?: -1
        val text = extracted?.text
        val canReplaceByRegion = text != null &&
            selectionStart == selectionEnd &&
            selectionStart >= previousText.length &&
            selectionStart <= text.length &&
            text.substring(selectionStart - previousText.length, selectionStart) == previousText
        var replaceStart = selectionStart - previousText.length
        if (canReplaceByRegion && collapseDuplicatePreviousText) {
            while (
                replaceStart >= previousText.length &&
                text?.substring(replaceStart - previousText.length, replaceStart) == previousText
            ) {
                replaceStart -= previousText.length
            }
        }

        inputConnection.beginBatchEdit()
        if (canReplaceByRegion) {
            inputConnection.setComposingRegion(replaceStart, selectionStart)
            inputConnection.commitText(output, 1)
        } else {
            inputConnection.deleteSurroundingText(previousText.length, 0)
            inputConnection.commitText(output, 1)
        }
        inputConnection.endBatchEdit()
    }

    private fun clearLongPressTracking(keyCode: Int) {
        insertedNormalChars.remove(keyCode)
        keyPressWasShifted.remove(keyCode)
        longPressRunnables.remove(keyCode)
    }

    private fun replacePreviousCharWithVariation(
        inputConnection: InputConnection,
        expectedChar: Char,
        variation: String
    ) {
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        val selectionStart = extracted?.selectionStart ?: -1
        val selectionEnd = extracted?.selectionEnd ?: -1
        val text = extracted?.text
        val canReplaceByRegion = text != null &&
            selectionStart == selectionEnd &&
            selectionStart > 0 &&
            selectionStart <= text.length &&
            text[selectionStart - 1] == expectedChar

        inputConnection.finishComposingText()
        inputConnection.beginBatchEdit()
        if (canReplaceByRegion) {
            inputConnection.setComposingRegion(selectionStart - 1, selectionStart)
            inputConnection.commitText(variation, 1)
        } else {
            inputConnection.deleteSurroundingText(1, 0)
            inputConnection.commitText(variation, 1)
        }
        inputConnection.endBatchEdit()
    }
}
