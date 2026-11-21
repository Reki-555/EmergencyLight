package com.emergencylight.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencylight.app.AppState
import com.emergencylight.app.R
import com.emergencylight.app.ui.theme.LocalAppStyle
import com.emergencylight.app.ui.theme.StyleId
import com.emergencylight.app.ui.theme.paletteFor

/** Settings sheet (F12): slides up from the bottom. Every change is applied and saved at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val st = LocalAppStyle.current
    val p = st.palette
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.surface,
        contentColor = p.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            AppText(stringResource(R.string.settings), size = 20.sp, bold = true, font = st.headerFont)
            Spacer(Modifier.height(8.dp))

            // 1) Keep screen on (F13)
            SwitchRow(stringResource(R.string.keep_screen_on), AppState.keepScreenOn) {
                AppState.updateKeepScreenOn(!AppState.keepScreenOn)
            }
            AppText(stringResource(R.string.keep_screen_on_desc), size = 16.sp, color = p.text2)
            SectionGap()

            // 2) Theme (F14)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(stringResource(R.string.theme), modifier = Modifier.weight(1f), size = 18.sp)
                Segment(stringResource(R.string.theme_dark), AppState.darkTheme) { AppState.updateDarkTheme(true) }
                Segment(stringResource(R.string.theme_light), !AppState.darkTheme) { AppState.updateDarkTheme(false) }
            }
            SectionGap()

            // 3) Style (F15)
            AppText(stringResource(R.string.style), size = 18.sp)
            Spacer(Modifier.height(4.dp))
            StyleId.entries.forEach { id ->
                StyleRow(id, selected = AppState.styleId == id, dark = AppState.darkTheme) {
                    AppState.updateStyle(id)
                }
            }
            SectionGap()

            // 4) Hold STOP for 2 seconds (F18)
            SwitchRow(stringResource(R.string.hold_stop), AppState.holdToStop) {
                AppState.updateHoldToStop(!AppState.holdToStop)
            }
            AppText(stringResource(R.string.hold_stop_desc), size = 16.sp, color = p.text2)
            SectionGap()

            // 5) Notifications are off (Android 13+ only, when not allowed)
            if (Build.VERSION.SDK_INT >= 33 && !AppState.notificationsGranted) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(stringResource(R.string.notifications_off), modifier = Modifier.weight(1f), size = 18.sp)
                    OutlineButton(stringResource(R.string.allow)) {
                        safeStart(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
                SectionGap()
            }

            // 6) Background running
            AppText(stringResource(R.string.background_running), size = 18.sp)
            Spacer(Modifier.height(4.dp))
            AppText(stringResource(R.string.background_running_desc), size = 16.sp, color = p.text2)
            Spacer(Modifier.height(8.dp))
            OutlineButton(stringResource(R.string.open_settings)) {
                safeStart(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                )
            }
            SectionGap()

            // 7) Version
            AppText(stringResource(R.string.version_label), size = 16.sp, color = p.text2)
        }
    }
}

private fun safeStart(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Settings screen not available on this phone: ignore.
    }
}

@Composable
private fun SectionGap() {
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Segment(label: String, selected: Boolean, onSelect: () -> Unit) {
    val p = LocalAppStyle.current.palette
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) p.accent else p.surface)
            .border(2.dp, if (selected) p.accent else p.outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, size = 18.sp, bold = selected, color = if (selected) p.onAccent else p.text, singleLine = true)
    }
}

@Composable
private fun StyleRow(id: StyleId, selected: Boolean, dark: Boolean, onSelect: () -> Unit) {
    val p = LocalAppStyle.current.palette
    val preview = paletteFor(id, dark)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(stringResource(id.label), modifier = Modifier.weight(1f), size = 18.sp)
        // 3 sample dots: background, card, accent
        listOf(preview.bg, preview.surface, preview.accent).forEach { color ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, p.outline, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (selected) AppText("✓", size = 22.sp, bold = true, align = TextAlign.Center)
        }
    }
}
