/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.ui.components.color.MorpheGradientEditor
import app.morphe.gui.ui.components.color.MorpheSwatchRow
import app.morphe.gui.ui.theme.LocalMorpheFont

private enum class FillMode { DEFAULT, SOLID, GRADIENT }

private val MorpheFill?.mode: FillMode
    get() = when (this) {
        is MorpheFill.Solid -> FillMode.SOLID
        is MorpheFill.Gradient -> FillMode.GRADIENT
        else -> FillMode.DEFAULT
    }

@Composable
fun AppCardFillDialog(
    appName: String,
    appIconColorHex: String?,
    initialFill: MorpheFill?,
    onDismiss: () -> Unit,
    onSave: (MorpheFill?) -> Unit,
) {
    val font = LocalMorpheFont.current
    var working by remember { mutableStateOf(initialFill) }
    val palette = cardPalette(working, appIconColorHex, defaultCardPalette())

    MorpheAlertDialog(
        onDismiss = onDismiss,
        maxWidth = 520.dp,
        title = {
            Column {
                Text(
                    text = "Customise card",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = appName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    appIconColorHex = appIconColorHex,
                    fill = working,
                    interactive = false,
                ) {
                    Text(
                        text = appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MorpheChoiceChip("Default", working.mode == FillMode.DEFAULT, font) {
                        working = null
                    }
                    MorpheChoiceChip("Solid", working.mode == FillMode.SOLID, font) {
                        working = MorpheFill.Solid(palette.base.toArgbInt())
                    }
                    MorpheChoiceChip("Gradient", working.mode == FillMode.GRADIENT, font) {
                        working = MorpheFill.Gradient(
                            stops = listOf(
                                MorpheFill.Stop(0f, palette.base.toArgbInt()),
                                MorpheFill.Stop(1f, palette.end.toArgbInt()),
                            ),
                        )
                    }
                }

                when (val fill = working) {
                    null -> Text(
                        text = "Using the colour that ships with the patch bundle.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is MorpheFill.Solid -> MorpheSwatchRow(fill.argb) {
                        working = MorpheFill.Solid(it)
                    }
                    is MorpheFill.Gradient -> MorpheGradientEditor(fill, font) {
                        working = it
                    }
                    else -> Unit
                }
            }
        },
        dismissButton = {
            MorpheChoiceChip("Cancel", active = false, font = font, onClick = onDismiss)
        },
        confirmButton = {
            MorpheChoiceChip("Save", active = true, font = font) { onSave(working) }
        },
    )
}

private fun Color.toArgbInt(): Int {
    fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}
