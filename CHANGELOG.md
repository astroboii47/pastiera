# Changelog

## Pastiera Enhanced 4.5

Builds on this branch are posted here first for faster troubleshooting and testing. These changes are intended to be split into pull requests against the main Pastiera branch once they are stable enough for upstream review.

### Added
- Optional Raycast-style emoji and media picker.
- Favourites and recent items in the media picker, with quick access to GIFs, stickers, local media, and packs.
- Saved custom emoji font slots, including automatic migration of the existing Apple emoji font.
- More emoji search terms across all supported languages.
- Master switch for Quick Launcher shortcuts.

### Fixed and Improved
- Improved emoji search layout, selection, keyboard navigation, category menus, scrolling, and background fade.
- Improved media picker layout and category menus to match the emoji picker.
- Fixed missing and oversized emoji when using custom emoji fonts, including better handling of multi-person emoji.
- Fixed local GIF sending in Messenger by using the current message field when the media is ready.
- Fixed emoji and media search focus handling in Messenger.
- Fixed Quick Launcher shortcuts firing after they were disabled.
- Fixed occasional Alt characters appearing after an input field restarts.
- Fixed auto-capitalisation not returning after sending a message in WhatsApp and similar apps.

## Pastiera Enhanced 4.4

Builds on this branch are posted here first for faster troubleshooting and testing. These changes are intended to be split into pull requests against the main Pastiera branch once they are stable enough for upstream review.

### Added
- WeChat-style emoji pack tab in the emoji picker.
- Import support for local sticker packs.
- Optional Titan SYM-as-Ctrl behaviour in text fields.

### Fixed and Improved
- Fixed intermittent Titan SYM-as-Ctrl hold state by syncing the physical SYM down/up state through the Pastiera Enhanced accessibility helper.
- Cleaned up the accessibility helper naming and removed noisy SYM debug logging.
- Improved auto-capitalisation after typed punctuation and spaces.
- Improved GIF/sticker media loading, sizing, search behaviour, and fallback sending.
- Improved shortcode popup dismissal, including Back closing emoji shortcode popups.

## Pastiera Enhanced 4.1

Builds on this branch are posted here first for faster troubleshooting and testing. These changes are intended to be split into pull requests against the main Pastiera branch once they are stable enough for upstream review.

### Added
- Per-app keyboard themes for hardware and software keyboard modes.
- Searchable per-app theme manager with overridden apps pinned to the top.
- Direct theme picker for each app override.
- Saved theme clone, rename, and delete actions.

### Improved
- Key Tap color now drives accent-style keyboard highlights more consistently.
- Per-app overrides can link to saved themes, so app themes update when the saved theme is edited.
- Emoji picker Media tab stays available even when the separate GIF SYM page is disabled.

## Pastiera Enhanced 4.0

Pastiera Enhanced 4.0 is a rewrite of the Enhanced fork on top of the newer upstream Pastiera base.

Builds on this branch are posted here first for faster troubleshooting and testing. These changes are intended to be split into pull requests against the main Pastiera branch once they are stable enough for upstream review.

### Added
- Predictive text integration with next-word predictions, bundled common phrase fallback, local learning, and removable predictions.
- Unified Mode, which can show predictions inside the existing variations/status bar instead of stacking a separate prediction row.
- Snippets with searchable shortcut popups, plus improved emoji/symbol shortcode completion.
- Media picker support inside the emoji picker, including GIFs, stickers, and local images.
- Theme controls for key tap color and modifier strip thickness.

### Fixed and Improved
- Fixed media sending crashes and restored GIF/sticker/image sending fallback behavior.
- Improved prediction replacement after selecting suggestions and advanced predictions after accepted words.
- Improved shortcode/snippet popup theming, sizing, and unified-mode positioning.
- Improved SYM popup layout, symbol page padding, page cycling, and close controls.
- Ported Enhanced behavior onto the newer Pastiera base to make future upstream pull requests easier to split and review.

## New Features Pastiera 0.2

### Keyboard Enhancements
- **Swipe Pad Navigation**: The keyboard status bar now doubles as a swipe pad, allowing you to move the cursor by swiping
- **Touch-Enabled Emojis and Symbols**: Emojis and symbols on the SYM keyboard are now also directly touchable for easier input
- **Keyboard Layout Conversion**: Added support for converting between different keyboard layouts (AZERTY, QWERTZ, etc.)

### Auto-Capitalization
- **Smart Sentence Capitalization**: Automatically capitalizes the first letter after sentences ending with periods, exclamation marks, or question marks

### Settings & Customization
- **Customizable Navigation Mode**: Navigation mode and Ctrl+key assignments can now be configured directly from the app settings
- **Quick Settings Access**: Added a quick toggle button (gear icon) to access settings directly from the keyboard
- **Enhanced Dictionary Management**: 
  - Added search functionality in the dictionary corrections interface
  - Custom dictionary entries now appear at the top of the list for easier access
  - Ricette Pastiera: autocorrections that are valid in all the languages. (such as ppp-> %)
  - Added a lot of new unicode chara for sym layer page 2

### User Interface
- **UI Improvements**: Redesigned and improved the app's user interface, various issues solved (white font on light background in android light mode)
- **Multi-Language Support**: Added translations for multiple languages (may require manual review and corrections)

## Bug Fixes

- **Fixed Alt+Space Pop-up Issue**: Resolved a bug that caused an unwanted pop-up to appear when pressing Alt+Space or Alt+Letter+Space
- **Fixed Speech Recognition Focus**: Fixed an issue where Google Voice Typing would incorrectly shift focus to another app when activated


*This changelog covers all changes since the last release (v0.1-alpha).*
