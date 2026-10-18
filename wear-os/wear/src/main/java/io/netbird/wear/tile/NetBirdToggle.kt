package io.netbird.wear.tile

import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import io.netbird.wear.model.ConnectionState

/**
 * NetBird custom toggle switch layout for Wear OS Tiles.
 * Size is scaled down to ~70% of standard size for comfortable fit on circular screens.
 */
internal object NetBirdToggle {
    private const val ORANGE = 0xFFF58431.toInt()
    private const val INACTIVE = 0xFF3A3A3A.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun isOn(state: ConnectionState): Boolean =
        state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING

    fun build(
        state: ConnectionState,
        clickable: ModifiersBuilders.Clickable,
        description: String,
    ): LayoutElementBuilders.Box {
        // ~70% dimensions: thumb 30dp (radius 15dp), track 74dp x 36dp (padding 3dp, radius 18dp)
        val thumb = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(30f))
            .setHeight(dp(30f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(background(WHITE, 15f))
                    .build(),
            )
            .build()

        val track = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(74f))
            .setHeight(dp(36f))
            // Physical OFF-left / ON-right alignment
            .setHorizontalAlignment(
                if (isOn(state)) LayoutElementBuilders.HORIZONTAL_ALIGN_RIGHT
                else LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT,
            )
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(background(if (isOn(state)) ORANGE else INACTIVE, 18f))
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(3f)).build())
                    .build(),
            )
            .addContent(thumb)
            .build()

        // Dedicated tappable area of 88 x 44 dp matching the toggle button
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(88f))
            .setHeight(dp(44f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(description)
                            .build(),
                    )
                    .build(),
            )
            .addContent(track)
            .build()
    }

    private fun background(color: Int, radius: Float) =
        ModifiersBuilders.Background.Builder()
            .setColor(ColorBuilders.argb(color))
            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build())
            .build()
}
