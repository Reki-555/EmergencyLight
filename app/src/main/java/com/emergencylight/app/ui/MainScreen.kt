package com.emergencylight.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencylight.app.AppState
import com.emergencylight.app.R
import com.emergencylight.app.ScreenMode
import com.emergencylight.app.UiMessage
import com.emergencylight.app.ui.theme.FixedColors
import com.emergencylight.app.ui.theme.LocalAppStyle
import com.emergencylight.app.ui.theme.StyleId
import kotlinx.coroutines.delay

/** Main screen (section 6.1): scrolling cards on top, fixed SOS area at the bottom, FAB above it. */
@Composable
fun MainScreen(snackbarHostState: SnackbarHostState) {
    val st = LocalAppStyle.current
    val p = st.palette
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var bottomHeightPx by remember { mutableIntStateOf(0) }
    val bottomHeight = with(LocalDensity.current) { bottomHeightPx.toDp() }
    val settingsDesc = stringResource(R.string.cd_settings)

    Box(Modifier.fillMaxSize().styledBackground(st)) {
        Column(Modifier.fillMaxSize()) {
            // ---------- scrolling part ----------
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                AppText(stringResource(R.string.app_name), size = 22.sp, bold = true, font = st.headerFont)
                StatusChips()
                Spacer(Modifier.height(16.dp))
                FlashlightCard(index = 0)
                if (st.id == StyleId.PARCHMENT) ParchmentDivider() else Spacer(Modifier.height(16.dp))
                ScreenLightCard(index = 1)
                // Room so the last card is never hidden behind the FAB.
                Spacer(Modifier.height(16.dp + 56.dp + 16.dp + 8.dp))
            }

            // ---------- fixed bottom part: SOS + SOS volume ----------
            BottomSosArea(Modifier.onSizeChanged { bottomHeightPx = it.height })
        }

        // ---------- FAB (settings) ----------
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(end = 16.dp, bottom = bottomHeight + 16.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(p.accent)
                .clickable(role = Role.Button) { showSettings = true }
                .clearAndSetSemantics {
                    contentDescription = settingsDesc
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙️", fontSize = 24.sp)
        }

        // Snackbar sits above the SOS area (and above the FAB), never covering SOS.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomHeight + 16.dp + 56.dp + 8.dp),
        ) { data -> AppSnackbar(data) }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

@Composable
private fun StatusChips() {
    val torch = AppState.torchOn
    val sos = AppState.sosActive
    if (!torch && !sos) return
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (torch) StatusChip(stringResource(R.string.status_torch_on))
        if (sos) StatusChip(stringResource(R.string.status_sos_on))
    }
}

// =====================================================================
// Flashlight card (F03, F04)
// =====================================================================

@Composable
private fun FlashlightCard(index: Int) {
    val st = LocalAppStyle.current
    val p = st.palette
    StyledCard(
        index = index,
        title = stringResource(R.string.card_flashlight),
        subtitle = stringResource(R.string.card_flashlight_subtitle),
        emoji = "🔦",
    ) {
        if (!AppState.hasFlash) {
            AppText(stringResource(R.string.no_flashlight), size = 16.sp, color = p.text2)
        } else {
            val sos = AppState.sosActive
            val level = AppState.torchLevel
            SwitchRow(
                label = stringResource(R.string.flashlight),
                checked = AppState.torchOn,
                enabled = !sos,
            ) { AppState.setTorch(!AppState.torchOn, fromApp = true) }
            if (sos) {
                AppText(stringResource(R.string.flashlight_signaling), size = 16.sp, color = p.text2)
            }
            Spacer(Modifier.height(8.dp))
            val levelsEnabled = AppState.torchStrengthSupported && !sos
            if (st.id != StyleId.INSTRUMENT) {
                AppText(stringResource(R.string.level), size = 18.sp)
            }
            if (st.id == StyleId.DASHBOARD) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    HalfGauge(level / 10f)
                }
            }
            StepperRow(
                valueText = stringResource(R.string.value_level, level),
                unitLabel = if (st.id == StyleId.INSTRUMENT) stringResource(R.string.unit_level) else null,
                valueDescription = stringResource(R.string.cd_level_value, level),
                minusDescription = stringResource(R.string.cd_dec_level),
                plusDescription = stringResource(R.string.cd_inc_level),
                canMinus = level > 1,
                canPlus = level < 10,
                enabled = levelsEnabled,
                onMinus = { AppState.updateTorchLevel(AppState.torchLevel - 1) },
                onPlus = { AppState.updateTorchLevel(AppState.torchLevel + 1) },
            )
            StepSlider(level, 1, 10, 1, levelsEnabled) { AppState.updateTorchLevel(it) }
            if (!AppState.torchStrengthSupported) {
                AppText(stringResource(R.string.levels_not_supported), size = 16.sp, color = p.text2)
            }
        }
    }
}

