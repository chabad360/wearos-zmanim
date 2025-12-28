package me.chabad360.zmanim.wearos.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import me.chabad360.zmanim.wearos.presentation.MainViewModel
import me.chabad360.zmanim.wearos.presentation.ui.ZmanimScreen
import me.chabad360.zmanim.wearos.presentation.ui.ZmanDetailScreen
import androidx.compose.runtime.collectAsState


@Composable
fun Navigation(
    navController: NavHostController,
    viewModel: MainViewModel,
    scrollToZmanKey: androidx.compose.runtime.MutableState<String?>,
    activity: me.chabad360.zmanim.wearos.presentation.MainActivity
) {
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "zmanim"
    ) {
        composable("zmanim") {
            ZmanimScreen(viewModel = viewModel, navController = navController, scrollToZmanKey = scrollToZmanKey, activity = activity)
        }
        composable("zman/{zmanKey}") { backStackEntry ->
            val zmanKey = backStackEntry.arguments?.getString("zmanKey")
            val zman = viewModel.uiState.collectAsState().value.zmanDisplays.find { it.key == zmanKey }
            if (zman != null) {
                ZmanDetailScreen(viewModel = viewModel, zman = zman, navController = navController)
            }
        }
    }
}
