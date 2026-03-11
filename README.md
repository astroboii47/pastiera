
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/astroboii47)
# Pastiera Enhanced

Pastiera Enhanced is a Titan 2-focused fork of [Pastiera](https://github.com/palsoftware/pastiera), based on upstream tag `nightly/v0.85-nightly.20260307.020619`.

It keeps Pastiera's physical-keyboard workflow and adds emoji shortcodes, a Klipy GIF page, and a cleaner shared row for suggestions and variations.

## This fork
- Adds emoji shortcodes with inline suggestions.
- Adds a Klipy-powered GIF SYM page with trending/search, animated previews, tap-to-preview before send, rich GIF send when supported, and link fallback when not.
- Merges typing suggestions into the existing variations row instead of stacking a separate suggestions row, while keeping the left and right status buttons visible.
- Tuned for Unihertz Titan 2 usage.

## Credits
- Upstream base project: [palsoftware/pastiera](https://github.com/palsoftware/pastiera)
- TypeQ25 reference and inspiration: [sriharshaKanukuntla/TypeQ25](https://github.com/sriharshaKanukuntla/TypeQ25)

This fork's emoji shortcode feature was adapted with TypeQ25 as a reference, and the shortcode-related data/assets in this fork were added for that feature. The current clipboard flow uses Pastiera's built-in clipboard implementation rather than a retained TypeQ25 clipboard port.

## Klipy API key setup
This fork does not ship a private Klipy API key. Each user should use their own key.

1. Create a Klipy developer account and generate a test API key at [klipy.com/docs](https://klipy.com/docs).
2. Install the APK.
3. Open `Pastiera Settings` -> `Text Input`.
4. Find the Klipy GIF API key setting.
5. Paste your API key there.
6. Enable the GIF SYM page if it is not already enabled.
7. Open the GIF page from SYM and test trending/search.

Notes:
- Klipy test keys are rate-limited, but they are usable for personal testing and normal keyboard use.
- Real GIF send works only in apps that accept rich content from keyboards.
- Unsupported apps fall back to inserting or copying a GIF link.

## Quick overview
- Compact status bar with LED indicators for Shift/SYM/Ctrl/Alt, variants/suggestions bar, and swipe-pad gestures to move the cursor.
- Multiple layouts (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, etc.) fully configurable; JSON import/export directly from the app. A web frontend for editing layouts is available at https://pastierakeyedit.vercel.app/
- SYM pages usable via touch or physical keys (emoji + symbols), reorderable/disableable, with an integrated layout editor.
- Clipboard support with multiple entries and pinnable items.
- Support for dictionary based suggestions/autocorrections plus swipe gestures to accept a suggestion (requires Shizuku).
- Full backup/restore (settings, layouts, variations, dictionaries), UI translated into multiple languages, and built-in GitHub update checks.

## Typing and modifiers
- Long press on a key can input Alt+key or Shift+Key (uppercase) timing configurable.
- Shift/Ctrl/Alt in one-shot or lock mode (double tap), option to clear Alt on space.
- Current behavior note: `Ctrl` used as a physically held shortcut modifier (e.g. hold `Ctrl` + `A`) intentionally follows the app shortcut path and is not the same flow as Nav Mode (`Ctrl` double-tap latch outside text fields). Nav Mode remains a separate implementation/state.
- Multi-tap support for keys with layout-defined variants (e.g. Cyrillic)
- Standard shortcuts: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, Ctrl+E/D/S/F or I/J/K/L for arrows, Ctrl+W/R for selection, Ctrl+T for Tab, Ctrl+Y/H for Page Up/Down, Ctrl+Q for Esc (all customizable in the Customize Nav screen).

## QOL features
- **Nav Mode**: double tap Ctrl outside text fields to use ESDF or IJKL as arrows, and many more useful mappings (everything is customizable in Customize Nav Mode settings)
- **Variations bar as swipe pad**: drag to move the cursor, with adjustable threshold.
- **Launcher shortcuts**: in the launcher, press a letter to open/assign an app.
- **Power shortcuts**: press SYM (5s timeout) then a letter to use the same shortcuts anywhere, even outside the launcher.
- Change language with a tap on language code in the status bar, longpress to enter pastiera settings

## Keyboard layouts
- Included layouts: qwerty, azerty, qwertz, greek, arabic, russian/armenian phonetic translit, plus dedicated Alt maps for Titan 2.
- Layout switching: select from the enabled layouts list (configurable).
- Multi-tap support and mapping for complex characters.
- JSON import/export directly from the app, with visual preview and list management (enable/disable, delete).
- Layout maps are stored in `files/keyboard_layouts` and can also be edited manually. A web frontend for editing layouts is available at https://pastierakeyedit.vercel.app/

## Symbols, emoji, and variations
- Two touch-based SYM pages (emoji + symbols): reorderable/enableable, auto-close after input, customizable keycaps.
- In-app SYM editor with emoji grid and Unicode picker.
- Variations bar above the keyboard: shows accents/variants of the last typed letter or static sets (utility/email) when needed.
- Dedicated variations editor to replace/add variants via JSON or Unicode picker; optional static bar.

## Suggestions and autocorrection
- Experimental support for dictionary based autocorrection/suggestions.
- User dictionary with search and edit abilities.
- Per-language auto substitution editor, quick search, and a global “Pastiera Recipes” set shared across all languages.
- Change language/keymap with a tap on the language code button or `Ctrl+Space`.

## Comfort and extra input
- Double space -> period + space + uppercase.
- Swipe left on the keyboard to delete a word (Titan 2).
- Optional Alt+Ctrl shortcut to start Google Voice Typing; microphone always available on the variants bar.
- Compact status bar to minimize vertical space. With on-screen keyboard disabled from the IME selector, it uses even less space (aka Pastierina mode).
- Translated UI (it/en/de/es/fr/pl/ru/hy) and onboarding tutorial.

## Backup, updates, and data
- UI-based backup/restore in ZIP format: includes preferences, custom layouts, variations, SYM/Ctrl maps, and user dictionaries.
- Restore merges saved variations with defaults to avoid losing newly added keys.
- Built-in GitHub update check when opening settings (with option to ignore a release).
- Customizable files in `files/`: `variations.json`, `ctrl_key_mappings.json`, `sym_key_mappings*.json`, `keyboard_layouts/*.json`, user dictionaries.
- Android autobackup function.

## Installation
1. Download the APK from this fork's [GitHub Releases](https://github.com/astroboii47/pastiera/releases).
2. Install the APK on your device.
3. Android Settings → System → Languages & input → Virtual keyboard → Manage keyboards.
4. Enable “Pastiera” and select it from the input selector when typing.
5. If you want GIF search, add your own Klipy API key in `Settings -> Text Input`.

## Requirements
- Android 10 (API 29) or higher.
- Device with a physical keyboard (profiled on Unihertz Titan 2, adaptable via JSON).

## Development
- Build debug APK:
  - `./gradlew :app:assembleDebug`
- Run main unit tests:
  - `./gradlew :app:testDebugUnitTest`

## Releases
- Publish APKs through GitHub Releases on this fork.
