package it.palsoftware.pastiera

import android.app.Activity
import android.content.Intent
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
        when (intent?.action) {
            Intent.ACTION_CREATE_SHORTCUT -> returnShortcut()
            ACTION_OPEN_QUICK_LAUNCHER -> QuickLauncherOpener.open(this)
        }
    }

    private fun returnShortcut() {
        val shortcutIntent = Intent(ACTION_OPEN_QUICK_LAUNCHER)
            .setClass(this, QuickLauncherActionActivity::class.java)
        val result = Intent()
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.quick_launcher_shortcut_short))
            .putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
            )
        setResult(RESULT_OK, result)
    }

    companion object {
        const val ACTION_OPEN_QUICK_LAUNCHER = "it.palsoftware.pastiera.action.OPEN_QUICK_LAUNCHER"
    }
}
