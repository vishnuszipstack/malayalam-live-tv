package com.malayalamlive.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.widget.doAfterTextChanged
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.malayalamlive.tv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private companion object {
        const val ACTION_REFRESH = "action:refresh"
        const val ACTION_SOURCES = "action:sources"
        const val ACTION_PLAYLISTS = "action:playlists"
        const val ACTION_UPDATE = "action:update"
        const val ACTION_HIDDEN = "action:hidden"
        const val CATEGORY_DELAY_MS = 180L
        const val SEARCH = "Search"
        const val SEARCH_DELAY_MS = 250L
        const val STATE_CATEGORY = "category"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlaylistRepository
    private lateinit var updater: Updater
    private var update: ReleaseInfo? = null
    /** Everything the playlists returned, including channels the viewer removed. */
    private var loadedChannels: List<Channel> = emptyList()

    /** What the app shows: [loadedChannels] minus removed ones. */
    private var allChannels: List<Channel> = emptyList()
    private var filter = ChannelFilter()
    private var currentCategory: String = Dashboard.HOME
    private var pendingCategory: String? = null
    private var firstLoad = true

    private val handler = Handler(Looper.getMainLooper())
    private val applyPending = Runnable { pendingCategory?.let { showCategory(it) }; pendingCategory = null }

    private val sidebarAdapter = SidebarAdapter { onSidebarClick(it) }
    private val channelAdapter: ChannelAdapter = ChannelAdapter(
        onClick = { playFrom(channelAdapter.items, it, currentCategory) },
        onLongClick = { position -> channelAdapter.items.getOrNull(position)?.let { showChannelOptions(it) } },
    )
    private val homeAdapter: HomeAdapter = HomeAdapter(
        onPlay = { row, position -> playFrom(row.channels, position, row.title) },
        onFavorite = { showChannelOptions(it) },
    )

    private val isHome get() = currentCategory == Dashboard.HOME
    private val isSearch get() = currentCategory == SEARCH
    private val content: View
        get() = when {
            isHome -> binding.home
            isSearch -> binding.searchInput
            else -> binding.channels
        }

    private val runSearch = Runnable { if (isSearch) showCategory(SEARCH, force = true) }

    private val voiceSearch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            binding.searchInput.setText(spoken)
            binding.searchInput.setSelection(spoken.length)
            handler.removeCallbacks(runSearch)
            runSearch.run()
            if (channelAdapter.itemCount > 0) binding.channels.requestFocus()
        }
    }

    /** Focus left the sidebar: commit any half-scrolled category and re-select the active one. */
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (newFocus != null && !isInside(newFocus, binding.sidebar)) {
            if (pendingCategory != null) {
                handler.removeCallbacks(applyPending)
                applyPending.run()
            }
            selectActiveCategoryRow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PlaylistRepository(this)
        updater = Updater(this)
        filter = ChannelStore.filter(this)
        setUpFilterBar()
        setUpSearch()

        binding.sidebar.adapter = sidebarAdapter
        binding.sidebar.setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
            override fun onChildViewHolderSelected(
                parent: RecyclerView,
                child: RecyclerView.ViewHolder?,
                position: Int,
                subposition: Int,
            ) {
                val item = sidebarAdapter.items.getOrNull(position) ?: return
                // Categories switch as you scroll through them; actions wait for OK.
                if (item.isAction || !binding.sidebar.hasFocus()) return
                pendingCategory = item.key
                handler.removeCallbacks(applyPending)
                handler.postDelayed(applyPending, CATEGORY_DELAY_MS)
            }
        })

        // Phones (the drawer layout) size the grid to the screen; TV and tablets use four columns.
        val drawer = binding.drawer
        if (drawer != null) {
            binding.channels.setNumColumns(maxOf(2, resources.configuration.screenWidthDp / 180))
            binding.menuButton?.setOnClickListener { drawer.openDrawer(GravityCompat.START) }
            binding.searchButton?.setOnClickListener { openSearch() }
        } else {
            binding.channels.setNumColumns(4)
        }
        savedInstanceState?.getString(STATE_CATEGORY)?.let { currentCategory = it }
        binding.channels.adapter = channelAdapter
        binding.home.adapter = homeAdapter

        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val drawer = binding.drawer
                if (drawer != null) {
                    // Phone: close the drawer, then return to Home, then leave.
                    when {
                        drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                        !isHome -> {
                            showCategory(Dashboard.HOME)
                            binding.home.scrollToPosition(0)
                        }
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                    return
                }
                // Back from the channel grid goes to the categories first, then exits.
                if (binding.channels.hasFocus() || binding.home.hasFocus() || binding.filterBar.hasFocus() ||
                    binding.searchBar.hasFocus()
                ) {
                    binding.sidebar.requestFocus()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        load(forceRefresh = false)
        checkForUpdate(userAsked = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, currentCategory)
    }

    override fun onDestroy() {
        binding.root.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Favourites and history change while watching; refresh counts without moving focus.
        if (allChannels.isNotEmpty()) {
            refreshSidebar()
            if (isHome) {
                refreshHome()
                return
            }
            val updated = gridChannels()
            channelAdapter.favorites = ChannelStore.favorites(this)
            if (updated != channelAdapter.items) {
                val position = binding.channels.selectedPosition
                channelAdapter.items = updated
                binding.channels.selectedPosition = position.coerceIn(0, (updated.size - 1).coerceAtLeast(0))
            } else {
                channelAdapter.notifyItemRangeChanged(0, updated.size)
            }
        }
    }

    private fun load(forceRefresh: Boolean) {
        binding.progress.visibility = View.VISIBLE
        binding.status.text = getString(R.string.loading)
        lifecycleScope.launch {
            val result = repository.load(forceRefresh)
            loadedChannels = result.channels
            allChannels = visibleChannels()
            binding.progress.visibility = View.GONE
            binding.status.text = getString(R.string.channel_count, allChannels.size)
            if (result.failedSources.isNotEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.sources_failed, result.failedSources.joinToString()),
                    Toast.LENGTH_LONG,
                ).show()
            }
            refreshSidebar()
            showCategory(currentCategory, force = true)
            if (firstLoad || !binding.root.hasFocus()) {
                firstLoad = false
                (if (allChannels.isEmpty()) binding.sidebar else content).requestFocus()
            }
        }
    }

    private fun filtered(category: String) =
        Categories.filter(category, allChannels, ChannelStore.favorites(this), ChannelStore.recent(this))

    private fun refreshSidebar() {
        val favorites = ChannelStore.favorites(this)
        val recent = ChannelStore.recent(this)
        val categories = listOf(Dashboard.HOME, SEARCH) + Categories.build(allChannels, favorites, recent)
        if (currentCategory !in categories) currentCategory = categories.first()

        val items = categories.map {
            val count = if (it == Dashboard.HOME || it == SEARCH) null else Categories.filter(it, allChannels, favorites, recent).size
            SidebarItem(it, if (it == SEARCH) getString(R.string.search) else it, count)
        } + listOf(
            SidebarItem(ACTION_REFRESH, getString(R.string.refresh), isAction = true),
            SidebarItem(ACTION_SOURCES, getString(R.string.channel_sources), isAction = true),
            SidebarItem(ACTION_PLAYLISTS, getString(R.string.my_playlists), isAction = true),
        ) + hiddenSidebarItem() + listOf(
            SidebarItem(
                ACTION_UPDATE,
                update?.let { getString(R.string.update_to, it.versionName) }
                    ?: getString(R.string.check_updates, updater.currentVersionName),
                isAction = true,
            ),
        )
        if (items != sidebarAdapter.items) {
            val hadFocus = binding.sidebar.hasFocus()
            val selectedKey = sidebarAdapter.items.getOrNull(binding.sidebar.selectedPosition)?.key
            sidebarAdapter.items = items
            sidebarAdapter.activeKey = currentCategory
            val restore = items.indexOfFirst { it.key == (if (hadFocus) selectedKey else currentCategory) }
            binding.sidebar.selectedPosition = restore.coerceAtLeast(0)
        }
    }

    private fun selectActiveCategoryRow() {
        val index = sidebarAdapter.items.indexOfFirst { it.key == currentCategory }
        if (index >= 0 && binding.sidebar.selectedPosition != index) binding.sidebar.selectedPosition = index
    }

    private fun showCategory(category: String, force: Boolean = false) {
        if (category == currentCategory && !force) return
        currentCategory = category
        sidebarAdapter.activeKey = category
        binding.home.visibility = if (isHome) View.VISIBLE else View.GONE
        binding.channels.visibility = if (isHome) View.GONE else View.VISIBLE
        binding.filterBar.visibility = if (isHome || isSearch) View.GONE else View.VISIBLE
        binding.searchBar.visibility = if (isSearch) View.VISIBLE else View.GONE
        if (isHome) {
            showHome()
            return
        }
        if (isSearch) {
            showSearchResults()
            return
        }
        val unfiltered = filtered(category)
        val channels = filter.apply(unfiltered)
        channelAdapter.favorites = ChannelStore.favorites(this)
        channelAdapter.items = channels
        binding.channels.selectedPosition = 0
        binding.title.text = category
        binding.subtitle.text = if (filter.isActive) {
            getString(R.string.filtered_count, channels.size, unfiltered.size)
        } else {
            resources.getQuantityString(R.plurals.channels, channels.size, channels.size)
        }
        binding.empty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = getString(
            when {
                allChannels.isEmpty() -> R.string.empty
                unfiltered.isNotEmpty() -> R.string.empty_filtered
                else -> R.string.empty_category
            },
        )
        updateFilterChips()
    }

    private fun onSidebarClick(item: SidebarItem) {
        binding.drawer?.closeDrawer(GravityCompat.START)
        when (item.key) {
            ACTION_REFRESH -> load(forceRefresh = true)
            ACTION_SOURCES -> showSourcesDialog()
            ACTION_PLAYLISTS -> showPlaylistsDialog()
            ACTION_HIDDEN -> showHiddenDialog()
            SEARCH -> openSearch()
            ACTION_UPDATE -> update?.let { startUpdate(it) } ?: checkForUpdate(userAsked = true)
            else -> {
                handler.removeCallbacks(applyPending)
                pendingCategory = null
                showCategory(item.key)
                if (isHome || channelAdapter.items.isNotEmpty()) content.requestFocus()
            }
        }
    }

    private fun playFrom(list: List<Channel>, position: Int, title: String) {
        val channel = list.getOrNull(position) ?: return
        if (channel.isYouTube) {
            openYouTube(channel)
            return
        }
        val playable = list.filterNot { it.isYouTube }
        ChannelStore.playing = playable
        ChannelStore.playingTitle = title
        startActivity(
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, playable.indexOf(channel)),
        )
    }

    private fun setUpFilterBar() {
        binding.chipLanguage.setOnClickListener { chooseLanguage() }
        binding.chipQuality.setOnClickListener { chooseQuality() }
        binding.chipSort.setOnClickListener { chooseSort() }
        binding.chipRegion.setOnClickListener { applyFilter(filter.copy(hideRegionLocked = !filter.hideRegionLocked)) }
        binding.chipPartTime.setOnClickListener { applyFilter(filter.copy(hidePartTime = !filter.hidePartTime)) }
        binding.chipYouTube.setOnClickListener { applyFilter(filter.copy(hideYouTube = !filter.hideYouTube)) }
        binding.chipClear.setOnClickListener {
            applyFilter(ChannelFilter())
            // The Clear chip hides itself; keep focus in the bar.
            binding.chipLanguage.requestFocus()
        }
        updateFilterChips()
    }

    private fun applyFilter(updated: ChannelFilter) {
        filter = updated
        ChannelStore.saveFilter(this, updated)
        if (!isHome) showCategory(currentCategory, force = true)
        updateFilterChips()
    }

    private fun updateFilterChips() {
        with(binding) {
            chipLanguage.text = getString(R.string.filter_language, filter.language ?: getString(R.string.filter_language_all))
            chipLanguage.isActivated = filter.language != null
            chipQuality.text = getString(
                R.string.filter_quality,
                when (filter.minQuality) {
                    0 -> getString(R.string.filter_quality_any)
                    1080 -> "Full HD"
                    else -> "HD"
                },
            )
            chipQuality.isActivated = filter.minQuality > 0
            chipSort.text = getString(R.string.filter_sort, getString(sortLabel(filter.sort)))
            chipSort.isActivated = filter.sort != ChannelFilter.Sort.DEFAULT
            chipRegion.isActivated = filter.hideRegionLocked
            chipPartTime.isActivated = filter.hidePartTime
            chipYouTube.isActivated = filter.hideYouTube
            chipClear.visibility = if (filter.isActive) View.VISIBLE else View.GONE
        }
    }

    private fun sortLabel(sort: ChannelFilter.Sort) = when (sort) {
        ChannelFilter.Sort.DEFAULT -> R.string.sort_default
        ChannelFilter.Sort.NAME -> R.string.sort_name
        ChannelFilter.Sort.QUALITY -> R.string.sort_quality
        ChannelFilter.Sort.SOURCES -> R.string.sort_sources
    }

    /** Languages in the current category, with counts, so empty choices never appear. */
    private fun chooseLanguage() {
        val category = filtered(currentCategory)
        val counts = ChannelFilter.languageCounts(category)
        val labels = listOf(getString(R.string.filter_option_count, getString(R.string.filter_language_all), category.size)) +
            counts.map { (language, count) -> getString(R.string.filter_option_count, language, count) }
        val values = listOf<String?>(null) + counts.map { it.first }
        AlertDialog.Builder(this)
            .setTitle(R.string.filter_language_all)
            .setSingleChoiceItems(labels.toTypedArray(), values.indexOf(filter.language).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                applyFilter(filter.copy(language = values[which]))
            }
            .show()
    }

    private fun chooseQuality() {
        val values = listOf(0, 720, 1080)
        val labels = listOf(R.string.filter_quality_any, R.string.filter_quality_hd, R.string.filter_quality_full_hd).map { getString(it) }
        AlertDialog.Builder(this)
            .setSingleChoiceItems(labels.toTypedArray(), values.indexOf(filter.minQuality).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                applyFilter(filter.copy(minQuality = values[which]))
            }
            .show()
    }

    private fun chooseSort() {
        val values = ChannelFilter.Sort.values()
        AlertDialog.Builder(this)
            .setSingleChoiceItems(values.map { getString(sortLabel(it)) }.toTypedArray(), values.indexOf(filter.sort)) { dialog, which ->
                dialog.dismiss()
                applyFilter(filter.copy(sort = values[which]))
            }
            .show()
    }

    private fun showHome() {
        val malayalam = allChannels.count { it.isMalayalam }
        binding.title.text = getString(greeting())
        binding.subtitle.text = getString(R.string.home_summary, allChannels.size, malayalam)
        binding.empty.visibility = if (allChannels.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = getString(R.string.empty)
        homeAdapter.favorites = ChannelStore.favorites(this)
        homeAdapter.rows = Dashboard.rows(allChannels, homeAdapter.favorites, ChannelStore.recent(this))
        binding.home.selectedPosition = 0
    }

    /** Rebuilds the rows after watching or favouriting, keeping the focused row when nothing moved. */
    private fun refreshHome() {
        val favorites = ChannelStore.favorites(this)
        val rows = Dashboard.rows(allChannels, favorites, ChannelStore.recent(this))
        if (rows == homeAdapter.rows && favorites == homeAdapter.favorites) return
        val position = binding.home.selectedPosition
        homeAdapter.favorites = favorites
        homeAdapter.rows = rows
        binding.home.selectedPosition = position.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        binding.title.text = getString(greeting())
    }

    private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.good_morning
        in 12..16 -> R.string.good_afternoon
        else -> R.string.good_evening
    }

    private fun openYouTube(channel: Channel) {
        ChannelStore.addRecent(this, channel)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channel.url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.youtube_missing, Toast.LENGTH_LONG).show()
        }
    }

    /** The list the grid shows for the current category: filtered, or search results. */
    private fun gridChannels(): List<Channel> =
        if (isSearch) ChannelSearch.search(allChannels, binding.searchInput.text.toString())
        else filter.apply(filtered(currentCategory))

    private fun setUpSearch() {
        binding.searchInput.doAfterTextChanged {
            handler.removeCallbacks(runSearch)
            handler.postDelayed(runSearch, SEARCH_DELAY_MS)
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                handler.removeCallbacks(runSearch)
                runSearch.run()
                hideKeyboard()
                if (channelAdapter.itemCount > 0) binding.channels.requestFocus()
                true
            } else {
                false
            }
        }
        binding.voiceSearch.setOnClickListener { startVoiceSearch() }
    }

    private fun openSearch() {
        handler.removeCallbacks(applyPending)
        pendingCategory = null
        showCategory(SEARCH)
        binding.searchInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showSearchResults() {
        val query = binding.searchInput.text.toString().trim()
        val results = gridChannels()
        channelAdapter.favorites = ChannelStore.favorites(this)
        channelAdapter.items = results
        binding.channels.selectedPosition = 0
        binding.title.text = getString(R.string.search)
        binding.subtitle.text = if (query.isEmpty()) "" else getString(R.string.search_results, results.size, query)
        binding.empty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = if (query.isEmpty()) getString(R.string.search_start) else getString(R.string.search_none, query)
    }

    /**
     * Uses the system speech recogniser when the device has one. Fire TV usually doesn't expose it
     * to apps, so fall back to the keyboard, whose microphone dictates on many models.
     */
    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            voiceSearch.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
            binding.searchInput.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    /** The remote's search key (where it has one) opens Search from anywhere on this screen. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            openSearch()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun visibleChannels(): List<Channel> {
        val hidden = ChannelStore.hidden(this)
        return loadedChannels.filterNot { it.id in hidden }
    }

    /** Only shown once something has been removed. */
    private fun hiddenSidebarItem(): List<SidebarItem> {
        val count = ChannelStore.hidden(this).count { id -> loadedChannels.any { it.id == id } }
        return if (count == 0) emptyList() else listOf(
            SidebarItem(ACTION_HIDDEN, getString(R.string.removed_channels, count), isAction = true),
        )
    }

    /** Hold OK on a channel: favourite or remove it. */
    private fun showChannelOptions(channel: Channel) {
        val isFavorite = Categories.isFavorite(channel, ChannelStore.favorites(this))
        val options = arrayOf(
            getString(if (isFavorite) R.string.option_unfavorite else R.string.option_favorite),
            getString(R.string.option_remove),
        )
        AlertDialog.Builder(this)
            .setTitle(channel.name)
            .setItems(options) { _, which ->
                if (which == 0) toggleFavorite(channel) else removeChannel(channel)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removeChannel(channel: Channel) {
        ChannelStore.hide(this, channel)
        Toast.makeText(this, getString(R.string.channel_removed, channel.name), Toast.LENGTH_LONG).show()
        reapplyHidden()
    }

    private fun reapplyHidden() {
        allChannels = visibleChannels()
        binding.status.text = getString(R.string.channel_count, allChannels.size)
        refreshSidebar()
        if (isHome) {
            refreshHome()
        } else {
            // Keep the cursor near where it was rather than jumping to the top.
            val position = binding.channels.selectedPosition
            showCategory(currentCategory, force = true)
            binding.channels.selectedPosition = position.coerceIn(0, (channelAdapter.itemCount - 1).coerceAtLeast(0))
        }
    }

    private fun showHiddenDialog() {
        val hidden = ChannelStore.hidden(this)
        val removed = loadedChannels.filter { it.id in hidden }.sortedBy { it.name.lowercase() }
        if (removed.isEmpty()) return
        val checked = BooleanArray(removed.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMultiChoiceItems(removed.map { it.name }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.restore_selected) { _, _ ->
                ChannelStore.unhide(this, removed.filterIndexed { i, _ -> checked[i] }.map { it.id })
                reapplyHidden()
            }
            .setNeutralButton(R.string.restore_all) { _, _ ->
                // Also clears ids no playlist lists any more.
                ChannelStore.unhide(this, hidden)
                reapplyHidden()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleFavorite(channel: Channel) {
        val added = ChannelStore.toggleFavorite(this, channel)
        val message = if (added) R.string.favorite_added else R.string.favorite_removed
        Toast.makeText(this, getString(message, channel.name), Toast.LENGTH_SHORT).show()
        if (isHome) {
            refreshHome()
        } else {
            channelAdapter.favorites = ChannelStore.favorites(this)
            val position = channelAdapter.items.indexOf(channel)
            if (position >= 0) channelAdapter.notifyItemChanged(position)
        }
        refreshSidebar()
    }

    private fun showSourcesDialog() {
        val sources = Sources.BUILT_IN
        val enabled = repository.enabledSourceIds()
        val checked = BooleanArray(sources.size) { sources[it].id in enabled }
        AlertDialog.Builder(this)
            .setTitle(R.string.channel_sources)
            .setMultiChoiceItems(sources.map { it.title }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                repository.setEnabledSourceIds(sources.filterIndexed { i, _ -> checked[i] }.mapTo(HashSet()) { it.id })
                load(forceRefresh = false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPlaylistsDialog() {
        val custom = repository.customUrls()
        val entries = arrayOf(getString(R.string.add_playlist)) + custom.map { getString(R.string.remove_playlist, it) }
        AlertDialog.Builder(this)
            .setTitle(R.string.my_playlists)
            .setItems(entries) { _, which ->
                if (which == 0) showAddPlaylistDialog() else confirmRemove(custom[which - 1])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddPlaylistDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/playlist.m3u"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_playlist)
            .setMessage(R.string.add_playlist_hint)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    repository.addCustomUrl(url)
                    load(forceRefresh = true)
                } else {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(url: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.remove_playlist, url) + "?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                repository.removeCustomUrl(url)
                load(forceRefresh = false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun checkForUpdate(userAsked: Boolean) {
        lifecycleScope.launch {
            val found = updater.availableUpdate()
            update = found
            if (allChannels.isNotEmpty()) refreshSidebar()
            when {
                found != null -> showUpdateDialog(found)
                userAsked -> Toast.makeText(
                    this@MainActivity,
                    getString(R.string.up_to_date, updater.currentVersionName),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun showUpdateDialog(release: ReleaseInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available)
            .setMessage(getString(R.string.update_message, release.versionName, updater.currentVersionName))
            .setPositiveButton(R.string.update_now) { _, _ -> startUpdate(release) }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun startUpdate(release: ReleaseInfo) {
        if (!updater.canInstall()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.allow_install_title)
                .setMessage(R.string.allow_install_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    if (!updater.openInstallPermissionSettings(this)) {
                        Toast.makeText(this, R.string.allow_install_manual, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = TextView(this).apply { text = getString(R.string.downloading, 0) }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
            addView(label)
            addView(progress)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_to, release.versionName))
            .setView(content)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val apk = updater.download(release) { percent ->
                    progress.progress = percent
                    label.text = getString(R.string.downloading, percent)
                }
                dialog.dismiss()
                updater.install(this@MainActivity, apk)
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(this@MainActivity, getString(R.string.update_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isInside(view: View, parent: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }
}
