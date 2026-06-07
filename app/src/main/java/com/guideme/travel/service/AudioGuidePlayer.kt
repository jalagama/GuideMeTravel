package com.guideme.travel.service

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.guideme.travel.domain.model.GuidePlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioGuidePlayer @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext

    private val _playbackState = MutableStateFlow(
        GuidePlaybackState(
            attractionId = null,
            attractionName = null,
            isPlaying = false,
            transcript = null
        )
    )
    val playbackState: StateFlow<GuidePlaybackState> = _playbackState.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            }
        })
    }

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            textToSpeech?.language = Locale.US
        }
    }

    fun playGuide(
        attractionId: String,
        attractionName: String,
        transcript: String,
        audioLocalPath: String?
    ) {
        stopInternal(resetState = false)

        _playbackState.value = GuidePlaybackState(
            attractionId = attractionId,
            attractionName = attractionName,
            isPlaying = true,
            transcript = transcript
        )

        val localFile = audioLocalPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
        if (localFile != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(localFile)))
            exoPlayer.prepare()
            exoPlayer.play()
            return
        }

        if (ttsReady) {
            textToSpeech?.speak(transcript, TextToSpeech.QUEUE_FLUSH, null, attractionId)
        }
    }

    fun stop() {
        stopInternal(resetState = true)
    }

    private fun stopInternal(resetState: Boolean) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        textToSpeech?.stop()

        if (resetState) {
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }
}
