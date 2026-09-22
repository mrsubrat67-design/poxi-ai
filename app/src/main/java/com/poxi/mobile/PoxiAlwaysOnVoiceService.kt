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
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private lateinit var router: PoxiCommandRouter

    private var isListening = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Starting voice engine...")
        )

        router = PoxiCommandRouter(this)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }

        setupSpeechRecognizer()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "POXI Voice",
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("POXI Voice")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)

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

        mainHandler.post {
            startListening()
        }

        return START_STICKY
    }

    private fun setupSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable")
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    updateNotification("Listening...")
                }

                override fun onBeginningOfSpeech() {
                    updateNotification("Hearing command...")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    updateNotification("Processing...")
                }

                override fun onError(error: Int) {
                    isListening = false
                    updateNotification("Listening again...")

                    scheduleListening()
                }

                override fun onResults(results: Bundle?) {

                    isListening = false

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches?.firstOrNull()?.trim().orEmpty()

                    if (command.isNotEmpty()) {
                        processCommand(command)
                    } else {
                        scheduleListening()
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun scheduleListening() {

        mainHandler.removeCallbacksAndMessages(null)

        mainHandler.postDelayed(
            {
                startListening()
            },
            700
        )
    }

    private fun startListening() {

        if (isListening) {
            return
        }

        val recognizer = speechRecognizer ?: run {
            setupSpeechRecognizer()
            speechRecognizer ?: return
        }

        val intent = Intent(
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
            updateNotification("Listening...")
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            updateNotification("Retrying microphone...")
            scheduleListening()
        }
    }

    private fun processCommand(command: String) {

        updateNotification("Processing: $command")

        val normalized =
            command.lowercase(Locale.getDefault())

        if (
            normalized == "hey poxi" ||
            normalized == "okay poxi" ||
            normalized == "hello poxi"
        ) {
            speak("Yes, I'm listening")
            return
        }

        val response = try {
            router.handle(command)
        } catch (_: Exception) {
            "Sorry, I couldn't process that command"
        }

        if (response.isNotBlank()) {
            speak(response)
        } else {
            scheduleListening()
        }
    }

    private fun speak(text: String) {

        updateNotification("POXI speaking...")

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "POXI_RESPONSE"
        )

        mainHandler.postDelayed(
            {
                startListening()
            },
            1200
        )
    }

    override fun onDestroy() {

        mainHandler.removeCallbacksAndMessages(null)

        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
