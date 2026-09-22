package com.poxi.mobile

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import java.util.Calendar
import java.util.Locale

class PoxiCommandRouter(private val context: Context) {

    private val memory = PoxiMemory(context)

    fun handle(command: String): String {
        val c = command.trim().lowercase(Locale.getDefault())

        return when {

            // ---------------- MEMORY ----------------

            c.startsWith("remember my name is") -> {
                val name = command
                    .substringAfter("remember my name is")
                    .trim()

                if (name.isEmpty()) {
                    "Please tell me your name."
                } else {
                    memory.save("name", name)
                    "Okay, I will remember your name is $name."
                }
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

            c.startsWith("remember that") ||
            c.startsWith("yaad rakho") -> {
                val value = when {
                    c.startsWith("remember that") ->
                        command.substringAfter("remember that").trim()

                    else ->
                        command.substringAfter("yaad rakho").trim()
                }

                if (value.isEmpty()) {
                    "What should I remember?"
                } else {
                    memory.save("note_memory", value)
                    "Okay, I will remember that."
                }
            }

            // ---------------- BATTERY ----------------

            c.contains("battery") ||
            c.contains("बैटरी") -> {
                batteryStatus()
            }

            // ---------------- INTERNET SEARCH ----------------

            c.startsWith("search for") -> {
                val query = command
                    .substringAfter("search for")
                    .trim()

                if (query.isEmpty()) {
                    "What should I search for?"
                } else {
                    webSearch(query)
                    "Searching the internet for $query."
                }
            }

            c.startsWith("search") -> {
                val query = command
                    .substringAfter("search")
                    .trim()

                if (query.isNotEmpty()) {
                    webSearch(query)
                    "Searching for $query."
                } else {
                    "What should I search for?"
                }
            }

            c.startsWith("google") -> {
                val query = command
                    .substringAfter("google")
                    .trim()

                if (query.isNotEmpty()) {
                    webSearch(query)
                    "Searching Google."
                } else {
                    "What should I search for?"
                }
            }

            // ---------------- ALARM ----------------

            c.contains("set alarm") ||
            c.contains("alarm lagao") ||
            c.contains("alarm set") -> {
                setAlarmFromCommand(command)
            }

            // ---------------- CAMERA ----------------

            c.contains("open camera") ||
            c.contains("camera kholo") ||
            c.contains("camera open") -> {
                openCamera()
                "Opening camera."
            }

            // ---------------- FLASHLIGHT ----------------

            c.contains("flashlight on") ||
            c.contains("torch on") ||
            c.contains("torch chalu") ||
            c.contains("torch on karo") ||
            c.contains("flashlight chalu") -> {
                if (setFlashlight(true)) {
                    "Flashlight on."
                } else {
                    "I couldn't turn on the flashlight."
                }
            }

            c.contains("flashlight off") ||
            c.contains("torch off") ||
            c.contains("torch band") ||
            c.contains("torch off karo") ||
            c.contains("flashlight band") -> {
                if (setFlashlight(false)) {
                    "Flashlight off."
                } else {
                    "I couldn't turn off the flashlight."
                }
            }

            // ---------------- SETTINGS ----------------

            c.contains("wifi settings") ||
            c.contains("wifi kholo") ||
            c.contains("wifi open") ||
            c.contains("wifi settings kholo") -> {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
                "Opening Wi-Fi settings."
            }

            c.contains("bluetooth settings") ||
            c.contains("bluetooth kholo") ||
            c.contains("bluetooth open") -> {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                "Opening Bluetooth settings."
            }

            c.contains("phone settings") ||
            c.contains("settings kholo") ||
            c == "settings" -> {
                openSettings(Settings.ACTION_SETTINGS)
                "Opening phone settings."
            }

            // ---------------- APP LAUNCHER ----------------

            c.startsWith("open ") ||
            c.startsWith("launch ") ||
            c.startsWith("khol ") ||
            c.startsWith("kholna ") -> {
                openAppByName(command)
            }

            // ---------------- NOTES ----------------

            c.startsWith("save note") ||
            c.startsWith("note likho") ||
            c.startsWith("save this") -> {

                val note = when {
                    c.startsWith("save note") ->
                        command.substringAfter("save note").trim()

                    c.startsWith("note likho") ->
                        command.substringAfter("note likho").trim()

                    else ->
                        command.substringAfter("save this").trim()
                }

                if (note.isEmpty()) {
                    "What note should I save?"
                } else {
                    memory.save("last_note", note)
                    "Note saved."
                }
            }

            c.contains("last note") ||
            c.contains("meri note") ||
            c.contains("my note") -> {
                memory.get("last_note")
                    ?: "I don't have a saved note."
            }

            // ---------------- TIME / DATE ----------------

            c.contains("what time") ||
            c.contains("time kya") ||
            c == "time" -> {
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
            c.contains("date kya") ||
            c == "date" -> {
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
            c.contains("music pause") ||
            c.contains("pause media") -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                "Pausing media."
            }

            c.contains("play music") ||
            c.contains("music play") ||
            c.contains("play media") -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                "Playing media."
            }

            c.contains("next song") ||
            c.contains("next track") ||
            c == "next" -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                "Next track."
            }

            c.contains("previous song") ||
            c.contains("previous track") ||
            c == "previous" -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "Previous track."
            }

            c.contains("volume up") ||
            c.contains("volume badhao") -> {
                adjustVolume(AudioManager.ADJUST_RAISE)
                "Volume increased."
            }

            c.contains("volume down") ||
            c.contains("volume kam") -> {
                adjustVolume(AudioManager.ADJUST_LOWER)
                "Volume decreased."
            }

            c.contains("mute phone") ||
            c.contains("phone mute") -> {
                adjustVolume(AudioManager.ADJUST_MUTE)
                "Phone muted."
            }

            // ---------------- PRIVACY / PERMISSIONS ----------------

            c.contains("privacy") ||
            c.contains("permissions") ||
            c.contains("permission status") -> {
                openAppSettings()
                "Opening POXI app permissions."
            }

            // ---------------- HELP ----------------

            c == "help" ||
            c.contains("what can you do") ||
            c.contains("tum kya kar sakte ho") -> {
                "I can open apps, search the internet, check battery, open camera, control flashlight, set alarms, save notes, tell time and date, open Wi-Fi and Bluetooth settings, and control media."
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

        return if (level >= 0) {
            "Battery is at $level percent."
        } else {
            "I couldn't read the battery level."
        }
    }

    // --------------------------------------------------
    // WEB SEARCH
    // --------------------------------------------------

    private fun webSearch(query: String) {
        val url =
            "https://www.google.com/search?q=" + Uri.encode(query)

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // --------------------------------------------------
    // ALARM
    // --------------------------------------------------

    private fun setAlarmFromCommand(command: String): String {

        val regex =
            Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")

        val match =
            regex.find(command.lowercase(Locale.getDefault()))

        if (match == null) {
            return "Please say the alarm time, for example, set alarm at 7 AM."
        }

        var hour = match.groupValues[1].toInt()

        val minute =
            match.groupValues[2]
                .ifEmpty { "0" }
                .toInt()

        val ampm = match.groupValues[3]

        if (ampm == "pm" && hour < 12) {
            hour += 12
        }

        if (ampm == "am" && hour == 12) {
            hour = 0
        }

        if (hour !in 0..23 || minute !in 0..59) {
            return "That doesn't look like a valid alarm time."
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

        return try {
            context.startActivity(intent)
            "Opening alarm setup."
        } catch (_: Exception) {
            "I couldn't open the alarm app."
        }
    }

    // --------------------------------------------------
    // CAMERA
    // --------------------------------------------------

    private fun openCamera() {
        val intent = Intent(
            "android.media.action.IMAGE_CAPTURE"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // --------------------------------------------------
    // FLASHLIGHT
    // --------------------------------------------------

    private fun setFlashlight(enabled: Boolean): Boolean {

        val cameraManager =
            context.getSystemService(
                Context.CAMERA_SERVICE
            ) as CameraManager

        return try {
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
                true
            } else {
                false
            }

        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------
    // SETTINGS
    // --------------------------------------------------

    private fun openSettings(action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // --------------------------------------------------
    // APP LAUNCHER
    // --------------------------------------------------

    private fun openAppByName(command: String): String {

        val name = command
            .replace(
                Regex("^(open|launch|khol|kholna)\\s+"),
                ""
            )
            .trim()

        if (name.isEmpty()) {
            return "Which app should I open?"
        }

        val pm = context.packageManager

        val intent = Intent(
            Intent.ACTION_MAIN,
            null
        ).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(
            intent,
            0
        )

        val app = apps.firstOrNull { info ->
            val label =
                info.loadLabel(pm)
                    .toString()
                    .lowercase(Locale.getDefault())

            label.contains(name.lowercase(Locale.getDefault()))
        }

        if (app == null) {
            return "I couldn't find $name."
        }

        val launchIntent =
            pm.getLaunchIntentForPackage(
                app.activityInfo.packageName
            )

        if (launchIntent == null) {
            return "I can't open $name."
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        return try {
            context.startActivity(launchIntent)
            "Opening ${app.loadLabel(pm)}."
        } catch (_: Exception) {
            "I couldn't open $name."
        }
    }

    // --------------------------------------------------
    // MEDIA
    // --------------------------------------------------

    private fun mediaKey(keyCode: Int) {

        val audioManager =
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        val down = KeyEvent(
            KeyEvent.ACTION_DOWN,
            keyCode
        )

        val up = KeyEvent(
            KeyEvent.ACTION_UP,
            keyCode
        )

        try {
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
        } catch (_: Exception) {
        }
    }

    private fun adjustVolume(direction: Int) {

        val audioManager =
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        try {
            audioManager.adjustVolume(
                direction,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {
        }
    }
}
