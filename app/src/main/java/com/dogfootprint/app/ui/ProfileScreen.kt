package com.dogfootprint.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dogfootprint.app.R
import com.dogfootprint.app.care.BreedPresets
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.care.breakIntervalMin
import com.dogfootprint.app.care.comfortBand
import com.dogfootprint.app.data.AgeGroup
import com.dogfootprint.app.data.CoatType
import com.dogfootprint.app.data.DogProfile
import com.dogfootprint.app.data.DogSeat
import com.dogfootprint.app.data.DogSize
import com.dogfootprint.app.data.Tendency
import java.util.UUID

@Composable
fun ProfileScreen(vm: CareViewModel, state: CareState) {
    var editing by remember { mutableStateOf<DogProfile?>(null) }
    val dogs = state.data.dogs
    if (editing == null && dogs.isEmpty()) editing = newDog()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { editing = newDog() }) { Text("＋ " + stringResource(R.string.profile_new)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dogs.forEach { dog ->
                    val selected = dog.id == state.dog?.id
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dog.name, style = MaterialTheme.typography.titleLarge)
                                Text("${breedName(dog.breedKey)} · ${stringResource(dog.size.labelRes)} · ${stringResource(dog.coat.labelRes)} · ${stringResource(dog.seat.labelRes)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (selected) Text(stringResource(R.string.active_dog), style = MaterialTheme.typography.bodyMedium) else TextButton(onClick = { vm.selectDog(dog.id) }) { Text(stringResource(R.string.select_dog)) }
                            TextButton(onClick = { editing = dog }) { Text("✎") }
                        }
                    }
                }
            }
            Column(Modifier.weight(1.4f)) {
                editing?.let { dog ->
                    DogEditor(dog, onChange = { editing = it }, onSave = { vm.saveDog(it); editing = null }, onDelete = {
                        if (dogs.any { d -> d.id == it.id }) vm.deleteDog(it.id); editing = null
                    })
                }
            }
        }
    }
}

private fun newDog() = DogProfile(id = UUID.randomUUID().toString(), name = "", breedKey = BreedPresets.all.first().key, size = DogSize.TOY, coat = CoatType.LONG_SINGLE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DogEditor(dog: DogProfile, onChange: (DogProfile) -> Unit, onSave: (DogProfile) -> Unit, onDelete: (DogProfile) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = dog.name, onValueChange = { onChange(dog.copy(name = it)) }, singleLine = true,
                label = { Text(stringResource(R.string.profile_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            Section(stringResource(R.string.profile_breed)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BreedPresets.all.forEach { b ->
                        FilterChip(
                            selected = dog.breedKey == b.key,
                            onClick = { onChange(dog.copy(breedKey = b.key, size = b.size, coat = b.coat, brachycephalic = b.brachycephalic)) },
                            label = { Text(if (isKorean()) b.nameKo else b.nameEn) },
                        )
                    }
                }
                if (dog.breedKey == BreedPresets.OTHER_KEY) Text(stringResource(R.string.breed_other_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Section(stringResource(R.string.profile_traits)) {
                ChipRow(stringResource(R.string.profile_size), DogSize.entries, dog.size, { stringResource(it.labelRes) }) { onChange(dog.copy(size = it)) }
                ChipRow(stringResource(R.string.profile_coat), CoatType.entries, dog.coat, { stringResource(it.labelRes) }) { onChange(dog.copy(coat = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_brachy), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = dog.brachycephalic, onCheckedChange = { onChange(dog.copy(brachycephalic = it)) })
                }
                ChipRow(stringResource(R.string.profile_age), AgeGroup.entries, dog.ageGroup, { stringResource(it.labelRes) }) { onChange(dog.copy(ageGroup = it)) }
                ChipRow(stringResource(R.string.profile_motion), Tendency.entries, dog.motionSickness, { stringResource(it.labelRes) }) { onChange(dog.copy(motionSickness = it)) }
                OutlinedTextField(
                    value = dog.weightKg?.toString() ?: "", onValueChange = { onChange(dog.copy(weightKg = it.toFloatOrNull())) }, singleLine = true,
                    label = { Text(stringResource(R.string.profile_weight)) }, modifier = Modifier.width(240.dp),
                )
            }
            Section(stringResource(R.string.profile_seat)) { SeatPicker(dog.seat) { onChange(dog.copy(seat = it)) } }
            val band = comfortBand(dog)
            Text(stringResource(R.string.profile_band_preview, band.lower, band.upper, breakIntervalMin(dog, false)), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSave(dog) }, enabled = dog.name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
                TextButton(onClick = { onDelete(dog) }) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun <T> ChipRow(label: String, options: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(96.dp))
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o)) }) }
    }
}

/** Top-view car: front row (driver fixed), rear row, cargo. */
@Composable
private fun SeatPicker(selected: DogSeat, onSelect: (DogSeat) -> Unit) {
    @Composable
    fun Seat(seat: DogSeat?, labelRes: Int, modifier: Modifier = Modifier) {
        FilterChip(
            selected = seat != null && seat == selected, enabled = seat != null,
            onClick = { seat?.let(onSelect) }, label = { Text(stringResource(labelRes)) }, modifier = modifier,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seat(null, R.string.seat_driver)
            Seat(DogSeat.ROW_1_RIGHT, DogSeat.ROW_1_RIGHT.labelRes)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seat(DogSeat.ROW_2_LEFT, DogSeat.ROW_2_LEFT.labelRes)
            Seat(DogSeat.ROW_2_CENTER, DogSeat.ROW_2_CENTER.labelRes)
            Seat(DogSeat.ROW_2_RIGHT, DogSeat.ROW_2_RIGHT.labelRes)
        }
        Seat(DogSeat.CARGO, DogSeat.CARGO.labelRes)
    }
}
