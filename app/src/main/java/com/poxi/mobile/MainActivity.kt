package com.poxi.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val microphoneGranted =
                result[Manifest.permission.RECORD_AUDIO] == true ||
                hasPermission(Manifest.permission.RECORD_AUDIO)

            val notificationGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                result[Manifest.permission.POST_NOTIFICATIONS] == true ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)

            if (microphoneGranted && notificationGranted) {
                updateStatus(
                    "Permissions ready.\nStarting POXI voice engine..."
                )
                startVoiceService()
            } else {
                updateStatus(
                    buildPermissionStatus()
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        buildUi()
        updateStatus(buildPermissionStatus())

        requestPermissionsIfNeeded()
    }

    // --------------------------------------------------
    // UI
    // --------------------------------------------------

    private fun buildUi() {

        statusText = TextView(this).apply {
            textSize = 19f
            setPadding(30, 40, 30, 30)
        }

        val startButton =
            Button(this).apply {
                text = "🎙️ Start POXI"
                setOnClickListener {
                    requestPermissionsIfNeeded()
                }
            }

        val stopButton =
            Button(this).apply {
                text = "🛑 Stop POXI"
                setOnClickListener {
                    stopVoiceService()
                }
            }

        val refreshButton =
            Button(this).apply {
                text = "🔄 Refresh Status"
                setOnClickListener {
                    updateStatus(
                        buildPermissionStatus()
                    )
                }
            }

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    30,
                    40,
                    30,
                    30
                )

                addView(statusText)

                addView(
                    startButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                addView(
                    stopButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                addView(
                    refreshButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        setContentView(layout)
    }

    // --------------------------------------------------
    // PERMISSIONS
    // --------------------------------------------------

    private fun requestPermissionsIfNeeded() {

        val permissions =
            mutableListOf<String>()

        if (
            !hasPermission(
                Manifest.permission.RECORD_AUDIO
            )
        ) {
            permissions.add(
                Manifest.permission.RECORD_AUDIO
            )
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            if (
                !hasPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                permissions.add(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        if (permissions.isEmpty()) {

            updateStatus(
                "Permissions ready.\nStarting POXI..."
            )

            startVoiceService()

        } else {

            permissionLauncher.launch(
                permissions.toTypedArray()
            )
        }
    }

    private fun hasPermission(
        permission: String
    ): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildPermissionStatus(): String {

        val microphone =
            if (
                hasPermission(
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                "✅ Microphone"
            } else {
                "❌ Microphone"
            }

        val notification =
            if (
                Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU
            ) {
                "✅ Notifications"
            } else if (
                hasPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                "✅ Notifications"
            } else {
                "❌ Notifications"
            }

        return """
            POXI
            
            $microphone
            $notification
            
            Voice engine: Ready
        """.trimIndent()
    }

    // --------------------------------------------------
    // VOICE SERVICE
    // --------------------------------------------------

    private fun startVoiceService() {

        val intent =
            Intent(
                this,
                PoxiAlwaysOnVoiceService::class.java
            )

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

            updateStatus(
                "🎙️ POXI Voice Engine\nRunning..."
            )

        } catch (_: Exception) {

            updateStatus(
                "Unable to start POXI voice service."
            )
        }
    }

    private fun stopVoiceService() {

        val intent =
            Intent(
                this,
                PoxiAlwaysOnVoiceService::class.java
            )

        try {

            stopService(intent)

            updateStatus(
                "🛑 POXI Voice Engine\nStopped."
            )

        } catch (_: Exception) {

            updateStatus(
                "Unable to stop POXI."
            )
        }
    }

    // --------------------------------------------------
    // STATUS
    // --------------------------------------------------

    private fun updateStatus(
        status: String
    ) {
        if (::statusText.isInitialized) {
            statusText.text = status
        }
    }
}
