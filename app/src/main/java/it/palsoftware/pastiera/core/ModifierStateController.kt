package it.palsoftware.pastiera.core

import android.view.KeyEvent
import it.palsoftware.pastiera.inputmethod.ModifierKeyHandler

enum class ShiftState { OFF, ONE_SHOT, CAPS }

/**
 * Centralizes modifier key state (Shift/Ctrl/Alt) and keeps one-shot / latch
 * bookkeeping in sync with the UI and auto-capitalization helpers.
 */
class ModifierStateController(
    private val doubleTapThreshold: Long
) {
    private enum class ShiftOneShotSource {
        NONE,
        MANUAL,
        AUTO_CAP
    }

    private val modifierKeyHandler = ModifierKeyHandler(doubleTapThreshold)
    private var lastKeyWasModifier = false
    private var lastModifierKeyCode: Int = 0

    private fun normalizeModifierTapKeyCode(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.KEYCODE_SHIFT_LEFT
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.KEYCODE_CTRL_LEFT
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.KEYCODE_ALT_LEFT
            else -> keyCode
        }
    }

    private class ShiftStateMachine(
        private val doubleTapThreshold: Long
    ) {
        var state: ShiftState = ShiftState.OFF
            private set

        private var pendingLatchUntil: Long = 0

        fun tap(
            now: Long = System.currentTimeMillis(),
            isConsecutiveTap: Boolean,
            allowDoubleTapLatch: Boolean = true
        ): ShiftState {
            state = when {
                state == ShiftState.CAPS -> {
                    pendingLatchUntil = 0
                    ShiftState.OFF
                }
                state == ShiftState.ONE_SHOT -> {
                    if (allowDoubleTapLatch && pendingLatchUntil > 0 && now <= pendingLatchUntil && isConsecutiveTap) {
                        pendingLatchUntil = 0
                        ShiftState.CAPS
                    } else {
                        pendingLatchUntil = if (allowDoubleTapLatch) now + doubleTapThreshold else 0
                        ShiftState.OFF
                    }
                }
                else -> {
                    if (allowDoubleTapLatch && pendingLatchUntil > 0 && now <= pendingLatchUntil && isConsecutiveTap) {
                        pendingLatchUntil = 0
                        ShiftState.CAPS
                    } else {
                        pendingLatchUntil = if (allowDoubleTapLatch) now + doubleTapThreshold else 0
                        ShiftState.ONE_SHOT
                    }
                }
            }
            return state
        }

        fun requestOneShot(): Boolean {
            if (state == ShiftState.CAPS) {
                return false
            }
            if (state != ShiftState.ONE_SHOT) {
                state = ShiftState.ONE_SHOT
                return true
            }
            return false
        }

        fun consumeOneShot(): Boolean {
            return if (state == ShiftState.ONE_SHOT) {
                state = ShiftState.OFF
                true
            } else {
                false
            }
        }

        fun setCapsLock(enabled: Boolean) {
            pendingLatchUntil = 0
            state = if (enabled) ShiftState.CAPS else ShiftState.OFF
        }

        fun restore(newState: ShiftState) {
            state = newState
        }

        fun reset() {
            state = ShiftState.OFF
            pendingLatchUntil = 0
        }
    }

    private val shiftStateMachine = ShiftStateMachine(doubleTapThreshold)
    private var shiftPressedFlag = false
    private var shiftPhysicallyPressedFlag = false
    private var shiftOneShotSource: ShiftOneShotSource = ShiftOneShotSource.NONE
    private var suppressCtrlKeyUp = false
    private var suppressAltKeyUp = false

    private val ctrlState = ModifierKeyHandler.CtrlState()
    private val altState = ModifierKeyHandler.AltState()

    fun registerModifierTap(keyCode: Int): Boolean {
        val normalizedKeyCode = normalizeModifierTapKeyCode(keyCode)
        val isConsecutive = lastKeyWasModifier && lastModifierKeyCode == normalizedKeyCode
        lastKeyWasModifier = true
        lastModifierKeyCode = normalizedKeyCode
        return isConsecutive
    }

    fun registerNonModifierKey() {
        lastKeyWasModifier = false
        lastModifierKeyCode = 0
    }

    fun clearModifierTapTracking() {
        lastKeyWasModifier = false
        lastModifierKeyCode = 0
    }

    data class Snapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean
    )

    data class LogicalState(
        val shiftState: ShiftState,
        val ctrlOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altOneShot: Boolean,
        val altLatchActive: Boolean
    )

    fun captureLogicalState(): LogicalState {
        return LogicalState(
            shiftState = shiftStateMachine.state,
            ctrlOneShot = ctrlState.oneShot,
            ctrlLatchActive = ctrlState.latchActive,
            ctrlLatchFromNavMode = ctrlState.latchFromNavMode,
            altOneShot = altState.oneShot,
            altLatchActive = altState.latchActive
        )
    }

    fun restoreLogicalState(captured: LogicalState) {
        shiftStateMachine.restore(captured.shiftState)
        shiftOneShotSource = if (captured.shiftState == ShiftState.ONE_SHOT) ShiftOneShotSource.MANUAL else ShiftOneShotSource.NONE
        ctrlState.oneShot = captured.ctrlOneShot
        ctrlState.latchActive = captured.ctrlLatchActive
        ctrlState.latchFromNavMode = captured.ctrlLatchFromNavMode
        altState.oneShot = captured.altOneShot
        altState.latchActive = captured.altLatchActive
    }

    val shiftState: ShiftState
        get() = shiftStateMachine.state

    val isShiftOneShotFromAutoCap: Boolean
        get() = shiftStateMachine.state == ShiftState.ONE_SHOT &&
            shiftOneShotSource == ShiftOneShotSource.AUTO_CAP

    var capsLockEnabled: Boolean
        get() = shiftStateMachine.state == ShiftState.CAPS
        set(value) { shiftStateMachine.setCapsLock(value) }

    var shiftPressed: Boolean
        get() = shiftPressedFlag
        set(value) { shiftPressedFlag = value }

    var shiftPhysicallyPressed: Boolean
        get() = shiftPhysicallyPressedFlag
        set(value) { shiftPhysicallyPressedFlag = value }

    var shiftOneShot: Boolean
        get() = shiftStateMachine.state == ShiftState.ONE_SHOT
        set(value) {
            if (value) {
                shiftStateMachine.requestOneShot()
                shiftOneShotSource = ShiftOneShotSource.MANUAL
            } else {
                shiftStateMachine.consumeOneShot()
                shiftOneShotSource = ShiftOneShotSource.NONE
            }
        }

    var ctrlLatchActive: Boolean
        get() = ctrlState.latchActive
        set(value) { ctrlState.latchActive = value }

    var ctrlPressed: Boolean
        get() = ctrlState.pressed
        set(value) { ctrlState.pressed = value }

    var ctrlPhysicallyPressed: Boolean
        get() = ctrlState.physicallyPressed
        set(value) { ctrlState.physicallyPressed = value }

    var ctrlOneShot: Boolean
        get() = ctrlState.oneShot
        set(value) { ctrlState.oneShot = value }

    var ctrlLatchFromNavMode: Boolean
        get() = ctrlState.latchFromNavMode
        set(value) { ctrlState.latchFromNavMode = value }

    var ctrlLastReleaseTime: Long
        get() = ctrlState.lastReleaseTime
        set(value) { ctrlState.lastReleaseTime = value }

    fun consumeSuppressedCtrlKeyUp(): Boolean {
        if (!suppressCtrlKeyUp) return false
        suppressCtrlKeyUp = false
        return true
    }

    var altLatchActive: Boolean
        get() = altState.latchActive
        set(value) { altState.latchActive = value }

    var altPressed: Boolean
        get() = altState.pressed
        set(value) { altState.pressed = value }

    var altPhysicallyPressed: Boolean
        get() = altState.physicallyPressed
        set(value) { altState.physicallyPressed = value }

    var altOneShot: Boolean
        get() = altState.oneShot
        set(value) { altState.oneShot = value }

    var altLastReleaseTime: Long
        get() = altState.lastReleaseTime
        set(value) { altState.lastReleaseTime = value }

    fun consumeSuppressedAltKeyUp(): Boolean {
        if (!suppressAltKeyUp) return false
        suppressAltKeyUp = false
        return true
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            capsLockEnabled = capsLockEnabled,
            shiftPhysicallyPressed = shiftPhysicallyPressed,
            shiftOneShot = shiftOneShot,
            ctrlLatchActive = ctrlLatchActive,
            ctrlPhysicallyPressed = ctrlPhysicallyPressed,
            ctrlOneShot = ctrlOneShot,
            ctrlLatchFromNavMode = ctrlLatchFromNavMode,
            altLatchActive = altLatchActive,
            altPhysicallyPressed = altPhysicallyPressed,
            altOneShot = altOneShot
        )
    }

    fun handleShiftKeyDown(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT &&
            keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return ModifierKeyHandler.ModifierKeyResult()
        }

        if (shiftPressedFlag) {
            return ModifierKeyHandler.ModifierKeyResult()
        }

        shiftPhysicallyPressedFlag = true
        shiftPressedFlag = true
        if (shiftStateMachine.state == ShiftState.ONE_SHOT && shiftOneShotSource == ShiftOneShotSource.AUTO_CAP) {
            shiftStateMachine.consumeOneShot()
            shiftOneShotSource = ShiftOneShotSource.NONE
            clearModifierTapTracking()
            return ModifierKeyHandler.ModifierKeyResult(
                shouldUpdateStatusBar = true,
                shouldRefreshStatusBar = true
            )
        }
        val previous = shiftStateMachine.state
        val isConsecutiveTap = registerModifierTap(keyCode)
        val current = shiftStateMachine.tap(
            isConsecutiveTap = isConsecutiveTap
        )
        shiftOneShotSource = when (current) {
            ShiftState.ONE_SHOT -> ShiftOneShotSource.MANUAL
            else -> ShiftOneShotSource.NONE
        }
        val changed = previous != current
        return ModifierKeyHandler.ModifierKeyResult(
            shouldUpdateStatusBar = changed,
            shouldRefreshStatusBar = changed
        )
    }

    fun handleShiftKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT &&
            keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return ModifierKeyHandler.ModifierKeyResult()
        }

        shiftPressedFlag = false
        shiftPhysicallyPressedFlag = false
        return ModifierKeyHandler.ModifierKeyResult(shouldUpdateStatusBar = true)
    }

    fun handleCtrlKeyDown(
        keyCode: Int,
        isInputViewActive: Boolean,
        eventTime: Long,
        onNavModeDeactivated: (() -> Unit)? = null
    ): ModifierKeyHandler.ModifierKeyResult {
        if (ctrlPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val hadToggleState = ctrlState.latchActive || ctrlState.oneShot
        val isConsecutiveTap = registerModifierTap(keyCode)
        val baseResult = modifierKeyHandler.handleCtrlKeyDown(
            keyCode,
            ctrlState,
            isInputViewActive,
            isConsecutiveTap = isConsecutiveTap,
            allowDoubleTapLatch = true,
            eventTime = eventTime,
            onNavModeDeactivated
        )
        val toggledOff = hadToggleState && !ctrlState.latchActive && !ctrlState.oneShot
        suppressCtrlKeyUp = toggledOff
        ctrlPressed = !toggledOff
        ctrlPhysicallyPressed = !toggledOff
        return if (toggledOff) {
            baseResult.copy(shouldConsume = true)
        } else {
            baseResult
        }
    }

    fun handleCtrlKeyUp(keyCode: Int, eventTime: Long): ModifierKeyHandler.ModifierKeyResult {
        suppressCtrlKeyUp = false
        val result = modifierKeyHandler.handleCtrlKeyUp(keyCode, ctrlState, eventTime)
        ctrlPressed = false
        return result
    }

    fun handleAltKeyDown(keyCode: Int, eventTime: Long): ModifierKeyHandler.ModifierKeyResult {
        if (altPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val hadToggleState = altState.latchActive || altState.oneShot
        val isConsecutiveTap = registerModifierTap(keyCode)
        val baseResult = modifierKeyHandler.handleAltKeyDown(
            keyCode,
            altState,
            isConsecutiveTap = isConsecutiveTap,
            allowDoubleTapLatch = true,
            eventTime = eventTime
        )
        val toggledOff = hadToggleState && !altState.latchActive && !altState.oneShot
        suppressAltKeyUp = toggledOff
        altPressed = !toggledOff
        altPhysicallyPressed = !toggledOff
        return if (toggledOff) {
            baseResult.copy(shouldConsume = true)
        } else {
            baseResult
        }
    }

    fun handleAltKeyUp(keyCode: Int, eventTime: Long): ModifierKeyHandler.ModifierKeyResult {
        suppressAltKeyUp = false
        val result = modifierKeyHandler.handleAltKeyUp(keyCode, altState, eventTime)
        altPressed = false
        return result
    }

    /**
     * Clears Shift state (one-shot/caps) and, when requested, resets pressed tracking.
     * Used when a visual shift latch is explicitly toggled off by tapping Shift again.
     */
    fun clearShiftState(resetPressedState: Boolean = false) {
        shiftStateMachine.reset()
        shiftOneShotSource = ShiftOneShotSource.NONE
        if (resetPressedState) {
            shiftPressedFlag = false
            shiftPhysicallyPressedFlag = false
        }
    }

    /**
     * Clears Alt latch/one-shot state (used when Space should auto-disable Alt).
     * Optionally resets pressed flags if they are not reliable anymore.
     */
    fun clearAltState(resetPressedState: Boolean = false) {
        altState.latchActive = false
        altState.oneShot = false
        altState.lastReleaseTime = 0
        suppressAltKeyUp = false
        if (resetPressedState) {
            altState.pressed = false
            altState.physicallyPressed = false
        }
    }

    /**
     * Clears Ctrl state (latch/one-shot/nav mode flags) and, when requested,
     * resets pressed tracking to avoid leaving Ctrl active after shortcuts.
     */
    fun clearCtrlState(resetPressedState: Boolean = false) {
        ctrlState.latchActive = false
        ctrlState.oneShot = false
        ctrlState.latchFromNavMode = false
        ctrlState.lastReleaseTime = 0
        suppressCtrlKeyUp = false
        if (resetPressedState) {
            ctrlState.pressed = false
            ctrlState.physicallyPressed = false
        }
    }

    fun requestShiftOneShotFromAutoCap(): Boolean {
        val changed = shiftStateMachine.requestOneShot()
        if (changed || shiftStateMachine.state == ShiftState.ONE_SHOT) {
            shiftOneShotSource = ShiftOneShotSource.AUTO_CAP
        }
        return changed
    }

    fun consumeShiftOneShot(): Boolean {
        val consumed = shiftStateMachine.consumeOneShot()
        if (consumed) {
            shiftOneShotSource = ShiftOneShotSource.NONE
        }
        return consumed
    }

    fun resetModifiers(
        preserveNavMode: Boolean,
        onNavModeCancelled: () -> Unit
    ) {
        val savedCtrlLatch = if (preserveNavMode && (ctrlLatchActive || ctrlLatchFromNavMode)) {
            if (ctrlLatchActive) {
                ctrlLatchFromNavMode = true
                true
            } else {
                ctrlLatchFromNavMode
            }
        } else {
            false
        }

        shiftStateMachine.reset()
        shiftOneShotSource = ShiftOneShotSource.NONE
        shiftPressedFlag = false
        shiftPhysicallyPressedFlag = false
        lastKeyWasModifier = false
        lastModifierKeyCode = 0

        if (preserveNavMode && savedCtrlLatch) {
            ctrlLatchActive = true
            suppressCtrlKeyUp = false
        } else {
            if (ctrlLatchFromNavMode || ctrlLatchActive) {
                onNavModeCancelled()
            }
            modifierKeyHandler.resetCtrlState(ctrlState, preserveNavMode = false)
            suppressCtrlKeyUp = false
        }
        modifierKeyHandler.resetAltState(altState)
        suppressAltKeyUp = false
    }
}
