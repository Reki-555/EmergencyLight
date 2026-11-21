package com.emergencylight.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencylight.app.AppState
import com.emergencylight.app.R
import com.emergencylight.app.ScreenMode
import com.emergencylight.app.UiMessage
import com.emergencylight.app.ui.theme.FixedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Full-screen light (F10) with control bar [Exit] [Torch] [Lock] [SOS] and pocket lock (F19).
 * Colors here never depend on Style or Theme.
 */
@Composable
fun LightModeScreen(snackbarHostState: SnackbarHostState) {
    val mode = AppState.screenMode
    val locked = AppState.lightModeLocked
    val lightColor = remember { mutableStateOf(AppState.steadyColor.color) }
    var hintToken by remember { mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(false) }

    // ----- color: steady, or flashing A <-> B on a real-time schedule (section 8.5) -----
    LaunchedEffect(mode, AppState.steadyColor) {
        if (mode == ScreenMode.STEADY) {
            lightColor.value = AppState.steadyColor.color
        } else if (mode == ScreenMode.FLASHING) {
            var showA = true
            lightColor.value = AppState.flashColorA.color
            var next = SystemClock.uptimeMillis()
            while (isActive) {
                // Interval is read each time, so a change applies from the next switch.
                next += AppState.flashIntervalTenths * 100L
                val wait = next - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)
                // If we fell far behind (app was in the background), restart the schedule.
                if (SystemClock.uptimeMillis() - next > 1000L) next = SystemClock.uptimeMillis()
                showA = !showA
                lightColor.value = if (showA) AppState.flashColorA.color else AppState.flashColorB.color
            }
        }
    }

    // ----- control bar: visible 4 s after entering / each tap -----
    LaunchedEffect(AppState.controlBarToken) {
        if (AppState.controlBarVisible) {
            delay(4000)
            AppState.controlBarVisible = false
        }
    }

    // ----- "Locked — hold 🔒 for 2 s" hint for 2 s -----
    LaunchedEffect(hintToken) {
        if (hintToken > 0) {
            hintVisible = true
            delay(2000)
            hintVisible = false
        }
    }

    // Back: leaves light mode, except while locked (then it does nothing).
    BackHandler(enabled = true) {
        if (!AppState.lightModeLocked) AppState.setScreenMode(ScreenMode.OFF)
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(lightColor.value) } // only redraws, no recomposition
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (AppState.lightModeLocked) hintToken++ else AppState.showControlBar()
                    }
                )
            }
    ) {
        AnimatedVisibility(
            visible = AppState.controlBarVisible && !locked,
            enter = fadeIn(animationSpec = snap()),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlBar()
        }

        if (locked) {
            LockedOverlay()
        }

        if (hintVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 24.dp, end = 24.dp, bottom = 96.dp)
                    .background(FixedColors.ControlBarBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.locked_hint),
                    color = FixedColors.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 80.dp),
        ) { data -> AppSnackbar(data) }
    }
}

@Composable
private fun ControlBar() {
    val sos = AppState.sosActive
    val torchEnabled = AppState.hasFlash && !sos
    Row(
        Modifier
            .fillMaxWidth()
            .background(FixedColors.ControlBarBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        BarButton(stringResource(R.string.lm_exit), enabled = true) {
            AppState.setScreenMode(ScreenMode.OFF)
        }
        BarButton(
            stringResource(if (AppState.torchOn) R.string.lm_torch_on else R.string.lm_torch_off),
            enabled = torchEnabled,
        ) {
            AppState.setTorch(!AppState.torchOn, fromApp = true)
            AppState.showControlBar()
        }
        BarButton(stringResource(R.string.lm_lock), enabled = true) {
            AppState.lightModeLocked = true
            AppState.controlBarVisible = false
        }
        BarSosButton()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarButton(
    text: String,
    enabled: Boolean,
    onPress: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPress)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = FixedColors.White, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarSosButton() {
    val active = AppState.sosActive
    val hold = AppState.holdToStop
    val progress = remember { Animatable(0f) }
    val sosDesc = stringResource(R.string.cd_sound_sos)
    val stopDesc = stringResource(R.string.cd_stop_alarm)
    val gesture = if (active && hold) {
        Modifier.holdToActivate(
            durationMs = 2000,
            progress = progress,
            onComplete = { AppState.requestStopSos() },
            onReleasedEarly = { AppState.post(UiMessage(R.string.snack_hold, 2000L)) },
        )
    } else {
        Modifier.clickable(role = Role.Button) {
            if (active) AppState.requestStopSos() else AppState.requestStartSos()
            AppState.showControlBar()
        }
    }
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .background(if (active) FixedColors.White else FixedColors.SosRed)
            .then(gesture)
            .clearAndSetSemantics {
                contentDescription = if (active) stopDesc else sosDesc
                role = Role.Button
            }
            .drawBehind {
                drawRect(FixedColors.SosRed.copy(alpha = 0.3f), size = Size(size.width * progress.value, size.height))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (active) R.string.lm_stop else R.string.lm_sos),
            color = if (active) FixedColors.SosRed else FixedColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Pocket mode (F19): only the two hold-buttons work; everything else is ignored. */
@Composable
private fun BoxScope.LockedOverlay() {
    val unlockProgress = remember { Animatable(0f) }
    val sosProgress = remember { Animatable(0f) }
    val active = AppState.sosActive
    val unlockDesc = stringResource(R.string.cd_hold_unlock)
    val sosDesc = stringResource(if (active) R.string.cd_stop_alarm else R.string.cd_sound_sos)
    // Start SOS: hold 1 s. Stop: hold 1 s (or 2 s if "Hold STOP for 2 seconds" is on).
    val sosHoldMs = if (active && AppState.holdToStop) 2000 else 1000

    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        // 🔒 unlock (hold 2 s)
        Box(
            Modifier
                .size(56.dp)
                .alpha(0.4f)
                .clip(CircleShape)
                .background(Color.Black)
                .border(1.dp, FixedColors.White, CircleShape)
                .holdToActivate(
                    durationMs = 2000,
                    progress = unlockProgress,
                    onComplete = {
                        AppState.lightModeLocked = false
                        AppState.showControlBar()
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = unlockDesc
                    role = Role.Button
                }
                .progressRing({ unlockProgress.value }, FixedColors.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔒", fontSize = 22.sp)
        }

        Box(Modifier.weight(1f))

        // SOS / STOP (hold)
        Box(
            Modifier
                .size(56.dp)
                .alpha(0.4f)
                .clip(CircleShape)
                .background(if (active) FixedColors.White else FixedColors.SosRed)
                .holdToActivate(
                    durationMs = sosHoldMs,
                    progress = sosProgress,
                    onComplete = {
                        if (AppState.sosActive) AppState.requestStopSos() else AppState.requestStartSos()
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = sosDesc
                    role = Role.Button
                }
                .progressRing({ sosProgress.value }, if (active) FixedColors.SosRed else FixedColors.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (active) R.string.lm_stop else R.string.lm_sos),
                color = if (active) FixedColors.SosRed else FixedColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
