package com.dogfootprint.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.data.Trip

@Composable
fun FootprintsScreen(state: CareState) {
    val dog = state.dog
    val trips = state.dogTrips.sortedByDescending { it.startedAt }
    var expanded by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.footprints_title, dog?.name ?: "—"), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.footprints_summary_fmt, trips.size, state.totalKm, durationText(trips.sumOf { it.drivingSec })),
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (trips.isEmpty()) Text(stringResource(R.string.footprints_empty), style = MaterialTheme.typography.bodyLarge)
        }
        items(trips, key = { it.id }) { trip ->
            TripCard(trip, expanded == trip.id) { expanded = if (expanded == trip.id) null else trip.id }
        }
    }
}

@Composable
private fun TripCard(trip: Trip, expanded: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.mood?.let { stringResource(it.labelRes).take(2) } ?: "🐾", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(dateText(trip.startedAt), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.footprint_item_fmt, trip.distanceKm, durationText(trip.drivingSec), trip.breaks), style = MaterialTheme.typography.bodyMedium)
                    trip.destinationName?.let { Text(stringResource(R.string.footprint_destination_fmt, it), style = MaterialTheme.typography.bodyMedium) }
                }
                val accent = when { trip.comfortScore >= 80 -> StatusColors.good; trip.comfortScore >= 50 -> StatusColors.warn; else -> StatusColors.bad }
                Text(stringResource(R.string.footprint_score_fmt, trip.comfortScore), style = MaterialTheme.typography.titleMedium, color = accent)
            }
            trip.note?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.footprint_timeline), style = MaterialTheme.typography.titleMedium)
                trip.events.forEach { e ->
                    Text(
                        "${timeText(e.at)}  ${stringResource(e.type.labelRes)}${e.detail?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
