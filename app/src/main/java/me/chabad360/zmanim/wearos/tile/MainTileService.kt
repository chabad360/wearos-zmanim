package me.chabad360.zmanim.wearos.tile

import android.content.Context
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_RIGHT
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_END
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.padding

import androidx.wear.protolayout.types.layoutString
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.flow.first
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay
import me.chabad360.zmanim.wearos.presentation.data.DataStoreManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val RESOURCES_VERSION = "0"

/**
 * Data class to hold tile display information
 */
private data class TileData(
    val location: String,
    val currentZman: ZmanDisplay?,
    val nextZman: ZmanDisplay?,
    val timeUntilCurrent: kotlin.time.Duration
)

fun column(builder: LayoutElementBuilders.Column.Builder.() -> Unit) = LayoutElementBuilders.Column.Builder().apply(builder).build()
fun row(builder: LayoutElementBuilders.Row.Builder.() -> Unit) = LayoutElementBuilders.Row.Builder().apply(builder).build()

fun box(builder: LayoutElementBuilders.Box.Builder.() -> Unit) = LayoutElementBuilders.Box.Builder().apply(builder).build()
fun Modifiers(builder: ModifiersBuilders.Modifiers.Builder.() -> Unit) = ModifiersBuilders.Modifiers.Builder().apply(builder).build()

/**
 * Skeleton for a tile with no images.
 */
@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ) = resources(requestParams)

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ) = tile(requestParams, this)

    companion object {
        /**
         * Request an update of the tile from anywhere in the app
         */
        fun requestUpdate(context: Context) {
            getUpdater(context)
                .requestUpdate(MainTileService::class.java)
        }
    }
}

private fun resources(
    requestParams: RequestBuilders.ResourcesRequest
): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

private suspend fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
): TileBuilders.Tile {
    // Load persisted zmanim data
    val dataStore = DataStoreManager(context)
    val persistentState = dataStore.stateFlow.first()

    val tileData = if (persistentState != null && !dataStore.isStateStale(persistentState)) {
        // Filter out disabled zmanim and get next upcoming ones
        val now = System.currentTimeMillis() / 1000 // Convert to seconds
        val enabledZmanim = persistentState.zmanim
            .filter { it.fromEpoch != null && !persistentState.disabledIds.contains(it.key) }
            .sortedBy { it.fromEpoch }

        val upcomingZmanim = enabledZmanim.filter { it.fromEpoch!! >= now }

        if (upcomingZmanim.size >= 2) {
            TileData(
                location = persistentState.location.name,
                currentZman = upcomingZmanim[0],
                nextZman = upcomingZmanim[1],
                timeUntilCurrent = (upcomingZmanim[0].fromEpoch!! - now).seconds
            )
        } else if (upcomingZmanim.size == 1) {
            TileData(
                location = persistentState.location.name,
                currentZman = upcomingZmanim[0],
                nextZman = null,
                timeUntilCurrent = (upcomingZmanim[0].fromEpoch!! - now).seconds
            )
        } else {
            // All zmanim have passed for today, show placeholder
            TileData(
                location = persistentState.location.name,
                currentZman = null,
                nextZman = null,
                timeUntilCurrent = 0.seconds
            )
        }
    } else {
        // No data or stale data, show placeholder
        TileData(
            location = "No Data",
            currentZman = null,
            nextZman = null,
            timeUntilCurrent = 0.seconds
        )
    }

    // Create timeline with entries that update when zmanim pass
    val timelineBuilder = TimelineBuilders.Timeline.Builder()

    // Add current timeline entry
    timelineBuilder.addTimelineEntry(
        TimelineBuilders.TimelineEntry.Builder()
            .setLayout(
                Layout.Builder()
                    .setRoot(tileLayout(requestParams, context, tileData))
                    .build()
            )
            .build()
    )

    // If there's a current zman, add a timeline entry for when it passes
    if (tileData.currentZman != null) {
        val validityEnd = (tileData.currentZman.fromEpoch!! * 1000).milliseconds
        timelineBuilder.addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    Layout.Builder()
                        .setRoot(tileLayout(requestParams, context, tileData.copy(
                            currentZman = tileData.nextZman,
                            nextZman = null // Will need to recalculate
                        )))
                        .build()
                )
                .setValidity(
                    TimelineBuilders.TimeInterval.Builder()
                        .setStartMillis(validityEnd.inWholeMilliseconds)
                        .setEndMillis(validityEnd.inWholeMilliseconds + 86400000) // +24 hours
                        .build()
                )
                .build()
        )
    }

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timelineBuilder.build())
        // Request update when next zman happens
        .setFreshnessIntervalMillis(
            if (tileData.currentZman != null) {
                tileData.timeUntilCurrent.inWholeMilliseconds.coerceAtLeast(60000) // At least 1 minute
            } else {
                3600000 // 1 hour if no data
            }
        )
        .build()
}

private fun tileLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    tileData: TileData
): LayoutElementBuilders.LayoutElement {
    return materialScope(
        context = context,
        deviceConfiguration = requestParams.deviceConfiguration,
        allowDynamicTheme = true
    ) {
        primaryLayout(
            titleSlot = { text(text = tileData.location.layoutString) },
            mainSlot = {
                column {
                    setWidth(expand())
                    setHeight(expand())
                    setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)

                    // Show current upcoming zman
                    if (tileData.currentZman != null) {
                        addContent(activeZman(tileData.currentZman, tileData.timeUntilCurrent))
                    }

                    // Show next zman
                    if (tileData.nextZman != null) {
                        addContent(nextZman(tileData.nextZman))
                    }

                    // Show placeholder if no data
                    if (tileData.currentZman == null && tileData.nextZman == null) {
                        addContent(
                            text(
                                text = "No upcoming zmanim".layoutString,
                                typography = Typography.BODY_MEDIUM
                            )
                        )
                    }
                }
            },
        )
    }
}

