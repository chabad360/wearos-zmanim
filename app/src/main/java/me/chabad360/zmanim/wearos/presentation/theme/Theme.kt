package me.chabad360.zmanim.wearos.presentation.theme

import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ZmanimTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        // Use Material3 defaults so components pick up the right colors/typography
        content = content
    )
}