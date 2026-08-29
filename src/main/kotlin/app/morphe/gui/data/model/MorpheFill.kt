/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// FILL MODEL
// =============================================================================

/**
 * A paintable fill: one colour, a multi-stop gradient, or an image.
 *
 * Shared by every surface that lets the user choose how something is painted,
 * currently the Icon Studio's tile background and the home app cards. The model
 * is deliberately UI-free so a renderer can target Compose ([toBrush]) or AWT
 * (the icon exporter) from the same data.
 *
 * The [SerialName]s are pinned to where these classes used to live, inside
 * `IconProject`. kotlinx.serialization defaults a sealed subtype's serial name to
 * its fully-qualified class name, so moving the file without pinning would make
 * every saved `project.json` fail to decode. Never change these strings.
 */
@Serializable
sealed interface MorpheFill {

    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Solid")
    data class Solid(val argb: Int) : MorpheFill

    /** Multi-stop gradient. [angleDeg] applies to LINEAR (0 = right, 90 = down) and CONIC. */
    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Gradient")
    data class Gradient(
        val stops: List<Stop> = listOf(Stop(0f, 0xFF00E5FF.toInt()), Stop(1f, 0xFF000000.toInt())),
        val type: GradientType = GradientType.LINEAR,
        val angleDeg: Float = 45f,
    ) : MorpheFill

    /** A gradient colour stop at [position] (0..1) along the gradient. */
    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Stop")
    data class Stop(val position: Float, val argb: Int)

    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Image")
    data class Image(val sourcePath: String) : MorpheFill
}

@Serializable
@SerialName("app.morphe.gui.icon.IconProject.GradientType")
enum class GradientType { LINEAR, RADIAL, CONIC }
