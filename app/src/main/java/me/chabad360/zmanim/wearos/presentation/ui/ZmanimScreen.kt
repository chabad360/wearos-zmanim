package me.chabad360.zmanim.wearos.presentation.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import io.github.kdroidfilter.kosherkotlin.Zman
import io.github.kdroidfilter.kosherkotlin.ZmanDescriptionFormatter
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.UiState
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay
import java.util.Locale
import java.util.Locale.getDefault
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalWearFoundationApi::class, ExperimentalTime::class)
@Composable
fun ZmanimScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val visibleZmanim by remember(state.zmanim, state.disabledIds) {
        derivedStateOf { state.zmanim.filter { it.zman.value != null && it.key !in state.disabledIds } }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val listState = rememberScalingLazyListState()

    ScreenScaffold (
        scrollState = listState,
        contentPadding = PaddingValues(top = 40.dp, bottom = 0.dp, start = 16.dp, end = 16.dp),
        edgeButton = {
            EdgeButton(
                onClick = viewModel::refreshLocation,
                modifier =  Modifier.scrollable(
                    listState,
                orientation = Orientation.Vertical,
                reverseDirection = true,
                overscrollEffect = rememberOverscrollEffect(),
            ),
            ) {
                Text(text = stringResource(id = R.string.refresh))
            }
        }
    ) { contentPadding ->
        ScalingLazyColumn(
            autoCentering = null,
            state = listState,
            contentPadding = contentPadding
            ) {
            item {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(
                items = visibleZmanim,
                key = { it.key }
            ) { item ->
                ZmanRow(item = item)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                IconButton(
//                    onClick = viewModel::toggleEdit,
                    onClick = {},
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

val formatter = ZmanDescriptionFormatter()

@Composable
private fun ZmanRow(item: ZmanDisplay) {
    val name = item.name
    val timeText = item.timeText

    Card (
        onClick = {}
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
