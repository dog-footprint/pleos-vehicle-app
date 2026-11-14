package com.dogfootprint.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dogfootprint.app.R
import com.dogfootprint.app.care.AlertLevel
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.ui.pleos.ButtonSize
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosButton
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosPopup
import com.dogfootprint.app.ui.pleos.PleosStatusLine
import com.dogfootprint.app.ui.pleos.PleosToggleButton
import com.dogfootprint.app.ui.pleos.PleosType

/** Opened by "태우기" while parked: records the last meal time (motion-sickness rule). */
val showMealDialog = mutableStateOf(false)

/** Opened by "메모 추가" on the check-in card; parked only (keyboard). */
val showNoteDialog = mutableStateOf(false)

/** "외 n건" while parked: every active warning, highest first. */
val showAlertSheet = mutableStateOf(false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripDialogs(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val dog = state.dog ?: return

    if (showMealDialog.value) {
        val hour = 60 * 60_000L
        val options = listOf(
            R.string.meal_just_now to 0L, R.string.meal_1h to 1 * hour, R.string.meal_2h to 2 * hour,
            R.string.meal_3h to 4 * hour, R.string.meal_unknown to -1L,
        )
        PleosPopup(
            title = stringResource(R.string.meal_title, dog.name),
            onDismiss = { showMealDialog.value = false; vm.board(null) },
            body = {
                Column {
                    Text(stringResource(R.string.meal_body), style = PleosType.bodyLarge, color = c.textSecondary)
                    FlowRow(Modifier.padding(top = PleosDims.space24), horizontalArrangement = Arrangement.spacedBy(PleosDims.space16), verticalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
                        options.forEach { (label, ago) ->
                            PleosToggleButton(stringResource(label), on = false, size = ButtonSize.Large, onToggle = {
                                showMealDialog.value = false
                                vm.board(if (ago < 0) null else System.currentTimeMillis() - ago)
                            })
                        }
                    }
                }
            },
            actions = {},
        )
    }

    // Parked for 10 minutes with a trip running: "did the dog get out?" — ending leads to the check-in card on Home.
    if (state.askTripEnd && state.trip != null) {
        PleosPopup(
            title = stringResource(R.string.trip_end_prompt_title, dog.name),
            onDismiss = vm::dismissTripEndPrompt,
            body = { Text(stringResource(R.string.trip_end_prompt_body), style = PleosType.bodyLarge, color = c.textSecondary) },
            actions = {
                PleosButton(stringResource(R.string.action_keep_driving), onClick = vm::dismissTripEndPrompt, primary = false)
                PleosButton(stringResource(R.string.action_end), onClick = { vm.dismissTripEndPrompt(); vm.endTrip(null, null) })
            },
        )
    }

    if (showAlertSheet.value && !state.moving) {
        PleosPopup(
            title = stringResource(R.string.alert_list_title),
            onDismiss = { showAlertSheet.value = false },
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
                    state.alerts.forEach { a ->
                        PleosStatusLine(a.kind.iconRes, alertText(a, state), tint = if (a.level == AlertLevel.DANGER) c.negative else c.textPrimary, style = PleosType.bodyLargeStrong)
                    }
                }
            },
            actions = { PleosButton(stringResource(R.string.action_close), onClick = { showAlertSheet.value = false }, primary = false) },
        )
    }

    if (showNoteDialog.value && state.pendingCheckIn != null && !state.moving) {
        var note by remember { mutableStateOf(state.pendingCheckIn.note ?: "") }
        PleosPopup(
            title = stringResource(R.string.trip_add_note),
            onDismiss = { showNoteDialog.value = false },
            body = {
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, singleLine = true, textStyle = PleosType.bodyLarge,
                    placeholder = { Text(stringResource(R.string.trip_end_note_hint), style = PleosType.bodyLarge) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            actions = {
                PleosButton(stringResource(R.string.action_cancel), onClick = { showNoteDialog.value = false }, primary = false)
                PleosButton(stringResource(R.string.action_save), onClick = { vm.addNote(note); showNoteDialog.value = false })
            },
        )
    }
}
