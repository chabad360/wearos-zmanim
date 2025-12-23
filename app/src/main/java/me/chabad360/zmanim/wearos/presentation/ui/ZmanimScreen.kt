package me.chabad360.zmanim.wearos.presentation.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import io.github.kdroidfilter.kosherkotlin.Zman
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.UiState
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalWearFoundationApi::class, ExperimentalTime::class)
@Composable
fun ZmanimScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val context = LocalContext.current


    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val listState = rememberScalingLazyListState()

    ScreenScaffold (
        scrollState = listState,
    ) {
        ScalingLazyColumn(
            autoCentering = null,
            state = listState,
            contentPadding = PaddingValues(top = 40.dp, bottom = 0.dp, start = 16.dp, end = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(state.zmanim.filter { it.value != null && !state.disabledIds.contains(it.definition.type) }) { item ->
                ZmanRow(item = item, timeZone = state.timeZone, is24h = is24h)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                IconButton(
                    onClick = viewModel::toggleEdit,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
//                    Text(text = stringResource(id = R.string.edit_zmanim))
                    Icon(
                        painter = painterResource(R.drawable.edit_24px),
                        contentDescription = stringResource(id = R.string.edit_zmanim)
                    )
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
}

@Composable
private fun LocationInfo(state: UiState) {
    val locationText = state.location ?: stringResource(id = R.string.location_error)
    Text(
        modifier = Modifier
            .padding(vertical = 16.dp),
        text = stringResource(id = R.string.zmanim_header) + " for " + locationText,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyExtraSmall
    )
}

@Composable
private fun ZmanRow(item: Zman.DateBased, timeZone: TimeZone, is24h: Boolean) {
    val name = item.definition.type.friendlyNameEnglish
    val timeText = formatTime(item, timeZone, is24h)
    val authorities = item.definition.supportingAuthorities.map { it.name }.joinToString(", ")

    Card (
        onClick = {}
    ) {
        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = name,
//                modifier = Modifier.align(Alignment.CenterStart),
//                textAlign = TextAlign.Start,
//                color = MaterialTheme.colorScheme.
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = timeText,
//                modifier = Modifier.align(Alignment.CenterEnd),
//                textAlign = TextAlign.End,
//                color = MaterialTheme.colorScheme.onSurface
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = authorities,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun formatTime(item: Zman.DateBased, timeZone: TimeZone, is24h: Boolean): String {
    val lt = item.value?.toLocalDateTime(timeZone)?.time
    return if (lt == null) {
        "--:--"
    } else if (is24h) {
        String.format("%02d:%02d", lt.hour, lt.minute)
    } else {
        val hour12 = (lt.hour % 12).let { if (it == 0) 12 else it }
        val ampm = if (lt.hour < 12) "AM" else "PM"
        String.format("%d:%02d %s", hour12, lt.minute, ampm)
    }
}

@OptIn(ExperimentalTime::class)
private fun formatClock(now: Instant, timeZone: TimeZone, is24h: Boolean): String {
    val lt = now.toLocalDateTime(timeZone).time
    return if (is24h) {
        String.format("%02d:%02d", lt.hour, lt.minute)
    } else {
        val hour12 = (lt.hour % 12).let { if (it == 0) 12 else it }
        val ampm = if (lt.hour < 12) "AM" else "PM"
        String.format("%d:%02d %s", hour12, lt.minute, ampm)
    }
}
