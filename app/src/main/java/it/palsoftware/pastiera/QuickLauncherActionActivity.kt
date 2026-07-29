package it.palsoftware.pastiera

import android.app.Activity
import android.os.Bundle
import it.palsoftware.pastiera.inputmethod.QuickLauncherOpener

class QuickLauncherActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
        finish()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
        finish()
    }

    private fun handleIntent() {
        if (intent?.action != ACTION_OPEN_QUICK_LAUNCHER) {
            return
        }
        QuickLauncherOpener.open(this)
    }

    companion object {
        const val ACTION_OPEN_QUICK_LAUNCHER = "it.palsoftware.pastiera.action.OPEN_QUICK_LAUNCHER"
    }
}