// =====================================================================
// Screen Light card (F05 - F09)
// =====================================================================

@Composable
private fun ScreenLightCard(index: Int) {
    val st = LocalAppStyle.current
    val p = st.palette
    var steadyOpen by rememberSaveable { mutableStateOf(false) }
    var flashOpen by rememberSaveable { mutableStateOf(false) }
    val mode = AppState.screenMode
    val down = stringResource(R.string.arrow_collapsed)
    val up = stringResource(R.string.arrow_expanded)

    StyledCard(
        index = index,
        title = stringResource(R.string.card_screen_light),
        subtitle = stringResource(R.string.card_screen_light_subtitle),
        emoji = "💡",
    ) {
        // ----- Steady light -----
        SwitchRow(
            label = stringResource(R.string.steady_light),
            checked = mode == ScreenMode.STEADY,
        ) {
            AppState.setScreenMode(if (AppState.screenMode == ScreenMode.STEADY) ScreenMode.OFF else ScreenMode.STEADY)
        }
        SummaryLine(
            stringResource(
                R.string.summary_steady,
                stringResource(AppState.steadyColor.label),
                if (steadyOpen) up else down,
            )
        ) { steadyOpen = !steadyOpen }
        if (steadyOpen) {
            ColorGrid(AppState.steadyColor) { AppState.updateSteadyColor(it) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        // ----- Flashing light -----
        val tenths = AppState.flashIntervalTenths
        val intervalText = stringResource(R.string.value_interval, formatTenths(tenths))
        SwitchRow(
            label = stringResource(R.string.flashing_light),
            checked = mode == ScreenMode.FLASHING,
        ) {
            AppState.setScreenMode(if (AppState.screenMode == ScreenMode.FLASHING) ScreenMode.OFF else ScreenMode.FLASHING)
        }
        SummaryLine(
            stringResource(
                R.string.summary_flashing,
                stringResource(AppState.flashColorA.label),
                stringResource(AppState.flashColorB.label),
                intervalText,
                if (flashOpen) up else down,
            )
        ) { flashOpen = !flashOpen }
        if (flashOpen) {
            AppText(stringResource(R.string.color_a), size = 18.sp)
            Spacer(Modifier.height(4.dp))
            ColorGrid(AppState.flashColorA) { AppState.updateFlashColorA(it) }
            Spacer(Modifier.height(12.dp))
            AppText(stringResource(R.string.color_b), size = 18.sp)
            Spacer(Modifier.height(4.dp))
            ColorGrid(AppState.flashColorB) { AppState.updateFlashColorB(it) }
            if (AppState.flashColorA == AppState.flashColorB) {
                Spacer(Modifier.height(4.dp))
                AppText(stringResource(R.string.pick_two_colors), size = 16.sp, color = p.text2)
            }
            Spacer(Modifier.height(12.dp))
            if (st.id != StyleId.INSTRUMENT) {
                AppText(stringResource(R.string.interval), size = 18.sp)
            }
            StepperRow(
                valueText = intervalText,
                unitLabel = if (st.id == StyleId.INSTRUMENT) stringResource(R.string.unit_interval) else null,
                valueDescription = stringResource(R.string.cd_interval_value, formatTenths(tenths)),
                minusDescription = stringResource(R.string.cd_dec_interval),
                plusDescription = stringResource(R.string.cd_inc_interval),
                canMinus = tenths > 3,
                canPlus = tenths < 50,
                enabled = true,
                repeat = true,
                onMinus = { AppState.updateFlashInterval(AppState.flashIntervalTenths - 1) },
                onPlus = { AppState.updateFlashInterval(AppState.flashIntervalTenths + 1) },
            )
            StepSlider(tenths, 3, 50, 1, true) { AppState.updateFlashInterval(it) }
        }

        Spacer(Modifier.height(8.dp))

        // ----- Screen brightness (F08) -----
        val bright = AppState.screenBrightnessPct
        if (st.id != StyleId.INSTRUMENT) {
            AppText(stringResource(R.string.screen_brightness), size = 18.sp)
        }
        StepperRow(
            valueText = stringResource(R.string.value_percent, bright),
            unitLabel = if (st.id == StyleId.INSTRUMENT) stringResource(R.string.unit_brightness) else null,
            valueDescription = stringResource(R.string.cd_brightness_value, bright),
            minusDescription = stringResource(R.string.cd_dec_brightness),
            plusDescription = stringResource(R.string.cd_inc_brightness),
            canMinus = bright > 10,
            canPlus = bright < 100,
            enabled = true,
            onMinus = { AppState.updateScreenBrightness(AppState.screenBrightnessPct - 10) },
            onPlus = { AppState.updateScreenBrightness(AppState.screenBrightnessPct + 10) },
        )
        StepSlider(bright, 10, 100, 10, true) { AppState.updateScreenBrightness(it) }
    }
}

// =====================================================================
// Fixed bottom area: SOS (F01, F18) + SOS volume (F02)
// =====================================================================

@Composable
private fun BottomSosArea(modifier: Modifier) {
    val p = LocalAppStyle.current.palette
    Column(
        modifier
            .fillMaxWidth()
            .background(p.surface)
            .drawBehind {
                drawLine(p.outline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        SosBar()
        Spacer(Modifier.height(12.dp))
        SosVolumeRow()
    }
}

@Composable
private fun SosBar() {
    val active = AppState.sosActive
    val hold = AppState.holdToStop
    val shape = RoundedCornerShape(16.dp)
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        blink = false
        while (active) {
            delay(500)
            blink = !blink
        }
    }
    val progress = remember { Animatable(0f) }
    val sosDesc = stringResource(R.string.cd_sound_sos)
    val stopDesc = stringResource(R.string.cd_stop_alarm)
    val base = Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(shape)

    if (!active) {
        Column(
            base
                .background(FixedColors.SosRed)
                .clickable(role = Role.Button) { AppState.requestStartSos() }
                .clearAndSetSemantics {
                    contentDescription = sosDesc
                    role = Role.Button
                }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppText(stringResource(R.string.sos), size = 32.sp, bold = true, color = FixedColors.White)
            AppText(stringResource(R.string.sos_tap_hint), size = 16.sp, color = FixedColors.White, align = TextAlign.Center)
        }
    } else {
        val gesture = if (hold) {
            Modifier.holdToActivate(
                durationMs = 2000,
                progress = progress,
                onComplete = { AppState.requestStopSos() },
                onReleasedEarly = { AppState.post(UiMessage(R.string.snack_hold, 2000L)) },
            )
        } else {
            Modifier.clickable(role = Role.Button) { AppState.requestStopSos() }
        }
        Box(
            base
                .background(if (blink) FixedColors.SosActiveB else FixedColors.SosActiveA)
                .border(4.dp, FixedColors.SosRed, shape)
                .then(gesture)
                .clearAndSetSemantics {
                    contentDescription = stopDesc
                    role = Role.Button
                },
        ) {
            // Hold progress bar (fills left to right).
            Box(
                Modifier.matchParentSize().drawBehind {
                    drawRect(
                        FixedColors.SosRed.copy(alpha = 0.3f),
                        size = Size(size.width * progress.value, size.height),
                    )
                }
            )
            Column(
                Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(stringResource(R.string.stop_alarm), size = 28.sp, bold = true, color = FixedColors.SosRed)
                if (hold) {
                    AppText(stringResource(R.string.sos_hold_hint), size = 16.sp, color = FixedColors.SosRed)
                }
            }
        }
    }
}

@Composable
private fun SosVolumeRow() {
    val st = LocalAppStyle.current
    val vol = AppState.sosVolumePct
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(stringResource(R.string.sos_volume), modifier = Modifier.weight(1f), size = 18.sp)
        StepButton("–", stringResource(R.string.cd_dec_sos_volume), vol > 10, 48.dp) {
            AppState.updateSosVolume(AppState.sosVolumePct - 10)
        }
        val desc = stringResource(R.string.cd_sos_volume_value, vol)
        Box(
            Modifier.widthIn(min = 64.dp).clearAndSetSemantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            AppText(stringResource(R.string.value_percent, vol), size = 20.sp, bold = true, font = st.numberFont, singleLine = true)
        }
        StepButton("+", stringResource(R.string.cd_inc_sos_volume), vol < 100, 48.dp) {
            AppState.updateSosVolume(AppState.sosVolumePct + 10)
        }
    }
}
