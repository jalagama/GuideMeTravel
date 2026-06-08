package com.guideme.travel.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.media3.common.AudioAttributes as MediaAudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.guideme.travel.domain.model.GuidePlaybackState
import com.guideme.travel.domain.model.LanguageOptions
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
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
    private var currentLanguageCode = "en"
    private var audioFocusRequest: AudioFocusRequest? = null

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        val audioAttributes = MediaAudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        setAudioAttributes(audioAttributes, true)
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (!isPlaying) abandonAudioFocus()
            }
        })
    }

    val mediaSession: MediaSession = MediaSession.Builder(appContext, exoPlayer).build()

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            updateTtsLocale(currentLanguageCode)
        }
    }

    fun playGuide(
        attractionId: String,
        attractionName: String,
        transcript: String,
        audioLocalPath: String?,
        languageCode: String
    ) {
        stopInternal(resetState = false)
        currentLanguageCode = languageCode
        updateTtsLocale(languageCode)

        if (!requestAudioFocus()) return

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
        abandonAudioFocus()

        if (resetState) {
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }

    private fun updateTtsLocale(languageCode: String) {
        val locale = localeForLanguage(languageCode)
        textToSpeech?.language = locale
    }

    private fun localeForLanguage(languageCode: String): Locale {
        return when (languageCode) {
            "zh" -> Locale.CHINESE
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "ar" -> Locale("ar")
            "hi" -> Locale("hi", "IN")
            "bn" -> Locale("bn", "IN")
            "ta" -> Locale("ta", "IN")
            "te" -> Locale("te", "IN")
            else -> Locale.forLanguageTag(languageCode)
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stopInternal(resetState = true)
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stopInternal(resetState = true)
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
