package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.TextView
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.Configuration
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.emoji.EmojiRepository
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.data.emoji.EmojiSearchRepository
import it.palsoftware.pastiera.emoji.CustomEmojiFontManager
import it.palsoftware.pastiera.gif.KlipyGifClient
import it.palsoftware.pastiera.gif.KlipyGifResult
import it.palsoftware.pastiera.gif.KlipyMediaType
import it.palsoftware.pastiera.inputmethod.ui.InlineMediaSearchType
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Emoji picker view: single vertical list with section headers and bottom tabs.
 */
class EmojiPickerView(
    context: Context,
    private val onCloseRequested: (() -> Unit)? = null,
    private val onGifSelected: ((KlipyGifResult) -> Unit)? = null
) : FrameLayout(context) {

    private var currentInputConnection: InputConnection? = null
    private val recyclerView: RecyclerView
    private val searchField: EditText
    private val loadingView: ProgressBar
    private val emptyView: TextView
    private val tabScrollView: HorizontalScrollView
    private val tabRow: LinearLayout
    private val vertical: LinearLayout
    private val keyboardSwitcherButton: ImageView
    private val contentFrame: FrameLayout
    private val frostedBackgroundView: FrostedBackgroundView
    private val searchPanel: FrameLayout
    private val topFadeView: View
    private val searchBackButton: ImageView
    private val categoryLabel: TextView
    private val bottomHintLeft: TextView
    private val bottomHintRight: TextView
    private val searchToggleButton: ImageView
    private val closeButton: ImageView

    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadingJob: Job? = null

    private val compactHeight = dpToPx(220f)
    private val expandedHeight = dpToPx(340f)
    private val raycastStyle = SettingsManager.getEmojiPickerRaycastStyle(context)
    private val emojiSize = dpToPx(if (raycastStyle) 64f else 48f)
    private val spacing = dpToPx(if (raycastStyle) 8f else 4f)
    private val smallPadding = dpToPx(8f)
    private val recentsApplyTopThreshold = 0

    // Data for sections
    private var sectionItems: List<SectionItem> = emptyList()
    private var itemCategoryIds: List<String> = emptyList()
    private var headerPositions: Map<String, Int> = emptyMap()
    private var selectedCategoryId: String? = null
    private var isTabClickScroll = false
    private var pendingRecentsRefresh = false
    private var pendingRecentsRefreshRequiresTop = false
    private var pendingRecentsRefreshRequiresNotRecents = false
    private var scrollState = RecyclerView.SCROLL_STATE_IDLE
    private var topFadeVisible = false

    // Adapter
    private val sectionAdapter: SectionAdapter
    private val searchAdapter: SearchAdapter
    private val columns: Int
    private var regularCategories: List<EmojiRepository.EmojiCategory> = emptyList()
    private var searchIndex: EmojiSearchRepository.EmojiSearchIndex? = null
    private var searchQuery: String = ""
    private var searchJob: Job? = null
    private var isSearchMode: Boolean = false
    private var selectedSearchPosition: Int = 0
    private var selectedSectionPosition: Int = RecyclerView.NO_POSITION
    private var categoryPopup: PopupWindow? = null
    private var isSearchPanelVisible: Boolean = raycastStyle
    private var searchInputCaptureEnabled: Boolean = true
    private var pendingSearchReplacementRange: IntRange? = null
    private var tabCategoryIds: List<String> = emptyList()
    private var gifTabView: TextView? = null
    private var wechatTabView: ImageView? = null
    private var wechatEmojiItems: List<WechatEmojiItem> = emptyList()
    private var gifPickerView: GifPickerView? = null
    private var customEmojiTypeface = CustomEmojiFontManager.getTypeface(context)
    private val emojiAccentCache = mutableMapOf<String, Int?>()
    private var isMediaMode: Boolean = false
    private var lastSoftwareKeyboardHeightPx: Int? = null
    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyTheme()
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)

        // Calculate columns based on screen width
        val screenWidth = context.resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - smallPadding * 2
        columns = ((availableWidth + spacing) / (emojiSize + spacing))
            .coerceAtLeast(4)
            .coerceAtMost(if (raycastStyle) 7 else 10)

        // Layout container: vertical stack (recycler + bottom tabs)
        vertical = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, compactHeight)
        }

        searchField = EditText(context).apply {
            hint = context.getString(R.string.emoji_picker_search_placeholder)
            textSize = if (raycastStyle) 22f else 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = if (raycastStyle) ColorDrawable(Color.TRANSPARENT) else createSearchFieldBackground()
            val padH = dpToPx(if (raycastStyle) 6f else 8f)
            val padV = dpToPx(if (raycastStyle) 10f else 5f)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                showSoftInputOnFocus = false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val newQuery = s?.toString().orEmpty()
                    if (newQuery == searchQuery) return
                    searchQuery = newQuery
                    scheduleSearch()
                }
            })
            setOnClickListener {
                // A search-field tap must always make it the hardware-key target.
                // Toggling here made a second tap silently turn capture off.
                setSearchInputCaptureEnabled(true)
            }
        }
        setSearchInputCaptureEnabled(false)

        searchBackButton = ImageView(context).apply {
            setImageResource(R.drawable.keyboard_arrow_left_24)
            contentDescription = context.getString(R.string.close)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(38f), dpToPx(if (raycastStyle) 52f else 40f))
            setOnClickListener {
                onCloseRequested?.invoke()
            }
        }

        categoryLabel = TextView(context).apply {
            text = context.getString(R.string.emoji_picker_all_categories)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            setPadding(dpToPx(10f), 0, dpToPx(8f), 0)
            background = createCategoryChipBackground()
            val icon = categoryIconDrawable(R.drawable.ic_emoji_symbols_24)
            setCompoundDrawablesWithIntrinsicBounds(icon, null, categoryIconDrawable(R.drawable.keyboard_arrow_down_24), null)
            compoundDrawablePadding = dpToPx(6f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                categoryPopup?.dismiss() ?: showCategoryPopup()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(if (raycastStyle) 48f else 40f)
            )
        }

        searchPanel = FrameLayout(context).apply {
            visibility = if (raycastStyle) View.VISIBLE else View.GONE
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(10f), 0)
            if (raycastStyle) {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(searchBackButton)
                    addView(searchField, LinearLayout.LayoutParams(0, dpToPx(52f), 1f))
                    addView(categoryLabel)
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ))
            } else {
                addView(searchField, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply {
                    setMargins(smallPadding, 0, smallPadding, smallPadding)
                })
            }
        }

        closeButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.close)
            background = createCloseButtonBackground()
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(32f))
            setOnClickListener {
                onCloseRequested?.invoke()
            }
        }

        // RecyclerView with headers and emoji grid
        recyclerView = RecyclerView(context).apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setHasFixedSize(false)
            // Selection changes only alter tile backgrounds. Animating those changes can
            // briefly draw the old and new tiles together during rapid keyboard navigation.
            itemAnimator = null
            clipToPadding = false
            setPadding(
                smallPadding,
                if (raycastStyle) dpToPx(56f) else smallPadding,
                smallPadding,
                smallPadding + dpToPx(44f)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val gridLayoutManager = GridLayoutManager(context, columns, RecyclerView.VERTICAL, false)
        sectionAdapter = SectionAdapter(columns)
        searchAdapter = SearchAdapter()
        gridLayoutManager.spanSizeLookup = sectionAdapter.spanSizeLookup
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = sectionAdapter

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                when (parent.adapter?.getItemViewType(pos) ?: return) {
                    VIEW_TYPE_HEADER -> {
                        outRect.set(0, spacing, 0, spacing)
                    }
                    VIEW_TYPE_EMOJI -> {
                        val layoutParams = view.layoutParams as? GridLayoutManager.LayoutParams
                        val column = layoutParams?.spanIndex ?: 0
                        outRect.left = if (column == 0) 0 else spacing / 2
                        outRect.right = if (column == columns - 1) 0 else spacing / 2
                        outRect.top = spacing / 2
                        outRect.bottom = spacing / 2
                    }
                }
            }
        })

        // Scroll listener to sync tabs and apply pending recents updates
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                scrollState = newState
                updateTopFadeVisibility()
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isTabClickScroll = false
                    val lm = recyclerView.layoutManager as? GridLayoutManager
                    val firstVisible = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                    if (firstVisible != RecyclerView.NO_POSITION) {
                        updateSelectedTopEmoji(firstVisible)
                    }
                    maybeApplyPendingRecentsRefresh()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateTopFadeVisibility()
                if (isSearchMode) return
                if (isTabClickScroll) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return
                val categoryId = itemCategoryIds.getOrNull(firstVisible) ?: return
                if (categoryId != selectedCategoryId) {
                    selectedCategoryId = categoryId
                    updateTabsSelection()
                }
            }
        })

        // Loading and empty views (overlay)
        loadingView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.VISIBLE
        }
        emptyView = TextView(context).apply {
            text = context.getString(R.string.emoji_picker_error)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        // Tabs at bottom (above LEDs) - full width, no scroll
        val tabHeight = dpToPx(32f) // Height cap
        searchToggleButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_search_24)
            contentDescription = context.getString(R.string.emoji_picker_search_label)
            background = createTabBackground(true)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(tabHeight, tabHeight).apply {
                marginEnd = spacing
            }
            setOnClickListener {
                setMediaMode(false)
                setSearchPanelVisible(true)
            }
        }
        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabHeight
            )
            setPadding(smallPadding / 2, 0, smallPadding / 2, 0)
        }
        // Keep tabScrollView reference for compatibility but use it as a simple wrapper
        tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabHeight
            )
        }
        tabScrollView.addView(tabRow)
        keyboardSwitcherButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.close)
            background = createTabBackground(false)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(tabHeight, tabHeight).apply {
                marginStart = spacing
            }
        }

        frostedBackgroundView = FrostedBackgroundView(context).apply {
            visibility = if (raycastStyle) View.VISIBLE else View.GONE
        }

        contentFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(frostedBackgroundView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(recyclerView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                // Keep the viewport full-height. Top padding positions the initial
                // content, while clipToPadding=false lets it glide behind the
                // pinned header and fade smoothly during scrolling.
                topMargin = 0
            })
        }
        topFadeView = View(context).apply {
            background = createTopFadeBackground()
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(122f),
                Gravity.TOP
            )
        }
        (topFadeView.layoutParams as? FrameLayout.LayoutParams)?.topMargin = 0
        contentFrame.addView(topFadeView)
        if (raycastStyle) {
            contentFrame.addView(searchPanel, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
        }
        bottomHintLeft = TextView(context).apply {
            text = context.getString(R.string.emoji_picker_search_placeholder)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12f), 0, dpToPx(12f), 0)
            background = createHintPillBackground()
            visibility = if (raycastStyle) View.VISIBLE else View.GONE
        }
        bottomHintRight = TextView(context).apply {
            text = "Paste"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(dpToPx(14f), 0, dpToPx(14f), 0)
            background = createHintPillBackground()
            setCompoundDrawablesWithIntrinsicBounds(null, null, categoryIconDrawable(R.drawable.keyboard_return_24), null)
            compoundDrawablePadding = dpToPx(8f)
            visibility = if (raycastStyle) View.VISIBLE else View.GONE
        }
        contentFrame.addView(bottomHintLeft, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(36f),
            Gravity.BOTTOM or Gravity.START
        ).apply { setMargins(dpToPx(8f), 0, 0, dpToPx(8f)) })
        contentFrame.addView(bottomHintRight, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(36f),
            Gravity.BOTTOM or Gravity.END
        ).apply { setMargins(0, 0, dpToPx(8f), dpToPx(8f)) })
        if (!raycastStyle) {
            contentFrame.addView(searchPanel, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }
        vertical.addView(contentFrame)
        if (!raycastStyle) {
            vertical.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        tabHeight
                    )
                    addView(searchToggleButton)
                    addView(keyboardSwitcherButton)
                    addView(tabScrollView, LinearLayout.LayoutParams(0, tabHeight, 1f))
                    addView(closeButton)
                }
            )
        }

        addView(vertical)
        addView(loadingView)
        addView(emptyView)

        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            compactHeight
        )

        applyTheme()
        setSearchInputCaptureEnabled(raycastStyle)
        if (raycastStyle) {
            post { focusSearchField() }
        }
        loadCategories()
    }

    fun setInputConnection(connection: InputConnection?) {
        currentInputConnection = connection
    }

    fun activateSearchOnOpen() {
        if (!raycastStyle || isMediaMode) return
        isSearchPanelVisible = true
        searchPanel.visibility = View.VISIBLE
        setSearchInputCaptureEnabled(true)
        post { focusSearchField() }
    }

    fun showMediaTab() {
        if (onGifSelected == null) return
        setMediaMode(true)
    }

    fun showMediaSearch(type: InlineMediaSearchType, query: String) {
        if (onGifSelected == null) return
        setMediaMode(true, refreshOnOpen = false)
        gifPickerView?.showInlineSearch(type, query)
    }

    fun isMediaTabActive(): Boolean = isMediaMode

    fun handleMediaSearchKeyDown(event: KeyEvent): Boolean {
        return isMediaMode && gifPickerView?.handleSearchKeyDown(event) == true
    }

    fun shouldConsumeMediaSearchKeyUp(event: KeyEvent): Boolean {
        return isMediaMode && gifPickerView?.shouldConsumeSearchKeyUp(event) == true
    }

    fun disableMediaSearchInputCapture() {
        gifPickerView?.disableSearchInputCapture()
    }

    fun isMediaSearchInputActive(): Boolean {
        return isMediaMode && gifPickerView?.isSearchInputActive() == true
    }

    fun configureSoftwareKeyboardMode(heightPx: Int?, onKeyboardLayoutRequested: (() -> Unit)?) {
        lastSoftwareKeyboardHeightPx = heightPx
        val configuredHeight = if (it.palsoftware.pastiera.SettingsManager.getEmojiPickerExpandedHeight(context)) {
            expandedHeight
        } else {
            compactHeight
        }
        val targetHeight = heightPx?.takeIf { it > 0 } ?: configuredHeight
        updateHeight(targetHeight)
        keyboardSwitcherButton.visibility = if (onKeyboardLayoutRequested != null) View.VISIBLE else View.GONE
        keyboardSwitcherButton.setOnClickListener {
            onKeyboardLayoutRequested?.invoke()
        }
    }

    private fun updateHeight(heightPx: Int) {
        (layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)).also {
            it.height = heightPx
            layoutParams = it
        }
        (vertical.layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)).also {
            it.height = heightPx
            vertical.layoutParams = it
        }
    }

    fun refresh() {
        if (isMediaMode) {
            gifPickerView?.refresh()
        } else {
            loadCategories()
        }
    }

    fun isSearchInputActive(): Boolean {
        return isSearchPanelVisible && searchInputCaptureEnabled
    }

    fun createSearchInputConnection(): InputConnection? {
        if (!isSearchInputActive()) return null
        focusSearchField()
        val baseConnection = searchField.onCreateInputConnection(EditorInfo()) ?: return null
        return object : InputConnectionWrapper(baseConnection, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return handleSearchInputConnectionKeyEvent(event) || super.sendKeyEvent(event)
            }

            override fun performContextMenuAction(id: Int): Boolean {
                return searchField.onTextContextMenuItem(id) || super.performContextMenuAction(id)
            }
        }
    }

    /**
     * IME hardware keys do not automatically target this EditText.
     * Handle printable keys manually while emoji picker page is open.
     */
    fun handleSearchKeyDown(
        event: KeyEvent,
        ctrlActive: Boolean = event.isCtrlPressed,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (event.isAltPressed || event.isMetaPressed) return false
        if (ctrlActive) {
            focusSearchField()
            if (handleTextEditingCtrlShortcut(event.keyCode)) {
                return true
            }
            val ctrlEvent = event.withCtrlMeta()
            return searchField.onKeyShortcut(ctrlEvent.keyCode, ctrlEvent) ||
                searchField.dispatchKeyEvent(ctrlEvent)
        }
        focusSearchField()

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> moveEmojiSelection(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveEmojiSelection(1)
            KeyEvent.KEYCODE_DPAD_UP -> moveEmojiSelection(-columns)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveEmojiSelection(columns)
            KeyEvent.KEYCODE_DEL -> {
                val text = searchField.text ?: return true
                if (text.isEmpty()) return true
                val replacementRange = selectedSearchRange(text.length)
                val start = replacementRange?.first
                    ?: minOf(searchField.selectionStart, searchField.selectionEnd).coerceAtLeast(0)
                val end = replacementRange?.last?.plus(1)
                    ?: maxOf(searchField.selectionStart, searchField.selectionEnd).coerceAtMost(text.length)
                if (start < end) {
                    text.delete(start, end)
                    pendingSearchReplacementRange = null
                } else {
                    text.delete(text.length - 1, text.length)
                }
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                appendSearchText(" ")
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                insertCurrentSelection()
                true
            }
            else -> {
                val typedText = resolveTypedText?.invoke(event) ?: run {
                    val unicode = event.unicodeChar
                    if (unicode <= 0) {
                        return searchField.dispatchKeyEvent(event)
                    }
                    val ch = unicode.toChar()
                    if (Character.isISOControl(ch)) {
                        return searchField.dispatchKeyEvent(event)
                    }
                    ch.toString()
                }
                appendSearchText(typedText)
                true
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (event.isAltPressed || event.isMetaPressed) return false
        if (event.isCtrlPressed) {
            focusSearchField()
            return searchField.onKeyShortcut(event.keyCode, event) ||
                searchField.dispatchKeyEvent(event) ||
                isTextEditingCtrlShortcut(event.keyCode)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> {
                val unicode = event.unicodeChar
                unicode > 0 && !Character.isISOControl(unicode.toChar())
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent, ctrlActive: Boolean): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (ctrlActive) {
            return true
        }
        return shouldConsumeSearchKeyUp(event)
    }

    private fun handleSearchInputConnectionKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_PAGE_UP ||
                event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        }

        if (event.isCtrlPressed && handleTextEditingCtrlShortcut(event.keyCode)) {
            return true
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveSearchCursorBy(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveSearchCursorBy(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                setSearchSelection(0)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                setSearchSelection(searchField.text?.length ?: 0)
                true
            }
            else -> false
        }
    }

    private fun moveSearchCursorBy(delta: Int) {
        val text = searchField.text ?: return
        if (text.isEmpty()) {
            setSearchSelection(0)
            return
        }
        val anchor = if (delta < 0) {
            minOf(searchField.selectionStart, searchField.selectionEnd)
        } else {
            maxOf(searchField.selectionStart, searchField.selectionEnd)
        }.coerceIn(0, text.length)
        setSearchSelection((anchor + delta).coerceIn(0, text.length))
    }

    private fun setSearchSelection(index: Int) {
        val text = searchField.text ?: return
        Selection.setSelection(text, index.coerceIn(0, text.length))
        pendingSearchReplacementRange = null
    }

    private fun selectedSearchRange(textLength: Int): IntRange? {
        val selectionStart = searchField.selectionStart
        val selectionEnd = searchField.selectionEnd
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, textLength)
            val endExclusive = maxOf(selectionStart, selectionEnd).coerceIn(0, textLength)
            if (start < endExclusive) return start until endExclusive
        }
        return pendingSearchReplacementRange?.let { range ->
            val start = range.first.coerceIn(0, textLength)
            val endExclusive = (range.last + 1).coerceIn(0, textLength)
            if (start < endExclusive) start until endExclusive else null
        }
    }

    private fun isTextEditingCtrlShortcut(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_A ||
            keyCode == KeyEvent.KEYCODE_C ||
            keyCode == KeyEvent.KEYCODE_X ||
            keyCode == KeyEvent.KEYCODE_V
    }

    private fun handleTextEditingCtrlShortcut(keyCode: Int): Boolean {
        focusSearchField()
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                searchField.text?.let { text ->
                    Selection.selectAll(text)
                    pendingSearchReplacementRange = 0 until text.length
                }
                true
            }
            KeyEvent.KEYCODE_C -> searchField.onTextContextMenuItem(android.R.id.copy)
            KeyEvent.KEYCODE_X -> {
                pendingSearchReplacementRange = null
                searchField.onTextContextMenuItem(android.R.id.cut)
            }
            KeyEvent.KEYCODE_V -> {
                pendingSearchReplacementRange = null
                searchField.onTextContextMenuItem(android.R.id.paste)
            }
            else -> false
        }
    }

    private fun focusSearchField() {
        if (!searchField.hasFocus()) {
            searchField.requestFocus()
        }
    }

    private fun KeyEvent.withCtrlMeta(): KeyEvent {
        if (isCtrlPressed) {
            return this
        }
        return KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
            deviceId,
            scanCode,
            flags,
            source
        )
    }
    
    /**
     * Scrolls to the top of the emoji picker.
     * Recents updates are applied only when safe for UX.
     */
    fun scrollToTop() {
        recyclerView.post {
            recyclerView.scrollToPosition(0)
            // Don't force a refresh here to avoid UI jumps while scrolling.
        }
    }

    private fun loadCategories() {
        customEmojiTypeface = CustomEmojiFontManager.getTypeface(context)
        // Cancel any previous loading job to avoid race conditions
        loadingJob?.cancel()

        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        loadingJob = coroutineScope.launch {
            try {
                val recentCategory = withContext(Dispatchers.IO) { RecentEmojiManager.getRecentEmojiCategory(context) }
                val regularCategories = withContext(Dispatchers.IO) { EmojiRepository.getEmojiCategories(context) }
                val loadedSearchIndex = withContext(Dispatchers.IO) { EmojiSearchRepository.getSearchIndex(context) }
                val loadedWechatEmoji = withContext(Dispatchers.IO) { loadWechatEmojiItems() }
                this@EmojiPickerView.regularCategories = regularCategories
                this@EmojiPickerView.searchIndex = loadedSearchIndex
                this@EmojiPickerView.wechatEmojiItems = loadedWechatEmoji

                val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
                if (recentCategory != null) allCategories.add(recentCategory)
                allCategories.addAll(regularCategories)

                // Always reset to first category when loading
                selectedCategoryId = allCategories.firstOrNull()?.id

                buildSections(allCategories)
                updateTabs(allCategories)

                if (isMediaMode) {
                    loadingView.visibility = View.GONE
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    gifPickerView?.apply {
                        visibility = View.VISIBLE
                        bringToFront()
                    }
                    return@launch
                }

                loadingView.visibility = View.GONE
                if (allCategories.isEmpty()) {
                    emptyView.text = context.getString(R.string.emoji_picker_error)
                    emptyView.visibility = View.VISIBLE
                } else {
                    if (searchQuery.isNotBlank()) {
                        applySearchNow()
                    } else {
                        setSearchMode(false)
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        // Always start from top when opening emoji picker
                        recyclerView.scrollToPosition(0)
                    }
                }
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation to properly cancel coroutine
            } catch (e: Exception) {
                loadingView.visibility = View.GONE
                emptyView.text = context.getString(R.string.emoji_picker_error)
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            kotlinx.coroutines.delay(120)
            applySearchNow()
        }
    }

    private fun appendSearchText(text: String) {
        if (text.isEmpty()) return
        val editable = searchField.text ?: return
        val selectionStart = searchField.selectionStart
        val selectionEnd = searchField.selectionEnd
        val replacementRange = selectedSearchRange(editable.length)
        if (replacementRange != null) {
            val start = replacementRange.first
            val end = replacementRange.last + 1
            editable.replace(start, end, text)
            searchField.setSelection(start + text.length)
            pendingSearchReplacementRange = null
        } else if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
            val end = maxOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
            editable.replace(start, end, text)
            searchField.setSelection(start + text.length)
        } else {
            editable.append(text)
            searchField.setSelection(editable.length)
        }
    }

    fun disableSearchInputCapture() {
        setSearchInputCaptureEnabled(false)
    }

    private fun setSearchInputCaptureEnabled(enabled: Boolean) {
        searchInputCaptureEnabled = enabled
        searchField.isCursorVisible = enabled
        searchField.alpha = if (enabled) 1f else 0.75f
        if (enabled) {
            val editable = searchField.text
            if (editable != null) {
                searchField.setSelection(editable.length)
            }
        } else {
            searchField.clearFocus()
            pendingSearchReplacementRange = null
        }
    }

    private fun setSearchPanelVisible(visible: Boolean) {
        if (visible) {
            setMediaMode(false)
        }
        isSearchPanelVisible = visible
        searchPanel.visibility = if (visible) View.VISIBLE else View.GONE
        searchToggleButton.background = createTabBackground(visible)
        setSearchInputCaptureEnabled(visible)
        if (visible) {
            searchField.requestFocus()
        }
    }

    private fun setMediaMode(enabled: Boolean, refreshOnOpen: Boolean = true) {
        if (isMediaMode == enabled) {
            updateTabsSelection()
            return
        }
        isMediaMode = enabled
        if (enabled) {
            setSearchInputCaptureEnabled(false)
            isSearchPanelVisible = false
            searchPanel.visibility = View.GONE
            searchToggleButton.background = createTabBackground(false)
            recyclerView.visibility = View.GONE
            loadingView.visibility = View.GONE
            emptyView.visibility = View.GONE
            bottomHintLeft.visibility = View.GONE
            bottomHintRight.visibility = View.GONE
            val mediaView = gifPickerView ?: GifPickerView(
                context = context,
                gifClient = KlipyGifClient(context),
                onGifSelected = { result -> onGifSelected?.invoke(result) },
                onExitRequested = { setMediaMode(false) },
                fillParentHeight = true
            ).also { gifPickerView = it }
            if (mediaView.parent !== contentFrame) {
                (mediaView.parent as? ViewGroup)?.removeView(mediaView)
                contentFrame.addView(
                    mediaView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            mediaView.visibility = View.VISIBLE
            mediaView.bringToFront()
            if (refreshOnOpen) {
                mediaView.refresh()
            }
        } else {
            gifPickerView?.visibility = View.GONE
            isSearchPanelVisible = raycastStyle
            searchPanel.visibility = if (raycastStyle) View.VISIBLE else View.GONE
            searchToggleButton.background = createTabBackground(true)
            bottomHintLeft.visibility = if (raycastStyle) View.VISIBLE else View.GONE
            bottomHintRight.visibility = if (raycastStyle) View.VISIBLE else View.GONE
            if (isSearchMode) {
                val hasResults = searchAdapter.itemCount > 0
                recyclerView.visibility = if (hasResults) View.VISIBLE else View.GONE
                emptyView.visibility = if (hasResults) View.GONE else View.VISIBLE
            } else {
                emptyView.visibility = View.GONE
                loadingView.visibility = View.GONE
                recyclerView.visibility = if (sectionAdapter.itemCount > 0) View.VISIBLE else View.GONE
            }
            recyclerView.bringToFront()
            topFadeView.bringToFront()
            searchPanel.bringToFront()
            bottomHintLeft.bringToFront()
            bottomHintRight.bringToFront()
        }
        updateTabsSelection()
    }

    private fun applySearchNow() {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            setSearchMode(false)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.GONE
            recyclerView.visibility = if (sectionAdapter.itemCount > 0) View.VISIBLE else View.GONE
            return
        }

        val index = searchIndex
        if (index == null) {
            setSearchMode(true)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val results = EmojiSearchRepository.search(index, query)
        setSearchMode(true)
        selectedSearchPosition = 0
        searchAdapter.submitList(results) {
            updateBottomHints()
            if (results.isNotEmpty()) {
                // ListAdapter applies diffs asynchronously. Reset only after the new
                // result list is attached, otherwise a stale scroll offset can leave
                // the first result hidden beneath the pinned search header.
                (recyclerView.layoutManager as? GridLayoutManager)
                    ?.scrollToPositionWithOffset(searchAdapter.firstEmojiAdapterPosition, recyclerView.paddingTop)
                // The search header occupies adapter position zero in Raycast mode.
                // Rebind after the async diff: unchanged results can otherwise retain
                // the accent background from the previous search selection.
                recyclerView.post {
                    if (isSearchMode && selectedSearchPosition == 0 && searchAdapter.itemCount > searchAdapter.firstEmojiAdapterPosition) {
                        searchAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
        if (results.isEmpty()) {
            emptyView.text = context.getString(R.string.emoji_picker_no_results)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setSearchMode(enabled: Boolean) {
        if (isSearchMode == enabled) {
            // Ensure adapter is set correctly if external code changed it during refresh.
            val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
            if (enabled && recyclerView.adapter !== searchAdapter) {
                recyclerView.adapter = searchAdapter
                lm.spanSizeLookup = searchAdapter.spanSizeLookup
            } else if (!enabled && recyclerView.adapter !== sectionAdapter) {
                recyclerView.adapter = sectionAdapter
                lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            }
            tabRow.alpha = if (enabled) 0.55f else 1f
            return
        }

        isSearchMode = enabled
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        if (enabled) {
            recyclerView.adapter = searchAdapter
            lm.spanSizeLookup = searchAdapter.spanSizeLookup
            selectedSearchPosition = 0
            selectedSectionPosition = RecyclerView.NO_POSITION
        } else {
            recyclerView.adapter = sectionAdapter
            lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            selectedSectionPosition = firstEmojiPosition()
            updateTabsSelection()
            // Search and section cells share the same RecyclerView. Rebind after the
            // adapter switch so a recycled search selection cannot retain its accent.
            recyclerView.post {
                if (!isSearchMode && recyclerView.adapter === sectionAdapter) {
                    sectionAdapter.notifyDataSetChanged()
                }
            }
        }
        tabRow.alpha = if (enabled) 0.55f else 1f
    }

    private fun buildSections(categories: List<EmojiRepository.EmojiCategory>) {
        val items = mutableListOf<SectionItem>()
        val categoryIds = ArrayList<String>()

        categories.forEach { category ->
            val title = category.displayNameRes?.let { context.getString(it) } ?: category.id
            items.add(SectionItem.Header(category.id, title, category.emojis.size))
            categoryIds.add(category.id)
            category.emojis.forEach { emojiEntry ->
                items.add(SectionItem.Emoji(category.id, emojiEntry))
                categoryIds.add(category.id)
            }
        }
        if (wechatEmojiItems.isNotEmpty()) {
            items.add(SectionItem.Header(WECHAT_EMOJI_CATEGORY_ID, "WeChat", wechatEmojiItems.size))
            categoryIds.add(WECHAT_EMOJI_CATEGORY_ID)
            wechatEmojiItems.forEach { item ->
                items.add(SectionItem.WechatEmoji(item))
                categoryIds.add(WECHAT_EMOJI_CATEGORY_ID)
            }
        }

        rebuildIndexCaches(items, categoryIds)
        selectedSectionPosition = firstEmojiPosition()
        sectionAdapter.submitList(items)
    }

    private fun updateTabs(categories: List<EmojiRepository.EmojiCategory>) {
        tabRow.removeAllViews()
        tabCategoryIds = categories.map { it.id }
        if (selectedCategoryId !in tabCategoryIds) {
            selectedCategoryId = tabCategoryIds.firstOrNull()
        }
        val tabHeight = dpToPx(32f)
        categories.forEach { category ->
            val iconRes = EmojiRepository.getCategoryIconRes(category.id)
            val label = category.displayNameRes?.let { context.getString(it) } ?: category.id
            val isSelected = category.id == selectedCategoryId
            val btn = ImageView(context).apply {
                setImageResource(iconRes)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
                background = createTabBackground(isSelected)
                // Icon always visible (alpha 1), background changes
                val pad = dpToPx(4f) // Minimal padding
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, // Use weight
                    tabHeight,
                    1f // Equal weight for all tabs
                )
                setOnClickListener {
                    if (isSearchMode) return@setOnClickListener
                    setMediaMode(false)
                    selectedCategoryId = category.id
                    updateTabsSelection()
                    isTabClickScroll = true
                    
                    // Recents is always at position 0 when present
                    if (category.id == EmojiRepository.RECENTS_CATEGORY_ID) {
                        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                        recyclerView.post {
                            requestRecentsRefresh(requireTop = true, requireNotRecents = false)
                        }
                    } else {
                        val headerPos = headerPositions[category.id] ?: return@setOnClickListener
                        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
                    }
                }
            }
            tabRow.addView(btn)
        }
        if (wechatEmojiItems.isNotEmpty()) {
            val isSelected = selectedCategoryId == WECHAT_EMOJI_CATEGORY_ID
            val btn = ImageView(context).apply {
                setImageResource(R.drawable.ic_wechat_emoji_24)
                contentDescription = "WeChat emoji"
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
                background = createTabBackground(isSelected)
                val pad = dpToPx(4f)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, tabHeight, 1f)
                setOnClickListener {
                    if (isSearchMode) return@setOnClickListener
                    setMediaMode(false)
                    selectedCategoryId = WECHAT_EMOJI_CATEGORY_ID
                    updateTabsSelection()
                    isTabClickScroll = true
                    val headerPos = headerPositions[WECHAT_EMOJI_CATEGORY_ID] ?: return@setOnClickListener
                    (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
                }
            }
            wechatTabView = btn
            tabRow.addView(btn)
        } else {
            wechatTabView = null
        }
        // The Media tab belongs to the emoji picker; gifPickerEnabled only controls
        // whether media appears as a separate SYM page in the SYM cycle.
        if (onGifSelected != null) {
            val btn = TextView(context).apply {
                text = context.getString(R.string.emoji_picker_gif_tab)
                contentDescription = context.getString(R.string.emoji_picker_gif_tab)
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                background = createTabBackground(false)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, tabHeight, 1f)
                setOnClickListener {
                    setMediaMode(true)
                }
            }
            gifTabView = btn
            tabRow.addView(btn)
        } else {
            gifTabView = null
        }
        updateTabsSelection()
    }

    private fun updateTabsSelection() {
        for (i in 0 until tabRow.childCount) {
            val view = tabRow.getChildAt(i)
            val categoryId = tabCategoryIds.getOrNull(i)
            val isSelected = !isMediaMode && categoryId == selectedCategoryId
            // Icon always visible, only background changes
            view.background = createTabBackground(isSelected)
            if (view is ImageView) {
                view.setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
            } else if (view is TextView) {
                view.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
            }
        }
        wechatTabView?.background = createTabBackground(!isMediaMode && selectedCategoryId == WECHAT_EMOJI_CATEGORY_ID)
        wechatTabView?.setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
        gifTabView?.background = createTabBackground(isMediaMode)
        updateCategoryChip()
        updateBottomHints()
    }

    private fun updateCategoryChip() {
        if (!raycastStyle) return
        val selectedHeader = sectionItems
            .filterIsInstance<SectionItem.Header>()
            .firstOrNull { it.categoryId == selectedCategoryId }
        val label = when {
            isMediaMode -> context.getString(R.string.emoji_picker_gif_tab)
            selectedHeader != null -> selectedHeader.title
            else -> context.getString(R.string.emoji_picker_all_categories)
        }
        val iconRes = when {
            isMediaMode -> R.drawable.ic_media_24
            selectedHeader != null -> categoryIconRes(selectedHeader.categoryId)
            else -> R.drawable.ic_emoji_symbols_24
        }
        categoryLabel.text = label
        categoryLabel.setCompoundDrawablesWithIntrinsicBounds(
            categoryIconDrawable(iconRes),
            null,
            categoryIconDrawable(R.drawable.keyboard_arrow_down_24),
            null
        )
    }

    private fun updateBottomHints() {
        if (!raycastStyle) return
        val selectedName = currentSelectedEmojiName()
        bottomHintLeft.text = selectedName?.let { "Search Emoji & Symbols - $it" }
            ?: context.getString(R.string.emoji_picker_search_placeholder)
        bottomHintRight.text = "Paste"
        bottomHintRight.setCompoundDrawablesWithIntrinsicBounds(
            null,
            null,
            if (currentSelectedEmoji() != null) categoryIconDrawable(R.drawable.keyboard_return_24) else null,
            null
        )
    }

    private fun currentSelectedEmoji(): String? {
        return currentSelectedEmojiEntry()?.base
    }

    private fun currentSelectedEmojiEntry(): EmojiRepository.EmojiEntry? {
        if (isMediaMode) return null
        if (isSearchMode) {
            return searchAdapter.currentList.getOrNull(selectedSearchPosition)?.entry
        }
        return (sectionItems.getOrNull(selectedSectionPosition) as? SectionItem.Emoji)?.entry
            ?: sectionItems.firstNotNullOfOrNull { item ->
                when (item) {
                    is SectionItem.Emoji -> item.entry
                    else -> null
                }
            }
    }

    private fun currentSelectedEmojiName(): String? {
        val selected = currentSelectedEmojiEntry() ?: return null
        val rawName = searchIndex?.items
            ?.firstOrNull { it.entry.base == selected.base }
            ?.terms
            ?.firstOrNull { it.kind == EmojiSearchRepository.TermKind.NAME }
            ?.normalizedText
            ?: return null
        return rawName.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase() }
        }
    }

    private fun firstEmojiPosition(): Int {
        return sectionItems.indexOfFirst { it is SectionItem.Emoji }
    }

    private fun updateSelectedTopEmoji(firstVisiblePosition: Int) {
        if (!raycastStyle || isSearchMode) return
        val nextSelected = sectionItems
            .withIndex()
            .drop(firstVisiblePosition.coerceAtLeast(0))
            .firstOrNull { (_, item) -> item is SectionItem.Emoji }
            ?.index
            ?: firstEmojiPosition()
        if (nextSelected == selectedSectionPosition) return
        val previous = selectedSectionPosition
        selectedSectionPosition = nextSelected
        val refreshTiles = {
            if (previous != RecyclerView.NO_POSITION) {
                sectionAdapter.notifyItemChanged(previous)
            }
            if (nextSelected != RecyclerView.NO_POSITION) {
                sectionAdapter.notifyItemChanged(nextSelected)
            }
        }
        if (recyclerView.isComputingLayout) {
            recyclerView.post(refreshTiles)
        } else {
            refreshTiles()
        }
        updateBottomHints()
    }

    private fun insertCurrentSelection(): Boolean {
        val item = if (isSearchMode) {
            searchAdapter.currentList.getOrNull(selectedSearchPosition)
        } else {
            null
        }
        if (item != null) {
            onEmojiSelected(item.entry.base, item.categoryId)
            return true
        }
        val sectionItem: SectionItem.Emoji = (sectionItems.getOrNull(selectedSectionPosition) as? SectionItem.Emoji)
            ?: sectionItems.firstNotNullOfOrNull { candidate ->
                when (candidate) {
                    is SectionItem.Emoji -> candidate
                    else -> null
                }
            } ?: return false
        onEmojiSelected(sectionItem.entry.base, sectionItem.categoryId)
        return true
    }

    private fun moveEmojiSelection(delta: Int): Boolean {
        if (isMediaMode) return false
        if (isSearchMode) {
            val count = searchAdapter.currentList.size
            if (count == 0) return true
            val previous = selectedSearchPosition
            selectedSearchPosition = (selectedSearchPosition + delta).coerceIn(0, count - 1)
            if (previous != selectedSearchPosition) {
                searchAdapter.notifyItemChanged(searchAdapter.emojiAdapterPosition(previous))
                searchAdapter.notifyItemChanged(searchAdapter.emojiAdapterPosition(selectedSearchPosition))
                recyclerView.scrollToPosition(searchAdapter.emojiAdapterPosition(selectedSearchPosition))
                updateBottomHints()
            }
            return true
        }

        val emojiPositions = sectionItems.indices.filter { sectionItems[it] is SectionItem.Emoji }
        if (emojiPositions.isEmpty()) return true
        val nextPosition = if (kotlin.math.abs(delta) == columns) {
            // Headers span the full grid width. Navigate by grid row and span index so
            // up/down stays in the current visual column instead of flattening headers.
            val lookup = sectionAdapter.spanSizeLookup
            val current = selectedSectionPosition.takeIf { it in sectionItems.indices } ?: emojiPositions.first()
            val currentGroup = lookup.getSpanGroupIndex(current, columns)
            val currentColumn = lookup.getSpanIndex(current, columns)
            val targetGroup = emojiPositions
                .map { lookup.getSpanGroupIndex(it, columns) }
                .distinct()
                .let { groups ->
                    if (delta > 0) groups.filter { it > currentGroup }.minOrNull()
                    else groups.filter { it < currentGroup }.maxOrNull()
                }
            emojiPositions
                .filter { targetGroup != null && lookup.getSpanGroupIndex(it, columns) == targetGroup }
                .minByOrNull { kotlin.math.abs(lookup.getSpanIndex(it, columns) - currentColumn) }
                ?: current
        } else {
            val currentIndex = emojiPositions.indexOf(selectedSectionPosition).let { if (it >= 0) it else 0 }
            emojiPositions[(currentIndex + delta).coerceIn(0, emojiPositions.lastIndex)]
        }
        if (nextPosition != selectedSectionPosition) {
            val previous = selectedSectionPosition
            selectedSectionPosition = nextPosition
            if (previous != RecyclerView.NO_POSITION) sectionAdapter.notifyItemChanged(previous)
            sectionAdapter.notifyItemChanged(nextPosition)
            recyclerView.scrollToPosition(nextPosition)
            updateBottomHints()
        }
        return true
    }

    private fun showCategoryPopup() {
        if (categoryPopup?.isShowing == true) {
            categoryPopup?.dismiss()
            return
        }
        val headers = sectionItems.filterIsInstance<SectionItem.Header>()
        if (headers.isEmpty() && onGifSelected == null) return
        val theme = themeOverride
        val popupWidth = minOf(context.resources.displayMetrics.widthPixels - dpToPx(24f), dpToPx(280f))
        val popupMaxHeight = dpToPx(260f)
        val rowHeight = dpToPx(46f)
        var popup: PopupWindow? = null

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            background = createCategoryPopupBackground()
        }

        fun addRow(label: String, count: Int?, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12f), 0, dpToPx(12f), 0)
                background = createCategoryPopupRowBackground(selected)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    rowHeight
                ).apply {
                    setMargins(0, dpToPx(2f), 0, dpToPx(2f))
                }
                setOnClickListener {
                    popup?.dismiss()
                    // Let the popup release its window/focus before changing the
                    // recycler underneath it. This keeps the IME picker open.
                    this@EmojiPickerView.post(onClick)
                }
            }
            row.addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(theme?.textAndIcons ?: Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    marginEnd = dpToPx(12f)
                }
            })
            row.addView(TextView(context).apply {
                text = label
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(theme?.textAndIcons ?: Color.WHITE)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (count != null) {
                row.addView(TextView(context).apply {
                    text = count.toString()
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 145))
                    includeFontPadding = false
                })
            }
            list.addView(row)
        }

        addRow(
            context.getString(R.string.emoji_picker_all_categories),
            headers.sumOf { it.count },
            R.drawable.ic_emoji_symbols_24,
            !isMediaMode && selectedCategoryId == null
        ) {
            setMediaMode(false)
            val firstCategory = headers.firstOrNull()?.categoryId ?: return@addRow
            jumpToCategory(firstCategory)
        }
        headers.forEach { header ->
            addRow(header.title, header.count, categoryIconRes(header.categoryId), !isMediaMode && selectedCategoryId == header.categoryId) {
                setMediaMode(false)
                jumpToCategory(header.categoryId)
            }
        }
        if (onGifSelected != null) {
            addRow(context.getString(R.string.emoji_picker_gif_tab), null, R.drawable.ic_media_24, isMediaMode) {
                setMediaMode(true)
            }
        }

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            background = createCategoryPopupBackground()
            clipToOutline = true
            addView(list)
        }
        list.background = null
        list.measure(
            MeasureSpec.makeMeasureSpec(popupWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = minOf(popupMaxHeight, list.measuredHeight)
        popup = PopupWindow(
            scrollView,
            popupWidth,
            popupHeight,
            false
        ).apply {
            isTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            isOutsideTouchable = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isTouchModal = false
            }
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dpToPx(8f).toFloat()
        }
        if (categoryLabel.isAttachedToWindow) {
            popup.showAsDropDown(categoryLabel, 0, dpToPx(6f), Gravity.END)
        }
        categoryPopup = popup
        popup.setOnDismissListener { categoryPopup = null }
    }

    private fun jumpToCategory(categoryId: String) {
        if (isSearchMode) {
            searchField.text?.clear()
            setSearchMode(false)
        }
        selectedCategoryId = categoryId
        updateTabsSelection()
        isTabClickScroll = true
        val headerPos = headerPositions[categoryId] ?: return
        selectedSectionPosition = sectionItems
            .withIndex()
            .drop(headerPos)
            .firstOrNull { (_, item) -> item is SectionItem.Emoji }
            ?.index
            ?: RecyclerView.NO_POSITION
        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
        recyclerView.post {
            if (categoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
                requestRecentsRefresh(requireTop = true, requireNotRecents = false)
            }
        }
    }

    private fun onEmojiSelected(emoji: String, categoryId: String) {
        val inputConnection = currentInputConnection
        if (
            SettingsManager.getSymAutoClose(context) &&
            SettingsManager.getSymAutoCloseOnTouch(context)
        ) {
            onCloseRequested?.invoke()
            post {
                inputConnection?.commitText(emoji, 1)
            }
        } else {
            inputConnection?.commitText(emoji, 1)
        }
        if (isSearchMode || searchField.text?.isNotEmpty() == true) {
            searchField.text?.clear()
            selectedSearchPosition = 0
        }
        // Save to storage and refresh recents when safe for UX.
        val requiresNotRecents = categoryId == EmojiRepository.RECENTS_CATEGORY_ID
        coroutineScope.launch(Dispatchers.IO) {
            val changed = RecentEmojiManager.addRecentEmoji(
                context,
                emoji,
                moveToTopWhenExists = true
            )
            if (changed) {
                withContext(Dispatchers.Main) {
                    requestRecentsRefresh(requireTop = !requiresNotRecents, requireNotRecents = requiresNotRecents)
                }
            }
        }
    }

    private fun onWechatEmojiSelected(item: WechatEmojiItem) {
        val result = KlipyGifResult(
            id = "wechat_${item.fileName}",
            title = item.title,
            mediaType = KlipyMediaType.LOCAL,
            previewUrl = item.assetUrl,
            gifUrl = item.assetUrl,
            mimeType = "image/png",
            shareUrl = "",
            isLocal = true
        )
        if (
            SettingsManager.getSymAutoClose(context) &&
            SettingsManager.getSymAutoCloseOnTouch(context)
        ) {
            onCloseRequested?.invoke()
            post { onGifSelected?.invoke(result) }
        } else {
            onGifSelected?.invoke(result)
        }
    }

    /**
     * Simple refresh of recents from storage.
     * Applies updates only when safe for UX.
     * Compares stored vs displayed recents and updates only if different.
     */
    private fun refreshRecentsFromStorage(allowInsertOrRemove: Boolean) {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }

            val recentsHeaderIndex = headerPositions[EmojiRepository.RECENTS_CATEGORY_ID]

            // Case 1: Recents in storage but not displayed -> full reload
            if (recentsHeaderIndex == null && recentCategory != null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val newRecentsItems = buildRecentsItems(recentCategory)
                val newItems = newRecentsItems + sectionItems
                rebuildIndexCaches(newItems)
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, newRecentsItems.size) }
                }
                updateTabs(buildAllCategories(recentCategory))
                return@launch
            }

            // Case 2: No recents in storage but displayed -> full reload
            if (recentsHeaderIndex != null && recentCategory == null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size
                val removedCount = nextHeaderIndex - recentsHeaderIndex
                val newItems = sectionItems.toMutableList()
                repeat(removedCount) {
                    newItems.removeAt(recentsHeaderIndex)
                }
                rebuildIndexCaches(newItems)
                if (selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
                    selectedCategoryId = itemCategoryIds.firstOrNull()
                }
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, -removedCount) }
                }
                updateTabs(buildAllCategories(null))
                return@launch
            }

            // Case 3: Both exist -> compare and update if different
            if (recentsHeaderIndex != null && recentCategory != null) {
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size

                val displayedRecents = sectionItems
                    .subList(recentsHeaderIndex + 1, nextHeaderIndex)
                    .filterIsInstance<SectionItem.Emoji>()
                    .map { it.entry.base }

                val storedRecents = recentCategory.emojis.map { it.base }

                // Only update if different
                if (displayedRecents != storedRecents) {
                    val newRecentsItems = buildRecentsItems(recentCategory)

                    val newItems = sectionItems.toMutableList()
                    for (i in recentsHeaderIndex until nextHeaderIndex) {
                        newItems.removeAt(recentsHeaderIndex)
                    }
                    newItems.addAll(recentsHeaderIndex, newRecentsItems)

                    rebuildIndexCaches(newItems)
                    val anchor = if (isAtAbsoluteTop()) null else captureScrollAnchor()
                    sectionAdapter.submitList(newItems) {
                        anchor?.let { restoreScrollAnchor(it, 0) }
                    }
                }
            }
        }
    }

    /**
     * Updates tabs asynchronously when recents section is added/removed.
     */
    private fun updateTabsAsync() {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }
            val regularCategories = withContext(Dispatchers.IO) {
                EmojiRepository.getEmojiCategories(context)
            }

            val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
            if (recentCategory != null) allCategories.add(recentCategory)
            allCategories.addAll(regularCategories)

            updateTabs(allCategories)
        }
    }

    private fun createTabBackground(isSelected: Boolean): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val color = if (theme != null) {
                if (isSelected) colorWithAlpha(theme.keyTap, 100) else Color.TRANSPARENT
            } else if (isSelected) {
                Color.argb(100, 255, 255, 255)
            } else {
                Color.TRANSPARENT
            }
            setColor(color)
            if (theme != null && isSelected) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun createEmojiTileBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            val baseColor = blendColors(theme?.normalKey ?: Color.rgb(74, 70, 70), Color.WHITE, 0.16f)
            colors = intArrayOf(
                colorWithAlpha(baseColor, if (raycastStyle) 118 else 48),
                colorWithAlpha(blendColors(baseColor, Color.BLACK, 0.10f), if (raycastStyle) 92 else 24)
            )
            if (theme != null && !raycastStyle) {
                setStroke(dpToPx(1f), colorWithAlpha(Color.WHITE, 36))
            } else if (raycastStyle) {
                setStroke(dpToPx(1f), colorWithAlpha(Color.WHITE, 34))
            }
            cornerRadius = dpToPx(if (raycastStyle) 14f else 14f).toFloat()
        }
    }

    private fun createSelectedEmojiTileBackground(emoji: String? = null): GradientDrawable {
        val accent = dominantEmojiAccentColor(emoji) ?: emojiAccentColor(emoji) ?: themeOverride?.keyTap ?: Color.argb(255, 255, 193, 7)
        val warmAccent = blendColors(accent, Color.rgb(255, 222, 157), 0.32f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(
                colorWithAlpha(warmAccent, 132),
                colorWithAlpha(accent, 78)
            )
            setStroke(dpToPx(2f), colorWithAlpha(Color.rgb(255, 232, 181), 235))
            cornerRadius = dpToPx(12f).toFloat()
        }
    }

    private fun createCategoryChipBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colorWithAlpha(themeOverride?.normalKey ?: Color.WHITE, 78))
            setStroke(dpToPx(1f), colorWithAlpha(Color.WHITE, 54))
            cornerRadius = dpToPx(18f).toFloat()
        }
    }

    private fun createCategoryPopupBackground(): GradientDrawable {
        val background = themeOverride?.background ?: Color.rgb(24, 24, 24)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colorWithAlpha(background, Color.alpha(background).coerceAtLeast(205)))
            setStroke(dpToPx(1f), colorWithAlpha(Color.WHITE, 64))
            cornerRadius = dpToPx(18f).toFloat()
        }
    }

    private fun createCategoryPopupRowBackground(isSelected: Boolean): GradientDrawable {
        val accent = themeOverride?.keyTap ?: Color.WHITE
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (isSelected) colorWithAlpha(accent, 105) else Color.TRANSPARENT)
            cornerRadius = dpToPx(12f).toFloat()
        }
    }

    private fun createHintPillBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colorWithAlpha(theme?.normalKey ?: Color.rgb(32, 32, 32), 205))
            setStroke(dpToPx(1f), colorWithAlpha(Color.WHITE, 62))
            cornerRadius = dpToPx(18f).toFloat()
        }
    }

    private fun createTopFadeBackground(): GradientDrawable {
        // The pinned controls remain visually part of the picker. When the grid
        // moves behind them, this creates a soft dark veil through the lower half
        // of the header instead of turning the whole header into a solid bar.
        val darkSurface = blendColors(themeOverride?.background ?: Color.rgb(24, 24, 24), Color.BLACK, 0.64f)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                colorWithAlpha(darkSurface, 190),
                colorWithAlpha(darkSurface, 142),
                colorWithAlpha(darkSurface, 0)
            )
        )
    }

    private fun createCloseButtonBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.statusBarButton ?: Color.argb(95, 220, 38, 38))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun createSearchFieldBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colorWithAlpha(Color.WHITE, if (theme != null) 30 else 36))
            if (theme != null) {
                setStroke(dpToPx(1f), colorWithAlpha(Color.WHITE, 42))
            }
            cornerRadius = dpToPx(14f).toFloat()
        }
    }

    private fun applyTheme() {
        val theme = themeOverride
        val background = theme?.background ?: Color.TRANSPARENT
        // Raycast mode gets its surface from the frosted layer below. An opaque
        // root background here would hide the theme's translucency completely.
        val pickerBackground = if (raycastStyle) Color.TRANSPARENT else background
        setBackgroundColor(pickerBackground)
        vertical.setBackgroundColor(if (raycastStyle) Color.TRANSPARENT else pickerBackground)
        frostedBackgroundView.themeColors = theme
        recyclerView.setBackgroundColor(Color.TRANSPARENT)
        contentFrame.setBackgroundColor(Color.TRANSPARENT)
        searchPanel.background = if (raycastStyle) ColorDrawable(Color.TRANSPARENT) else ColorDrawable(background)
        topFadeView.background = createTopFadeBackground()
        updateTopFadeVisibility()
        loadingView.setBackgroundColor(Color.TRANSPARENT)
        emptyView.setBackgroundColor(Color.TRANSPARENT)
        searchField.setTextColor(theme?.textAndIcons ?: Color.WHITE)
        searchField.setHintTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 145))
        searchField.background = if (raycastStyle) ColorDrawable(Color.TRANSPARENT) else createSearchFieldBackground()
        searchBackButton.setColorFilter(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 190))
        categoryLabel.setTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 175))
        categoryLabel.background = createCategoryChipBackground()
        updateCategoryChip()
        bottomHintLeft.setTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 175))
        bottomHintLeft.background = createHintPillBackground()
        bottomHintRight.setTextColor(theme?.textAndIcons ?: Color.WHITE)
        bottomHintRight.background = createHintPillBackground()
        closeButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        closeButton.background = createCloseButtonBackground()
        searchToggleButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        searchToggleButton.background = createTabBackground(isSearchPanelVisible)
        keyboardSwitcherButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        keyboardSwitcherButton.background = createTabBackground(false)
        emptyView.setTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 128))
        updateTabsSelection()
        sectionAdapter.notifyDataSetChanged()
        searchAdapter.notifyDataSetChanged()
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun updateTopFadeVisibility() {
        if (!raycastStyle) {
            topFadeView.visibility = View.GONE
            topFadeVisible = false
            return
        }
        val shouldShow = recyclerView.canScrollVertically(-1)
        if (shouldShow == topFadeVisible) return
        topFadeVisible = shouldShow
        topFadeView.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color.rgb(
            (Color.red(from) * inverse + Color.red(to) * clamped).toInt(),
            (Color.green(from) * inverse + Color.green(to) * clamped).toInt(),
            (Color.blue(from) * inverse + Color.blue(to) * clamped).toInt()
        )
    }

    private fun emojiAccentColor(emoji: String?): Int? {
        if (emoji.isNullOrEmpty()) return null
        return when {
            emoji.containsAny("💕", "💖", "💗", "💓", "💞", "💘", "💝", "💟", "🩷") ->
                Color.rgb(245, 106, 170)
            emoji.containsAny("❤", "♥", "💔", "❤️") ->
                Color.rgb(236, 58, 47)
            emoji.containsAny("💙", "🩵", "💧", "💦", "🌊", "🥶") ->
                Color.rgb(78, 153, 255)
            emoji.containsAny("💚", "✅", "☘", "🌿", "🍀", "🥬") ->
                Color.rgb(62, 190, 108)
            emoji.containsAny("💜", "😈", "☂", "🍆") ->
                Color.rgb(154, 92, 230)
            emoji.containsAny("🖤", "💀", "♠", "♣") ->
                Color.rgb(96, 96, 104)
            emoji.containsAny("🤎", "🍫", "☕", "🪵") ->
                Color.rgb(151, 91, 48)
            emoji.containsAny("🧡", "🔥", "🍊", "🎃") ->
                Color.rgb(245, 128, 43)
            emoji.containsAny("⭐", "🌟", "✨", "⚡", "💛") ->
                Color.rgb(247, 200, 65)
            emoji.containsAny("🌹", "🍎", "🍓", "🍒") ->
                Color.rgb(225, 53, 61)
            emoji.codePoints().anyMatch { codePoint -> codePoint in 0x1F600..0x1F64F } ->
                Color.rgb(246, 187, 55)
            else -> themeOverride?.keyTap
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean {
        return needles.any { contains(it) }
    }

    private fun dominantEmojiAccentColor(emoji: String?): Int? {
        if (emoji.isNullOrEmpty()) return null
        if (emojiAccentCache.containsKey(emoji)) return emojiAccentCache[emoji]
        val sampled = runCatching {
            val size = 40
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = size * 0.72f
                typeface = customEmojiTypeface ?: Typeface.DEFAULT
            }
            val baseline = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(emoji, size / 2f, baseline, paint)
            val buckets = LinkedHashMap<Int, Int>()
            val step = 4
            var y = 0
            while (y < size) {
                var x = 0
                while (x < size) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) > 48) {
                        val hsv = FloatArray(3)
                        Color.colorToHSV(pixel, hsv)
                        if (hsv[1] > 0.22f && hsv[2] > 0.22f) {
                            val quantized = Color.HSVToColor(floatArrayOf(
                                (hsv[0] / 18f).toInt() * 18f,
                                0.78f,
                                0.92f
                            ))
                            buckets[quantized] = (buckets[quantized] ?: 0) + 1
                        }
                    }
                    x += step
                }
                y += step
            }
            bitmap.recycle()
            buckets.maxByOrNull { it.value }?.key
        }.getOrNull()
        emojiAccentCache[emoji] = sampled
        return sampled
    }

    private fun categoryIconRes(categoryId: String): Int {
        return if (categoryId == WECHAT_EMOJI_CATEGORY_ID) {
            R.drawable.ic_wechat_emoji_24
        } else {
            EmojiRepository.getCategoryIconRes(categoryId)
        }
    }

    private fun categoryIconDrawable(iconRes: Int): Drawable? {
        return context.getDrawable(iconRes)?.mutate()?.apply {
            setTint(themeOverride?.textAndIcons ?: Color.WHITE)
            setBounds(0, 0, dpToPx(18f), dpToPx(18f))
        }
    }

    private fun showVariantsPopup(anchor: View, entry: EmojiRepository.EmojiEntry, categoryId: String) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        val itemHorizontalPadding = (12 * density).toInt()
        val itemVerticalPadding = (8 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            gravity = Gravity.CENTER
        }

        var popup: PopupWindow? = null
        val options = listOf(entry.base) + entry.variants
        options.forEach { emoji ->
            val textView = TextView(context).apply {
                text = emoji
                gravity = Gravity.CENTER
                CustomEmojiFontManager.applyToTextView(
                    context = context,
                    textView = this,
                    emoji = emoji,
                    fallbackTypeface = Typeface.DEFAULT,
                    systemTextSizeSp = 24f,
                    customTextSizeSp = 30f
                )
                setPadding(itemHorizontalPadding, itemVerticalPadding, itemHorizontalPadding, itemVerticalPadding)
                setTextColor(themeOverride?.textAndIcons ?: Color.BLACK)
            }
            textView.setOnClickListener {
                onEmojiSelected(emoji, categoryId)
                popup?.dismiss()
            }
            container.addView(textView)
        }

        val maxPopupWidth = context.resources.displayMetrics.widthPixels - dpToPx(16f)
        val popupContent: View = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(container)
        }

        popupContent.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val popupWidth = minOf(popupContent.measuredWidth, maxPopupWidth)
        val popupHeight = popupContent.measuredHeight

        popup = PopupWindow(
            popupContent,
            popupWidth,
            popupHeight,
            false // Don't take focus to avoid closing emoji picker
        ).apply {
            setBackgroundDrawable(ColorDrawable(themeOverride?.keyPopup ?: Color.parseColor("#EEFFFFFF")))
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12f
        }

        // Position popup above the anchor
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val windowWidth = context.resources.displayMetrics.widthPixels
        val anchorX = location[0]
        val anchorY = location[1]
        val desiredX = anchorX + (anchor.width - popupWidth) / 2
        val maxX = (windowWidth - popupWidth).coerceAtLeast(0)
        val clampedX = desiredX.coerceIn(0, maxX)
        val xOffset = clampedX - anchorX
        val desiredYOffset = -(popupHeight + anchor.height)
        val minYOffset = -(anchorY + anchor.height)
        val yOffset = maxOf(desiredYOffset, minYOffset)
        popup.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Recreate coroutine scope if it was cancelled
        if (!coroutineScope.isActive) {
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    private inner class SectionAdapter(private val columns: Int) :
        ListAdapter<SectionItem, RecyclerView.ViewHolder>(SectionItemDiffCallback()) {
        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> columns
                    else -> 1
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is SectionItem.Header -> VIEW_TYPE_HEADER
                is SectionItem.Emoji -> VIEW_TYPE_EMOJI
                is SectionItem.WechatEmoji -> VIEW_TYPE_WECHAT_EMOJI
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                val header = TextView(parent.context).apply {
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dpToPx(2f), if (raycastStyle) dpToPx(10f) else 0, dpToPx(2f), if (raycastStyle) dpToPx(4f) else 0)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        if (raycastStyle) ViewGroup.LayoutParams.WRAP_CONTENT else dpToPx(1f)
                    )
                }
                HeaderViewHolder(header)
            } else if (viewType == VIEW_TYPE_WECHAT_EMOJI) {
                val image = ImageView(parent.context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = false
                    background = if (raycastStyle) createEmojiTileBackground() else null
                    setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        emojiSize
                    )
                }
                WechatEmojiViewHolder(image)
            } else {
                val tv = TextView(parent.context).apply {
                    gravity = Gravity.CENTER
                    background = if (raycastStyle) createEmojiTileBackground() else null
                    minHeight = emojiSize
                    minWidth = emojiSize
                    includeFontPadding = false
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        emojiSize
                    )
                }
                EmojiViewHolder(tv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is SectionItem.Header -> {
                    (holder as HeaderViewHolder).textView.text = if (raycastStyle) "${item.title}   ${item.count}" else ""
                    holder.textView.setTextColor(colorWithAlpha(themeOverride?.textAndIcons ?: Color.WHITE, 175))
                }
                is SectionItem.Emoji -> {
                    (holder as EmojiViewHolder).textView.text = item.entry.base
                    holder.textView.background = if (raycastStyle) {
                        if (position == selectedSectionPosition) createSelectedEmojiTileBackground(item.entry.base) else createEmojiTileBackground()
                    } else {
                        null
                    }
                    CustomEmojiFontManager.applyToTextView(
                        context = holder.textView.context,
                        textView = holder.textView,
                        emoji = item.entry.base,
                        fallbackTypeface = Typeface.DEFAULT,
                        systemTextSizeSp = if (raycastStyle) 34f else 28.8f,
                        customTextSizeSp = if (raycastStyle) 40f else 34f
                    )
                    holder.textView.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                    holder.textView.setOnClickListener {
                        onEmojiSelected(item.entry.base, item.categoryId)
                    }
                    holder.textView.setOnLongClickListener {
                        if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                        showVariantsPopup(holder.textView, item.entry, item.categoryId)
                        true
                    }
                }
                is SectionItem.WechatEmoji -> {
                    val imageView = (holder as WechatEmojiViewHolder).imageView
                    imageView.contentDescription = item.item.title
                    imageView.background = if (raycastStyle) createEmojiTileBackground() else null
                    imageView.setImageDrawable(loadAssetDrawable(item.item.assetPath))
                    imageView.setOnClickListener {
                        onWechatEmojiSelected(item.item)
                    }
                    imageView.setOnLongClickListener(null)
                }
            }
        }
    }

    private class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    private class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    private class WechatEmojiViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
    private class SearchEmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private inner class SearchAdapter :
        ListAdapter<EmojiSearchRepository.EmojiSearchResult, RecyclerView.ViewHolder>(SearchResultDiffCallback()) {
        private val hasResultsHeader: Boolean
            get() = raycastStyle && currentList.isNotEmpty()

        val firstEmojiAdapterPosition: Int
            get() = if (hasResultsHeader) 1 else 0

        fun emojiAdapterPosition(resultPosition: Int): Int = resultPosition + firstEmojiAdapterPosition

        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (getItemViewType(position) == VIEW_TYPE_HEADER) columns else 1
        }

        override fun getItemCount(): Int = super.getItemCount() + if (hasResultsHeader) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_HEADER) {
                return HeaderViewHolder(TextView(parent.context).apply {
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dpToPx(2f), dpToPx(10f), dpToPx(2f), dpToPx(4f))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                background = if (raycastStyle) createEmojiTileBackground() else null
                minHeight = emojiSize
                minWidth = emojiSize
                includeFontPadding = false
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    emojiSize
                )
            }
            return SearchEmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == VIEW_TYPE_HEADER) {
                (holder as HeaderViewHolder).textView.apply {
                    text = "Results   ${currentList.size}"
                    setTextColor(colorWithAlpha(themeOverride?.textAndIcons ?: Color.WHITE, 175))
                }
                return
            }
            val resultPosition = position - firstEmojiAdapterPosition
            val item = getItem(resultPosition)
            val emojiHolder = holder as SearchEmojiViewHolder
            emojiHolder.textView.text = item.entry.base
            emojiHolder.textView.background = if (raycastStyle && resultPosition == selectedSearchPosition) {
                createSelectedEmojiTileBackground(item.entry.base)
            } else if (raycastStyle) {
                createEmojiTileBackground()
            } else {
                null
            }
            CustomEmojiFontManager.applyToTextView(
                context = emojiHolder.textView.context,
                textView = emojiHolder.textView,
                emoji = item.entry.base,
                fallbackTypeface = Typeface.DEFAULT,
                systemTextSizeSp = if (raycastStyle) 34f else 28.8f,
                customTextSizeSp = if (raycastStyle) 40f else 34f
            )
            emojiHolder.textView.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
            emojiHolder.textView.setOnClickListener {
                onEmojiSelected(item.entry.base, item.categoryId)
            }
            emojiHolder.textView.setOnLongClickListener {
                if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                showVariantsPopup(emojiHolder.textView, item.entry, item.categoryId)
                true
            }
        }

        override fun getItemViewType(position: Int): Int =
            if (hasResultsHeader && position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_EMOJI
    }

    private sealed class SectionItem {
        data class Header(val categoryId: String, val title: String, val count: Int) : SectionItem()
        data class Emoji(val categoryId: String, val entry: EmojiRepository.EmojiEntry) : SectionItem()
        data class WechatEmoji(val item: WechatEmojiItem) : SectionItem()
    }

    private data class WechatEmojiItem(
        val fileName: String,
        val title: String,
        val assetPath: String
    ) {
        val assetUrl: String = "asset://$assetPath"
    }

    private class SectionItemDiffCallback : DiffUtil.ItemCallback<SectionItem>() {
        override fun areItemsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return when {
                oldItem is SectionItem.Header && newItem is SectionItem.Header ->
                    oldItem.categoryId == newItem.categoryId
                oldItem is SectionItem.Emoji && newItem is SectionItem.Emoji ->
                    oldItem.categoryId == newItem.categoryId && oldItem.entry.base == newItem.entry.base
                oldItem is SectionItem.WechatEmoji && newItem is SectionItem.WechatEmoji ->
                    oldItem.item.fileName == newItem.item.fileName
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return oldItem == newItem
        }
    }

    private class SearchResultDiffCallback :
        DiffUtil.ItemCallback<EmojiSearchRepository.EmojiSearchResult>() {
        override fun areItemsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem.entry.base == newItem.entry.base && oldItem.categoryId == newItem.categoryId
        }

        override fun areContentsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem == newItem
        }
    }

    private data class ScrollAnchor(val position: Int, val offset: Int)

    private inner class FrostedBackgroundView(context: Context) : View(context) {
        private val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var themeColors: KeyboardThemeColors? = null
            set(value) {
                field = value
                invalidate()
            }

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val theme = themeColors
            // A dark translucent surface keeps the themed backdrop visible without
            // introducing the white/pink wash caused by highlight stops.
            val intensity = (theme?.frostIntensity ?: 1f).coerceIn(0.45f, 1.4f)
            val shadeAlpha = (78f * intensity).toInt().coerceIn(44, 112)
            frostPaint.shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(
                    Color.argb((shadeAlpha * 0.82f).toInt(), 0, 0, 0),
                    Color.argb((shadeAlpha * 0.9f).toInt(), 0, 0, 0),
                    Color.argb(shadeAlpha, 0, 0, 0),
                    Color.argb((shadeAlpha * 1.06f).toInt().coerceAtMost(128), 0, 0, 0)
                ),
                floatArrayOf(0f, 0.42f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), frostPaint)
            frostPaint.shader = null
        }
    }

    private fun rebuildIndexCaches(items: List<SectionItem>, categoryIds: List<String>? = null) {
        val headers = mutableMapOf<String, Int>()
        val ids = categoryIds?.toMutableList() ?: ArrayList(items.size)
        if (categoryIds == null) {
            items.forEach { item ->
                ids.add(item.categoryId())
            }
        }
        items.forEachIndexed { index, item ->
            if (item is SectionItem.Header) {
                headers[item.categoryId] = index
            }
        }
        sectionItems = items
        headerPositions = headers
        itemCategoryIds = ids
    }

    private fun buildRecentsItems(recentCategory: EmojiRepository.EmojiCategory): List<SectionItem> {
        val recentsTitle = recentCategory.displayNameRes?.let { context.getString(it) }
            ?: EmojiRepository.RECENTS_CATEGORY_ID
        val items = ArrayList<SectionItem>(recentCategory.emojis.size + 1)
        items.add(SectionItem.Header(EmojiRepository.RECENTS_CATEGORY_ID, recentsTitle, recentCategory.emojis.size))
        recentCategory.emojis.forEach { entry ->
            items.add(SectionItem.Emoji(EmojiRepository.RECENTS_CATEGORY_ID, entry))
        }
        return items
    }

    private fun buildAllCategories(recentCategory: EmojiRepository.EmojiCategory?): List<EmojiRepository.EmojiCategory> {
        return if (recentCategory == null) {
            regularCategories
        } else {
            listOf(recentCategory) + regularCategories
        }
    }

    private fun captureScrollAnchor(): ScrollAnchor? {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return null
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null
        val topView = recyclerView.getChildAt(0)
        val offset = topView?.top ?: 0
        return ScrollAnchor(firstVisible, offset)
    }

    private fun restoreScrollAnchor(anchor: ScrollAnchor, positionDelta: Int) {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        val targetPosition = (anchor.position + positionDelta).coerceAtLeast(0)
        if (targetPosition >= sectionAdapter.itemCount) return
        lm.scrollToPositionWithOffset(targetPosition, anchor.offset)
    }

    private fun requestRecentsRefresh(requireTop: Boolean, requireNotRecents: Boolean) {
        markRecentsRefreshPending(requireTop, requireNotRecents)
        maybeApplyPendingRecentsRefresh()
    }

    private fun markRecentsRefreshPending(requireTop: Boolean, requireNotRecents: Boolean) {
        pendingRecentsRefresh = true
        pendingRecentsRefreshRequiresTop = pendingRecentsRefreshRequiresTop || requireTop
        pendingRecentsRefreshRequiresNotRecents = pendingRecentsRefreshRequiresNotRecents || requireNotRecents
    }

    private fun maybeApplyPendingRecentsRefresh() {
        if (!pendingRecentsRefresh) return
        if (scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        val requiresNotRecents = pendingRecentsRefreshRequiresNotRecents
        val requiresTop = pendingRecentsRefreshRequiresTop && !requiresNotRecents
        if (requiresTop && !isNearTop()) return
        if (requiresNotRecents &&
            selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
            return
        }
        pendingRecentsRefresh = false
        pendingRecentsRefreshRequiresTop = false
        pendingRecentsRefreshRequiresNotRecents = false
        refreshRecentsFromStorage(allowInsertOrRemove = isNearTop())
    }

    private fun isNearTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        return firstVisible != RecyclerView.NO_POSITION && firstVisible <= recentsApplyTopThreshold
    }

    private fun isAtAbsoluteTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible != 0) return false
        val firstView = lm.findViewByPosition(0) ?: return false
        return firstView.top >= recyclerView.paddingTop
    }

    private fun SectionItem.categoryId(): String {
        return when (this) {
            is SectionItem.Header -> categoryId
            is SectionItem.Emoji -> categoryId
            is SectionItem.WechatEmoji -> WECHAT_EMOJI_CATEGORY_ID
        }
    }

    private fun loadWechatEmojiItems(): List<WechatEmojiItem> {
        return runCatching {
            context.assets.list(WECHAT_EMOJI_ASSET_DIR)
                ?.filter { it.endsWith(".png", ignoreCase = true) }
                ?.sorted()
                ?.map { fileName ->
                    val title = fileName.removeSuffix(".png")
                        .replace('_', ' ')
                        .replace('-', ' ')
                    WechatEmojiItem(
                        fileName = fileName,
                        title = title,
                        assetPath = "$WECHAT_EMOJI_ASSET_DIR/$fileName"
                    )
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun loadAssetDrawable(assetPath: String): Drawable? {
        return runCatching {
            context.assets.open(assetPath).use { input ->
                Drawable.createFromStream(input, assetPath)
            }
        }.getOrNull()
    }

    companion object {
        private const val WECHAT_EMOJI_CATEGORY_ID = "wechat_emoji"
        private const val WECHAT_EMOJI_ASSET_DIR = "common/wechat_emoji"
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMOJI = 1
        private const val VIEW_TYPE_WECHAT_EMOJI = 2
    }
}
