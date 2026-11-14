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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.dogfootprint.app.ui.pleos.ButtonSize
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosButton
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosListRow
import com.dogfootprint.app.ui.pleos.PleosStatusLine
import com.dogfootprint.app.ui.pleos.PleosSwitch
import com.dogfootprint.app.ui.pleos.PleosToggleButton
import com.dogfootprint.app.ui.pleos.PleosType
import java.util.UUID

@Composable
fun ProfileScreen(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    var editing by remember { mutableStateOf<DogProfile?>(null) }
    val dogs = state.data.dogs
    if (editing == null && dogs.isEmpty() && !state.moving) editing = newDog()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PleosDims.space40, vertical = PleosDims.space32)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_title), style = PleosType.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (!state.moving) PleosButton(stringResource(R.string.profile_new), onClick = { editing = newDog() }, size = ButtonSize.Small, primary = false, icon = R.drawable.ic_add)
        }
        Spacer(Modifier.height(PleosDims.space24))
        if (state.moving) {
            PleosStatusLine(R.drawable.ic_lock, stringResource(R.string.driving_edit_locked), tint = c.textTertiary, modifier = Modifier.padding(bottom = PleosDims.space16))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosDims.space40)) {
            Column(Modifier.weight(1f)) {
                dogs.forEach { dog ->
                    val selected = dog.id == state.dog?.id
                    PleosListRow(
                        title = dog.name,
                        subtitle = listOf(breedName(dog.breedKey), stringResource(dog.size.labelRes), stringResource(dog.coat.labelRes), stringResource(dog.seat.labelRes)).joinToString(" · "),
                        icon = R.drawable.ic_pets, iconTint = if (selected) c.accent else c.textPrimary,
                        onClick = if (state.moving) null else { { editing = dog } },
                    ) {
                        if (selected) Text(stringResource(R.string.active_dog), style = PleosType.bodyMedium, color = c.accent)
                        else if (!state.moving) PleosButton(stringResource(R.string.select_dog), onClick = { vm.selectDog(dog.id) }, size = ButtonSize.Small, primary = false)
                    }
                }
            }
            if (!state.moving) {
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
}

private fun newDog() = DogProfile(id = UUID.randomUUID().toString(), name = "", breedKey = BreedPresets.all.first().key, size = DogSize.TOY, coat = CoatType.LONG_SINGLE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DogEditor(dog: DogProfile, onChange: (DogProfile) -> Unit, onSave: (DogProfile) -> Unit, onDelete: (DogProfile) -> Unit) {
    val c = Pleos.colors
    Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space24)) {
        OutlinedTextField(
            value = dog.name, onValueChange = { onChange(dog.copy(name = it)) }, singleLine = true, textStyle = PleosType.bodyLarge,
            label = { Text(stringResource(R.string.profile_name), style = PleosType.labelMedium) }, modifier = Modifier.fillMaxWidth(),
        )
        Section(stringResource(R.string.profile_breed)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12), verticalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
                BreedPresets.all.forEach { b ->
                    PleosToggleButton(
                        if (isKorean()) b.nameKo else b.nameEn, on = dog.breedKey == b.key, size = ButtonSize.Small,
                        onToggle = { onChange(dog.copy(breedKey = b.key, size = b.size, coat = b.coat, brachycephalic = b.brachycephalic)) },
                    )
                }
            }
            if (dog.breedKey == BreedPresets.OTHER_KEY) Text(stringResource(R.string.breed_other_hint), style = PleosType.bodyMedium, color = c.textTertiary)
        }
        Section(stringResource(R.string.profile_traits)) {
            OptionRow(stringResource(R.string.profile_size), DogSize.entries, dog.size, { stringResource(it.labelRes) }) { onChange(dog.copy(size = it)) }
            OptionRow(stringResource(R.string.profile_coat), CoatType.entries, dog.coat, { stringResource(it.labelRes) }) { onChange(dog.copy(coat = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_brachy), style = PleosType.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                PleosSwitch(dog.brachycephalic, { onChange(dog.copy(brachycephalic = it)) })
            }
            OptionRow(stringResource(R.string.profile_age), AgeGroup.entries, dog.ageGroup, { stringResource(it.labelRes) }) { onChange(dog.copy(ageGroup = it)) }
            OptionRow(stringResource(R.string.profile_motion), Tendency.entries, dog.motionSickness, { stringResource(it.labelRes) }) { onChange(dog.copy(motionSickness = it)) }
            OutlinedTextField(
                value = dog.weightKg?.toString() ?: "", onValueChange = { onChange(dog.copy(weightKg = it.toFloatOrNull())) }, singleLine = true,
                textStyle = PleosType.bodyLarge, label = { Text(stringResource(R.string.profile_weight), style = PleosType.labelMedium) }, modifier = Modifier.width(320.dp),
            )
        }
        Section(stringResource(R.string.profile_seat)) { SeatPicker(dog.seat) { onChange(dog.copy(seat = it)) } }
        val band = comfortBand(dog)
        PleosStatusLine(R.drawable.ic_thermostat, stringResource(R.string.profile_band_preview, band.lower, band.upper, breakIntervalMin(dog, false)), tint = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
            PleosButton(stringResource(R.string.action_save), onClick = { onSave(dog) }, enabled = dog.name.isNotBlank(), size = ButtonSize.Large)
            PleosButton(stringResource(R.string.action_delete), onClick = { onDelete(dog) }, primary = false, size = ButtonSize.Large, icon = R.drawable.ic_delete)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
        Text(title, style = PleosType.bodyMediumStrong, color = Pleos.colors.textTertiary)
        content()
    }
}

@Composable
private fun <T> OptionRow(label: String, options: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
        Text(label, style = PleosType.bodyLarge, color = Pleos.colors.textPrimary, modifier = Modifier.width(120.dp))
        options.forEach { o -> PleosToggleButton(text(o), on = o == selected, onToggle = { onSelect(o) }, size = ButtonSize.Small) }
    }
}

/** Top-view car: front row (driver fixed), rear row, cargo. */
@Composable
private fun SeatPicker(selected: DogSeat, onSelect: (DogSeat) -> Unit) {
    @Composable
    fun Seat(seat: DogSeat?, labelRes: Int) {
        PleosToggleButton(stringResource(labelRes), on = seat != null && seat == selected, enabled = seat != null, size = ButtonSize.Small, onToggle = { seat?.let(onSelect) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space12), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
            Seat(null, R.string.seat_driver)
            Seat(DogSeat.ROW_1_RIGHT, DogSeat.ROW_1_RIGHT.labelRes)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
            Seat(DogSeat.ROW_2_LEFT, DogSeat.ROW_2_LEFT.labelRes)
            Seat(DogSeat.ROW_2_CENTER, DogSeat.ROW_2_CENTER.labelRes)
            Seat(DogSeat.ROW_2_RIGHT, DogSeat.ROW_2_RIGHT.labelRes)
        }
        Seat(DogSeat.CARGO, DogSeat.CARGO.labelRes)
    }
}
