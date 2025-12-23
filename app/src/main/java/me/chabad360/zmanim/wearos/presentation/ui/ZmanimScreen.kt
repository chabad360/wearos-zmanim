package me.chabad360.zmanim.wearos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.UiState
import me.chabad360.zmanim.wearos.presentation.ZmanItem

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun ZmanimScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        timeText = { TimeText() },
    ) {
        if (state.showingEdit) {
            EditZmanimScreen(state = state, onBack = { viewModel.toggleEdit() }, onToggle = viewModel::toggleZman)
        } else {
            MainScreenContent(
                state = state,
                onRefresh = viewModel::refreshLocation,
                onToggleEdit = viewModel::toggleEdit,
            )
        }
    }
}

@Composable
private fun MainScreenContent(
    state: UiState,
    onRefresh: () -> Unit,
    onToggleEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 8.dp)
    ) {
        LocationHeader(state = state, onRefresh = onRefresh)
        StaleBanner(state = state)
        ZmanimList(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Chip(
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleEdit,
            label = { Text(text = stringResource(id = R.string.edit_zmanim)) },
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

@Composable
private fun LocationHeader(state: UiState, onRefresh: () -> Unit) {
    val locationText = when {
        state.isRefreshing -> stringResource(id = R.string.location_updating)
        state.error != null -> stringResource(id = R.string.location_error)
        state.location != null -> stringResource(id = R.string.last_known_location, state.location)
        else -> stringResource(id = R.string.location_error)
    }

    Chip(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onRefresh,
        label = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = locationText,
                textAlign = TextAlign.Center,
            )
        },
        enabled = !state.isRefreshing
    )
}

@Composable
private fun StaleBanner(state: UiState) {
    if (!state.isStale) return
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        text = stringResource(id = R.string.stale_location),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onSurface
    )
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
private fun ZmanimList(state: UiState, modifier: Modifier = Modifier) {
    ScalingLazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        anchorType = ScalingLazyListAnchorType.ItemStart
    ) {
        items(state.zmanim) { item ->
            ZmanRow(item = item)
        }
    }
}

@Composable
private fun ZmanRow(item: ZmanItem) {
    val timeText = item.time?.toString() ?: stringResource(id = R.string.zman_time_unknown)
    ToggleChip(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        checked = item.enabled,
        onCheckedChange = { },
        enabled = false,
        label = { Text(text = item.name) },
        secondaryLabel = { Text(text = timeText) },
        toggleControl = {
            Text(text = if (item.enabled) stringResource(id = R.string.enabled) else stringResource(id = R.string.disabled))
        }
    )
}

@Composable
private fun EditZmanimScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.edit_zmanim),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.title3
        )
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            state.zmanim.forEach { item ->
                ToggleChip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    checked = item.enabled,
                    onCheckedChange = { onToggle(item.id) },
                    label = { Text(text = item.name) },
                    secondaryLabel = { Text(text = item.time?.toString() ?: stringResource(id = R.string.zman_time_unknown)) },
                    toggleControl = {
                        Text(text = if (item.enabled) stringResource(id = R.string.enabled) else stringResource(id = R.string.disabled))
                    }
                )
            }
        }
        Chip(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
            label = { Text(text = stringResource(id = R.string.save)) }
        )
    }
}
