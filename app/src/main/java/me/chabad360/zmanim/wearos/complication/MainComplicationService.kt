package me.chabad360.zmanim.wearos.complication

import android.text.format.DateFormat
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.flow.first
import me.chabad360.zmanim.wearos.presentation.data.DataStoreManager
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MainComplicationService"
private val COUNTDOWN_THRESHOLD = 15.minutes

/**
 * Complication data source that shows the next zman with a countdown timer.
 * Displays a RANGED_VALUE complication with:
 * - The time of the next zman in the center
 * - A progress bar that counts down when less than 15 minutes remain
 */
class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) {
            return null
        }
        // Preview shows a zman 10 minutes away
        return createRangedValueComplication(
            zmanName = "Mincha",
            zmanTime = if (DateFormat.is24HourFormat(applicationContext)) "17:21" else "5:21",
            timeUntilZman = 600.seconds
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return try {
            // Load persisted zmanim data
            val dataStore = DataStoreManager(applicationContext)
            val persistentState = dataStore.stateFlow.first()

            if (persistentState == null || dataStore.isStateStale(persistentState)) {
                Log.w(TAG, "No data or stale data available for complication")
                return createNoDataComplication()
            }

            // Filter out disabled zmanim and get next upcoming one
            val now = System.currentTimeMillis() / 1000 // Convert to seconds
            val nextZman = persistentState.zmanim
                .filter { it.fromEpoch != null && !persistentState.disabledIds.contains(it.key) }
                .filter { it.fromEpoch!! >= now }
                .minByOrNull { it.fromEpoch!! }

            if (nextZman == null) {
                Log.w(TAG, "No upcoming zmanim found")
                return createNoDataComplication()
            }

            val timeUntilZman = (nextZman.fromEpoch!! - now).seconds

            createRangedValueComplication(
                zmanName = nextZman.name,
                zmanTime = nextZman.timeText,
                timeUntilZman = timeUntilZman
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating complication", e)
            createNoDataComplication()
        }
    }

    /**
     * Create a RANGED_VALUE complication showing the zman with countdown
     */
    private fun createRangedValueComplication(
        zmanName: String,
        zmanTime: String,
        timeUntilZman: kotlin.time.Duration
    ): RangedValueComplicationData {
        val timeText = zmanTime.trimEnd('a', 'p', 'm', ' ')

        // Calculate progress value for the countdown
        // When > 15 min away, show as "full" (no progress bar visible)
        // When < 15 min, show countdown from 15 min to 0
        val (value, max, showProgress) = if (timeUntilZman <= COUNTDOWN_THRESHOLD) {
            // Show countdown: progress decreases as time approaches
            val remainingSeconds = timeUntilZman.inWholeSeconds.toFloat()
            val maxSeconds = COUNTDOWN_THRESHOLD.inWholeSeconds.toFloat()
            Triple(remainingSeconds, maxSeconds, true)
        } else {
            // Don't show progress bar when more than 15 min away
            Triple(0f, 1f, false)
        }

        return RangedValueComplicationData.Builder(
            value = value,
            min = 0f,
            max = max,
            contentDescription = PlainComplicationText.Builder(
                "Next zman: $zmanName at $timeText" +
                if (showProgress) ", ${formatDuration(timeUntilZman)} remaining" else ""
            ).build()
        )
            .setText(PlainComplicationText.Builder(timeText).build())
            .setTitle(PlainComplicationText.Builder(zmanName).build())
            .build()
    }

    /**
     * Create a fallback complication when no data is available
     */
    private fun createNoDataComplication(): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = 0f,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder("No zmanim data available").build()
        )
            .setText(PlainComplicationText.Builder("--:--").build())
            .setTitle(PlainComplicationText.Builder("Zmanim").build())
            .build()
    }

    /**
     * Format duration for content description
     */
    private fun formatDuration(duration: kotlin.time.Duration): String {
        val minutes = duration.inWholeMinutes
        return when {
            minutes < 1 -> "less than 1 minute"
            minutes == 1L -> "1 minute"
            else -> "$minutes minutes"
        }
    }
}