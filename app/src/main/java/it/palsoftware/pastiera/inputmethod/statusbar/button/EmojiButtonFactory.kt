package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating the emoji picker button.
 * Opens the Gboard-like emoji picker (symPage 4).
 */
class EmojiButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        refreshCallbacks(button, callbacks)
        return ButtonCreationResult(view = button)
    }

    fun refreshCallbacks(button: View, callbacks: StatusBarCallbacks) {
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            callbacks.onEmojiPickerRequested?.invoke()
        }
        button.setOnLongClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            callbacks.onEmojiMediaPickerRequested?.invoke()
            button.isPressed = false
            button.refreshDrawableState()
            true
        }
    }
    
    override fun update(view: View, state: ButtonState) {
        // No state to update for emoji button
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_emoji_emotions_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_emoji_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
}
