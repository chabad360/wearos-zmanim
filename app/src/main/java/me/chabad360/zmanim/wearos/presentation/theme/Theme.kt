package me.chabad360.zmanim.wearos.presentation.theme

import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun ZmanimTheme(
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = dynamicColorScheme(LocalContext.current)
    MaterialTheme(
        colorScheme = dynamicColorScheme ?: MaterialTheme.colorScheme,
        // Use Material3 defaults so components pick up the right colors/typography
        content = content
    )
}