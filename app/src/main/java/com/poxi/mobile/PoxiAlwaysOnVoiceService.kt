package com.poxi.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var router: PoxiCommandRouter

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "POXI Voice",
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("POXI Voice")
            .setContentText("POXI is ready for voice commands")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        router = PoxiCommandRouter(this)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        }

        setupSpeechRecognizer()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startListening()

        return START_STICKY
    }

    private fun setupSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    startListening()
                }

                override fun onResults(results: Bundle?) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command = matches
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (command.isNotEmpty()) {
                        processCommand(command)
                    } else {
                        startListening()
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

    private fun startListening() {

        val recognizer = speechRecognizer ?: return

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
            recognizer.startListening(intent)
        } catch (_: Exception) {
        }
    }

    private fun processCommand(command: String) {

        val normalized = command.lowercase(Locale.getDefault())

        if (
            normalized == "hey poxi" ||
            normalized == "okay poxi" ||
            normalized == "hello poxi"
        ) {
            speak("Yes, I'm listening")
            startListening()
            return
        }

        val response = try {
            router.handle(command)
        } catch (e: Exception) {
            "Sorry, I couldn't process that command"
        }

        if (response.isNotBlank()) {
            speak(response)
        }

        startListening()
    }

    private fun speak(text: String) {

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "POXI_RESPONSE"
        )
    }

    override fun onDestroy() {

        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
