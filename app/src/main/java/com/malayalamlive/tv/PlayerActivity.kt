package com.malayalamlive.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import coil.load
import com.malayalamlive.tv.databinding.ActivityPlayerBinding

/**
 * Full-screen live player. Remote controls:
 *  ▲ / ▼ (or CH+ / CH-)  previous / next channel
 *  ◀ or OK               channel list
 *  ▶                     picture size: stretch / original / zoom
 *  Menu (☰)              add / remove favourite
 *  Play/Pause            pause / resume
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        private const val INFO_TIMEOUT_MS = 5000L
        private const val LIST_TIMEOUT_MS = 10000L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var streamIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val hideInfo = Runnable { binding.info.visibility = View.GONE }
    private val hideList = Runnable { hideChannelList() }
    private val listAdapter = PlayerListAdapter { position ->
        hideChannelList()
        if (position != index) {
            index = position
            playChannel()
        }
    }

    private val channel get() = channels[index]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()

        channels = ChannelStore.playing
        if (channels.isEmpty()) {
            finish()
            return
        }
        index = (savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0))
            .coerceIn(0, channels.size - 1)

        binding.listTitle.text = ChannelStore.playingTitle
        binding.channelList.adapter = listAdapter
        listAdapter.items = channels

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.listPanel.visibility == View.VISIBLE) {
                    hideChannelList()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.addListener(listener)
        }
        playChannel()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun playChannel() {
        streamIndex = 0
        ChannelStore.addRecent(this, channel)
        listAdapter.current = index
        binding.playerView.resizeMode = ChannelStore.resizeMode(this, channel)
        playStream()
    }

    private fun playStream() {
        val exo = player ?: return
        val stream = channel.playableStreams[streamIndex]

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        stream.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(Uri.parse(stream.url)).apply {
            val path = stream.url.substringBefore('?').lowercase()
            when {
                path.endsWith(".m3u8") || path.endsWith(".m3u") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                path.endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()

        exo.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
        exo.prepare()
        exo.playWhenReady = true
        showInfo()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            binding.buffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
        }

        override fun onPlayerError(error: PlaybackException) {
            val exo = player ?: return
            // Live streams drift out of their window; jump back to the live edge.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exo.seekToDefaultPosition()
                exo.prepare()
                return
            }
            // Same channel from another playlist / host.
            if (streamIndex + 1 < channel.playableStreams.size) {
                streamIndex++
                playStream()
                return
            }
            binding.buffering.visibility = View.GONE
            showInfo(getString(R.string.channel_unavailable, error.errorCodeName), sticky = true)
        }
    }

    private fun zap(delta: Int) {
        index = (index + delta + channels.size) % channels.size
        playChannel()
    }

    private fun cycleResizeMode() {
        val modes = ChannelStore.RESIZE_MODES
        val next = modes[(modes.indexOf(binding.playerView.resizeMode) + 1) % modes.size]
        binding.playerView.resizeMode = next
        ChannelStore.setResizeMode(this, channel, next)
        showInfo(getString(R.string.picture_changed, resizeLabel(next)))
    }

    private fun resizeLabel(mode: Int) = getString(
        when (mode) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.picture_stretch
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.picture_zoom
            else -> R.string.picture_original
        },
    )

    private fun showInfo(detail: String? = null, sticky: Boolean = false) {
        val streams = channel.playableStreams
        binding.infoNumber.text = (index + 1).toString()
        binding.infoTitle.text = channel.name
        binding.infoMeta.text = buildList {
            streams[streamIndex].quality?.let { add("${it}p") }
            if (streams.size > 1) add(getString(R.string.source_of, streamIndex + 1, streams.size))
            channel.groups.firstOrNull()?.let { add(it) }
        }.joinToString("  ·  ")
        binding.infoDetail.text = detail.orEmpty()
        binding.infoDetail.visibility = if (detail == null) View.GONE else View.VISIBLE
        binding.infoHints.text = getString(R.string.player_hints, resizeLabel(binding.playerView.resizeMode))
        binding.infoLogo.load(channel.logo) {
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
            fallback(R.drawable.ic_tv)
        }
        binding.info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        if (!sticky) handler.postDelayed(hideInfo, INFO_TIMEOUT_MS)
    }

    private fun showChannelList() {
        binding.listPanel.visibility = View.VISIBLE
        binding.channelList.selectedPosition = index
        binding.channelList.requestFocus()
        handler.removeCallbacks(hideList)
        handler.postDelayed(hideList, LIST_TIMEOUT_MS)
    }

    private fun hideChannelList() {
        handler.removeCallbacks(hideList)
        binding.listPanel.visibility = View.GONE
        binding.root.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.listPanel.visibility == View.VISIBLE) {
            handler.removeCallbacks(hideList)
            handler.postDelayed(hideList, LIST_TIMEOUT_MS)
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.action == KeyEvent.ACTION_DOWN) hideChannelList()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (binding.listPanel.visibility == View.VISIBLE) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> zap(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> showChannelList()
            KeyEvent.KEYCODE_DPAD_RIGHT -> cycleResizeMode()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.let { it.playWhenReady = !it.playWhenReady }
                showInfo()
            }
            KeyEvent.KEYCODE_MENU -> {
                val added = ChannelStore.toggleFavorite(this, channel)
                showInfo(getString(if (added) R.string.favorite_added else R.string.favorite_removed, channel.name))
            }
            KeyEvent.KEYCODE_INFO -> showInfo()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
