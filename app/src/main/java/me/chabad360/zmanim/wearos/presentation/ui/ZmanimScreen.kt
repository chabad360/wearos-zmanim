package me.chabad360.zmanim.wearos.presentation.ui

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.angularSize
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.TimeTextDefaults.timeFormat
import io.github.kdroidfilter.kosherkotlin.ZmanDescriptionFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.UiState
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalWearFoundationApi::class, ExperimentalTime::class)
@Composable
fun ZmanimScreen(viewModel: MainViewModel, navController: NavController) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val visibleZmanim by remember(state.zmanim, state.disabledIds) {
        derivedStateOf { state.zmanim.filter { it.zman.value != null && it.key !in state.disabledIds } }
    }

    val beforeNowZmanim by remember(visibleZmanim) {
        derivedStateOf {
            visibleZmanim.filter { (it.zman.value?.compareTo(Clock.System.now()) ?: -1) < 0 }
        }
    }

    val afterNowZmanim by remember(visibleZmanim) {
        derivedStateOf {
            visibleZmanim.filter { (it.zman.value?.compareTo(Clock.System.now()) ?: -1) > 0 }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        viewModel.clearError()
    }

    val listState = rememberScalingLazyListState()
    val time = TimeTextDefaults.rememberTimeSource(timeFormat())

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
    ) { contentPadding  ->
        ScalingLazyColumn(
            autoCentering = null,
            state = listState,
            contentPadding = contentPadding
            ) {
            item { LocationHeader(state.location) }
            items(
                items = beforeNowZmanim,
                key = { it.key }
            ) { item ->
                ZmanRow(item = item, onClick = { navController.navigate("zman/${item.key}") })
            }
            item {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .border(
                            Dp.Hairline,
                            MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                        )
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = time.currentTime()
                    )
                }
            }
            items(
                items = afterNowZmanim,
                key = { it.key }
            ) { item ->
                ZmanRow(item = item, onClick = { navController.navigate("zman/${item.key}") })
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
        }
    }
}

@Composable
private fun LocationHeader(location: String?) {
    Text(
        modifier = Modifier.padding(vertical = 16.dp),
        text = location ?: stringResource(id = R.string.location_error),
        style = MaterialTheme.typography.titleLarge,
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

