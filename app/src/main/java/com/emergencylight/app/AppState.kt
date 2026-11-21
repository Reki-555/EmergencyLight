package com.emergencylight.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.emergencylight.app.ui.theme.StyleId
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class ScreenMode { OFF, STEADY, FLASHING }

/** The 8 screen-light colors (section 7.2). */
enum class ScreenColor(val id: String, argb: Long, @StringRes val label: Int) {
    WHITE("white", 0xFFFFFFFF, R.string.color_white),
    AMBER("amber", 0xFFFFB300, R.string.color_amber),
    YELLOW("yellow", 0xFFFFEB3B, R.string.color_yellow),
    RED("red", 0xFFFF1744, R.string.color_red),
    GREEN("green", 0xFF00E676, R.string.color_green),
    CYAN("cyan", 0xFF00E5FF, R.string.color_cyan),
    BLUE("blue", 0xFF2979FF, R.string.color_blue),
    MAGENTA("magenta", 0xFFFF00FF, R.string.color_magenta);

    val color: Color = Color(argb)

    companion object {
        fun fromId(id: String?): ScreenColor? = entries.firstOrNull { it.id == id }
    }
}

data class UiMessage(@StringRes val textRes: Int, val durationMs: Long = 3000L)

/**
 * Central app state shared by MainActivity and LightService.
 * It lives as long as the app process, so nothing is lost when the screen rotates,
 * the theme changes or the Activity is re-created.
 * All functions are meant to be called on the main thread.
 */
object AppState {

    const val ACTION_SOS = "com.emergencylight.app.SOS"
    const val ACTION_FLASHLIGHT = "com.emergencylight.app.FLASHLIGHT"
    const val ACTION_FLASHING = "com.emergencylight.app.FLASHING"

    private const val DEBOUNCE_MS = 500L

    /**
     * Morse code "SOS" for the flashlight (section 8.4).
     * 1 unit = 0.2 s. Positive number = light ON for N units, negative = OFF for N units.
     * S = on1 off1 on1 off1 on1 (5) | gap 3 | O = on3 off1 on3 off1 on3 (11) | gap 3 | S (5) | word gap 7
     * Total = 34 units = 6.8 s, then repeat.
     */
    private const val MORSE_UNIT_MS = 200L
    private val MORSE_SOS = intArrayOf(1, -1, 1, -1, 1, -3, 3, -1, 3, -1, 3, -3, 1, -1, 1, -1, 1, -7)

    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var store: SettingsStore
    private lateinit var siren: SirenPlayer
    private var torch: TorchController? = null
    private val scope = MainScope()
    private var morseJob: Job? = null
    private var lastSosChange = 0L

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    // ---------------- saved settings ----------------
    var torchLevel by mutableIntStateOf(10)
        private set
    var steadyColor by mutableStateOf(ScreenColor.WHITE)
        private set
    var flashColorA by mutableStateOf(ScreenColor.RED)
        private set
    var flashColorB by mutableStateOf(ScreenColor.WHITE)
        private set
    var flashIntervalTenths by mutableIntStateOf(5)
        private set
    var screenBrightnessPct by mutableIntStateOf(100)
        private set
    var sosVolumePct by mutableIntStateOf(90)
        private set
    var keepScreenOn by mutableStateOf(true)
        private set
    var darkTheme by mutableStateOf(true)
        private set
    var styleId by mutableStateOf(StyleId.MINIMAL)
        private set
    var holdToStop by mutableStateOf(false)
        private set

    // ---------------- runtime state (not saved) ----------------
    var torchOn by mutableStateOf(false)
        private set
    var screenMode by mutableStateOf(ScreenMode.OFF)
        private set
    var sosActive by mutableStateOf(false)
        private set
    var torchWasOnBeforeSos = false
        private set
    var lightModeLocked by mutableStateOf(false)
    var controlBarVisible by mutableStateOf(false)
    var controlBarToken by mutableIntStateOf(0)
        private set
    var hasFlash by mutableStateOf(false)
        private set
    var torchStrengthSupported by mutableStateOf(false)
        private set
    var deviceMaxTorchLevel by mutableIntStateOf(1)
        private set
    var notificationsGranted by mutableStateOf(true)
        private set
    var askNotificationPermission by mutableStateOf(false)

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        store = SettingsStore(appContext)

        torchLevel = store.torchLevel
        steadyColor = store.steadyColor
        flashColorA = store.flashColorA
        flashColorB = store.flashColorB
        flashIntervalTenths = store.flashIntervalTenths
        screenBrightnessPct = store.screenBrightnessPct
        sosVolumePct = store.sosVolumePct
        keepScreenOn = store.keepScreenOn
        darkTheme = store.themeMode == "dark"
        styleId = StyleId.fromId(store.styleId)
        holdToStop = store.holdToStop

        siren = SirenPlayer(appContext)

        val t = TorchController.create(appContext)
        torch = t
        hasFlash = t.hasFlash
        torchStrengthSupported = t.strengthSupported
        deviceMaxTorchLevel = t.maxLevel
        t.listener = object : TorchController.Listener {
            override fun onTorchChanged(enabled: Boolean) = handleTorchCallback(enabled)
            override fun onTorchUnavailable() = handleTorchUnavailable()
        }
        t.startListening()

