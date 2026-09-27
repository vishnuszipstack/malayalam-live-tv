package com.malayalamlive.tv

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.malayalamlive.tv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlaylistRepository
    private var allChannels: List<Channel> = emptyList()
    private var currentCategory: String = Categories.MALAYALAM

    private val categoryAdapter = CategoryAdapter { showCategory(it) }
    private val channelAdapter = ChannelAdapter(
        onClick = { play(it) },
        onLongClick = { toggleFavorite(it) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PlaylistRepository(this)

        binding.categories.layoutManager = LinearLayoutManager(this)
        binding.categories.adapter = categoryAdapter
        binding.channels.layoutManager = GridLayoutManager(this, 4)
        binding.channels.adapter = channelAdapter

        binding.refresh.setOnClickListener { load(forceRefresh = true) }
        binding.playlists.setOnClickListener { showPlaylistsDialog() }

        load(forceRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        channelAdapter.favorites = ChannelStore.favorites(this)
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
                    getString(R.string.sources_failed, result.failedSources.size),
                    Toast.LENGTH_LONG,
                ).show()
            }
            if (allChannels.isEmpty()) {
                binding.empty.visibility = View.VISIBLE
                binding.refresh.requestFocus()
                return@launch
            }
            binding.empty.visibility = View.GONE
            refreshCategories()
            binding.channels.post { binding.channels.getChildAt(0)?.requestFocus() }
        }
    }

    private fun refreshCategories() {
        val favorites = ChannelStore.favorites(this)
        val categories = Categories.build(allChannels, favorites)
        if (currentCategory !in categories) currentCategory = categories.first()
        categoryAdapter.items = categories
        channelAdapter.favorites = favorites
        showCategory(currentCategory)
    }

    private fun showCategory(category: String) {
        if (category == categoryAdapter.selected && channelAdapter.items.isNotEmpty()) return
        currentCategory = category
        categoryAdapter.selected = category
        channelAdapter.items = Categories.filter(category, allChannels, ChannelStore.favorites(this))
        binding.channels.scrollToPosition(0)
    }

    private fun play(position: Int) {
        if (position !in channelAdapter.items.indices) return
        ChannelStore.playing = channelAdapter.items
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, position))
    }

    private fun toggleFavorite(position: Int) {
        val channel = channelAdapter.items.getOrNull(position) ?: return
        val added = ChannelStore.toggleFavorite(this, channel)
        val message = if (added) R.string.favorite_added else R.string.favorite_removed
        Toast.makeText(this, getString(message, channel.name), Toast.LENGTH_SHORT).show()
        val favorites = ChannelStore.favorites(this)
        categoryAdapter.items = Categories.build(allChannels, favorites)
        channelAdapter.favorites = favorites
    }

    private fun showPlaylistsDialog() {
        val custom = repository.customUrls()
        val entries = arrayOf(getString(R.string.add_playlist)) + custom.map { getString(R.string.remove_playlist, it) }
        AlertDialog.Builder(this)
            .setTitle(R.string.playlists)
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
}
