package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceStatus

@Composable
fun DeviceIndicator(modifier: Modifier = Modifier) {
    val monitorState by DeviceMonitor.state.collectAsState()

    val isAdbAvailable = monitorState.isAdbAvailable
    val readyDevices = monitorState.devices.filter { it.isReady }
    val unauthorizedDevices = monitorState.devices.filter { it.status == DeviceStatus.UNAUTHORIZED }
    val selectedDevice = monitorState.selectedDevice
    val hasDevices = monitorState.devices.isNotEmpty()

    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { showPopup = !showPopup },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Status dot
                val dotColor = when {
                    isAdbAvailable == false -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    selectedDevice != null && selectedDevice.isReady -> MorpheColors.Teal
                    unauthorizedDevices.isNotEmpty() -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                // Display text
                val displayText = when {
                    isAdbAvailable == null -> "Checking..."
                    isAdbAvailable == false -> "No ADB"
                    selectedDevice != null -> {
                        val arch = selectedDevice.architecture?.let { " \u2022 $it" } ?: ""
                        "${selectedDevice.displayName}$arch"
                    }
                    unauthorizedDevices.isNotEmpty() -> "Unauthorized"
                    else -> "No device"
                }

                Text(
                    text = displayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isAdbAvailable == false -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        selectedDevice != null -> MaterialTheme.colorScheme.onSurface
                        unauthorizedDevices.isNotEmpty() -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )

                // Always show dropdown arrow — popup has useful info in every state
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Device details",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Popup with device list / status info
        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = { showPopup = false }
        ) {
            when {
                isAdbAvailable == false -> {
                    // ADB not found
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UsbOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Text(
                                        text = "ADB not found",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Install Android SDK Platform Tools",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }

                monitorState.devices.isEmpty() -> {
                    // ADB available but no devices visible
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Column {
                                    Text(
                                        text = "No devices detected",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Only devices with USB debugging enabled will appear here",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                    modifier = Modifier.size(16.dp),
                                    tint = MorpheColors.Blue.copy(alpha = 0.7f)
                                )
                                Column {
                                    Text(
                                        text = "How to enable USB debugging",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MorpheColors.Blue
                                    )
                                    Text(
                                        text = "Settings > Developer Options > USB Debugging",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }

                else -> {
                    // Device list
                    monitorState.devices.forEach { device ->
                        val isSelected = device.id == selectedDevice?.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = when {
                                            isSelected -> MorpheColors.Teal
                                            device.isReady -> MorpheColors.Blue
                                            device.status == DeviceStatus.UNAUTHORIZED -> Color(0xFFFF9800)
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            device.architecture?.let { arch ->
                                                Text(
                                                    text = arch,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = when (device.status) {
                                                    DeviceStatus.DEVICE -> "Connected"
                                                    DeviceStatus.UNAUTHORIZED -> "Unauthorized"
                                                    DeviceStatus.OFFLINE -> "Offline"
                                                    DeviceStatus.UNKNOWN -> "Unknown"
                                                },
                                                fontSize = 11.sp,
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

                    // USB debugging hint
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
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Column {
                                    Text(
                                        text = "Device connected but not listed?",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Enable USB Debugging in Developer Options",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
