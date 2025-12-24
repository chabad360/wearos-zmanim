package me.chabad360.zmanim.wearos.tile

import android.content.Context
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.ANGULAR_ALIGNMENT_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_RIGHT
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_END
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_TOP
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.padding

import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService

private const val RESOURCES_VERSION = "0"

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
}

private fun resources(
    requestParams: RequestBuilders.ResourcesRequest
): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
): TileBuilders.Tile {
    val singleTileTimeline = TimelineBuilders.Timeline.Builder()
        .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    Layout.Builder()
                        .setRoot(tileLayout(requestParams, context))
                        .build()
                )
                .build()
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(singleTileTimeline)
        .build()
}

private fun tileLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
): LayoutElementBuilders.LayoutElement {
    return materialScope(
        context = context,
        deviceConfiguration = requestParams.deviceConfiguration,
        allowDynamicTheme = true
    ) {
        primaryLayout(
            titleSlot = { text(text = "Vancouver".layoutString) },
            mainSlot = { column {
                setWidth(expand())
                setHeight(expand())
                setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                addContent(
                    box {
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
                                addContent( column {
//                                    setWidth(expand())
                                    setHorizontalAlignment(HORIZONTAL_ALIGN_LEFT)
                                    addContent(
                                        text(
                                            text = "41".layoutString,
                                            typography = Typography.DISPLAY_SMALL,
                                            color = colorScheme.onSurface
                                        )
                                    )
                                    addContent(
                                        text(
                                            text = "min".layoutString,
                                            typography = Typography.TITLE_SMALL,
                                            color = colorScheme.onSecondaryContainer
                                        )
                                    )
                                } )
                                addContent( column {
                                    setWidth(expand())
                                    setHorizontalAlignment(HORIZONTAL_ALIGN_RIGHT)
                                    addContent(
                                        text(
                                            text = "Alos".layoutString,
                                            typography = Typography.TITLE_LARGE,
                                            color = colorScheme.onSecondaryContainer
                                        )
                                    )
                                    addContent(
                                        text(
                                            text = "10:15 AM".layoutString,
                                            typography = Typography.DISPLAY_SMALL,
                                            color = colorScheme.onSurface
                                        )
                                    )
                                } )
                            }
                        )
                    }
                )
                addContent(
                    box {
                        setModifiers(
                            Modifiers {
                                setPadding(padding(vertical=4f, horizontal=10f))
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
                                        addContent( column {
                                            setHeight(expand())
                                            addContent(text(
                                                text = "Next:".layoutString,
                                            ))
                                        })
                                        addContent(
                                        column {
                                            setWidth(expand())
                                            setHorizontalAlignment(TEXT_ALIGN_END)
                                            addContent(
                                                text(
                                                    text = "Neitz".layoutString,
                                                    typography = Typography.TITLE_SMALL,
                                                    color = colorScheme.onSecondaryContainer,
                                                    alignment = TEXT_ALIGN_END
                                                )
                                            )
                                            addContent(
                                                text(
                                                    text = "11:00 AM".layoutString,
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
                )
            } },
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    tile(it, context)
}