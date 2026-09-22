package com.poxi.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class PoxiAlwaysOnVoiceService : Service() {

    companion object {

        const val ACTION_LISTEN =
            "com.poxi.mobile.action.LISTEN"

        private const val CHANNEL_ID = "poxi_voice"
        private const val NOTIFICATION_ID = 1001
        private const val TTS_RESTART_DELAY = 1200L
    }

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var speechRecognizer:
            SpeechRecognizer? = null

    private var textToSpeech:
            TextToSpeech? = null

    private lateinit var router:
            PoxiCommandRouter

    private var isListening = false
    private var isSpeaking = false
    private var serviceRunning = false

    override fun onCreate() {
        super.onCreate()

        serviceRunning = true

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification("POXI ready")
        )

        router = PoxiCommandRouter(this)

        setupTextToSpeech()
        setupSpeechRecognizer()

        updateNotification(
            "POXI ready — press Talk to POXI"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        serviceRunning = true

        if (intent?.action == ACTION_LISTEN) {

            mainHandler.post {
                startListeningSafely()
            }
        }

        return START_STICKY
    }

    // --------------------------------------------------
    // SPEECH RECOGNIZER
    // --------------------------------------------------

    private fun setupSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            updateNotification(
                "Speech recognition unavailable"
            )

            return
        }

        speechRecognizer?.cancel()
        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    isListening = true

                    updateNotification(
                        "🎤 Listening..."
                    )
                }

                override fun onBeginningOfSpeech() {

                    updateNotification(
                        "👂 Hearing command..."
                    )
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {

                    isListening = false

                    updateNotification(
                        "⚙️ Processing..."
                    )
                }

                override fun onError(
                    error: Int
                ) {

                    isListening = false

                    if (!serviceRunning) {
                        return
                    }

                    updateNotification(
                        "POXI ready — press Talk to POXI"
                    )
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    isListening = false

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (command.isNotEmpty()) {

                        processCommand(command)

                    } else {

                        updateNotification(
                            "POXI ready — press Talk to POXI"
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }

    private fun startListeningSafely() {

        if (!serviceRunning) return
        if (isSpeaking) return
        if (isListening) return

        val recognizer =
            speechRecognizer ?: run {

                setupSpeechRecognizer()

                speechRecognizer ?: return
            }

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }

        try {

            updateNotification(
                "🎤 Listening..."
            )

            recognizer.startListening(intent)

        } catch (_: Exception) {

            isListening = false

            updateNotification(
                "POXI ready — press Talk to POXI"
            )
        }
    }

    // --------------------------------------------------
    // COMMAND
    // --------------------------------------------------

    private fun processCommand(
        command: String
    ) {

        if (!serviceRunning) return

        updateNotification(
            "Processing: $command"
        )

        val response =
            try {
                router.handle(command)
            } catch (_: Exception) {
                "Sorry, I couldn't process that command."
            }

        if (response.isNotBlank()) {
            speak(response)
        } else {
            updateNotification(
                "POXI ready — press Talk to POXI"
            )
        }
    }

    // --------------------------------------------------
    // TEXT TO SPEECH
    // --------------------------------------------------

    private fun setupTextToSpeech() {

        textToSpeech =
            TextToSpeech(this) { status ->

                if (status == TextToSpeech.SUCCESS) {

                    val result =
                        textToSpeech?.setLanguage(
                            Locale.getDefault()
                        )

                    if (
                        result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        textToSpeech?.language = Locale.US
                    }
                }
            }
    }

    private fun speak(text: String) {

        if (!serviceRunning) return

        isSpeaking = true
        isListening = false

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        updateNotification(
            "🔊 POXI speaking..."
        )

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "POXI_RESPONSE"
        )

        mainHandler.removeCallbacks(
            restartAfterSpeechRunnable
        )

        mainHandler.postDelayed(
            restartAfterSpeechRunnable,
            TTS_RESTART_DELAY
        )
    }

    private val restartAfterSpeechRunnable =
        Runnable {

            isSpeaking = false

            if (!serviceRunning) return@Runnable

            updateNotification(
                "POXI ready — press Talk to POXI"
            )
        }

    // --------------------------------------------------
    // NOTIFICATION
    // --------------------------------------------------

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "POXI Voice",
                NotificationManager.IMPORTANCE_LOW
            )

        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        status: String
    ): Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("POXI Voice")
            .setContentText(status)
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        status: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(status)
        )
    }

    // --------------------------------------------------
    // DESTROY
    // --------------------------------------------------

    override fun onDestroy() {

        serviceRunning = false
        isListening = false
        isSpeaking = false

        mainHandler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }

        textToSpeech = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