        // The app was closed in the middle of an SOS: give the user back their alarm volume.
        val pending = store.pendingRestoreAlarmVolume
        if (pending >= 0 && !sosActive) {
            setAlarmVolume(pending)
            store.pendingRestoreAlarmVolume = -1
        }

        LightService.ensureChannel(appContext)
        refreshNotificationPermission()
    }

    fun post(message: UiMessage) {
        messageChannel.trySend(message)
    }

    // ---------------- tap protection ----------------

    private fun now() = SystemClock.elapsedRealtime()

    fun canChangeSos(): Boolean = now() - lastSosChange >= DEBOUNCE_MS

    // ---------------- flashlight ----------------

    /** Section 8.3: app level 1..10 -> device level, rounded (.5 up), at least 1, at most device max. */
    fun deviceLevelFor(appLevel: Int): Int {
        val max = deviceMaxTorchLevel.coerceAtLeast(1)
        return ((appLevel * max + 5) / 10).coerceIn(1, max)
    }

    /** Turns the flashlight on or off. [fromApp] = the user did it inside the app (not notification/shortcut). */
    fun setTorch(on: Boolean, fromApp: Boolean) {
        if (sosActive) return // during SOS the flashlight belongs to the Morse signal
        val t = torch ?: return
        if (!t.hasFlash) return
        if (on) {
            val level = if (torchStrengthSupported) deviceLevelFor(torchLevel) else null
            when (t.turnOn(level)) {
                TorchController.Result.OK -> torchOn = true
                else -> {
                    torchOn = false
                    post(UiMessage(R.string.snack_busy))
                }
            }
        } else {
            torchOn = false
            t.turnOff()
            if (fromApp) maybeAskNotificationPermission()
        }
        syncService()
    }

    private fun handleTorchCallback(enabled: Boolean) {
        if (sosActive) return
        val t = torch ?: return
        if (t.recentlySelfChanged()) return
        if (!enabled && torchOn) {
            // Turned off from outside the app (Quick Settings, overheating, camera app ...)
            torchOn = false
            post(UiMessage(R.string.snack_system_off))
            syncService()
        }
    }

    private fun handleTorchUnavailable() {
        if (sosActive) return
        if (torchOn) {
            torchOn = false
            post(UiMessage(R.string.snack_system_off))
            syncService()
        }
    }

    // ---------------- SOS ----------------

    fun requestStartSos() {
        if (!canChangeSos()) return
        startSos()
    }

    fun requestStopSos() {
        if (!canChangeSos()) return
        stopSos(fromApp = true)
    }

    fun startSos() {
        if (sosActive) return
        lastSosChange = now()
        torchWasOnBeforeSos = torchOn

        // 1) remember the phone's current alarm volume
        val am = audioManager()
        if (store.pendingRestoreAlarmVolume < 0) {
            val current = try { am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1 } catch (e: Exception) { -1 }
            if (current >= 0) store.pendingRestoreAlarmVolume = current
        }
        // 2) set alarm volume from "SOS volume"
        applySosVolume()
        if (doNotDisturbMayBlock()) post(UiMessage(R.string.snack_dnd))

        // 3) siren
        sosActive = true
        siren.start()

        // 4) foreground service (+ wake lock) and 5) Morse flashlight
        torchOn = false
        syncService()
        startMorse()
    }

    fun stopSos(fromApp: Boolean) {
        if (!sosActive) return
        lastSosChange = now()
        siren.stop()
        stopMorse()
        sosActive = false
        restoreAlarmVolume()

        // Put the flashlight back the way it was before SOS.
        val t = torch
        torchOn = false
        if (torchWasOnBeforeSos && t != null && t.hasFlash) {
            val level = if (torchStrengthSupported) deviceLevelFor(torchLevel) else null
            torchOn = t.turnOn(level) == TorchController.Result.OK
        }
        torchWasOnBeforeSos = false
        syncService()
        if (fromApp) maybeAskNotificationPermission()
    }

    private fun startMorse() {
        val t = torch ?: return
        if (!t.hasFlash) return
        morseJob?.cancel()
        morseJob = scope.launch {
            // Strongest level the phone allows (if it supports levels).
            val level = if (torchStrengthSupported) deviceMaxTorchLevel else null
            // Timing is based on the real clock, so errors do not add up.
            var next = SystemClock.elapsedRealtime()
            while (isActive) {
                for (units in MORSE_SOS) {
                    if (units > 0) {
                        if (t.turnOn(level) != TorchController.Result.OK) {
                            // No flash / used by another app: skip silently, the siren keeps going.
                            t.turnOff()
                            return@launch
                        }
                    } else {
                        t.turnOff()
                    }
                    next += abs(units) * MORSE_UNIT_MS
                    delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                }
            }
        }
    }

    private fun stopMorse() {
        morseJob?.cancel()
        morseJob = null
        torch?.turnOff()
    }

    // ---------------- volume ----------------

    private fun audioManager(): AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Section 8.2: target = round(maxAlarmVolume * sosVolumePct / 100), .5 rounds up, minimum 1.
     * Examples: max 15 @ 90% -> 14, max 7 @ 90% -> 6, max 15 @ 50% -> 8, max 7 @ 10% -> 1.
     */
    fun targetAlarmVolume(maxVolume: Int, pct: Int): Int {
        val max = maxVolume.coerceAtLeast(1)
        return ((max * pct + 50) / 100).coerceIn(1, max)
    }

    private fun applySosVolume() {
        try {
            val am = audioManager() ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVolume(max, sosVolumePct), 0)
        } catch (e: Exception) {
            // Some phones refuse volume changes in Do Not Disturb; keep playing anyway.
        }
    }

    private fun setAlarmVolume(value: Int) {
        try {
            audioManager()?.setStreamVolume(AudioManager.STREAM_ALARM, value, 0)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun restoreAlarmVolume() {
        val saved = store.pendingRestoreAlarmVolume
        if (saved >= 0) setAlarmVolume(saved)
        store.pendingRestoreAlarmVolume = -1
    }

    private fun doNotDisturbMayBlock(): Boolean = try {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
    } catch (e: Exception) {
        false
    }

    // ---------------- screen light ----------------

    // @JvmName: avoids a clash with the hidden setter of the `screenMode` property
    // (both would be called setScreenMode inside the compiled app).
    @JvmName("changeScreenMode")
    fun setScreenMode(mode: ScreenMode) {
        screenMode = mode
        lightModeLocked = false
        if (mode == ScreenMode.OFF) controlBarVisible = false else showControlBar()
    }

    /** Shows the light-mode control bar for 4 seconds (re-tapping restarts the 4 seconds). */
    fun showControlBar() {
        controlBarVisible = true
        controlBarToken++
    }

    // ---------------- shortcuts ----------------

    fun handleShortcut(action: String?) {
        when (action) {
            ACTION_SOS -> if (!sosActive) startSos()
            ACTION_FLASHLIGHT -> if (hasFlash && !torchOn && !sosActive) setTorch(true, fromApp = false)
            ACTION_FLASHING -> setScreenMode(ScreenMode.FLASHING)
        }
    }

    // ---------------- settings updates (saved immediately) ----------------

    fun updateTorchLevel(value: Int) {
        val v = value.coerceIn(1, 10)
        if (v == torchLevel) return
        torchLevel = v
        store.torchLevel = v
        if (torchOn && !sosActive && torchStrengthSupported) {
            torch?.turnOn(deviceLevelFor(v))
        }
    }

    fun updateSteadyColor(c: ScreenColor) {
        steadyColor = c
        store.steadyColor = c
    }

    fun updateFlashColorA(c: ScreenColor) {
        flashColorA = c
        store.flashColorA = c
    }

    fun updateFlashColorB(c: ScreenColor) {
        flashColorB = c
        store.flashColorB = c
    }

    fun updateFlashInterval(tenths: Int) {
        val v = tenths.coerceIn(3, 50) // never faster than 0.3 s (photosensitivity safety)
        flashIntervalTenths = v
        store.flashIntervalTenths = v
    }

    fun updateScreenBrightness(pct: Int) {
        val v = ((pct + 5) / 10 * 10).coerceIn(10, 100)
        screenBrightnessPct = v
        store.screenBrightnessPct = v
    }

    fun updateSosVolume(pct: Int) {
        val v = ((pct + 5) / 10 * 10).coerceIn(10, 100)
        if (v == sosVolumePct) return
        sosVolumePct = v
        store.sosVolumePct = v
        if (sosActive) applySosVolume() // change is heard immediately
    }

    fun updateKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        store.keepScreenOn = on
    }

    fun updateDarkTheme(dark: Boolean) {
        darkTheme = dark
        store.themeMode = if (dark) "dark" else "light"
    }

    fun updateStyle(id: StyleId) {
        styleId = id
        store.styleId = id.id
    }

    fun updateHoldToStop(on: Boolean) {
        holdToStop = on
        store.holdToStop = on
        LightService.instance?.refresh() // notification button changes behavior
    }

    // ---------------- notifications permission ----------------

    fun refreshNotificationPermission() {
        notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Asked only once, right after the user turns the flashlight off or presses STOP ALARM. */
    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        refreshNotificationPermission()
        if (notificationsGranted || store.notificationAsked) return
        store.notificationAsked = true
        askNotificationPermission = true
    }

    // ---------------- service ----------------

    /**
     * LightService runs while the flashlight is on or SOS is sounding, and stops itself
     * when both are off.
     */
    fun syncService() {
        val needed = torchOn || sosActive
        val running = LightService.instance
        if (needed) {
            if (running != null) running.refresh() else LightService.start(appContext)
        } else {
            running?.refresh()
        }
    }

    /** The system stopped the service on its own: make the app state match reality (all off). */
    fun onServiceKilledBySystem() {
        if (sosActive) {
            siren.stop()
            stopMorse()
            sosActive = false
            restoreAlarmVolume()
            torchWasOnBeforeSos = false
        }
        if (torchOn) {
            torchOn = false
            torch?.turnOff()
        }
    }
}
