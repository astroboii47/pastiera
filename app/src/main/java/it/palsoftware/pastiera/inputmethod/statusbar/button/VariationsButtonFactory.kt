package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks

class VariationsButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            callbacks.onVariationsToggleRequested?.invoke()
        }
        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) {
        if (view !is ImageView) return
        val isActive = (state as? ButtonState.VariationsState)?.isActive == true
        view.alpha = if (isActive) 1f else 0.85f
        view.background = if (isActive) {
            StatusBarButtonStyles.createButtonDrawable(
                heightPx = view.height.takeIf { it > 0 } ?: view.layoutParams?.height ?: 1,
                normalColor = StatusBarButtonStyles.PRESSED_BLUE
            )
        } else {
            StatusBarButtonStyles.createButtonDrawable(
                heightPx = view.height.takeIf { it > 0 } ?: view.layoutParams?.height ?: 1
            )
        }
    }

    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_emoji_symbols_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_variations_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
    }
}
