package com.poxi.mobile

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

        if (c.isBlank()) {
            return "Please say a command."
        }

        // =========================
        // MEMORY — GET NAME
        // =========================

        if (
            c == "mera naam kya hai" ||
            c == "mera name kya hai" ||
            c == "what is my name" ||
            c == "what's my name"
        ) {
            val name = memory.getName()

            return if (name.isNullOrBlank()) {
                "Mujhe abhi tumhara naam yaad nahi hai."
            } else {
                "Tumhara naam $name hai."
            }
        }

        // =========================
        // MEMORY — SAVE NAME
        // =========================

        if (
            c.startsWith("remember my name is ") ||
            c.startsWith("mera naam ")
        ) {

            val name = when {

                c.startsWith("remember my name is ") ->
                    command.trim()
                        .substringAfter("remember my name is ", "")
                        .trim()

                c.startsWith("mera naam ") ->
                    command.trim()
                        .substringAfter("mera naam ", "")
                        .trim()
                        .removeSuffix("hai")
                        .trim()

                else -> ""
            }

            if (name.isNotBlank()) {
                memory.saveName(name)
                return "Theek hai, main yaad rakhunga ki tumhara naam $name hai."
            }

            return "Naam batao, jaise: mera naam Subrat hai."
        }

        // =========================
        // MEMORY — REMEMBER FACT
        // =========================

        if (
            c.startsWith("remember ") ||
            c.startsWith("yaad rakho ") ||
            c.startsWith("yaad rakhna ")
        ) {

            val fact = when {
                c.startsWith("remember ") ->
                    command.trim().substringAfter("remember ").trim()

                c.startsWith("yaad rakho ") ->
                    command.trim().substringAfter("yaad rakho ").trim()

                else ->
                    command.trim().substringAfter("yaad rakhna ").trim()
            }

            if (fact.isNotBlank()) {
                memory.saveFact(fact)
                return "Theek hai, main yaad rakhunga."
            }
        }

        if (
            c == "what did i ask you to remember" ||
            c == "maine kya yaad rakhne ko bola" ||
            c == "maine kya yaad rakhwaya"
        ) {
            val fact = memory.getFact()

            return if (fact.isNullOrBlank()) {
                "Abhi meri memory mein kuch saved nahi hai."
            } else {
                "Tumne mujhe ye yaad rakhne ko bola tha: $fact"
            }
        }

        // =========================
        // NOTES
        // =========================

        if (
            c.startsWith("note karo") ||
            c.startsWith("note kar") ||
            c.startsWith("ek note karo") ||
            c.startsWith("ek note likho") ||
            c.startsWith("write a note") ||
            c.startsWith("take a note")
        ) {

            val note = extractNote(command)

            if (note.isBlank()) {
                return "Kya note karna hai?"
            }

            memory.saveLastNote(note)

            return "Note save kar diya."
        }

        if (
            c == "last note" ||
            c == "mera last note" ||
            c == "last note kya hai" ||
            c == "mera last note batao"
        ) {

            val note = memory.getLastNote()

            return if (note.isNullOrBlank()) {
                "Abhi koi note saved nahi hai."
            } else {
                "Tumhara last note hai: $note"
            }
        }

        // =========================
        // BATTERY
        // =========================

        if (
            c.contains("battery") ||
            c.contains("charge kitna") ||
            c.contains("battery kitna")
        ) {
            return batteryStatus()
        }

        // =========================
        // INTERNET SEARCH
        // =========================

        if (
            c.startsWith("search ") ||
            c.startsWith("search for ") ||
            c.startsWith("google ") ||
            c.startsWith("internet pe ") ||
            c.startsWith("internet par ") ||
            c.contains("search karo")
        ) {
            val query = extractSearchQuery(command)

            if (query.isBlank()) {
                return "Kya search karna hai?"
            }

            webSearch(query)

            return "Internet par search kar raha hoon."
        }

        // =========================
        // ALARM
        // =========================

        if (
            c.contains("alarm") ||
            c.contains("alaram")
        ) {
            return setAlarmFromCommand(command)
        }

        // =========================
        // CAMERA
        // =========================

        if (
            c == "camera" ||
            c == "camera chalu karo" ||
            c == "camera kholo" ||
            c == "camera open karo" ||
            c == "open camera"
        ) {
            openCamera()
            return "Camera khol raha hoon."
        }

        // =========================
        // FLASHLIGHT
        // =========================

        if (
            c == "torch jala" ||
            c == "torch on" ||
            c == "flashlight on" ||
            c == "flashlight chalu karo" ||
            c == "torch chalu karo"
        ) {
            return setFlashlight(true)
        }

        if (
            c == "torch bujha" ||
            c == "torch off" ||
            c == "flashlight off" ||
            c == "flashlight band karo" ||
            c == "torch band karo"
        ) {
            return setFlashlight(false)
        }

        // =========================
        // WIFI
        // =========================

        if (
            c == "wifi" ||
            c == "wifi settings" ||
            c == "wifi setting kholo" ||
            c.contains("wifi settings kholo")
        ) {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
            return "Wi-Fi settings khol raha hoon."
        }

        // =========================
        // BLUETOOTH
        // =========================

        if (
            c == "bluetooth" ||
            c == "bluetooth settings" ||
            c == "bluetooth setting kholo" ||
            c.contains("bluetooth settings kholo")
        ) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            return "Bluetooth settings khol raha hoon."
        }

        // =========================
        // GENERAL SETTINGS
        // =========================

        if (
            c == "settings" ||
            c == "setting" ||
            c == "settings kholo" ||
            c == "setting kholo"
        ) {
            openSettings(Settings.ACTION_SETTINGS)
            return "Settings khol raha hoon."
        }

        // =========================
        // APP LAUNCHER
        // =========================

        if (
            c.startsWith("open ") ||
            c.startsWith("launch ") ||
            c.startsWith("start ") ||
            c.startsWith("chalao ")
        ) {

            val appName = command
                .trim()
                .removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("start ")
                .removePrefix("chalao ")
                .trim()

            if (appName.isNotBlank()) {
                val opened = openAppByName(appName)

                if (opened) {
                    return "$appName khol raha hoon."
                }
            }
        }

        // =========================
        // COMMON APPS — HINGLISH
        // =========================

        if (
            c == "youtube chalao" ||
            c == "youtube kholo" ||
            c == "youtube open karo"
        ) {
            return if (openAppByName("youtube")) {
                "YouTube khol raha hoon."
            } else {
                "YouTube app nahi mili."
            }
        }

        if (
            c == "whatsapp chalao" ||
            c == "whatsapp kholo" ||
            c == "whatsapp open karo"
        ) {
            return if (openAppByName("whatsapp")) {
                "WhatsApp khol raha hoon."
            } else {
                "WhatsApp app nahi mili."
            }
        }

        if (
            c == "spotify chalao" ||
            c == "spotify kholo" ||
            c == "spotify open karo"
        ) {
            return if (openAppByName("spotify")) {
                "Spotify khol raha hoon."
            } else {
                "Spotify app nahi mili."
            }
        }

        if (
            c == "instagram chalao" ||
            c == "instagram kholo"
        ) {
            return if (openAppByName("instagram")) {
                "Instagram khol raha hoon."
            } else {
                "Instagram app nahi mili."
            }
        }

        if (
            c == "facebook chalao" ||
            c == "facebook kholo"
        ) {
            return if (openAppByName("facebook")) {
                "Facebook khol raha hoon."
            } else {
                "Facebook app nahi mili."
            }
        }

        // =========================
        // MEDIA
        // =========================

        if (
            c == "play" ||
            c == "play music" ||
            c == "music chalao" ||
            c == "gaana chalao"
        ) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return "Music play kar raha hoon."
        }

        if (
            c == "pause" ||
            c == "pause music" ||
            c == "gaana roko" ||
            c == "song roko"
        ) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return "Music pause kar raha hoon."
        }

        if (
            c == "next" ||
            c == "next song" ||
            c == "agla gaana"
        ) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            return "Next song."
        }

        if (
            c == "previous" ||
            c == "previous song" ||
            c == "pichla gaana"
        ) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return "Previous song."
        }

        // =========================
        // VOLUME
        // =========================

        if (
            c == "volume up" ||
            c == "awaz tez" ||
            c == "awaaz tez" ||
            c == "awaz tez karo" ||
            c == "awaaz tez karo" ||
            c == "sound badhao"
        ) {
            adjustVolume(AudioManager.ADJUST_RAISE)
            return "Awaaz badha raha hoon."
        }

        if (
            c == "volume down" ||
            c == "awaz dheere" ||
            c == "awaaz dheere" ||
            c == "awaz dheere karo" ||
            c == "awaaz dheere karo" ||
            c == "sound kam"
        ) {
            adjustVolume(AudioManager.ADJUST_LOWER)
            return "Awaaz kam kar raha hoon."
        }

        if (
            c == "mute" ||
            c == "sound mute karo"
        ) {
            adjustVolume(AudioManager.ADJUST_MUTE)
            return "Sound mute kar diya."
        }

        // =========================
        // TIME
        // =========================

        if (
            c == "time" ||
            c == "what time is it" ||
            c == "abhi time" ||
            c == "abhi kitne baje hain"
        ) {
            val now = Calendar.getInstance()

            val hour = now.get(Calendar.HOUR)
            val minute = now.get(Calendar.MINUTE)
            val amPm = if (
                now.get(Calendar.AM_PM) == Calendar.AM
            ) "AM" else "PM"

            val displayHour =
                if (hour == 0) 12 else hour

            return "Abhi time $displayHour:${
                minute.toString().padStart(2, '0')
            } $amPm hai."
        }

        // =========================
        // DATE
        // =========================

        if (
            c == "date" ||
            c == "today's date" ||
            c == "aaj date" ||
            c == "aaj ki date"
        ) {
            val now = Calendar.getInstance()

            val day = now.get(Calendar.DAY_OF_MONTH)
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR)

            return "Aaj ki date $day/$month/$year hai."
        }

        // =========================
        // WEATHER
        // =========================

        if (
            c == "weather" ||
            c == "weather batao" ||
            c == "mausam batao"
        ) {
            webSearch("weather near me")
            return "Weather search kar raha hoon."
        }

        // =========================
        // PRIVACY / PERMISSIONS
        // =========================

        if (
            c == "permissions" ||
            c == "permission status" ||
            c == "meri permissions" ||
            c == "privacy status"
        ) {
            return permissionStatus()
        }

        // =========================
        // HELP
        // =========================

        if (
            c == "help" ||
            c == "what can you do" ||
            c == "tum kya kar sakte ho" ||
            c == "kya kar sakte ho"
        ) {
            return """
                Main ye commands handle kar sakta hoon:

                Mera naam Subrat hai
                Mera naam kya hai
                Note karo ...
                Last note kya hai
                Battery kitna hai
                Internet par ... search karo
                Alarm set karo
                Camera chalu karo
                Torch jala
                Torch bujha
                WiFi settings kholo
                Bluetooth settings kholo
                YouTube chalao
                WhatsApp chalao
                Spotify chalao
                Awaz tez karo
                Awaz dheere karo
                Music chalao
                Gaana roko
                Agla gaana
                Pichla gaana
                Time
                Aaj ki date
                Weather
            """.trimIndent()
        }

        return "Sorry, mujhe ye command samajh nahi aayi."
    }

    // =========================
    // NOTE EXTRACTION
    // =========================

    private fun extractNote(command: String): String {

        val original = command.trim()

        val prefixes = listOf(
            "ek note likho",
            "ek note karo",
            "note karo",
            "note kar",
            "write a note",
            "take a note"
        )

        for (prefix in prefixes) {
            if (
                original.startsWith(
                    prefix,
                    ignoreCase = true
                )
            ) {
                return original
                    .substring(prefix.length)
                    .trim()
                    .removePrefix(":")
                    .trim()
            }
        }

        return ""
    }

    // =========================
    // BATTERY
    // =========================

    private fun batteryStatus(): String {

        val batteryManager =
            context.getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

        val level =
            batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

        return if (level >= 0) {
            "Battery $level percent hai."
        } else {
            "Battery status unavailable hai."
        }
    }

    // =========================
    // SEARCH
    // =========================

    private fun extractSearchQuery(
        command: String
    ): String {

        var query = command.trim()

        val prefixes = listOf(
            "search for ",
            "search ",
            "google ",
            "internet pe ",
            "internet par "
        )

        for (prefix in prefixes) {
            if (
                query.startsWith(
                    prefix,
                    ignoreCase = true
                )
            ) {
                query =
                    query.substring(
                        prefix.length
                    ).trim()

                break
            }
        }

        query = query
            .removeSuffix("search karo")
            .removeSuffix("search kar")
            .trim()

        return query
    }

    private fun webSearch(query: String) {

        val uri =
            Uri.parse(
                "https://www.google.com/search?q=" +
                    Uri.encode(query)
            )

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                uri
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // =========================
    // ALARM
    // =========================

    private fun setAlarmFromCommand(
        command: String
    ): String {

        val regex =
            Regex(
                """(\d{1,2})(?::(\d{1,2}))?\s*(am|pm)?""",
                RegexOption.IGNORE_CASE
            )

        val match =
            regex.find(command)

        if (match == null) {
            return "Alarm ka time batao, jaise alarm 7 am."
        }

        var hour =
            match.groupValues[1].toIntOrNull()
                ?: return "Alarm ka time samajh nahi aaya."

        val minute =
            match.groupValues[2]
                .toIntOrNull()
                ?: 0

        val amPm =
            match.groupValues[3]
                .lowercase()

        if (amPm == "pm" && hour < 12) {
            hour += 12
        }

        if (amPm == "am" && hour == 12) {
            hour = 0
        }

        if (hour > 23 || minute > 59) {
            return "Valid alarm time batao."
        }

        val intent =
            Intent(
                AlarmClock.ACTION_SET_ALARM
            ).apply {

                putExtra(
                    AlarmClock.EXTRA_HOUR,
                    hour
                )

                putExtra(
                    AlarmClock.EXTRA_MINUTES,
                    minute
                )

                putExtra(
                    AlarmClock.EXTRA_SKIP_UI,
                    false
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        return try {

            context.startActivity(intent)

            "Alarm set karne ka screen khol raha hoon."

        } catch (_: Exception) {

            "Alarm app open nahi ho paayi."
        }
    }

    // =========================
    // CAMERA
    // =========================

    private fun openCamera() {

        val intent =
            Intent(
                android.provider.MediaStore.ACTION_IMAGE_CAPTURE
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // =========================
    // FLASHLIGHT
    // =========================

    private fun setFlashlight(
        enabled: Boolean
    ): String {

        val cameraManager =
            context.getSystemService(
                Context.CAMERA_SERVICE
            ) as CameraManager

        return try {

            val cameraId =
                cameraManager.cameraIdList.firstOrNull { id ->

                    val characteristics =
                        cameraManager.getCameraCharacteristics(
                            id
                        )

                    val lensFacing =
                        characteristics.get(
                            CameraCharacteristics.LENS_FACING
                        )

                    val flashAvailable =
                        characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true

                    lensFacing ==
                        CameraCharacteristics.LENS_FACING_BACK &&
                        flashAvailable
                }

            if (cameraId == null) {
                "Torch available nahi hai."
            } else {

                cameraManager.setTorchMode(
                    cameraId,
                    enabled
                )

                if (enabled) {
                    "Torch on kar diya."
                } else {
                    "Torch off kar diya."
                }
            }

        } catch (_: Exception) {

            "Torch control nahi ho paaya."
        }
    }

    // =========================
    // SETTINGS
    // =========================

    private fun openSettings(
        action: String
    ) {

        val intent =
            Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openAppSettings(
        packageName: String
    ) {

        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // =========================
    // APP SEARCH
    // =========================

    private fun openAppByName(
        appName: String
    ): Boolean {

        val pm = context.packageManager

        val launchableApps =
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(
                        Intent.CATEGORY_LAUNCHER
                    )
                },
                0
            )

        val target =
            appName
                .trim()
                .lowercase(Locale.getDefault())

        val match =
            launchableApps.firstOrNull { info ->

                val label =
                    info.loadLabel(pm)
                        .toString()
                        .lowercase(Locale.getDefault())

                val packageName =
                    info.activityInfo.packageName
                        .lowercase(Locale.getDefault())

                label == target ||
                    label.contains(target) ||
                    target.contains(label) ||
                    packageName.contains(
                        target.replace(" ", "")
                    )
            }

        if (match == null) {
            return false
        }

        val launchIntent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {

                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )

                setPackage(
                    match.activityInfo.packageName
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        return try {

            context.startActivity(
                launchIntent
            )

            true

        } catch (_: Exception) {

            false
        }
    }

    // =========================
    // MEDIA
    // =========================

    private fun mediaKey(
        keyCode: Int
    ) {

        val audioManager =
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        try {

            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    keyCode
                )
            )

            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    keyCode
                )
            )

        } catch (_: Exception) {
        }
    }

    // =========================
    // VOLUME
    // =========================

    private fun adjustVolume(
        direction: Int
    ) {

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

    // =========================
    // PERMISSIONS
    // =========================

    private fun permissionStatus(): String {

        val microphone =
            if (
                android.os.Build.VERSION.SDK_INT >= 23 &&
                context.checkSelfPermission(
                    android.Manifest.permission.RECORD_AUDIO
                ) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                "Microphone permission: granted"
            } else {
                "Microphone permission: not granted"
            }

        return microphone
    }
}
