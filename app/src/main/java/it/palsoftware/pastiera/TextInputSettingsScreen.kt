package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.R

/**
 * Text Input settings screen.
 */
@Composable
fun TextInputSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showSnippetsScreen by remember { mutableStateOf(false) }
    
    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }

    var autoCapitalizeAfterPeriod by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeAfterPeriod(context))
    }

    var doubleSpaceToPeriod by remember {
        mutableStateOf(SettingsManager.getDoubleSpaceToPeriod(context))
    }

    var clearAltOnSpace by remember {
        mutableStateOf(SettingsManager.getClearAltOnSpace(context))
    }

    var shiftKeymapperGuardEnabled by remember {
        mutableStateOf(SettingsManager.getShiftKeymapperGuardEnabled(context))
    }

    var ctrlKeymapperGuardEnabled by remember {
        mutableStateOf(SettingsManager.getCtrlKeymapperGuardEnabled(context))
    }

    var altKeymapperGuardEnabled by remember {
        mutableStateOf(SettingsManager.getAltKeymapperGuardEnabled(context))
    }

    var emojiShortcodeEnabled by remember {
        mutableStateOf(SettingsManager.getEmojiShortcodeEnabled(context))
    }

    var symbolShortcodeEnabled by remember {
        mutableStateOf(SettingsManager.getSymbolShortcodeEnabled(context))
    }

    var snippetsEnabled by remember {
        mutableStateOf(SettingsManager.getSnippetsEnabled(context))
    }

    var snippetsTrigger by remember {
        mutableStateOf(SettingsManager.getSnippetsTrigger(context))
    }

    var klipyApiKey by remember {
        mutableStateOf(SettingsManager.getKlipyApiKey(context))
    }

    var showKlipyDialog by remember { mutableStateOf(false) }
    var pendingKlipyApiKey by remember { mutableStateOf(klipyApiKey) }
    var showSnippetsTriggerDialog by remember { mutableStateOf(false) }
    var pendingSnippetsTrigger by remember { mutableStateOf(snippetsTrigger) }
    
    var swipeToDelete by remember {
        mutableStateOf(SettingsManager.getSwipeToDelete(context))
    }
    
    var autoShowKeyboard by remember {
        mutableStateOf(SettingsManager.getAutoShowKeyboard(context))
    }
    
    var altCtrlSpeechShortcut by remember {
        mutableStateOf(SettingsManager.getAltCtrlSpeechShortcutEnabled(context))
    }

    var titan2LayoutEnabled by remember {
        mutableStateOf(SettingsManager.isTitan2LayoutEnabled(context))
    }

    var shiftBackspaceAction by remember {
        mutableStateOf(SettingsManager.getShiftBackspaceAction(context))
    }

    var altBackspaceAction by remember {
        mutableStateOf(SettingsManager.getAltBackspaceAction(context))
    }
    var ctrlBackspaceAction by remember {
        mutableStateOf(SettingsManager.getCtrlBackspaceAction(context))
    }
    var symDeleteAction by remember {
        mutableStateOf(SettingsManager.getSymDeleteAction(context))
    }

    var showShiftBackspaceActionDialog by remember { mutableStateOf(false) }
    var showAltBackspaceActionDialog by remember { mutableStateOf(false) }
    var showCtrlBackspaceActionDialog by remember { mutableStateOf(false) }
    var showSymDeleteActionDialog by remember { mutableStateOf(false) }

    var backspaceAtStartDelete by remember {
        mutableStateOf(SettingsManager.getBackspaceAtStartDelete(context))
    }
    
    // Handle system back button
    BackHandler {
        if (showSnippetsScreen) {
            showSnippetsScreen = false
        } else {
            onBack()
        }
    }

    if (showSnippetsScreen) {
        SnippetsScreen(
            modifier = modifier,
            onBack = { showSnippetsScreen = false }
        )
        return
    }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_category_text_input),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.klipy_api_key_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = if (klipyApiKey.isBlank()) {
                                stringResource(R.string.klipy_api_key_missing_description)
                            } else {
                                stringResource(R.string.klipy_api_key_configured_description)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    TextButton(onClick = {
                        pendingKlipyApiKey = klipyApiKey
                        showKlipyDialog = true
                    }) {
                        Text(stringResource(R.string.klipy_api_key_edit))
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.snippets_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.snippets_setting_description, snippetsTrigger),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = snippetsEnabled,
                        onCheckedChange = { enabled ->
                            snippetsEnabled = enabled
                            SettingsManager.setSnippetsEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.snippets_manage_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.snippets_manage_description, snippetsTrigger),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    TextButton(onClick = {
                        pendingSnippetsTrigger = snippetsTrigger
                        showSnippetsTriggerDialog = true
                    }) {
                        Text(stringResource(R.string.snippets_trigger_button))
                    }
                    TextButton(onClick = { showSnippetsScreen = true }) {
                        Text(stringResource(R.string.snippets_manage_button))
                    }
                }
            }

            Text(
                text = stringResource(R.string.text_input_section_typing),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )

            // Auto Capitalize
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_capitalize_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = autoCapitalizeFirstLetter,
                        onCheckedChange = { enabled ->
                            autoCapitalizeFirstLetter = enabled
                            SettingsManager.setAutoCapitalizeFirstLetter(context, enabled)
                        }
                    )
                }
            }

            // Auto Capitalize After Period
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_capitalize_after_period_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.auto_capitalize_after_period_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = autoCapitalizeAfterPeriod,
                        onCheckedChange = { enabled ->
                            autoCapitalizeAfterPeriod = enabled
                            SettingsManager.setAutoCapitalizeAfterPeriod(context, enabled)
                        }
                    )
                }
            }

            // Double Space to Period
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.double_space_to_period_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.double_space_to_period_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = doubleSpaceToPeriod,
                        onCheckedChange = { enabled ->
                            doubleSpaceToPeriod = enabled
                            SettingsManager.setDoubleSpaceToPeriod(context, enabled)
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.text_input_section_shortcuts),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_emoji_emotions_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.emoji_shortcodes_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.emoji_shortcodes_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = emojiShortcodeEnabled,
                        onCheckedChange = { enabled ->
                            emojiShortcodeEnabled = enabled
                            SettingsManager.setEmojiShortcodeEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.symbol_shortcodes_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.symbol_shortcodes_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = symbolShortcodeEnabled,
                        onCheckedChange = { enabled ->
                            symbolShortcodeEnabled = enabled
                            SettingsManager.setSymbolShortcodeEnabled(context, enabled)
                        }
                    )
                }
            }

            // Clear Alt on Space
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.clear_alt_on_space_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.clear_alt_on_space_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = clearAltOnSpace,
                        onCheckedChange = { enabled ->
                            clearAltOnSpace = enabled
                            SettingsManager.setClearAltOnSpace(context, enabled)
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.text_input_section_modifiers),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )

            // Keymapper Guard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.keymapper_modifier_guard_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.keymapper_modifier_guard_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.keymapper_guard_shift_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Switch(
                            checked = shiftKeymapperGuardEnabled,
                            onCheckedChange = { enabled ->
                                shiftKeymapperGuardEnabled = enabled
                                SettingsManager.setShiftKeymapperGuardEnabled(context, enabled)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.keymapper_guard_ctrl_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Switch(
                            checked = ctrlKeymapperGuardEnabled,
                            onCheckedChange = { enabled ->
                                ctrlKeymapperGuardEnabled = enabled
                                SettingsManager.setCtrlKeymapperGuardEnabled(context, enabled)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.keymapper_guard_alt_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Switch(
                            checked = altKeymapperGuardEnabled,
                            onCheckedChange = { enabled ->
                                altKeymapperGuardEnabled = enabled
                                SettingsManager.setAltKeymapperGuardEnabled(context, enabled)
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.text_input_section_keyboard_actions),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )

            // Swipe to Delete
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.swipe_to_delete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = swipeToDelete,
                        onCheckedChange = { enabled ->
                            swipeToDelete = enabled
                            SettingsManager.setSwipeToDelete(context, enabled)
                        }
                    )
                }
            }
        
            // Auto Show Keyboard
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_show_keyboard_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = autoShowKeyboard,
                        onCheckedChange = { enabled ->
                            autoShowKeyboard = enabled
                            SettingsManager.setAutoShowKeyboard(context, enabled)
                        }
                    )
                }
            }

            // Alt+Ctrl Speech Recognition Shortcut
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alt_ctrl_speech_shortcut_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.alt_ctrl_speech_shortcut_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = altCtrlSpeechShortcut,
                        onCheckedChange = { enabled ->
                            altCtrlSpeechShortcut = enabled
                            SettingsManager.setAltCtrlSpeechShortcutEnabled(context, enabled)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.delete_alternatives_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.delete_alternatives_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shift_backspace_action_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (shiftBackspaceAction) {
                                SettingsManager.DeleteShortcutAction.NORMAL ->
                                    stringResource(R.string.alt_backspace_action_normal)
                                SettingsManager.DeleteShortcutAction.FORWARD_DELETE ->
                                    stringResource(R.string.alt_backspace_action_forward)
                                SettingsManager.DeleteShortcutAction.DELETE_WORD ->
                                    stringResource(R.string.alt_backspace_action_word)
                                SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT ->
                                    stringResource(R.string.alt_backspace_action_system)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showShiftBackspaceActionDialog = true }) {
                        Text(stringResource(R.string.change))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alt_backspace_delete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (altBackspaceAction) {
                                SettingsManager.DeleteShortcutAction.NORMAL ->
                                    stringResource(R.string.alt_backspace_action_normal)
                                SettingsManager.DeleteShortcutAction.FORWARD_DELETE ->
                                    stringResource(R.string.alt_backspace_action_forward)
                                SettingsManager.DeleteShortcutAction.DELETE_WORD ->
                                    stringResource(R.string.alt_backspace_action_word)
                                SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT ->
                                    stringResource(R.string.alt_backspace_action_system)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showAltBackspaceActionDialog = true }) {
                        Text(stringResource(R.string.change))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ctrl_backspace_action_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (ctrlBackspaceAction) {
                                SettingsManager.DeleteShortcutAction.NORMAL ->
                                    stringResource(R.string.alt_backspace_action_normal)
                                SettingsManager.DeleteShortcutAction.FORWARD_DELETE ->
                                    stringResource(R.string.alt_backspace_action_forward)
                                SettingsManager.DeleteShortcutAction.DELETE_WORD ->
                                    stringResource(R.string.alt_backspace_action_word)
                                SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT ->
                                    stringResource(R.string.alt_backspace_action_system)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showCtrlBackspaceActionDialog = true }) {
                        Text(stringResource(R.string.change))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sym_delete_action_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (symDeleteAction) {
                                SettingsManager.DeleteShortcutAction.NORMAL,
                                SettingsManager.DeleteShortcutAction.FORWARD_DELETE ->
                                    stringResource(R.string.sym_delete_action_normal)
                                SettingsManager.DeleteShortcutAction.DELETE_WORD ->
                                    stringResource(R.string.alt_backspace_action_word)
                                SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT ->
                                    stringResource(R.string.alt_backspace_action_system)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showSymDeleteActionDialog = true }) {
                        Text(stringResource(R.string.change))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.backspace_at_start_delete_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = backspaceAtStartDelete,
                        onCheckedChange = { enabled ->
                            backspaceAtStartDelete = enabled
                            SettingsManager.setBackspaceAtStartDelete(context, enabled)
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.delete_alternatives_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showKlipyDialog) {
        AlertDialog(
            onDismissRequest = { showKlipyDialog = false },
            title = {
                Text(text = stringResource(R.string.klipy_api_key_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.klipy_api_key_dialog_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pendingKlipyApiKey,
                        onValueChange = { pendingKlipyApiKey = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.klipy_api_key_field_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    klipyApiKey = pendingKlipyApiKey.trim()
                    SettingsManager.setKlipyApiKey(context, klipyApiKey)
                    showKlipyDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        klipyApiKey = ""
                        pendingKlipyApiKey = ""
                        SettingsManager.setKlipyApiKey(context, "")
                        showKlipyDialog = false
                    }) {
                        Text(stringResource(R.string.clear))
                    }
                    TextButton(onClick = { showKlipyDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    if (showSnippetsTriggerDialog) {
        AlertDialog(
            onDismissRequest = { showSnippetsTriggerDialog = false },
            title = { Text(stringResource(R.string.snippets_trigger_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = pendingSnippetsTrigger,
                    onValueChange = { value ->
                        pendingSnippetsTrigger = value.takeLast(1)
                    },
                    label = { Text(stringResource(R.string.snippets_trigger_label)) },
                    supportingText = {
                        Text(stringResource(R.string.snippets_trigger_dialog_description))
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = pendingSnippetsTrigger.trim().take(1).ifEmpty { "!" }
                    snippetsTrigger = normalized
                    pendingSnippetsTrigger = normalized
                    SettingsManager.setSnippetsTrigger(context, normalized)
                    showSnippetsTriggerDialog = false
                }) {
                    Text(stringResource(R.string.snippets_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSnippetsTriggerDialog = false }) {
                    Text(stringResource(R.string.auto_correct_cancel))
                }
            }
        )
    }

    if (showAltBackspaceActionDialog) {
        AlertDialog(
            onDismissRequest = { showAltBackspaceActionDialog = false },
            title = { Text(stringResource(R.string.alt_backspace_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SettingsManager.DeleteShortcutAction.NORMAL to
                            stringResource(R.string.alt_backspace_action_normal),
                        SettingsManager.DeleteShortcutAction.FORWARD_DELETE to
                            stringResource(R.string.alt_backspace_action_forward),
                        SettingsManager.DeleteShortcutAction.DELETE_WORD to
                            stringResource(R.string.alt_backspace_action_word),
                        SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT to
                            stringResource(R.string.alt_backspace_action_system)
                    ).forEach { (action, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = altBackspaceAction == action,
                                onClick = {
                                    altBackspaceAction = action
                                    SettingsManager.setAltBackspaceAction(context, action)
                                    showAltBackspaceActionDialog = false
                                }
                            )
                            TextButton(
                                onClick = {
                                    altBackspaceAction = action
                                    SettingsManager.setAltBackspaceAction(context, action)
                                    showAltBackspaceActionDialog = false
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAltBackspaceActionDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    if (showCtrlBackspaceActionDialog) {
        AlertDialog(
            onDismissRequest = { showCtrlBackspaceActionDialog = false },
            title = { Text(stringResource(R.string.ctrl_backspace_action_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SettingsManager.DeleteShortcutAction.NORMAL to
                            stringResource(R.string.alt_backspace_action_normal),
                        SettingsManager.DeleteShortcutAction.FORWARD_DELETE to
                            stringResource(R.string.alt_backspace_action_forward),
                        SettingsManager.DeleteShortcutAction.DELETE_WORD to
                            stringResource(R.string.alt_backspace_action_word),
                        SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT to
                            stringResource(R.string.alt_backspace_action_system)
                    ).forEach { (action, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ctrlBackspaceAction == action,
                                onClick = {
                                    ctrlBackspaceAction = action
                                    SettingsManager.setCtrlBackspaceAction(context, action)
                                    showCtrlBackspaceActionDialog = false
                                }
                            )
                            TextButton(
                                onClick = {
                                    ctrlBackspaceAction = action
                                    SettingsManager.setCtrlBackspaceAction(context, action)
                                    showCtrlBackspaceActionDialog = false
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCtrlBackspaceActionDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    if (showShiftBackspaceActionDialog) {
        AlertDialog(
            onDismissRequest = { showShiftBackspaceActionDialog = false },
            title = { Text(stringResource(R.string.shift_backspace_action_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SettingsManager.DeleteShortcutAction.NORMAL to
                            stringResource(R.string.alt_backspace_action_normal),
                        SettingsManager.DeleteShortcutAction.FORWARD_DELETE to
                            stringResource(R.string.alt_backspace_action_forward),
                        SettingsManager.DeleteShortcutAction.DELETE_WORD to
                            stringResource(R.string.alt_backspace_action_word),
                        SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT to
                            stringResource(R.string.alt_backspace_action_system)
                    ).forEach { (action, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = shiftBackspaceAction == action,
                                onClick = {
                                    shiftBackspaceAction = action
                                    SettingsManager.setShiftBackspaceAction(context, action)
                                    showShiftBackspaceActionDialog = false
                                }
                            )
                            TextButton(
                                onClick = {
                                    shiftBackspaceAction = action
                                    SettingsManager.setShiftBackspaceAction(context, action)
                                    showShiftBackspaceActionDialog = false
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShiftBackspaceActionDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    if (showSymDeleteActionDialog) {
        AlertDialog(
            onDismissRequest = { showSymDeleteActionDialog = false },
            title = { Text(stringResource(R.string.sym_delete_action_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SettingsManager.DeleteShortcutAction.NORMAL to
                            stringResource(R.string.sym_delete_action_normal),
                        SettingsManager.DeleteShortcutAction.DELETE_WORD to
                            stringResource(R.string.alt_backspace_action_word),
                        SettingsManager.DeleteShortcutAction.SYSTEM_DEFAULT to
                            stringResource(R.string.alt_backspace_action_system)
                    ).forEach { (action, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = symDeleteAction == action,
                                onClick = {
                                    symDeleteAction = action
                                    SettingsManager.setSymDeleteAction(context, action)
                                    showSymDeleteActionDialog = false
                                }
                            )
                            TextButton(
                                onClick = {
                                    symDeleteAction = action
                                    SettingsManager.setSymDeleteAction(context, action)
                                    showSymDeleteActionDialog = false
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSymDeleteActionDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }
}
