package com.poxi.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val microphoneGranted =
                result[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

            if (microphoneGranted) {
                startVoiceService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = """
                POXI

                Voice foundation ready.

                Microphone: Checking...
                Voice service: Preparing...
            """.trimIndent()

            textSize = 20f
            setPadding(40, 60, 40, 40)
        }

        setContentView(textView)

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startVoiceService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startVoiceService() {
        val intent = Intent(
            this,
            PoxiAlwaysOnVoiceService::class.java
        )

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }
}
