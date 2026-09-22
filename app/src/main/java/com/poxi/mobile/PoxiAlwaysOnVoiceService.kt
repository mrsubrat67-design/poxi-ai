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
        private const val CHANNEL_ID = "poxi_voice"
        private const val NOTIFICATION_ID = 1001

        private const val LISTEN_DELAY = 700L
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

    private lateinit var wakeWordEngine:
        PoxiWakeWordEngine

    private var isListening = false
    private var isSpeaking = false
    private var serviceRunning = false
    private var wakeWordMode = false

    override fun onCreate() {
        super.onCreate()

        serviceRunning = true

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification("POXI starting...")
        )

        router = PoxiCommandRouter(this)

        wakeWordEngine =
            PoxiWakeWordEngine(this)

        setupTextToSpeech()

        initializeWakeWordEngine()
    }

    private fun initializeWakeWordEngine() {

        wakeWordMode =
            wakeWordEngine.initialize()

        if (wakeWordMode) {

            updateNotification(
                "Hey Poxi ready"
            )

            startWakeWordDetection()

        } else {

            updateNotification(
                "Wake word model not installed"
            )

            setupSpeechRecognizer()

            mainHandler.post {
                startListeningSafely()
            }
        }
    }

    private fun startWakeWordDetection() {

        if (!serviceRunning) return
        if (!wakeWordEngine.isReady()) return

        updateNotification(
            "Waiting for Hey Poxi..."
        )

        val started =
            wakeWordEngine.start { score ->

                mainHandler.post {

                    if (
                        serviceRunning &&
                        !isSpeaking &&
                        !isListening
                    ) {
                        onWakeWordDetected(score)
                    }
                }
            }

        if (!started) {

            wakeWordMode = false

            updateNotification(
                "Wake word start failed"
            )

            setupSpeechRecognizer()

            scheduleListening()
        }
    }

    private fun onWakeWordDetected(
        score: Float
    ) {

        if (!serviceRunning) return
        if (isSpeaking) return
        if (isListening) return

        updateNotification(
            "Hey Poxi detected"
        )

        wakeWordEngine.stop()

        speakAndListen()
    }

    private fun speakAndListen() {

        isSpeaking = true

        textToSpeech?.speak(
            "Yes, I'm listening.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "POXI_WAKE"
        )

        mainHandler.removeCallbacks(
            commandListeningRunnable
        )

        mainHandler.postDelayed(
            commandListeningRunnable,
            900L
        )
    }

    private val commandListeningRunnable =
        Runnable {

            isSpeaking = false

            setupSpeechRecognizer()

            startListeningSafely()
        }

    private fun setupTextToSpeech() {

        textToSpeech =
            TextToSpeech(this) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    val result =
                        textToSpeech?.setLanguage(
                            Locale.getDefault()
                        )

                    if (
                        result ==
                        TextToSpeech.LANG_MISSING_DATA ||
                        result ==
                        TextToSpeech.LANG_NOT_SUPPORTED
                    ) {

                        textToSpeech?.language =
                            Locale.US
                    }
                }
            }
    }

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

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        serviceRunning = true

        if (wakeWordMode) {

            mainHandler.post {
                startWakeWordDetection()
            }

        } else {

            mainHandler.post {
                startListeningSafely()
            }
        }

        return START_STICKY
    }

    private fun setupSpeechRecognizer() {

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ) {

            updateNotification(
                "Speech recognition unavailable"
            )

            return
        }

        speechRecognizer?.cancel()
        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    isListening = true

                    updateNotification(
                        if (wakeWordMode)
                            "Command listening..."
                        else
                            "Listening..."
                    )
                }

                override fun onBeginningOfSpeech() {

                    updateNotification(
                        "Hearing command..."
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
                        "Processing..."
                    )
                }

                override fun onError(
                    error: Int
                ) {

                    isListening = false

                    if (!serviceRunning) {
                        return
                    }

                    if (wakeWordMode) {

                        updateNotification(
                            "Waiting for Hey Poxi..."
                        )

                        startWakeWordDetection()

                    } else {

                        updateNotification(
                            "Listening again..."
                        )

                        scheduleListening()
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    isListening = false

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer
                                .RESULTS_RECOGNITION
                        )

                    val command =
                        matches
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (
                        command.isNotEmpty()
                    ) {

                        processCommand(
                            command
                        )

                    } else {

                        returnToWakeWord()
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

                speechRecognizer
                    ?: return
            }

        val intent =
            Intent(
                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE,
                    Locale.getDefault()
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_PARTIAL_RESULTS,
                    false
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_MAX_RESULTS,
                    1
                )
            }

        try {

            updateNotification(
                "Listening for command..."
            )

            recognizer.startListening(
                intent
            )

        } catch (_: Exception) {

            isListening = false

            scheduleListening()
        }
    }

    private fun scheduleListening() {

        if (!serviceRunning) return

        mainHandler.removeCallbacks(
            listenRunnable
        )

        mainHandler.postDelayed(
            listenRunnable,
            LISTEN_DELAY
        )
    }

    private val listenRunnable =
        Runnable {
            startListeningSafely()
        }

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

            returnToWakeWord()
        }
    }

    private fun speak(
        text: String
    ) {

        if (!serviceRunning) return

        isSpeaking = true
        isListening = false

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        updateNotification(
            "POXI speaking..."
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

            returnToWakeWord()
        }

    private fun returnToWakeWord() {

        if (!serviceRunning) return

        isListening = false

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        if (wakeWordMode) {

            updateNotification(
                "Waiting for Hey Poxi..."
            )

            mainHandler.postDelayed(
                {
                    startWakeWordDetection()
                },
                300L
            )

        } else {

            updateNotification(
                "Listening..."
            )

            scheduleListening()
        }
    }

    override fun onDestroy() {

        serviceRunning = false
        isListening = false
        isSpeaking = false

        mainHandler.removeCallbacksAndMessages(
            null
        )

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        try {
            wakeWordEngine.release()
        } catch (_: Exception) {
        }

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
