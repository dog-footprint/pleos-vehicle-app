package com.dogfootprint.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dogfootprint.app.R
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.data.Mood

/** Shared so the Home "end trip" button and the parked-too-long prompt open the same mood dialog. */
val showMoodDialog = mutableStateOf(false)

@Composable
fun TripDialogs(vm: CareViewModel, state: CareState) {
    val dog = state.dog ?: return

    if (state.askTripEnd && state.trip != null && !showMoodDialog.value) {
        AlertDialog(
            onDismissRequest = vm::dismissTripEndPrompt,
            title = { Text(stringResource(R.string.trip_end_prompt_title, dog.name)) },
            text = { Text(stringResource(R.string.trip_end_prompt_body)) },
            confirmButton = { Button(onClick = { vm.dismissTripEndPrompt(); showMoodDialog.value = true }) { Text(stringResource(R.string.action_end)) } },
            dismissButton = { TextButton(onClick = vm::dismissTripEndPrompt) { Text(stringResource(R.string.action_keep_driving)) } },
        )
    }

    if (showMoodDialog.value && state.trip != null) {
        var mood by remember { mutableStateOf<Mood?>(null) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoodDialog.value = false },
            title = { Text(stringResource(R.string.trip_end_mood_title, dog.name)) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Mood.entries.forEach { m ->
                            FilterChip(selected = mood == m, onClick = { mood = m }, label = { Text(stringResource(m.labelRes)) })
                        }
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.trip_end_note_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.endTrip(mood, note); showMoodDialog.value = false }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showMoodDialog.value = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
