package com.emergencylight.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.emergencylight.app.ui.LightModeScreen
import com.emergencylight.app.ui.MainScreen
import com.emergencylight.app.ui.theme.AppTheme
import com.emergencylight.app.ui.theme.appStyleFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The only Activity. Opens straight to the main screen: no splash delay, no dialogs,
 * no permission requests at start, so SOS can be pressed immediately.
 */
class MainActivity : ComponentActivity() {

    /** Shortcut action waiting to run once the Activity is visible (onResume). */
    private var pendingAction: String? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            AppState.refreshNotificationPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppState.init(applicationContext)

        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) {
            pendingAction = intent?.action
        }

        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction = intent.action
    }

    override fun onResume() {
        super.onResume()
        AppState.refreshNotificationPermission()
        val action = pendingAction
        pendingAction = null
        if (action != null) AppState.handleShortcut(action)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // ---------------- window settings driven by app state ----------------

    private fun applyKeepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** F08 + F10: only the app window's brightness changes, never the system setting. */
    private fun applyLightMode(inLightMode: Boolean, brightnessPct: Int) {
        val attrs = window.attributes
        attrs.screenBrightness = if (inLightMode) {
            brightnessPct / 100f
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attrs

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (inLightMode) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyBarIcons(darkBackground: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
    }

    /** During SOS or light mode the app may show over the lock screen (to press STOP). */
    @Suppress("DEPRECATION")
    private fun applyShowWhenLocked(show: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(show)
        } else if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    @Composable
    private fun AppRoot() {
        val style = appStyleFor(AppState.styleId, AppState.darkTheme)
        val inLightMode = AppState.screenMode != ScreenMode.OFF
        val keepOn = AppState.keepScreenOn
        val brightness = AppState.screenBrightnessPct
        val showWhenLocked = inLightMode || AppState.sosActive

        LaunchedEffect(keepOn) { applyKeepScreenOn(keepOn) }
        LaunchedEffect(inLightMode, brightness) { applyLightMode(inLightMode, brightness) }
        LaunchedEffect(style.dark) { applyBarIcons(style.dark) }
        LaunchedEffect(showWhenLocked) { applyShowWhenLocked(showWhenLocked) }

        // Ask for notification permission only at the allowed moments (F11).
        val ask = AppState.askNotificationPermission
        LaunchedEffect(ask) {
            if (ask) {
                AppState.askNotificationPermission = false
                requestNotificationPermission()
            }
        }

        AppTheme(style) {
            val snackbarHostState = remember { SnackbarHostState() }
            // Messages from AppState -> snackbar, shown for exactly their duration.
            LaunchedEffect(Unit) {
                AppState.messages.collect { msg ->
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val text = getString(msg.textRes)
                    val job = launch {
                        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Indefinite)
                    }
                    launch {
                        delay(msg.durationMs)
                        job.cancel()
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (inLightMode) {
                    LightModeScreen(snackbarHostState)
                } else {
                    MainScreen(snackbarHostState)
                }
            }
        }
    }
}
