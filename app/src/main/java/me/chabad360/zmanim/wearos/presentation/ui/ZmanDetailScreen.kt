package me.chabad360.zmanim.wearos.presentation.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.github.kdroidfilter.kosherkotlin.ZmanDescriptionFormatter
import me.chabad360.zmanim.wearos.R
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay

val formatter = ZmanDescriptionFormatter()

@Composable
fun ZmanDetailScreen(
    viewModel: MainViewModel,
    zman: ZmanDisplay,
    navController: NavHostController,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberScalingLazyListState()

    val isDisabled by remember(state.disabledIds) {
        derivedStateOf { state.disabledIds.contains(zman.key) }
    }

    val actualZman by remember(zman.key) {
        derivedStateOf { state.zmanim[zman.key]!!.invoke() }
    }

    val zmanDescription by remember(zman.key) {
        derivedStateOf { formatter.formatLongDescription(actualZman.definition).split("Supporting authorities")[0] }
    }
    val supportingAuthorities by remember(zman.key) {
        derivedStateOf {
            "Supporting Authorities: \n⋅ ${
                actualZman.definition.supportingAuthorities.joinToString(
                    separator = "\n⋅ "
                ) { it.name }
            }"
        }
    }

    ScreenScaffold (
        scrollState = listState,
        contentPadding = PaddingValues(top = 40.dp, bottom = 0.dp, start = 16.dp, end = 16.dp),
        edgeButton = {
            EdgeButton(
                onClick = { navController.popBackStack() },
                modifier =  Modifier.scrollable(
                    listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    overscrollEffect = rememberOverscrollEffect(),
                ),
            ) {
                Text(text = stringResource(id = R.string.back))
            }
        }
    ) { contentPadding ->
        ScalingLazyColumn(
            autoCentering = null,
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = zman.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = zman.timeText,
                    style = MaterialTheme.typography.displaySmall
                )
            }
            item {
                Card(
                    onClick = {}
                ) {
                    Text(
                        text = zmanDescription,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (actualZman.definition.supportingAuthorities.isNotEmpty()) {
                item {
                    Text(
                        text = supportingAuthorities,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Left,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    )
                }
            }
            item {
                SwitchButton(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    checked = !isDisabled,
                    onCheckedChange = {
                        viewModel.toggleZmanVisiblity(zman)
                    }
                ) {
                    Text(text = "Visible")
                }
            }
        }
    }
}
