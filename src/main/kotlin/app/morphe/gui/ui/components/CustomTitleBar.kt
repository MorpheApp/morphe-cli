package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import app.morphe.gui.ui.theme.LocalMorpheFont
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

@Composable
fun FrameWindowScope.CustomTitleBar() {
    val mono = LocalMorpheFont.current

    WindowDraggableArea(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App title
            Text(
                text = "Morphe",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Window controls
            WindowButton(
                symbol = "─",
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                symbolColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                onClick = {
                    window.extendedState = Frame.ICONIFIED
                }
            )

            val isMaximized = remember { mutableStateOf(window.extendedState == Frame.MAXIMIZED_BOTH) }

            // Listen for external maximize state changes (e.g. OS double-click on title bar)
            DisposableEffect(window) {
                val listener = object : java.awt.event.WindowStateListener {
                    override fun windowStateChanged(e: java.awt.event.WindowEvent) {
                        isMaximized.value = (e.newState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
                    }
                }
                window.addWindowStateListener(listener)
                onDispose { window.removeWindowStateListener(listener) }
            }

            WindowButton(
                symbol = if (isMaximized.value) "❐" else "□",
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                symbolColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                onClick = {
                    if (isMaximized.value) {
                        window.extendedState = Frame.NORMAL
                    } else {
                        window.extendedState = Frame.MAXIMIZED_BOTH
                    }
                    isMaximized.value = !isMaximized.value
                }
            )

            WindowButton(
                symbol = "✕",
                hoverColor = Color(0xFFE81123),
                symbolColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                hoverSymbolColor = Color.White,
                onClick = {
                    window.dispatchEvent(
                        java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING)
                    )
                }
            )
        }
    }

    // Double-click to maximize/restore
    DisposableEffect(window) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.y <= 36) {
                    val isMax = (window.extendedState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
                    window.extendedState = if (isMax) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                }
            }
        }
        window.addMouseListener(listener)
        onDispose { window.removeMouseListener(listener) }
    }
}

@Composable
private fun WindowButton(
    symbol: String,
    hoverColor: Color,
    symbolColor: Color,
    hoverSymbolColor: Color? = null,
    onClick: () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (isHovered) hoverColor else Color.Transparent,
        animationSpec = tween(100)
    )
    val fg by animateColorAsState(
        if (isHovered && hoverSymbolColor != null) hoverSymbolColor else symbolColor,
        animationSpec = tween(100)
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .hoverable(hover)
            .clickable(onClick = onClick)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 11.sp,
            color = fg
        )
    }
}
