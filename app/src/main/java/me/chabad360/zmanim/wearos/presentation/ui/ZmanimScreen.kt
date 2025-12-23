package me.chabad360.zmanim.wearos.presentation.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.kdroidfilter.kosherkotlin.Zman
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.UiState
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun ZmanimScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Clock.System.now()) }

    LaunchedEffect(state.timeZone) {
        while (isActive) {
            currentTime = Clock.System.now()
            delay(30_000)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    ScalingLazyColumn(
        autoCentering = AutoCenteringParams(itemIndex = 1),
    ) {
        item {
            Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        item {
            CurrentTimeHeader(currentTime, state.timeZone, is24h)
        }
        items(state.zmanim.filter {!state.disabledIds.contains(it.definition.type)}) { item ->
            ZmanRow(item = item, timeZone = state.timeZone, is24h = is24h)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            IconButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::toggleEdit
            ) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = stringResource(id = R.string.edit_zmanim))
            }
        }
        item {
            LocationInfo(state)
        }
        item {
            EdgeButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::refreshLocation
            ) {
                Text(text = stringResource(id = R.string.refresh))
            }
        }
    }
}

@Composable
private fun LocationInfo(state: UiState) {
    val locationText = state.location ?: stringResource(id = R.string.location_error)
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        text = stringResource(id = R.string.zmanim_header) + " for " + locationText,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyExtraSmall
    )
}

@Composable
private fun StaleBanner(state: UiState) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        text = stringResource(id = R.string.stale_location),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ZmanRow(item: Zman.DateBased, timeZone: TimeZone, is24h: Boolean) {
    val name = item.definition.type.friendlyNameEnglish
    val timeText = formatTime(item, timeZone, is24h)
    val authorities = item.definition.supportingAuthorities
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyExtraSmall
                )
                if (!authorities.isNullOrEmpty()) {
                    Text(
                        text = authorities.joinToString(),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyExtraSmall
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = timeText,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun formatTime(item: Zman.DateBased, timeZone: TimeZone, is24h: Boolean): String {
    val lt = item.value?.toLocalDateTime(timeZone)?.time ?: return "--:--"
    return formatLocalTime(lt.hour, lt.minute, is24h)
}

private fun formatNow(now: Instant, timeZone: TimeZone, is24h: Boolean): String {
    val lt = now.toLocalDateTime(timeZone).time
    return formatLocalTime(lt.hour, lt.minute, is24h)
}

private fun formatLocalTime(hour: Int, minute: Int, is24h: Boolean): String {
    return if (is24h) {
        String.format("%02d:%02d", hour, minute)
    } else {
        val hour12 = (hour % 12).let { if (it == 0) 12 else it }
        val ampm = if (hour < 12) "AM" else "PM"
        String.format("%d:%02d %s", hour12, minute, ampm)
    }
}

@Composable
private fun CurrentTimeHeader(now: Instant, timeZone: TimeZone, is24h: Boolean) {
    ListHeader {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Current time: " + formatNow(now, timeZone, is24h),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
