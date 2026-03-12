package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/**
 * Handles modifier key state management and double-tap detection
 * for Ctrl (one-shot/latch) and Alt (one-shot/latch).
 */
class ModifierKeyHandler(
    private val doubleTapThreshold: Long = 500L
) {
    data class CtrlState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false,
        var physicallyPressed: Boolean = false,
        var lastReleaseTime: Long = 0,
        var pendingLatchUntil: Long = 0,
        var latchFromNavMode: Boolean = false,
        var suppressNextReleaseTime: Boolean = false
    )

    data class AltState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false,
        var physicallyPressed: Boolean = false,
        var lastReleaseTime: Long = 0,
        var pendingLatchUntil: Long = 0,
        var suppressNextReleaseTime: Boolean = false
    )

    data class ModifierKeyResult(
        val shouldConsume: Boolean = false,
        val shouldUpdateStatusBar: Boolean = false,
        val shouldRefreshStatusBar: Boolean = false
    )

    // ========== Ctrl Handling ==========

    fun handleCtrlKeyDown(
        keyCode: Int,
        state: CtrlState,
        isInputViewActive: Boolean,
        isConsecutiveTap: Boolean,
        allowDoubleTapLatch: Boolean,
        eventTime: Long,
        onNavModeDeactivated: (() -> Unit)? = null
    ): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_CTRL_LEFT && keyCode != KeyEvent.KEYCODE_CTRL_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            return ModifierKeyResult()
        }

        state.physicallyPressed = true
        when {
            state.latchActive -> {
                // Latch active: single tap disables it
                // Special handling for nav mode
                if (state.latchFromNavMode && !isInputViewActive) {
                    state.latchActive = false
                    state.latchFromNavMode = false
                    state.suppressNextReleaseTime = true
                    state.pendingLatchUntil = 0
                    onNavModeDeactivated?.invoke()
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldConsume = true)
                } else if (!state.latchFromNavMode) {
                    state.latchActive = false
                    state.suppressNextReleaseTime = true
                    state.pendingLatchUntil = 0
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Nav mode in text field (should not happen)
                    state.latchActive = false
                    state.latchFromNavMode = false
                    state.suppressNextReleaseTime = true
                    state.pendingLatchUntil = 0
                    onNavModeDeactivated?.invoke()
                    state.lastReleaseTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
            state.oneShot -> {
                if (allowDoubleTapLatch && state.pendingLatchUntil > 0 && eventTime <= state.pendingLatchUntil) {
                    state.oneShot = false
                    state.latchActive = true
                } else {
                    state.oneShot = false
                    state.suppressNextReleaseTime = true
                    state.pendingLatchUntil = if (allowDoubleTapLatch) eventTime + doubleTapThreshold else 0
                }
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            else -> {
                if (allowDoubleTapLatch && state.pendingLatchUntil > 0 && eventTime <= state.pendingLatchUntil) {
                    state.latchActive = true
                    state.pendingLatchUntil = 0
                } else {
                    state.pendingLatchUntil = if (allowDoubleTapLatch) eventTime + doubleTapThreshold else 0
                    state.oneShot = true
                }
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
        }
    }

    fun handleCtrlKeyUp(keyCode: Int, state: CtrlState, eventTime: Long): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_CTRL_LEFT && keyCode != KeyEvent.KEYCODE_CTRL_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            if (state.suppressNextReleaseTime) {
                state.lastReleaseTime = 0
                state.suppressNextReleaseTime = false
            } else {
                state.lastReleaseTime = eventTime
            }
            state.pressed = false
            state.physicallyPressed = false
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Alt Handling ==========

    fun handleAltKeyDown(
        keyCode: Int,
        state: AltState,
        isConsecutiveTap: Boolean,
        allowDoubleTapLatch: Boolean,
        eventTime: Long
    ): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            return ModifierKeyResult()
        }

        state.physicallyPressed = true
        when {
            state.latchActive -> {
                // Latch active: single tap disables it
                state.latchActive = false
                state.suppressNextReleaseTime = true
                state.pendingLatchUntil = 0
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            state.oneShot -> {
                if (allowDoubleTapLatch && state.pendingLatchUntil > 0 && eventTime <= state.pendingLatchUntil) {
                    state.oneShot = false
                    state.latchActive = true
                } else {
                    state.oneShot = false
                    state.suppressNextReleaseTime = true
                    state.pendingLatchUntil = if (allowDoubleTapLatch) eventTime + doubleTapThreshold else 0
                }
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            else -> {
                if (allowDoubleTapLatch && state.pendingLatchUntil > 0 && eventTime <= state.pendingLatchUntil) {
                    state.latchActive = true
                    state.pendingLatchUntil = 0
                } else {
                    state.pendingLatchUntil = if (allowDoubleTapLatch) eventTime + doubleTapThreshold else 0
                    state.oneShot = true
                }
                state.lastReleaseTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
        }
    }

    fun handleAltKeyUp(keyCode: Int, state: AltState, eventTime: Long): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            if (state.suppressNextReleaseTime) {
                state.lastReleaseTime = 0
                state.suppressNextReleaseTime = false
            } else {
                state.lastReleaseTime = eventTime
            }
            state.pressed = false
            state.physicallyPressed = false
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Reset Helpers ==========

    fun resetCtrlState(state: CtrlState, preserveNavMode: Boolean = false) {
        if (!preserveNavMode || !state.latchFromNavMode) {
            state.latchActive = false
            state.latchFromNavMode = false
        }
        state.pressed = false
        state.oneShot = false
        state.lastReleaseTime = 0
        state.pendingLatchUntil = 0
        state.suppressNextReleaseTime = false
    }

    fun resetAltState(state: AltState) {
        state.pressed = false
        state.oneShot = false
        state.latchActive = false
        state.lastReleaseTime = 0
        state.pendingLatchUntil = 0
        state.suppressNextReleaseTime = false
    }
}
