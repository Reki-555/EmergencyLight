package com.emergencylight.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Saved settings (SharedPreferences file "emergency_light_settings").
 *
 * Rules:
 * - Key names must NEVER be renamed in future versions.
 * - Any value that is missing, of the wrong type or out of range falls back to the default.
 * - To change the structure later: raise SCHEMA_VERSION and add a conversion step in migrate().
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    init {
        migrate()
    }

    private fun migrate() {
        val stored = readInt(K_SCHEMA, 0)
        if (stored < SCHEMA_VERSION) {
            // Version 1 is the first schema, nothing to convert yet.
            // Future: if (stored < 2) { ...convert v1 -> v2... }
            prefs.edit().putInt(K_SCHEMA, SCHEMA_VERSION).apply()
        }
    }

    // ---------- safe readers (never crash on bad data) ----------

    private fun readInt(key: String, def: Int): Int =
        try { prefs.getInt(key, def) } catch (e: Exception) { def }

    private fun readBool(key: String, def: Boolean): Boolean =
        try { prefs.getBoolean(key, def) } catch (e: Exception) { def }

    private fun readString(key: String, def: String): String =
        try { prefs.getString(key, def) ?: def } catch (e: Exception) { def }

    private fun intInRange(key: String, def: Int, min: Int, max: Int, step: Int = 1): Int {
        val v = readInt(key, def)
        return if (v in min..max && (v - min) % step == 0) v else def
    }

    private fun color(key: String, def: ScreenColor): ScreenColor =
        ScreenColor.fromId(readString(key, def.id)) ?: def

    private fun putInt(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    private fun putBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    private fun putString(key: String, v: String) = prefs.edit().putString(key, v).apply()

    // ---------- settings ----------

    var torchLevel: Int
        get() = intInRange(K_TORCH_LEVEL, 10, 1, 10)
        set(v) = putInt(K_TORCH_LEVEL, v)

    var steadyColor: ScreenColor
        get() = color(K_STEADY_COLOR, ScreenColor.WHITE)
        set(v) = putString(K_STEADY_COLOR, v.id)

    var flashColorA: ScreenColor
        get() = color(K_FLASH_A, ScreenColor.RED)
        set(v) = putString(K_FLASH_A, v.id)

    var flashColorB: ScreenColor
        get() = color(K_FLASH_B, ScreenColor.WHITE)
        set(v) = putString(K_FLASH_B, v.id)

    /** Stored as whole tenths of a second (3..50), never as a decimal. */
    var flashIntervalTenths: Int
        get() = intInRange(K_FLASH_INTERVAL, 5, 3, 50)
        set(v) = putInt(K_FLASH_INTERVAL, v)

    var screenBrightnessPct: Int
        get() = intInRange(K_SCREEN_BRIGHTNESS, 100, 10, 100, 10)
        set(v) = putInt(K_SCREEN_BRIGHTNESS, v)

    var sosVolumePct: Int
        get() = intInRange(K_SOS_VOLUME, 90, 10, 100, 10)
        set(v) = putInt(K_SOS_VOLUME, v)

    var keepScreenOn: Boolean
        get() = readBool(K_KEEP_SCREEN_ON, true)
        set(v) = putBool(K_KEEP_SCREEN_ON, v)

    var themeMode: String
        get() = readString(K_THEME_MODE, "dark").let { if (it == "dark" || it == "light") it else "dark" }
        set(v) = putString(K_THEME_MODE, v)

    var styleId: String
        get() = readString(K_STYLE_ID, "minimal").let { if (it in STYLE_IDS) it else "minimal" }
        set(v) = putString(K_STYLE_ID, v)

    var holdToStop: Boolean
        get() = readBool(K_HOLD_TO_STOP, false)
        set(v) = putBool(K_HOLD_TO_STOP, v)

    var notificationAsked: Boolean
        get() = readBool(K_NOTIFICATION_ASKED, false)
        set(v) = putBool(K_NOTIFICATION_ASKED, v)

    /** Alarm volume to restore after SOS. -1 = nothing to restore. */
    var pendingRestoreAlarmVolume: Int
        get() = readInt(K_PENDING_RESTORE, -1).let { if (it >= -1) it else -1 }
        set(v) {
            // commit() so the value survives even if the app is killed right after.
            prefs.edit().putInt(K_PENDING_RESTORE, v).commit()
        }

    companion object {
        const val FILE_NAME = "emergency_light_settings"
        const val SCHEMA_VERSION = 1

        private const val K_SCHEMA = "schemaVersion"
        private const val K_TORCH_LEVEL = "torchLevel"
        private const val K_STEADY_COLOR = "steadyColor"
        private const val K_FLASH_A = "flashColorA"
        private const val K_FLASH_B = "flashColorB"
        private const val K_FLASH_INTERVAL = "flashIntervalTenths"
        private const val K_SCREEN_BRIGHTNESS = "screenBrightnessPct"
        private const val K_SOS_VOLUME = "sosVolumePct"
        private const val K_KEEP_SCREEN_ON = "keepScreenOn"
        private const val K_THEME_MODE = "themeMode"
        private const val K_STYLE_ID = "styleId"
        private const val K_HOLD_TO_STOP = "holdToStop"
        private const val K_NOTIFICATION_ASKED = "notificationAsked"
        private const val K_PENDING_RESTORE = "pendingRestoreAlarmVolume"

        private val STYLE_IDS = setOf("minimal", "dashboard", "instrument", "parchment", "sticker")
    }
}
