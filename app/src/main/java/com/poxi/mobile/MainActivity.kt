package com.poxi.mobile

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var stateText: TextView
    private lateinit var orb: TextView
    private lateinit var talkButton: Button

    private var pulseAnimator: ObjectAnimator? = null

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
                startVoiceService()
            } else {
                updatePermissionStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(9, 11, 18)
        window.navigationBarColor = Color.rgb(9, 11, 18)

        buildUi()
        updatePermissionStatus()
    }

    // ============================================================
    // UI
    // ============================================================

    private fun buildUi() {

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER_HORIZONTAL

            setPadding(
                dp(22),
                dp(28),
                dp(22),
                dp(20)
            )

            background = GradientDrawable().apply {
                setColor(Color.rgb(9, 11, 18))
            }
        }

        val scrollContent = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --------------------------------------------------------
        // HEADER
        // --------------------------------------------------------

        val logo = TextView(this).apply {

            text = "P O X I"

            textSize = 30f

            setTextColor(
                Color.rgb(235, 232, 255)
            )

            gravity = Gravity.CENTER

            typeface = android.graphics.Typeface.DEFAULT_BOLD

            letterSpacing = 0.16f
        }

        scrollContent.addView(
            logo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {

            text = "YOUR VOICE ASSISTANT"

            textSize = 11f

            letterSpacing = 0.18f

            setTextColor(
                Color.rgb(145, 139, 175)
            )

            gravity = Gravity.CENTER

            setPadding(
                0,
                dp(4),
                0,
                dp(18)
            )
        }

        scrollContent.addView(subtitle)

        // --------------------------------------------------------
        // ORB
        // --------------------------------------------------------

        orb = TextView(this).apply {

            text = "✦"

            textSize = 58f

            gravity = Gravity.CENTER

            setTextColor(
                Color.WHITE
            )

            background = GradientDrawable().apply {

                shape = GradientDrawable.OVAL

                setColor(
                    Color.rgb(83, 63, 170)
                )

                setStroke(
                    dp(2),
                    Color.rgb(142, 116, 255)
                )
            }

            elevation = dp(12).toFloat()
        }

        val orbParams =
            LinearLayout.LayoutParams(
                dp(170),
                dp(170)
            ).apply {

                gravity = Gravity.CENTER_HORIZONTAL

                bottomMargin = dp(18)
            }

        scrollContent.addView(
            orb,
            orbParams
        )

        startPulse()

        // --------------------------------------------------------
        // STATE
        // --------------------------------------------------------

        stateText = TextView(this).apply {

            text = "●  READY"

            textSize = 15f

            gravity = Gravity.CENTER

            typeface = android.graphics.Typeface.DEFAULT_BOLD

            setTextColor(
                Color.rgb(104, 235, 163)
            )

            setPadding(
                0,
                dp(2),
                0,
                dp(5)
            )
        }

        scrollContent.addView(stateText)

        statusText = TextView(this).apply {

            text = "POXI is ready"

            textSize = 13f

            gravity = Gravity.CENTER

            setTextColor(
                Color.rgb(155, 151, 173)
            )

            setPadding(
                dp(10),
                0,
                dp(10),
                dp(18)
            )
        }

        scrollContent.addView(statusText)

        // --------------------------------------------------------
        // TALK BUTTON
        // --------------------------------------------------------

        talkButton = Button(this).apply {

            text = "🎙  TALK TO POXI"

            textSize = 16f

            typeface = android.graphics.Typeface.DEFAULT_BOLD

            isAllCaps = false

            setTextColor(Color.WHITE)

            background = roundedBackground(
                Color.rgb(108, 82, 235),
                dp(24)
            )

            elevation = dp(6).toFloat()

            setPadding(
                dp(20),
                dp(6),
                dp(20),
                dp(6)
            )

            setOnClickListener {

                setListeningState()

                requestPermissionsIfNeeded(
                    listenAfterPermission = true
                )
            }
        }

        scrollContent.addView(
            talkButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        // --------------------------------------------------------
        // START / STOP ROW
        // --------------------------------------------------------

        val controlRow = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER

            setPadding(
                0,
                0,
                0,
                dp(18)
            )
        }

        val startButton = smallButton(
            "▶  Start"
        ) {
            requestPermissionsIfNeeded(
                listenAfterPermission = false
            )
        }

        val stopButton = smallButton(
            "■  Stop"
        ) {
            stopVoiceService()
        }

        controlRow.addView(
            startButton,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                marginEnd = dp(6)
            }
        )

        controlRow.addView(
            stopButton,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                marginStart = dp(6)
            }
        )

        scrollContent.addView(
            controlRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // STATUS CARDS
        // --------------------------------------------------------

        val infoRow = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER
        }

        infoRow.addView(
            infoCard(
                "🎤",
                "MIC",
                if (
                    hasPermission(
                        Manifest.permission.RECORD_AUDIO
                    )
                ) "READY" else "OFF"
            ),
            LinearLayout.LayoutParams(
                0,
                dp(86),
                1f
            ).apply {
                marginEnd = dp(5)
            }
        )

        infoRow.addView(
            infoCard(
                "🔊",
                "VOICE",
                "TTS"
            ),
            LinearLayout.LayoutParams(
                0,
                dp(86),
                1f
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        )

        infoRow.addView(
            infoCard(
                "⚡",
                "POXI",
                "ONLINE"
            ),
            LinearLayout.LayoutParams(
                0,
                dp(86),
                1f
            ).apply {
                marginStart = dp(5)
            }
        )

        scrollContent.addView(
            infoRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scrollContent,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // --------------------------------------------------------
        // FOOTER
        // --------------------------------------------------------

        val footer = TextView(this).apply {

            text = "POXI • Voice AI"

            textSize = 11f

            gravity = Gravity.CENTER

            setTextColor(
                Color.rgb(91, 88, 105)
            )

            setPadding(
                0,
                dp(12),
                0,
                0
            )
        }

        root.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    // ============================================================
    // SMALL UI HELPERS
    // ============================================================

    private fun smallButton(
        label: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            text = label

            textSize = 14f

            isAllCaps = false

            setTextColor(
                Color.rgb(221, 217, 240)
            )

            background = roundedBackground(
                Color.rgb(25, 27, 39),
                dp(18)
            )

            elevation = dp(2).toFloat()

            setOnClickListener {
                action()
            }
        }
    }

    private fun infoCard(
        icon: String,
        title: String,
        value: String
    ): LinearLayout {

        val card = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER

            background = roundedBackground(
                Color.rgb(20, 22, 32),
                dp(18)
            )

            elevation = dp(2).toFloat()

            setPadding(
                dp(5),
                dp(7),
                dp(5),
                dp(7)
            )
        }

        val iconText = TextView(this).apply {

            text = icon

            textSize = 18f

            gravity = Gravity.CENTER
        }

        card.addView(
            iconText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
            )
        )

        val titleText = TextView(this).apply {

            text = title

            textSize = 9f

            gravity = Gravity.CENTER

            letterSpacing = 0.08f

            setTextColor(
                Color.rgb(119, 115, 137)
            )
        }

        card.addView(titleText)

        val valueText = TextView(this).apply {

            text = value

            textSize = 10f

            gravity = Gravity.CENTER

            typeface =
                android.graphics.Typeface.DEFAULT_BOLD

            setTextColor(
                Color.rgb(188, 178, 255)
            )
        }

        card.addView(valueText)

        return card
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius = radius.toFloat()
        }
    }

    // ============================================================
    // ANIMATION
    // ============================================================

    private fun startPulse() {

        pulseAnimator?.cancel()

        pulseAnimator =
            ObjectAnimator.ofFloat(
                orb,
                View.ALPHA,
                0.72f,
                1f
            ).apply {

                duration = 1300L

                repeatMode =
                    ValueAnimator.REVERSE

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    AccelerateDecelerateInterpolator()

                start()
            }
    }

    private fun setListeningState() {

        stateText.text = "●  LISTENING"

        stateText.setTextColor(
            Color.rgb(105, 185, 255)
        )

        statusText.text =
            "I'm listening..."

        orb.setTextColor(Color.WHITE)

        orb.background =
            GradientDrawable().apply {

                shape = GradientDrawable.OVAL

                setColor(
                    Color.rgb(42, 92, 160)
                )

                setStroke(
                    dp(2),
                    Color.rgb(95, 178, 255)
                )
            }

        animateOrb(1.08f)
    }

    private fun setReadyState() {

        stateText.text = "●  READY"

        stateText.setTextColor(
            Color.rgb(104, 235, 163)
        )

        statusText.text =
            "POXI is ready"

        orb.background =
            GradientDrawable().apply {

                shape = GradientDrawable.OVAL

                setColor(
                    Color.rgb(83, 63, 170)
                )

                setStroke(
                    dp(2),
                    Color.rgb(142, 116, 255)
                )
            }

        animateOrb(1f)
    }

    private fun animateOrb(
        scale: Float
    ) {

        orb.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(280L)
            .setInterpolator(
                AccelerateDecelerateInterpolator()
            )
            .start()
    }

    // ============================================================
    // PERMISSIONS
    // ============================================================

    private fun requestPermissionsIfNeeded(
        listenAfterPermission: Boolean
    ) {

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

            startVoiceService()

            if (listenAfterPermission) {

                window.decorView.postDelayed({

                    sendCommand(
                        PoxiAlwaysOnVoiceService.ACTION_LISTEN
                    )

                }, 500L)
            }

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

    // ============================================================
    // SERVICE
    // ============================================================

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

            setReadyState()

            statusText.text =
                "POXI is ready"

        } catch (_: Exception) {

            statusText.text =
                "Unable to start POXI."
        }
    }

    private fun sendCommand(
        action: String
    ) {

        val intent =
            Intent(
                this,
                PoxiAlwaysOnVoiceService::class.java
            ).apply {
                this.action = action
            }

        try {

            startService(intent)

            setListeningState()

        } catch (_: Exception) {

            setReadyState()

            statusText.text =
                "Unable to start listening."
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

            pulseAnimator?.cancel()

            orb.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .start()

            stateText.text =
                "●  STOPPED"

            stateText.setTextColor(
                Color.rgb(255, 112, 112)
            )

            statusText.text =
                "POXI is stopped"

        } catch (_: Exception) {

            statusText.text =
                "Unable to stop POXI."
        }
    }

    // ============================================================
    // STATUS
    // ============================================================

    private fun updatePermissionStatus() {

        if (
            hasPermission(
                Manifest.permission.RECORD_AUDIO
            )
        ) {

            setReadyState()

        } else {

            stateText.text =
                "●  MIC PERMISSION NEEDED"

            stateText.setTextColor(
                Color.rgb(255, 183, 77)
            )

            statusText.text =
                "Allow microphone access to talk to POXI"
        }
    }

    // ============================================================
    // UTILITY
    // ============================================================

    private fun dp(value: Int): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
