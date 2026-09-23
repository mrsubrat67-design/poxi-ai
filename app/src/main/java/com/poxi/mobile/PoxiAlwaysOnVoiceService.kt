package com.poxi.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class PoxiAlwaysOnVoiceService :
    Service(),
    TextToSpeech.OnInitListener {

    companion object {

        const val ACTION_LISTEN =
            "com.poxi.mobile.ACTION_LISTEN"

        const val ACTION_STOP =
            "com.poxi.mobile.ACTION_STOP"

        private const val CHANNEL_ID =
            "poxi_voice_channel"

        private const val NOTIFICATION_ID =
            1001

        private const val SAMPLE_RATE =
            16000

        private const val MODEL_FOLDER =
            "vosk-model-small-hi-0.22"
    }

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val audioExecutor =
        Executors.newSingleThreadExecutor()

    private var audioRecord: AudioRecord? = null

    private var voskModel: Model? = null

    private var recognizer: Recognizer? = null

    private var tts: TextToSpeech? = null

    private var serviceRunning = false

    private var continuousMode = false

    private var isSpeaking = false

    private var audioThreadRunning = false

    private var modelReady = false

    private lateinit var commandRouter: PoxiCommandRouter

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification("POXI ready")
        )

        commandRouter =
            PoxiCommandRouter(this)

        tts =
            TextToSpeech(
                this,
                this
            )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        serviceRunning = true

        when (intent?.action) {

            ACTION_LISTEN -> {

                continuousMode = true

                startContinuousRecognition()
            }

            ACTION_STOP -> {

                continuousMode = false

                stopContinuousRecognition()
            }
        }

        return START_STICKY
    }

    private fun startContinuousRecognition() {

        if (!serviceRunning) {
            return
        }

        if (audioThreadRunning) {
            return
        }

        updateNotification(
            "POXI listening continuously"
        )

        audioExecutor.execute {

            try {

                if (!prepareVosk()) {
                    return@execute
                }

                startAudioLoop()

            } catch (e: Exception) {

                updateNotification(
                    "POXI microphone error"
                )
            }
        }
    }

    private fun prepareVosk(): Boolean {

        if (modelReady) {
            return true
        }

        val modelDirectory =
            File(
                filesDir,
                MODEL_FOLDER
            )

        if (!modelDirectory.exists()) {

            copyModelFromAssets(
                MODEL_FOLDER,
                modelDirectory
            )
        }

        if (!modelDirectory.exists()) {
            return false
        }

        voskModel =
            Model(
                modelDirectory.absolutePath
            )

        recognizer =
            Recognizer(
                voskModel,
                SAMPLE_RATE.toFloat()
            )

        modelReady = true

        return true
    }

    private fun copyModelFromAssets(
        assetFolder: String,
        destination: File
    ) {

        destination.mkdirs()

        val assetManager =
            assets

        val entries =
            assetManager.list(assetFolder)
                ?: return

        for (entry in entries) {

            val assetPath =
                "$assetFolder/$entry"

            val outputFile =
                File(
                    destination,
                    entry
                )

            val children =
                assetManager.list(assetPath)

            if (
                children != null &&
                children.isNotEmpty()
            ) {

                copyModelFromAssets(
                    assetPath,
                    outputFile
                )

            } else {

                assetManager
                    .open(assetPath)
                    .use { input ->

                        FileOutputStream(
                            outputFile
                        ).use { output ->

                            val buffer =
                                ByteArray(8192)

                            while (true) {

                                val count =
                                    input.read(buffer)

                                if (count <= 0) {
                                    break
                                }

                                output.write(
                                    buffer,
                                    0,
                                    count
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun startAudioLoop() {

        if (audioThreadRunning) {
            return
        }

        val minimumBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

        if (minimumBuffer <= 0) {
            return
        }

        val bufferSize =
            maxOf(
                minimumBuffer * 2,
                4096
            )

        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

        audioRecord =
            recorder

        audioThreadRunning = true

        recorder.startRecording()

        updateNotification(
            "🎤 POXI listening"
        )

        val buffer =
            ByteArray(bufferSize)

        while (
            serviceRunning &&
            continuousMode
        ) {

            try {

                val bytesRead =
                    recorder.read(
                        buffer,
                        0,
                        buffer.size
                    )

                if (bytesRead <= 0) {
                    continue
                }

                if (isSpeaking) {
                    continue
                }

                val currentRecognizer =
                    recognizer
                        ?: continue

                val accepted =
                    currentRecognizer.acceptWaveForm(
                        buffer,
                        bytesRead
                    )

                if (accepted) {

                    val result =
                        currentRecognizer
                            .result

                    handleVoskResult(
                        result
                    )
                }
            } catch (_: Exception) {

                break
            }
        }

        try {
            recorder.stop()
        } catch (_: Exception) {
        }

        recorder.release()

        audioRecord = null

        audioThreadRunning = false
    }

    private fun handleVoskResult(
        jsonResult: String
    ) {

        try {

            val json =
                JSONObject(
                    jsonResult
                )

            val text =
                json.optString(
                    "text",
                    ""
                )
                    .trim()
android.util.Log.d("POXI_VOSK", "Vosk heard: [$text]")
            if (text.isBlank()) {
                return
            }

            mainHandler.post {

                if (
                    serviceRunning &&
                    continuousMode &&
                    !isSpeaking
                ) {

                    processCommand(text)
                }
            }

        } catch (_: Exception) {
        }
    }

    private fun processCommand(
        command: String
    ) {
android.util.Log.d("POXI_CMD", "Router received: [$command]")
        val cleanCommand =
            command
                .trim()
                .lowercase(
                    Locale("hi", "IN")
                )

        if (cleanCommand.isBlank()) {
            return
        }

        if (
            cleanCommand == "stop" ||
            cleanCommand.contains("poxi band") ||
            cleanCommand.contains("listening band") ||
            cleanCommand.contains("sunna band") ||
            cleanCommand.contains("ruk jao") ||
            cleanCommand.contains("bas karo")
        ) {

            continuousMode = false

            speak(
                "Theek hai, main listening band kar raha hoon."
            )

            return
        }

        val response =
            try {

                commandRouter
                    .handle(
                        command
                    )

            } catch (_: Exception) {

                "Sorry, command process nahi ho payi."
            }

        if (
            response.isNotBlank()
        ) {

            speak(response)
        }
    }

    private fun speak(
        text: String
    ) {

        val engine =
            tts
                ?: return

        if (text.isBlank()) {
            return
        }

        isSpeaking = true

        updateNotification(
            "🔊 POXI speaking"
        )

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "POXI_TTS"
        )
    }

    override fun onInit(
        status: Int
    ) {

        if (
            status != TextToSpeech.SUCCESS
        ) {
            return
        }

        val engine =
            tts
                ?: return

        engine.language =
            Locale(
                "hi",
                "IN"
            )

        engine.setSpeechRate(
            0.95f
        )

        engine.setPitch(
            1.0f
        )

        engine.setOnUtteranceProgressListener(

            object : UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {
                    isSpeaking = true
                }

                override fun onDone(
                    utteranceId: String?
                ) {

                    mainHandler.post {

                        isSpeaking = false

                        if (
                            serviceRunning &&
                            continuousMode
                        ) {

                            updateNotification(
                                "🎤 POXI listening"
                            )
                        }
                    }
                }

                override fun onError(
                    utteranceId: String?
                ) {

                    mainHandler.post {

                        isSpeaking = false

                        if (
                            serviceRunning &&
                            continuousMode
                        ) {

                            updateNotification(
                                "🎤 POXI listening"
                            )
                        }
                    }
                }
            }
        )
    }

    private fun stopContinuousRecognition() {

        continuousMode = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        updateNotification(
            "POXI stopped"
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "POXI Voice",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun buildNotification(
        text: String
    ): Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "POXI"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    override fun onDestroy() {

        serviceRunning = false

        continuousMode = false

        audioThreadRunning = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null

        try {
            recognizer?.close()
        } catch (_: Exception) {
        }

        try {
            voskModel?.close()
        } catch (_: Exception) {
        }

        try {
            tts?.stop()
        } catch (_: Exception) {
        }

        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }

        audioExecutor.shutdownNow()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
