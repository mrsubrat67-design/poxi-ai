package com.poxi.mobile

import android.app.*
import android.content.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Calendar
import java.util.Locale

class PoxiCommandRouter(private val context: Context) {

    private val memory = PoxiMemory(context)

    fun handle(command: String): String {
        val c = command.trim().lowercase(Locale.getDefault())

        return when {

            // ---------------- MEMORY ----------------
            c.startsWith("remember my name is") -> {
                val name = command.substringAfter("remember my name is").trim()
                memory.save("name", name)
                "Okay, I will remember your name is $name."
            }

            c.contains("what is my name") ||
            c.contains("mera naam kya hai") -> {
                val name = memory.get("name")
                if (name != null) {
                    "Your name is $name."
                } else {
                    "I don't know your name yet."
                }
            }

            c.startsWith("remember that") -> {
                val value = command.substringAfter("remember that").trim()
                memory.save("note_memory", value)
                "Okay, I will remember that."
            }

            // ---------------- BATTERY ----------------
            c.contains("battery") ||
            c.contains("बैटरी") -> {
                batteryStatus()
            }

            // ---------------- INTERNET SEARCH ----------------
            c.startsWith("search for") -> {
                val query = command.substringAfter("search for").trim()
                webSearch(query)
                "Searching the internet for $query."
            }

            c.startsWith("search") -> {
                val query = command.substringAfter("search").trim()
                if (query.isNotEmpty()) {
                    webSearch(query)
                    "Searching for $query."
                } else {
                    "What should I search for?"
                }
            }

            c.startsWith("google") -> {
                val query = command.substringAfter("google").trim()
                if (query.isNotEmpty()) {
                    webSearch(query)
                    "Searching Google."
                } else {
                    "What should I search for?"
                }
            }

            // ---------------- ALARM ----------------
            c.contains("set alarm") ||
            c.contains("alarm lagao") -> {
                setAlarmFromCommand(command)
            }

            // ---------------- CAMERA ----------------
            c.contains("open camera") ||
            c.contains("camera kholo") -> {
                openCamera()
                "Opening camera."
            }

            // ---------------- FLASHLIGHT ----------------
            c.contains("flashlight on") ||
            c.contains("torch on") ||
            c.contains("torch chalu") -> {
                setFlashlight(true)
                "Flashlight on."
            }

            c.contains("flashlight off") ||
            c.contains("torch off") ||
            c.contains("torch band") -> {
                setFlashlight(false)
                "Flashlight off."
            }

            // ---------------- SETTINGS ----------------
            c.contains("wifi settings") ||
            c.contains("wifi kholo") -> {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
                "Opening Wi-Fi settings."
            }

            c.contains("bluetooth settings") ||
            c.contains("bluetooth kholo") -> {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                "Opening Bluetooth settings."
            }

            // ---------------- APP LAUNCHER ----------------
            c.startsWith("open ") ||
            c.startsWith("launch ") ||
            c.startsWith("khol") -> {
                openAppByName(command)
            }

            // ---------------- NOTES ----------------
            c.startsWith("save note") ||
            c.startsWith("note likho") ||
            c.startsWith("save this") -> {
                val note = command
                    .substringAfter(
                        when {
                            c.startsWith("save note") -> "save note"
                            c.startsWith("note likho") -> "note likho"
                            else -> "save this"
                        }
                    )
                    .trim()

                if (note.isEmpty()) {
                    "What note should I save?"
                } else {
                    memory.save(
                        "last_note",
                        note
                    )
                    "Note saved."
                }
            }

            c.contains("last note") ||
            c.contains("meri note") -> {
                memory.get("last_note")
                    ?: "I don't have a saved note."
            }

            // ---------------- TIME / DATE ----------------
            c.contains("what time") ||
            c.contains("time kya") -> {
                val now = Calendar.getInstance()
                String.format(
                    Locale.getDefault(),
                    "The time is %02d:%02d.",
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE)
                )
            }

            c.contains("today's date") ||
            c.contains("what date") ||
            c.contains("date kya") -> {
                val now = Calendar.getInstance()
                String.format(
                    Locale.getDefault(),
                    "Today is %02d/%02d/%04d.",
                    now.get(Calendar.DAY_OF_MONTH),
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.YEAR)
                )
            }

