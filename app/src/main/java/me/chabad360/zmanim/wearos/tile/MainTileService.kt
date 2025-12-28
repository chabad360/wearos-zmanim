package me.chabad360.zmanim.wearos.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.*
import androidx.wear.protolayout.DimensionBuilders.*
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32.IntFormatter
import androidx.wear.protolayout.material3.*
import androidx.wear.protolayout.material3.PrimaryLayoutMargins.Companion.MIN_PRIMARY_LAYOUT_MARGIN
import androidx.wear.protolayout.modifiers.padding
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import me.chabad360.zmanim.wearos.presentation.ZmanDisplay
import me.chabad360.zmanim.wearos.presentation.data.DataStoreManager
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds


private const val RESOURCES_VERSION = "0"
private const val TAG = "MainTileService"

/**
 * Data class to hold tile display information
 */
private data class TileData(
    val location: String,
    val currentZman: ZmanDisplay?,
    val nextZman: ZmanDisplay?,
    val timeUntilCurrent: kotlin.time.Duration
)

fun column(builder: Column.Builder.() -> Unit) = Column.Builder().apply(builder).build()
fun row(builder: Row.Builder.() -> Unit) = Row.Builder().apply(builder).build()

fun box(builder: Box.Builder.() -> Unit) = Box.Builder().apply(builder).build()
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
    return try {
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

        // Dynamic values require a state to be attached to the tile. An empty state is sufficient
        // for platform-time based dynamic expressions.
        val state = StateBuilders.State.Builder()
            .build()

        // Always include a first entry.
        timelineBuilder.addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    Layout.Builder()
                        .setRoot(tileLayout(requestParams, context, tileData))
                        .build()
                )
                .build()
        )

        // If there's a current zman, add a timeline entry for when it passes.
        // Important: TimeInterval end must be strictly > start.
        if (tileData.currentZman?.fromEpoch != null) {
            val startMillis = tileData.currentZman.fromEpoch * 1000
            val endMillis = startMillis + 86400000L // +24 hours
            if (endMillis > startMillis) {
                timelineBuilder.addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(
                            Layout.Builder()
                                .setRoot(
                                    tileLayout(
                                        requestParams,
                                        context,
                                        tileData.copy(
                                            currentZman = tileData.nextZman,
                                            nextZman = null // Will need to recalculate
                                        )
                                    )
                                )
                                .build()
                        )
                        .setValidity(
                            TimelineBuilders.TimeInterval.Builder()
                                .setStartMillis(startMillis)
                                .setEndMillis(endMillis)
                                .build()
                        )
                        .build()
                )
            } else {
                Log.w(TAG, "Skipping timeline validity because endMillis <= startMillis ($startMillis..$endMillis)")
            }
        }

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timelineBuilder.build())
            .setState(state)
            // Request update when next zman happens
            .setFreshnessIntervalMillis(
                if (tileData.currentZman != null) {
                    tileData.timeUntilCurrent.inWholeMilliseconds.coerceAtLeast(60000) // At least 1 minute
                } else {
                    3600000 // 1 hour if no data
                }
            )
            .build()
    } catch (t: Throwable) {
        Log.e(TAG, "Tile failed to render", t)
        fallbackTile(requestParams, context, t)
    }
}

