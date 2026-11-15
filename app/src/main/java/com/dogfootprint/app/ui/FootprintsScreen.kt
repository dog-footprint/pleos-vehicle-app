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

/** Same numbers as ComfortRules.comfortScore, so the detail can say why points were lost. */
private val penalties = listOf(
    EventType.BRAKE_BURST to 10, EventType.SPORT_MODE to 15, EventType.WINDOW_OPEN to 10, EventType.BELT_UNBUCKLED to 10, EventType.TEMP_OUT_OF_BAND to 5,
)

@Composable
fun FootprintsScreen(state: CareState, initialTripId: String? = null) {
    val c = Pleos.colors
    val dog = state.dog
    val trips = state.dogTrips.sortedByDescending { it.startedAt }
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val week = trips.filter { it.startedAt >= weekAgo }
    var expanded by remember(initialTripId) { mutableStateOf(initialTripId) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = PleosDims.space40, vertical = PleosDims.space24)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dog?.let { DogAvatar(it.breedKey, size = PleosDims.iconButtonSmall, icon = PleosDims.iconSmall); Spacer(Modifier.width(PleosDims.space16)) }
                Text(stringResource(R.string.footprints_title, dog?.name ?: "—"), style = PleosType.titleLarge, color = c.textPrimary)
            }
            Spacer(Modifier.height(PleosDims.space16))
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
        subtitle = stringResource(R.string.fp_summary_fmt, trip.distanceKm, durationText(trip.drivingSec), trip.breaks) + "  ·  " + stringResource(R.string.fp_score_fmt, trip.comfortScore) +
            (trip.mood?.let { "  ·  " + stringResource(R.string.fp_mood_fmt, stringResource(it.labelRes)) } ?: ""),
        icon = trip.mood?.iconRes ?: R.drawable.ic_footprint,
        onClick = onClick,
        trailing = { PleosIcon(R.drawable.ic_chevron_right, c.textTertiary, size = PleosDims.iconSmall) },
    )
    if (expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 72.dp, top = PleosDims.space16, bottom = PleosDims.space24)) {
            // Summary first: what happened, and why the score is what it is.
            Text(stringResource(R.string.fp_summary_fmt, trip.distanceKm, durationText(trip.drivingSec), trip.breaks), style = PleosType.bodyLargeStrong, color = c.textPrimary)
            val reasons = penalties.mapNotNull { (t, p) -> counts[t]?.takeIf { it > 0 }?.let { n -> stringResource(R.string.fp_penalty_fmt, stringResource(t.labelRes), n, n * p) } }
            Text(
                stringResource(R.string.fp_score_fmt, trip.comfortScore) + " — " + (reasons.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: stringResource(R.string.fp_score_perfect)),
                style = PleosType.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space4),
            )
            trip.note?.let { Text(it, style = PleosType.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space8)) }
            Spacer(Modifier.height(PleosDims.space16))
            trip.events.forEach { e ->
                val tint = if (e.type.isDanger) c.negative else if (e.type == EventType.BELT_UNBUCKLED) c.accent else c.textSecondary
                Row(Modifier.padding(vertical = PleosDims.space8), verticalAlignment = Alignment.CenterVertically) {
                    PleosIcon(e.type.iconRes, tint, size = PleosDims.iconSmall)
                    Spacer(Modifier.width(PleosDims.space16))
                    Text(timeText(e.at), style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.width(150.dp))
                    Text(stringResource(e.type.labelRes) + (e.detail?.let { " · $it" } ?: ""), style = PleosType.bodyLarge, color = if (e.type.isDanger) c.negative else c.textPrimary)
                }
            }
        }
    }
}