            // ---------------- WEATHER ----------------
            c.contains("weather") ||
            c.contains("mausam") -> {
                webSearch("weather today")
                "Opening today's weather information."
            }

            // ---------------- MEDIA ----------------
            c.contains("pause music") ||
            c.contains("music pause") -> {
                mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                "Pausing media."
            }

            c.contains("play music") ||
            c.contains("music play") -> {
                mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                "Playing media."
            }

            c.contains("next song") ||
            c.contains("next") -> {
                mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                "Next track."
            }

            c.contains("previous song") ||
            c.contains("previous") -> {
                mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "Previous track."
            }

            // ---------------- PRIVACY / PERMISSIONS ----------------
            c.contains("privacy") ||
            c.contains("permissions") ||
            c.contains("permission status") -> {
                openSettings(Settings.ACTION_APP_DETAILS_SETTINGS)
                "Opening POXI app permissions."
            }

            else -> {
                "I don't know that command yet."
            }
        }
    }

    // --------------------------------------------------
    // BATTERY
    // --------------------------------------------------
    private fun batteryStatus(): String {
        val manager =
            context.getSystemService(Context.BATTERY_SERVICE)
                    as BatteryManager

        val level = manager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )

        return "Battery is at $level percent."
    }

    // --------------------------------------------------
    // WEB SEARCH
    // --------------------------------------------------
    private fun webSearch(query: String) {
        val url =
            "https://www.google.com/search?q=" +
                    Uri.encode(query)

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // --------------------------------------------------
    // ALARM
    // --------------------------------------------------
    private fun setAlarmFromCommand(command: String): String {

        val regex =
            Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")

        val match = regex.find(command.lowercase())

        if (match == null) {
            return "Please say the alarm time, for example, set alarm at 7 AM."
        }

        var hour =
            match.groupValues[1].toInt()

        val minute =
            match.groupValues[2]
                .ifEmpty { "0" }
                .toInt()

        val ampm =
            match.groupValues[3]

        if (ampm == "pm" && hour < 12) {
            hour += 12
        }

        if (ampm == "am" && hour == 12) {
            hour = 0
        }

        val intent = Intent(
            AlarmClock.ACTION_SET_ALARM
        ).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(
                AlarmClock.EXTRA_MESSAGE,
                "POXI Alarm"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        return "Opening alarm setup."
    }

    // --------------------------------------------------
    // CAMERA
    // --------------------------------------------------
    private fun openCamera() {
        val intent =
            Intent("android.media.action.IMAGE_CAPTURE")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    // --------------------------------------------------
    // FLASHLIGHT
    // --------------------------------------------------
    private fun setFlashlight(enabled: Boolean) {

        val cameraManager =
            context.getSystemService(
                Context.CAMERA_SERVICE
            ) as CameraManager

        try {
            val cameraId =
                cameraManager.cameraIdList.firstOrNull { id ->
                    val characteristics =
                        cameraManager.getCameraCharacteristics(id)

                    characteristics.get(
                        CameraCharacteristics.FLASH_INFO_AVAILABLE
                    ) == true
                }

            if (cameraId != null) {
                cameraManager.setTorchMode(
                    cameraId,
                    enabled
                )
            }
        } catch (_: Exception) {
        }
    }

    // --------------------------------------------------
    // SETTINGS
    // --------------------------------------------------
    private fun openSettings(action: String) {

        val intent = Intent(action)

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(intent)
    }

    // --------------------------------------------------
    // APP LAUNCHER
    // --------------------------------------------------
    private fun openAppByName(command: String): String {

        val name = command
            .replace(
                Regex("^(open|launch|khol)\\s+"),
                ""
            )
            .trim()

        val pm = context.packageManager

        val packages =
            pm.getInstalledApplications(0)

        val app = packages.firstOrNull {
            val label =
                pm.getApplicationLabel(it)
                    .toString()
                    .lowercase()

            label.contains(name)
        }

        if (app == null) {
            return "I couldn't find $name."
        }

        val launchIntent =
            pm.getLaunchIntentForPackage(
                app.packageName
            )

        if (launchIntent == null) {
            return "I can't open $name."
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(launchIntent)

        return "Opening $name."
    }

    // --------------------------------------------------
    // MEDIA
    // --------------------------------------------------
    private fun mediaKey(keyCode: Int) {

        val audioManager =
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        val down =
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                keyCode
            )

        val up =
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP,
                keyCode
            )

        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }
}
