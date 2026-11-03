package com.malayalamlive.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        const val CATEGORY_DELAY_MS = 180L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlaylistRepository
    private lateinit var updater: Updater
    private var update: ReleaseInfo? = null
    private var allChannels: List<Channel> = emptyList()
    private var currentCategory: String = Dashboard.HOME
    private var pendingCategory: String? = null
    private var firstLoad = true

    private val handler = Handler(Looper.getMainLooper())
    private val applyPending = Runnable { pendingCategory?.let { showCategory(it) }; pendingCategory = null }

    private val sidebarAdapter = SidebarAdapter { onSidebarClick(it) }
    private val channelAdapter: ChannelAdapter = ChannelAdapter(
        onClick = { playFrom(channelAdapter.items, it, currentCategory) },
        onLongClick = { position -> channelAdapter.items.getOrNull(position)?.let { toggleFavorite(it) } },
    )
    private val homeAdapter: HomeAdapter = HomeAdapter(
        onPlay = { row, position -> playFrom(row.channels, position, row.title) },
        onFavorite = { toggleFavorite(it) },
    )

    private val isHome get() = currentCategory == Dashboard.HOME
    private val content: View get() = if (isHome) binding.home else binding.channels

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

        binding.channels.setNumColumns(4)
        binding.channels.adapter = channelAdapter
        binding.home.adapter = homeAdapter

        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back from the channel grid goes to the categories first, then exits.
                if (binding.channels.hasFocus() || binding.home.hasFocus()) {
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
            val updated = filtered(currentCategory)
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
            allChannels = result.channels
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
        val categories = listOf(Dashboard.HOME) + Categories.build(allChannels, favorites, recent)
        if (currentCategory !in categories) currentCategory = categories.first()

        val items = categories.map {
            val count = if (it == Dashboard.HOME) null else Categories.filter(it, allChannels, favorites, recent).size
            SidebarItem(it, it, count)
        } + listOf(
            SidebarItem(ACTION_REFRESH, getString(R.string.refresh), isAction = true),
            SidebarItem(ACTION_SOURCES, getString(R.string.channel_sources), isAction = true),
            SidebarItem(ACTION_PLAYLISTS, getString(R.string.my_playlists), isAction = true),
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
        if (isHome) {
            showHome()
            return
        }
        val channels = filtered(category)
        channelAdapter.favorites = ChannelStore.favorites(this)
        channelAdapter.items = channels
        binding.channels.selectedPosition = 0
        binding.title.text = category
        binding.subtitle.text = resources.getQuantityString(R.plurals.channels, channels.size, channels.size)
        binding.empty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = getString(if (allChannels.isEmpty()) R.string.empty else R.string.empty_category)
    }

    private fun onSidebarClick(item: SidebarItem) {
        when (item.key) {
            ACTION_REFRESH -> load(forceRefresh = true)
            ACTION_SOURCES -> showSourcesDialog()
            ACTION_PLAYLISTS -> showPlaylistsDialog()
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
