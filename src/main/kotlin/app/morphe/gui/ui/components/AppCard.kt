/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.components.color.toBrush
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalThemeState
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.shiftLightness
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheCorners

fun parseHexColor(hexString: String?, fallback: Color): Color {
    if (hexString.isNullOrBlank()) return fallback
    return try {
        val cleanHex = hexString.trim().removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> cleanHex.toLong(16) or 0xFF000000L
            8 -> cleanHex.toLong(16)
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

/**
 * An accent adjusted to stay legible on an app card, keeping its hue.
 *
 * The card's gradient sits around 0.23 luminance. The dark themes' accents are
 * 0.49 to 0.77 and read fine as they are, so they are left alone. The light
 * themes' accents are 0.22 to 0.25, indistinguishable from the card, so those are
 * lightened until they clear it.
 *
 * Lightening everything unconditionally is what washed the chips out: it dragged
 * green, amber and blue to nearly the same pale tone and lost the very
 * distinction the colours exist to make.
 */
internal fun Color.onCardGradient(): Color =
    if (luminance() < CARD_CONTRAST_FLOOR) lerp(this, Color.White, 0.55f) else this

/** Below this luminance an accent is too close to the card to read. */
private const val CARD_CONTRAST_FLOOR = 0.40f

/** The three colours the card's layers are painted from. */
internal data class CardPalette(val base: Color, val mid: Color, val end: Color)

/**
 * The Manager themes' card colours, copied from morphe-manager's own
 * `DEFAULT_COLORS`. Every other theme derives its card from its accents instead,
 * so a Nord or Sakura card is not silently blue and teal.
 */
internal val MANAGER_MID = Color(0xFF1E5AA8)
internal val MANAGER_END = Color(0xFF00AFAE)

/**
 * The card colours for the active theme, used wherever the user and the bundle
 * have said nothing.
 *
 * #259 hardcoded these, which is why every theme's cards looked like the
 * manager's. The Manager themes still get the manager palette, deliberately.
 */
@Composable
internal fun defaultCardPalette(): CardPalette {
    val accents = LocalMorpheAccents.current
    if (LocalThemeState.current.current.isManager()) {
        return CardPalette(MANAGER_MID, MANAGER_MID, MANAGER_END)
    }
    return CardPalette(
        base = accents.primary,
        mid = accents.primary.shiftLightness(-0.12f),
        end = accents.secondary,
    )
}

/**
 * Resolve the palette the layers use, in priority order: the user's [fill], then
 * the bundle's [appIconColorHex], then the built-in blue and teal.
 *
 * A fill feeds the existing layers rather than replacing them, so a recoloured
 * card keeps the same material and depth as every other card. A solid colour is
 * spread across the three slots by lightness, because collapsing all three to one
 * value flattens the blooms into a single wash.
 */
internal fun cardPalette(
    fill: MorpheFill?,
    appIconColorHex: String?,
    fallback: CardPalette,
): CardPalette = when (fill) {
    // One colour in every slot. Deriving lighter and darker shades here is what
    // let a "solid" card come out shaded by the layers that read them.
    is MorpheFill.Solid -> Color(fill.argb).let { CardPalette(it, it, it) }
    is MorpheFill.Gradient -> {
        val ordered = fill.stops.sortedBy { it.position }
        when (ordered.size) {
            0 -> fallback.withBundleBase(appIconColorHex)
            1 -> Color(ordered[0].argb).let { CardPalette(it, it.shiftLightness(-0.10f), it.shiftLightness(0.12f)) }
            else -> CardPalette(
                base = Color(ordered.first().argb),
                mid = Color(ordered[ordered.size / 2].argb),
                end = Color(ordered.last().argb),
            )
        }
    }
    // An image fill is not something the card layers can express, so fall back.
    else -> fallback.withBundleBase(appIconColorHex)
}

/**
 * The theme's palette with the bundle's own colour swapped into the base slot.
 *
 * The bundle names a per-app brand colour (YouTube red, Reddit orange), which is
 * worth keeping as the card's identity, while the structural blooms follow the
 * theme. With no bundle colour the whole palette is the theme's.
 */
private fun CardPalette.withBundleBase(appIconColorHex: String?): CardPalette =
    copy(base = parseHexColor(appIconColorHex, base))

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalMorpheCorners.current.medium,
    appIconColorHex: String? = null,
    /** The user's colour override for this app, or null to use the bundle's. */
    fill: MorpheFill? = null,
    isExpanded: Boolean = false,
    interactive: Boolean = true,
    onClick: () -> Unit = {},
    /** When set, the card reveals a control on hover for recolouring it. */
    onCustomise: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val hoverProgress by animateFloatAsState(
        targetValue = if (isHovered && interactive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hover_progress"
    )

    val shape = RoundedCornerShape(cornerRadius)

    val (baseColor, midColor, endColor) = cardPalette(fill, appIconColorHex, defaultCardPalette())

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                val w = size.width
                val h = size.height
                val cr = CornerRadius(cornerRadius.toPx())

                // Hover expands Layer 1 & 2 radii to simulate light blooming.
                val hoverBloom = hoverProgress * 60f

                // Base. Opaque, so the card is its own surface. Every layer used to
                // top out at 0.80 alpha with nothing solid underneath, which let the
                // window background bleed through all of them and drained the colour.
                val userBrush = fill?.toBrush(size)
                // A solid fill means solid. The sweep and reflection below are what
                // still gave it a gradient, so a card the user set to one colour
                // came out shaded anyway.
                val isFlatFill = fill is MorpheFill.Solid
                if (userBrush != null) {
                    // The user's own fill, painted as they specified it. Going
                    // through toBrush is what makes gradient type, angle and stop
                    // positions actually do something: the fixed radial blooms
                    // below can only take colours from a fill, never its shape.
                    drawRoundRect(brush = userBrush, cornerRadius = cr)
                } else {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(baseColor, midColor, endColor),
                            center = Offset(w * 0.15f, h * 0.85f),
                            radius = w * 1.1f + hoverBloom,
                        ),
                        cornerRadius = cr,
                    )
                    // Secondary bloom from the top end, for depth across the card.
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                endColor.copy(alpha = 0.55f),
                                midColor.copy(alpha = 0.25f),
                                Color.Transparent,
                            ),
                            center = Offset(w * 0.88f, h * 0.12f),
                            radius = w * 0.75f + hoverBloom,
                        ),
                        cornerRadius = cr,
                    )
                }

                if (!isFlatFill) {
                    // Specular sweep. The old frosted layer painted white at 1 to 3
                    // percent, which is below what a display resolves, so it did
                    // nothing but sit between the others. One sweep at a visible
                    // strength reads as a highlight where four competing washes did not.
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.6f, h),
                        ),
                        cornerRadius = cr,
                    )

                    // Bottom edge reflection, grounding the card.
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, endColor.copy(alpha = 0.22f)),
                            center = Offset(w * 0.5f, h),
                            radius = w * 0.65f,
                        ),
                        cornerRadius = cr,
                    )
                }

                drawContent()

                // Border. Even on a flat fill, graded otherwise: a glassy outline
                // shading from corner to corner is still a gradient on a card the
                // user asked to be one colour.
                val borderBrush = if (isFlatFill) {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.30f))
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            midColor.copy(alpha = 0.30f),
                            endColor.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.20f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                }
                drawRoundRect(
                    brush = borderBrush,
                    cornerRadius = cr,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            // Hover is tracked even when the card is not clickable, because a
            // non-interactive card still reveals its customise control on hover.
            // Only the click affordance is gated on [interactive].
            .hoverable(hoverInteraction)
            .then(if (interactive) Modifier
                .handCursor()
                .clickable(onClick = onClick)
            else Modifier)
    ) {
        content()

        // Revealed on hover only. A card's right edge already carries badges in
        // some states, so a permanent control would collide with them.
        if (onCustomise != null) {
            // Square by default, because sharp corners are the house geometry.
            // The Manager themes round everything else, so they get a circle.
            val buttonShape = if (LocalThemeState.current.current.isManager()) {
                CircleShape
            } else {
                RoundedCornerShape(LocalMorpheCorners.current.small)
            }
            // Solid, in the theme's accent, and adjusted the same way the chips on
            // this card are: kept as-is when it already reads against the card, and
            // lightened only when it is too close to it. A fixed black or white
            // control ignored the theme entirely.
            val buttonFill = LocalMorpheAccents.current.primary.onCardGradient()
            val buttonInk = buttonFill.contrastingForeground()
            AnimatedVisibility(
                visible = isHovered,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(buttonShape)
                        .background(buttonFill)
                        .border(1.dp, buttonInk.copy(alpha = 0.25f), buttonShape)
                        .handCursor()
                        .clickable(onClick = onCustomise),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MorpheIcons.Settings,
                        contentDescription = "Customise card",
                        tint = buttonInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
