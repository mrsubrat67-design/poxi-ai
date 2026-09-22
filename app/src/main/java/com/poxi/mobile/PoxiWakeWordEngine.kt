package com.poxi.mobile

import android.content.Context
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PoxiWakeWordEngine(
    private val context: Context
) {

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private var engine: WakeWordEngine? = null
    private var detectionJob: Job? = null

    fun initialize(): Boolean {
        return try {
            val models =
                listOf(
                    WakeWordModel(
                        name = "Hey Poxi",
                        modelPath = "hey_poxi.onnx",
                        threshold = 0.1f
                    )
                )

            engine =
                WakeWordEngine(
                    context = context.applicationContext,
                    models = models,
                    detectionMode = DetectionMode.SINGLE_BEST,
                    detectionCooldownMs = 2000L,
                    scope = scope
                )

            true
        } catch (_: Exception) {
            engine = null
            false
        }
    }

    fun isReady(): Boolean {
        return engine != null
    }

    fun start(
        onDetected: (Float) -> Unit
    ): Boolean {

        val wakeEngine =
            engine ?: return false

        return try {
            detectionJob?.cancel()

            detectionJob =
                scope.launch {

                    launch {
                        wakeEngine
                            .detections
                            .collect { detection ->
                                onDetected(
                                    detection.score
                                )
                            }
                    }

                    wakeEngine.start()
                }

            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try {
            engine?.stop()
        } catch (_: Exception) {
        }

        detectionJob?.cancel()
        detectionJob = null
    }

    fun release() {
        try {
            engine?.stop()
            engine?.release()
        } catch (_: Exception) {
        }

        detectionJob?.cancel()
        detectionJob = null
        engine = null

        scope.cancel()
    }
}
