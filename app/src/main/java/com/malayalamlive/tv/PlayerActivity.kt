package com.malayalamlive.tv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.malayalamlive.tv.databinding.ActivityPlayerBinding

/**
 * Full-screen live player. Remote controls:
 *  ▲ / ▼ (or CH+ / CH-)  previous / next channel in the list you came from
 *  OK                    show channel info
 *  Play/Pause            pause / resume
 *  Menu                  add / remove favourite
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        private const val INFO_TIMEOUT_MS = 4000L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var retried = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideInfo = Runnable { binding.info.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        channels = ChannelStore.playing
        index = savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0)
        if (channels.isEmpty()) finish()
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.addListener(listener)
        }
        playCurrent()
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

    private fun playCurrent() {
        val exo = player ?: return
        val channel = channels[index]
        retried = false
        ChannelStore.setLastUrl(this, channel.url)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(channel.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        channel.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(Uri.parse(channel.url)).apply {
            val path = channel.url.substringBefore('?').lowercase()
            when {
                path.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                path.endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()

        exo.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
        exo.prepare()
        exo.playWhenReady = true
        showInfo(channel.name, null)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            binding.buffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
        }

        override fun onPlayerError(error: PlaybackException) {
            val exo = player ?: return
            // Live streams commonly drift out of their window; jump back to the live edge once.
            if (!retried) {
                retried = true
                exo.seekToDefaultPosition()
                exo.prepare()
                return
            }
            binding.buffering.visibility = View.GONE
            showInfo(channels[index].name, getString(R.string.channel_unavailable, error.errorCodeName), sticky = true)
        }
    }

    private fun zap(delta: Int) {
        index = (index + delta + channels.size) % channels.size
        playCurrent()
    }

    private fun showInfo(title: String, detail: String?, sticky: Boolean = false) {
        binding.infoTitle.text = getString(R.string.channel_position, index + 1, channels.size, title)
        binding.infoDetail.text = detail.orEmpty()
        binding.infoDetail.visibility = if (detail == null) View.GONE else View.VISIBLE
        binding.info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        if (!sticky) handler.postDelayed(hideInfo, INFO_TIMEOUT_MS)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> zap(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> showInfo(channels[index].name, null)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.let { it.playWhenReady = !it.playWhenReady }
            }
            KeyEvent.KEYCODE_MENU -> {
                val added = ChannelStore.toggleFavorite(this, channels[index])
                val message = if (added) R.string.favorite_added else R.string.favorite_removed
                showInfo(channels[index].name, getString(message, channels[index].name))
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
