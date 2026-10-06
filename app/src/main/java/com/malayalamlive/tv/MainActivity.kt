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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.malayalamlive.tv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private companion object {
        const val ACTION_REFRESH = "action:refresh"
        const val ACTION_SOURCES = "action:sources"
        const val ACTION_PLAYLISTS = "action:playlists"
        const val CATEGORY_DELAY_MS = 180L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlaylistRepository
    private var allChannels: List<Channel> = emptyList()
    private var currentCategory: String = Categories.MALAYALAM
    private var pendingCategory: String? = null
    private var firstLoad = true

    private val handler = Handler(Looper.getMainLooper())
    private val applyPending = Runnable { pendingCategory?.let { showCategory(it) }; pendingCategory = null }

    private val sidebarAdapter = SidebarAdapter { onSidebarClick(it) }
    private val channelAdapter = ChannelAdapter(
        onClick = { play(it) },
        onLongClick = { toggleFavorite(it) },
    )

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

        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back from the channel grid goes to the categories first, then exits.
                if (binding.channels.hasFocus()) {
                    binding.sidebar.requestFocus()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        load(forceRefresh = false)
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
                (if (channelAdapter.items.isEmpty()) binding.sidebar else binding.channels).requestFocus()
            }
        }
    }

    private fun filtered(category: String) =
        Categories.filter(category, allChannels, ChannelStore.favorites(this), ChannelStore.recent(this))

    private fun refreshSidebar() {
        val favorites = ChannelStore.favorites(this)
        val recent = ChannelStore.recent(this)
        val categories = Categories.build(allChannels, favorites, recent)
        if (currentCategory !in categories) currentCategory = categories.first()

        val items = categories.map {
            SidebarItem(it, it, Categories.filter(it, allChannels, favorites, recent).size)
        } + listOf(
            SidebarItem(ACTION_REFRESH, getString(R.string.refresh), isAction = true),
            SidebarItem(ACTION_SOURCES, getString(R.string.channel_sources), isAction = true),
            SidebarItem(ACTION_PLAYLISTS, getString(R.string.my_playlists), isAction = true),
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
            else -> {
                handler.removeCallbacks(applyPending)
                pendingCategory = null
                showCategory(item.key)
                if (channelAdapter.items.isNotEmpty()) binding.channels.requestFocus()
            }
        }
    }

    private fun play(position: Int) {
        val channel = channelAdapter.items.getOrNull(position) ?: return
        if (channel.isYouTube) {
            openYouTube(channel)
            return
        }
        val playable = channelAdapter.items.filterNot { it.isYouTube }
        ChannelStore.playing = playable
        ChannelStore.playingTitle = currentCategory
        startActivity(
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, playable.indexOf(channel)),
        )
    }

    private fun openYouTube(channel: Channel) {
        ChannelStore.addRecent(this, channel)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channel.url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.youtube_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFavorite(position: Int) {
        val channel = channelAdapter.items.getOrNull(position) ?: return
        val added = ChannelStore.toggleFavorite(this, channel)
        val message = if (added) R.string.favorite_added else R.string.favorite_removed
        Toast.makeText(this, getString(message, channel.name), Toast.LENGTH_SHORT).show()
        channelAdapter.favorites = ChannelStore.favorites(this)
        channelAdapter.notifyItemChanged(position)
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

    private fun isInside(view: View, parent: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }
}
