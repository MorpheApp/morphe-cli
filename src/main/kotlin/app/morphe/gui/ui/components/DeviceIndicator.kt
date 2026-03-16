package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceStatus

@Composable
fun DeviceIndicator(modifier: Modifier = Modifier) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val monitorState by DeviceMonitor.state.collectAsState()

    val isAdbAvailable = monitorState.isAdbAvailable
    val readyDevices = monitorState.devices.filter { it.isReady }
    val unauthorizedDevices = monitorState.devices.filter { it.status == DeviceStatus.UNAUTHORIZED }
    val selectedDevice = monitorState.selectedDevice

    var showPopup by remember { mutableStateOf(false) }

    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val dotColor = when {
        isAdbAvailable == false -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        selectedDevice != null && selectedDevice.isReady -> MorpheColors.Teal
        unauthorizedDevices.isNotEmpty() -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    }

    val borderColor by animateColorAsState(
        when {
            isHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            selectedDevice != null && selectedDevice.isReady -> MorpheColors.Teal.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(150)
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .hoverable(hoverInteraction)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                .clickable { showPopup = !showPopup }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, RoundedCornerShape(1.dp))
                )

                val displayText = when {
                    isAdbAvailable == null -> "Checking…"
                    isAdbAvailable == false -> "No ADB"
                    selectedDevice != null -> {
                        val arch = selectedDevice.architecture?.let { " · $it" } ?: ""
                        "${selectedDevice.displayName}$arch"
                    }
                    unauthorizedDevices.isNotEmpty() -> "Unauthorized"
                    else -> "No device"
                }

                Text(
                    text = displayText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = mono,
                    color = when {
                        isAdbAvailable == false -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        selectedDevice != null -> MaterialTheme.colorScheme.onSurface
                        unauthorizedDevices.isNotEmpty() -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Device details",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        // Popup
        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = { showPopup = false }
        ) {
            when {
                isAdbAvailable == false -> {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UsbOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Text(
                                        text = "ADB not found",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Install Android SDK Platform Tools",
                                        fontSize = 10.sp,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }

                monitorState.devices.isEmpty() -> {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Column {
                                    Text(
                                        text = "No devices detected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Connect a device with USB debugging enabled",
                                        fontSize = 10.sp,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MorpheColors.Blue.copy(alpha = 0.6f)
                                )
                                Column {
                                    Text(
                                        text = "Enable USB debugging",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = mono,
                                        color = MorpheColors.Blue
                                    )
                                    Text(
                                        text = "Settings → Developer Options → USB Debugging",
                                        fontSize = 10.sp,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }

                else -> {
                    monitorState.devices.forEach { device ->
                        val isSelected = device.id == selectedDevice?.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Device status dot
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                when {
                                                    isSelected -> MorpheColors.Teal
                                                    device.isReady -> MorpheColors.Blue
                                                    device.status == DeviceStatus.UNAUTHORIZED -> Color(0xFFFF9800)
                                                    else -> MaterialTheme.colorScheme.error
                                                },
                                                RoundedCornerShape(1.dp)
                                            )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontFamily = mono
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            device.architecture?.let { arch ->
                                                Text(
                                                    text = arch,
                                                    fontSize = 10.sp,
                                                    fontFamily = mono,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            Text(
                                                text = when (device.status) {
                                                    DeviceStatus.DEVICE -> "Connected"
                                                    DeviceStatus.UNAUTHORIZED -> "Unauthorized"
                                                    DeviceStatus.OFFLINE -> "Offline"
                                                    DeviceStatus.UNKNOWN -> "Unknown"
                                                },
                                                fontSize = 10.sp,
                                                fontFamily = mono,
                                                color = when (device.status) {
                                                    DeviceStatus.DEVICE -> MorpheColors.Teal
                                                    DeviceStatus.UNAUTHORIZED -> Color(0xFFFF9800)
                                                    else -> MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                if (device.isReady) {
                                    DeviceMonitor.selectDevice(device)
                                }
                                showPopup = false
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Column {
                                    Text(
                                        text = "Device not listed?",
                                        fontSize = 11.sp,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "Enable USB Debugging in Developer Options",
                                        fontSize = 10.sp,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }
            }
        }
    }
}
