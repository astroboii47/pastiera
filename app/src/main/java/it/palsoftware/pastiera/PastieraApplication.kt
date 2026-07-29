package it.palsoftware.pastiera

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build

class PastieraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPackageChangeMonitor.register(this)
        publishActionShortcuts()
    }

    private fun publishActionShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val softwareKeyboardModeShortcut = ShortcutInfo.Builder(this, SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID)
            .setShortLabel(getString(R.string.software_keyboard_mode_toggle_shortcut_short))
            .setLongLabel(getString(R.string.software_keyboard_mode_toggle_shortcut_long))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(SoftwareKeyboardModeActions.ACTION_TOGGLE)
                    .setClass(this, SoftwareKeyboardModeActionActivity::class.java)
            )
            .build()
        val quickLauncherShortcut = ShortcutInfo.Builder(this, QUICK_LAUNCHER_SHORTCUT_ID)
            .setShortLabel(getString(R.string.quick_launcher_shortcut_short))
            .setLongLabel(getString(R.string.quick_launcher_shortcut_long))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(QuickLauncherActionActivity.ACTION_OPEN_QUICK_LAUNCHER)
                    .setClass(this, QuickLauncherActionActivity::class.java)
            )
            .build()
        runCatching {
            val shortcutIds = listOf(SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID, QUICK_LAUNCHER_SHORTCUT_ID)
            shortcutManager.removeDynamicShortcuts(shortcutIds)
            shortcutManager.addDynamicShortcuts(listOf(softwareKeyboardModeShortcut, quickLauncherShortcut))
        }
    }

    companion object {
        private const val SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID = "software_keyboard_mode_toggle"
        private const val QUICK_LAUNCHER_SHORTCUT_ID = "quick_launcher_open"
    }
}
