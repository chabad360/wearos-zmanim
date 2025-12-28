package me.chabad360.zmanim.wearos.presentation

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.chabad360.zmanim.wearos.presentation.navigation.Navigation
import me.chabad360.zmanim.wearos.presentation.theme.ZmanimTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    private val scrollToZmanKey = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            // Launch background location refresh
            lifecycleScope.launch {
                // Small delay to ensure UI is ready
                delay(500)
                viewModel.refreshLocation()

                // Check if we should prompt for background location
                if (viewModel.shouldRequestBackgroundLocation(applicationContext)) {
                    delay(1000) // Wait a bit before showing prompt
                    viewModel.showBackgroundLocationPrompt()
                }
            }
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Background location permission granted
            lifecycleScope.launch {
                viewModel.refreshLocation()
            }
        }
        viewModel.dismissBackgroundLocationPrompt()
    }

    fun requestBackgroundLocationPermission() {
        backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        ensureLocationPermission()

        // Handle intent from tile
        handleIntent(intent)

        setContent {
            WearApp(viewModel, scrollToZmanKey, this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("zmanKey")?.let { key ->
            scrollToZmanKey.value = key
        }
    }

    private fun ensureLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PermissionChecker.PERMISSION_GRANTED || coarse == PermissionChecker.PERMISSION_GRANTED) {
            // Launch background location refresh, don't block startup
            lifecycleScope.launch {
                // Small delay to ensure UI is ready
                delay(500)
                viewModel.refreshLocation()

                // Check if we should prompt for background location
                if (viewModel.shouldRequestBackgroundLocation(applicationContext)) {
                    delay(1000) // Wait a bit before showing prompt
                    viewModel.showBackgroundLocationPrompt()
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

@Composable
fun WearApp(viewModel: MainViewModel, scrollToZmanKey: androidx.compose.runtime.MutableState<String?>, activity: MainActivity) {
    val navController = rememberSwipeDismissableNavController()
    ZmanimTheme {
        AppScaffold {
            Navigation(navController = navController, viewModel = viewModel, scrollToZmanKey = scrollToZmanKey, activity = activity)
        }
    }
}
