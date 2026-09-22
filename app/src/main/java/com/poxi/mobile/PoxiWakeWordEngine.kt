package com.poxi.mobile

import android.content.Context
import com.rementia.openwakeword.lib.WakeWordModel
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

    companion object {
        private const val MODEL_NAME = "hey_poxi.onnx"
        private const val THRESHOLD = 0.5f
    }

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private var engine:
        com.rementia.openwakeword.lib.WakeWordEngine? = null

    private var detectionJob: Job? = null

    private var ready = false

    fun initialize(): Boolean {

        if (!assetExists(MODEL_NAME)) {
            ready = false
            return false
        }

        return try {

            val model = WakeWordModel(
                name = "Hey Poxi",
                modelPath = MODEL_NAME,
                threshold = THRESHOLD
            )

            engine =
                com.rementia.openwakeword.lib.WakeWordEngine(
                    context = context.applicationContext,
                    models = listOf(model),
                    detectionCooldownMs = 2000L
                )

            ready = true
            true

        } catch (_: Exception) {

            engine = null
            ready = false
            false
        }
    }

    fun isReady(): Boolean {
        return ready && engine != null
    }

    fun start(
        onDetected: (Float) -> Unit
    ): Boolean {

        val wakeEngine =
            engine ?: return false

        if (!ready) return false

        return try {

            detectionJob?.cancel()

            detectionJob =
                scope.launch {

                    launch {

                        wakeEngine.detections.collect { detection ->

                            if (
                                detection.model.name ==
                                "Hey Poxi"
                            ) {
                                onDetected(
                                    detection.score
                                )
                            }
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
        ready = false

        scope.cancel()
    }

    private fun assetExists(
        name: String
    ): Boolean {

        return try {

            context.assets.open(name).use {
                true
            }

        } catch (_: Exception) {

            false
        }
    }
}