private fun fallbackTile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    error: Throwable? = null,
): TileBuilders.Tile {
    val msg = (error?.javaClass?.simpleName ?: "Tile error")
    val timeline = TimelineBuilders.Timeline.Builder()
        .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    Layout.Builder()
                        .setRoot(
                            materialScope(
                                context = context,
                                deviceConfiguration = requestParams.deviceConfiguration,
                                allowDynamicTheme = true
                            ) {
                                primaryLayout(
                                    titleSlot = { text(text = "Zmanim".layoutString) },
                                    mainSlot = {
                                        column {
                                            setWidth(expand())
                                            setHeight(expand())
                                            setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                                            addContent(
                                                text(
                                                    text = "Tile unavailable".layoutString,
                                                    typography = Typography.BODY_MEDIUM
                                                )
                                            )
                                            addContent(
                                                text(
                                                    text = msg.layoutString,
                                                    typography = Typography.LABEL_SMALL
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        )
                        .build()
                )
                .build()
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timeline)
        .setState(StateBuilders.State.Builder().build())
        .setFreshnessIntervalMillis(60000)
        .build()
}

private fun tileLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    tileData: TileData
): LayoutElement {
    return materialScope(
        context = context,
        deviceConfiguration = requestParams.deviceConfiguration,
        allowDynamicTheme = true
    ) {
        primaryLayout(
            titleSlot = { text(text = tileData.location.layoutString) },
            margins = MIN_PRIMARY_LAYOUT_MARGIN,
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

private fun MaterialScope.activeZman(zman: ZmanDisplay, timeUntil: kotlin.time.Duration): Box {
    // Create dynamic instant for the target time
    val targetInstant = Instant.ofEpochSecond(zman.fromEpoch!!)
    val dynamicTargetInstant = DynamicInstant.withSecondsPrecision(targetInstant)

    // Calculate dynamic duration between now and target time
    val dynamicDuration = DynamicInstant.platformTimeWithSecondsPrecision().durationUntil(dynamicTargetInstant)

    // Get total seconds as DynamicInt32
    val totalSeconds = dynamicDuration.toIntSeconds()


    return box {
        setModifiers(
            Modifiers {
                setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(colorScheme.secondaryContainer.prop)
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(28F)).build())
                        .build()
                )
                setPadding(padding(vertical = 12f, horizontal = 16f))
            }
        )
        setWidth(expand())
        addContent(
            row {
                setWidth(expand())
                setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                addContent(column {
//                    setWidth(expand())
                    setHorizontalAlignment(HORIZONTAL_ALIGN_LEFT)
                    addContent(
                        Text.Builder()
                            .setText(
                                TypeBuilders.StringProp.Builder("--")
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicString.onCondition(totalSeconds.lt(60))
                                        .use(totalSeconds.format()) // seconds
                                        .elseUse(
                                            DynamicBuilders.DynamicString.onCondition(totalSeconds.lt(3600))
                                                .use(totalSeconds.div(60).format()) // minutes
                                                .elseUse(
                                                    DynamicBuilders.DynamicString.onCondition(totalSeconds.lt(86400))
                                                        .use(totalSeconds.div(60).asFloat().div(60f).format(
                                                            DynamicBuilders.DynamicFloat.FloatFormatter.Builder().setMaxFractionDigits(1).setMinIntegerDigits(1).build())) // hours with decimal
                                                        .elseUse(totalSeconds.div(86400).format()) // days
                                                )
                                        ))
                                    .build()
                            )
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(24f))
                                    .setColor(colorScheme.onSurface.prop)
                                    .build()
                            )
                            .build()
                    )
                    addContent(
                        Text.Builder()
                            .setText(
                                TypeBuilders.StringProp.Builder("min")
                                    .setDynamicValue(
                                        DynamicBuilders.DynamicString.onCondition(totalSeconds.lt(60))
                                        .use(DynamicBuilders.DynamicString.constant("sec"))
                                        .elseUse(
                                            DynamicBuilders.DynamicString.onCondition(totalSeconds.lt(3600))
                                                .use(DynamicBuilders.DynamicString.constant("min"))
                                                .elseUse(
                                                    DynamicBuilders.DynamicString.onCondition(totalSeconds.lt(86400))
                                                        .use(DynamicBuilders.DynamicString.constant("hrs"))
                                                        .elseUse(DynamicBuilders.DynamicString.constant("days"))
                                                )
                                        ))
                                    .build()
                            )
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(16f)) // TITLE_SMALL size
                                    .setColor(colorScheme.onSecondaryContainer.prop)
                                    .build()
                            )
                            .build()
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
}

private fun MaterialScope.nextZman(zman: ZmanDisplay): Box = box {
    setModifiers(
        Modifiers {
            setPadding(padding(vertical = 4f, horizontal = 16f))
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
                    setPadding(padding(vertical = 10f, horizontal = 16f))
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

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) { requestParams ->
    // Create mock data for preview
    val mockData = TileData(
        location = "Jerusalem",
        currentZman = ZmanDisplay(
            key = "alos",
            name = "Alos Hashachar",
            fromEpoch = System.currentTimeMillis() / 1000 + 2460, // 41 minutes from now
            timeZone = TimeZone.UTC,
            uses24h = false
        ),
        nextZman = ZmanDisplay(
            key = "sunrise",
            name = "Sunrise",
            fromEpoch = System.currentTimeMillis() / 1000 + 5400, // 1.5 hours from now
            timeZone = TimeZone.UTC,
            uses24h = false
        ),
        timeUntilCurrent = 2460.seconds
    )

    TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(
                            Layout.Builder()
                                .setRoot(tileLayout(requestParams, context, mockData))
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()
}
