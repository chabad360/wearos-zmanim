package me.chabad360.zmanim.wearos.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import kotlinx.coroutines.flow.first
import me.chabad360.zmanim.wearos.presentation.MainActivity
import me.chabad360.zmanim.wearos.presentation.data.DataStoreManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private const val TAG = "MainComplicationService"
private val COUNTDOWN_THRESHOLD = 15

/**
 * Complication data source that shows the next zman with a countdown timer.
 * Displays a RANGED_VALUE complication with:
 * - The time of the next zman in the center
 * - A progress bar that counts down when less than 15 minutes remain
 */
class MainComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) {
            return null
        }
        // Preview shows a zman 10 minutes away
        return createRangedValueComplication(
            zmanKey = "minchaGedola",
            zmanName = "Mincha",
            zmanTime = if (DateFormat.is24HourFormat(applicationContext)) "17:21" else "5:21",
            zmanEpoch = (System.currentTimeMillis() / 1000) + 10.minutes.inWholeSeconds
        )
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline {
        return try {
            // Load persisted zmanim data
            val dataStore = DataStoreManager(applicationContext)
            val persistentState = dataStore.stateFlow.first()

            if (persistentState == null || dataStore.isStateStale(persistentState)) {
                Log.w(TAG, "No data or stale data available for complication")
                return ComplicationDataTimeline(createNoDataComplication(), emptyList())
            }

            // Filter out disabled zmanim and get next upcoming one
            val now = System.currentTimeMillis() / 1000 // Convert to seconds
            val nextZmanim = persistentState.zmanim
                .filter { it.fromEpoch != null && !persistentState.disabledIds.contains(it.key) }
                .filter { it.fromEpoch!! >= now }
                .sortedBy { it.fromEpoch }

            if (nextZmanim.isEmpty()) {
                Log.w(TAG, "No upcoming zmanim found")
                return ComplicationDataTimeline(createNoDataComplication(), emptyList())
            }

            return ComplicationDataTimeline(createNoDataComplication(), nextZmanim.map { nextZman ->
                TimelineEntry(
                    validity = TimeInterval(
                        start = Clock.System.now().toJavaInstant(),
                        end = Instant.fromEpochSeconds(nextZman.fromEpoch!!).toJavaInstant()
                    ),
                    complicationData = createRangedValueComplication(
                        zmanKey = nextZman.key,
                        zmanName = nextZman.name,
                        zmanTime = nextZman.timeText,
                        zmanEpoch = nextZman.fromEpoch
                    )
                )
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error creating complication", e)
            ComplicationDataTimeline(createNoDataComplication(), emptyList())
        }
    }

    /**
     * Create a RANGED_VALUE complication showing the zman with countdown
     */
    @OptIn(ExperimentalTime::class)
    private fun createRangedValueComplication(
        zmanKey: String,
        zmanName: String,
        zmanTime: String,
        zmanEpoch: Long?,
    ): RangedValueComplicationData {
        val timeText = zmanTime.trimEnd('A', 'P', 'M', ' ')

        val dynamicTime = DynamicBuilders.DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicBuilders.DynamicInstant.withSecondsPrecision(Instant.fromEpochSeconds(zmanEpoch!!).toJavaInstant()))
        val dynamicValue = DynamicBuilders.DynamicFloat.onCondition(dynamicTime.toIntMinutes().gt(COUNTDOWN_THRESHOLD))
            .use(0f)
            .elseUse(DynamicBuilders.DynamicFloat.onCondition(dynamicTime.toIntSeconds().lt(0))
                .use(1f)
                .elseUse(
                    DynamicBuilders.DynamicFloat.constant(15f)
                        .minus(dynamicTime.toIntMinutes())
                        .div(COUNTDOWN_THRESHOLD.toFloat())
                )
            )

        // Create tap action to open the app and scroll to this zman
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("zmanKey", zmanKey)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            zmanKey.hashCode(), // Use zmanKey hash as request code to ensure uniqueness
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RangedValueComplicationData.Builder(
            fallbackValue = 0f,
            dynamicValue = dynamicValue,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder(
                "$zmanName is at $timeText"
            ).build()
        )
            .setText(PlainComplicationText.Builder(timeText).build())
            .setTitle(PlainComplicationText.Builder(zmanName).build())
            .setTapAction(pendingIntent)
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

    companion object {
        /**
         * Request an update of the tile from anywhere in the app
         */
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(
                context = context,
                complicationDataSourceComponent = ComponentName(context, MainComplicationService::class.java)
            ).requestUpdateAll()
        }
    }
}