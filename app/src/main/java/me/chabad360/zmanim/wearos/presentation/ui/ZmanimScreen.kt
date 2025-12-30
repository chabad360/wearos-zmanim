package me.chabad360.zmanim.wearos.presentation.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CornerSize
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.TimeTextDefaults.timeFormat
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.protolayout.DimensionBuilders.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay
import java.util.Date
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalWearFoundationApi::class, ExperimentalTime::class)
@Composable
fun ZmanimScreen(
    viewModel: MainViewModel,
    navController: NavController,
    scrollToZmanKey: androidx.compose.runtime.MutableState<String?>? = null,
    activity: me.chabad360.zmanim.wearos.presentation.MainActivity? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 00:00 tomorrow, used to filter the actual zmanim to be just from today.
    val tomorrowEpoch = remember {
        Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
            .date
            .plus(1, DateTimeUnit.DAY)
            .atTime(0, 0)
            .toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
            .epochSeconds
    }

    val visibleZmanim by remember(state.zmanDisplays, state.disabledIds) {
        derivedStateOf { state.zmanDisplays.filter { it.fromEpoch != null && it.key !in state.disabledIds && it.fromEpoch <= tomorrowEpoch } }
    }

    var currentTimeTick by remember { mutableStateOf(Clock.System.now().epochSeconds) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // Update every second
            currentTimeTick = Clock.System.now().epochSeconds
        }
    }

    val beforeNowZmanim by remember(visibleZmanim, currentTimeTick) {
        derivedStateOf {
            visibleZmanim.filter { (it.fromEpoch ?: 0) < currentTimeTick }
        }
    }

    val afterNowZmanim by remember(visibleZmanim, currentTimeTick) {
        derivedStateOf {
            visibleZmanim.filter { (it.fromEpoch ?: 0) >= currentTimeTick }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        viewModel.clearError()
    }

    val listState = rememberScalingLazyListState()
    val time = TimeTextDefaults.rememberTimeSource(timeFormat())

    // Handle scrolling to specific zman from tile click
    if (visibleZmanim.isNotEmpty()) {
        LaunchedEffect(scrollToZmanKey?.value) {
            scrollToZmanKey?.value?.let { key ->
                // Find the index of the zman in the list
                val beforeIndex = beforeNowZmanim.indexOfFirst { it.key == key }
                val afterIndex = afterNowZmanim.indexOfFirst { it.key == key }

                // Calculate the actual index in the ScalingLazyColumn
                // Items: LocationHeader (0) + beforeNowZmanim + current time divider + afterNowZmanim + spacer + edit button
                val targetIndex = when {
                    beforeIndex >= 0 -> 2 + beforeIndex // LocationHeader + beforeNowZmanim items
                    afterIndex >= 0 -> 2 + beforeNowZmanim.size + 1 + afterIndex // LocationHeader + beforeNowZmanim + divider + afterNowZmanim items
                    else -> -1
                }

                Log.d("ZmanimScreen", "Target index: $targetIndex")

                if (targetIndex >= 0) {
                    listState.animateScrollToItem(targetIndex)
                }

                // Clear the scroll target
                scrollToZmanKey.value = null
            }
        }
    }

    // Show background location permission dialog
    if (state.showBackgroundLocationPrompt && activity != null) {
        BackgroundLocationDialog(
            onAllow = {
                activity.requestBackgroundLocationPermission()
            },
            onDeny = {
                viewModel.dismissBackgroundLocationPrompt()
            }
        )
    }

    ScreenScaffold (
        scrollState = listState,
        contentPadding = PaddingValues(top = 32.dp, bottom = 0.dp, start = 16.dp, end = 16.dp),
        edgeButton = {
            EdgeButton(
                onClick = { viewModel.refreshLocation(true) },
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
    ) { contentPadding  ->
        ScalingLazyColumn(
            autoCentering = null,
            state = listState,
            contentPadding = contentPadding
            ) {
            item {
                FilledIconButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(beforeNowZmanim.size + 3) }
                    },
                    modifier = Modifier
                        .touchTargetAwareSize(IconButtonDefaults.ExtraSmallButtonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.clock_arrow_down_24px),
                        contentDescription = stringResource(id = R.string.scroll_to_next_zman),
                        modifier = Modifier
                            .size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.ExtraSmallButtonSize))
                    )
                }
            }
            item { LocationHeader(state.location) }
            items(
                items = beforeNowZmanim,
//                key = { it.key }
            ) { item ->
                ZmanRow(item = item, onClick = { navController.navigate("zman/${item.key}") })
            }
            item {
                Box(
//                    modifier = Modifier
//                        .padding(vertical = 8.dp)
//                        .border(
//                            Dp.Hairline,
//                            MaterialTheme.colorScheme.primary,
//                            shape = MaterialTheme.shapes.small,
//                        )
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = time.currentTime()
                    )
                }
            }
            items(
                items = afterNowZmanim,
//                key = { it.key }
            ) { item ->
                ZmanRow(item = item, onClick = { navController.navigate("zman/${item.key}") })
            }
//            item { Spacer(modifier = Modifier.height(8.dp)) }
//            item {
//                IconButton(
////                    onClick = viewModel::toggleEdit,
//                    onClick = {},
//                    colors = IconButtonDefaults.iconButtonColors(
//                        containerColor = MaterialTheme.colorScheme.primary,
//                        contentColor = MaterialTheme.colorScheme.onPrimary
//                    )
//                ) {
////                    Text(text = stringResource(id = R.string.edit_zmanim))
//                    Icon(
//                        painter = painterResource(R.drawable.edit_24px),
//                        contentDescription = stringResource(id = R.string.edit_zmanim)
//                    )
//                }
//            }
        }
    }
}

@Composable
private fun LocationHeader(location: String?) {
    Text(
        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        text = location ?: stringResource(id = R.string.location_error),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ZmanRow(item: ZmanDisplay, onClick: () -> Unit) {
    val name = item.name
    val timeText = item.timeText

    Card (
        onClick = onClick
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

@Composable
private fun BackgroundLocationDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    androidx.wear.compose.material3.AlertDialog(
        visible = true,
        onDismissRequest = onDeny,
        icon = {
            Icon(
                painter = painterResource(R.drawable.location_on_24px),
                contentDescription = null
            )
        },
        title = {
            Text(
                text = "Background Location",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Allow background location access to automatically update your location throughout the day?",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = onAllow)
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = onDeny)
        }
    )
}