/**
 * Format time remaining as a pair of (value, unit) for display
 * e.g., (41, "min") or (2, "hrs") or (30, "sec")
 */
private fun formatTimeRemaining(duration: kotlin.time.Duration): Pair<String, String> {
    val totalSeconds = duration.inWholeSeconds

    return when {
        totalSeconds < 60 -> {
            totalSeconds.toString() to "sec"
        }
        totalSeconds < 3600 -> {
            (totalSeconds / 60).toString() to "min"
        }
        totalSeconds < 86400 -> {
            val hours = (totalSeconds / 60).toFloat() / 60
            "%.1f".format(hours) to "hrs"
        }
        else -> {
            (totalSeconds / 86400).toString() to "days"
        }
    }
}

private fun MaterialScope.nextZman(zman: ZmanDisplay): LayoutElementBuilders.Box = box {
    setModifiers(
        Modifiers {
            setPadding(padding(vertical = 4f, horizontal = 10f))
        }
    )
    setWidth(expand())
    addContent(
        box {
            setModifiers(
                Modifiers {
                    setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(colorScheme.secondaryContainer.prop)
                            .setCorner(
                                ModifiersBuilders.Corner.Builder().setRadius(dp(22F)).build()
                            )
                            .build()
                    )
                    setPadding(padding(vertical = 6f, horizontal = 16f))
                }
            )
            setWidth(expand())
            addContent(
                row {
                    setWidth(expand())
                    setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    addContent(column {
                        setHeight(expand())
                        addContent(
                            text(
                                text = "Next:".layoutString,
                            )
                        )
                    })
                    addContent(
                        column {
                            setWidth(expand())
                            setHorizontalAlignment(HORIZONTAL_ALIGN_RIGHT)
                            addContent(
                                text(
                                    text = zman.name.layoutString,
                                    typography = Typography.TITLE_SMALL,
                                    color = colorScheme.onSecondaryContainer,
                                    alignment = TEXT_ALIGN_END
                                )
                            )
                            addContent(
                                text(
                                    text = zman.timeText.layoutString,
                                    typography = Typography.LABEL_LARGE,
                                    alignment = TEXT_ALIGN_END,
                                    color = colorScheme.onSurface
                                )
                            )
                        }
                    )
                }
            )
        }
    )
}

private fun MaterialScope.activeZman(zman: ZmanDisplay, timeUntil: kotlin.time.Duration): LayoutElementBuilders.Box = box {
    setModifiers(
        Modifiers {
            setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(colorScheme.secondaryContainer.prop)
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(28F)).build())
                    .build()
            )
            setPadding(padding(vertical = 8f, horizontal = 16f))
        }
    )
    setWidth(expand())
    addContent(
        row {
            setWidth(expand())
            setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            addContent(column {
                setHorizontalAlignment(HORIZONTAL_ALIGN_LEFT)
                addContent(
                    text(
                        text = formatTimeRemaining(timeUntil).first.layoutString,
                        typography = Typography.DISPLAY_SMALL,
                        color = colorScheme.onSurface
                    )
                )
                addContent(
                    text(
                        text = formatTimeRemaining(timeUntil).second.layoutString,
                        typography = Typography.TITLE_SMALL,
                        color = colorScheme.onSecondaryContainer
                    )
                )
            })
            addContent(column {
                setWidth(expand())
                setHorizontalAlignment(HORIZONTAL_ALIGN_RIGHT)
                addContent(
                    text(
                        text = zman.name.layoutString,
                        typography = Typography.TITLE_LARGE,
                        color = colorScheme.onSecondaryContainer
                    )
                )
                addContent(
                    text(
                        text = zman.timeText.layoutString,
                        typography = Typography.DISPLAY_SMALL,
                        color = colorScheme.onSurface
                    )
                )
            })
        }
    )
}

//@Preview(device = WearDevices.SMALL_ROUND)
//@Preview(device = WearDevices.LARGE_ROUND)
//fun tilePreview(context: Context) = TilePreviewData(::resources) { requestParams ->
//    // Create mock data for preview
//    val mockData = TileData(
//        location = "Jerusalem",
//        currentZman = ZmanDisplay(
//            key = "alos",
//            name = "Alos Hashachar",
//            fromEpoch = System.currentTimeMillis() / 1000 + 2460, // 41 minutes from now
//            timeZone = kotlinx.datetime.TimeZone.UTC,
//            uses24h = false
//        ),
//        nextZman = ZmanDisplay(
//            key = "sunrise",
//            name = "Sunrise",
//            fromEpoch = System.currentTimeMillis() / 1000 + 5400, // 1.5 hours from now
//            timeZone = kotlinx.datetime.TimeZone.UTC,
//            uses24h = false
//        ),
//        timeUntilCurrent = 2460.seconds
//    )
//
//    TileBuilders.Tile.Builder()
//        .setResourcesVersion(RESOURCES_VERSION)
//        .setTileTimeline(
//            TimelineBuilders.Timeline.Builder()
//                .addTimelineEntry(
//                    TimelineBuilders.TimelineEntry.Builder()
//                        .setLayout(
//                            Layout.Builder()
//                                .setRoot(tileLayout(requestParams, context, mockData))
//                                .build()
//                        )
//                        .build()
//                )
//                .build()
//        )
//        .build()
//}

