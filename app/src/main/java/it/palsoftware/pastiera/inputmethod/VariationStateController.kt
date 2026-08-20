package it.palsoftware.pastiera.inputmethod

import android.os.SystemClock
import android.view.inputmethod.InputConnection

/**
 * Tracks character variation availability for the current cursor position
 * and exposes snapshots for the status / variation bars.
 */
class VariationStateController(
    private val variationsMap: Map<Char, List<String>>
) {

    data class Snapshot(
        val isActive: Boolean,
        val lastInsertedChar: Char?,
        val variations: List<String>
    )

    private var lastInsertedChar: Char? = null
    private var availableVariations: List<String> = emptyList()
    private var variationsActive: Boolean = false
    private var localCommitSnapshotValid: Boolean = false
    private var lastCursorRefreshAtMs: Long = 0L

    fun refreshFromCursor(
        inputConnection: InputConnection?,
        shouldDisableVariations: Boolean,
        hasActiveSelection: Boolean = false,
        minRefreshIntervalMs: Long = 120L
    ): Snapshot {
        if (shouldDisableVariations || inputConnection == null) {
            clear()
            return snapshot()
        }

        if (hasActiveSelection) {
            clear()
            return snapshot()
        }

        val now = SystemClock.elapsedRealtime()
        if (lastCursorRefreshAtMs > 0L && now - lastCursorRefreshAtMs < minRefreshIntervalMs) {
            return snapshot()
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
        lastCursorRefreshAtMs = now
        if (!textBeforeCursor.isNullOrEmpty()) {
            val charBeforeCursor = textBeforeCursor.last()
            val variations = variationsMap[charBeforeCursor]
            if (!variations.isNullOrEmpty()) {
                lastInsertedChar = charBeforeCursor
                availableVariations = variations
                variationsActive = true
            } else {
                clear()
            }
        } else {
            clear()
        }

        return snapshot()
    }

    fun updateFromCommittedText(text: CharSequence) {
        val char = text.lastOrNull()
        localCommitSnapshotValid = true
        lastCursorRefreshAtMs = SystemClock.elapsedRealtime()
        if (char == null) {
            clearActiveState()
            return
        }

        val variations = variationsMap[char]
        if (!variations.isNullOrEmpty()) {
            lastInsertedChar = char
            availableVariations = variations
            variationsActive = true
        } else {
            clearActiveState()
        }
    }

    fun hasLocalCommitSnapshot(): Boolean = localCommitSnapshotValid

    fun markCursorContextUnknown() {
        localCommitSnapshotValid = false
        lastCursorRefreshAtMs = 0L
    }

    fun hasVariationsFor(char: Char): Boolean = variationsMap.containsKey(char)

    fun clear() {
        localCommitSnapshotValid = false
        lastCursorRefreshAtMs = 0L
        clearActiveState()
    }

    private fun clearActiveState() {
        variationsActive = false
        lastInsertedChar = null
        availableVariations = emptyList()
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            isActive = variationsActive,
            lastInsertedChar = if (variationsActive) lastInsertedChar else null,
            variations = if (variationsActive) availableVariations else emptyList()
        )
    }
}
