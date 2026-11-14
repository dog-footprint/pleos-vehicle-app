package com.dogfootprint.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Trip
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosIcon
import com.dogfootprint.app.ui.pleos.PleosListRow
import com.dogfootprint.app.ui.pleos.PleosStatusLine
import com.dogfootprint.app.ui.pleos.PleosType

@Composable
fun FootprintsScreen(state: CareState) {
    val c = Pleos.colors
    val dog = state.dog
    val trips = state.dogTrips.sortedByDescending { it.startedAt }
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val week = trips.filter { it.startedAt >= weekAgo }
    var expanded by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = PleosDims.space40, vertical = PleosDims.space32)) {
        item {
            Text(stringResource(R.string.footprints_title, dog?.name ?: "—"), style = PleosType.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(PleosDims.space16))
            // While driving only this summary is shown — it is the one thing worth a glance.
            Text(stringResource(R.string.footprints_week_fmt, week.size, week.sumOf { it.distanceKm.toDouble() }.toFloat()), style = PleosType.headlineSmall, color = c.textPrimary)
            Text(
                stringResource(R.string.footprints_total_fmt, trips.size, state.totalKm, durationText(trips.sumOf { it.drivingSec })),
                style = PleosType.bodyLarge, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space8, bottom = PleosDims.space32),
            )
            when {
                state.moving -> PleosStatusLine(R.drawable.ic_lock, stringResource(R.string.driving_locked), tint = c.textTertiary)
                trips.isEmpty() -> Text(stringResource(R.string.footprints_empty), style = PleosType.bodyLarge, color = c.textSecondary)
            }
        }
        if (!state.moving) {
            items(trips, key = { it.id }) { trip ->
                TripRow(trip, expanded == trip.id) { expanded = if (expanded == trip.id) null else trip.id }
            }
        }
    }
}

@Composable
private fun TripRow(trip: Trip, expanded: Boolean, onClick: () -> Unit) {
    val c = Pleos.colors
    val counts = trip.events.groupingBy { it.type }.eachCount()
    PleosListRow(
        title = dateText(trip.startedAt) + (trip.destinationName?.let { "  →  $it" } ?: ""),
        subtitle = stringResource(R.string.trip_line_fmt, trip.distanceKm, durationText(trip.drivingSec), trip.comfortScore) + "  ·  " +
            stringResource(R.string.trip_reason_fmt, counts[EventType.BRAKE_BURST] ?: 0, counts[EventType.WINDOW_OPEN] ?: 0, counts[EventType.BELT_UNBUCKLED] ?: 0),
        icon = trip.mood?.iconRes ?: R.drawable.ic_footprint,
        onClick = onClick,
        trailing = { PleosIcon(R.drawable.ic_chevron_right, c.textTertiary, size = PleosDims.iconSmall) },
    )
    if (expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 72.dp, top = PleosDims.space16, bottom = PleosDims.space24)) {
            trip.note?.let { Text(it, style = PleosType.bodyLarge, color = c.textPrimary, modifier = Modifier.padding(bottom = PleosDims.space16)) }
            trip.events.forEach { e ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeText(e.at), style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.width(140.dp))
                    Text(stringResource(e.type.labelRes) + (e.detail?.let { " · $it" } ?: ""), style = PleosType.bodyMedium, color = c.textSecondary)
                }
            }
        }
    }
}
