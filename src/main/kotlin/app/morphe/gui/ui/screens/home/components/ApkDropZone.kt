package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.screens.home.ApkInfo
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ApkDropZone(
    apkInfo: ApkInfo?,
    isDragHovering: Boolean,
    onDragHoverChange: (Boolean) -> Unit,
    onFilesDropped: (List<File>) -> Unit,
    onBrowseClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        apkInfo != null -> MaterialTheme.colorScheme.primary
        isDragHovering -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val backgroundColor = when {
        apkInfo != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isDragHovering -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                onDragHoverChange(true)
            }

            override fun onEnded(event: DragAndDropEvent) {
                onDragHoverChange(false)
            }

            override fun onExited(event: DragAndDropEvent) {
                onDragHoverChange(false)
            }

            override fun onEntered(event: DragAndDropEvent) {
                onDragHoverChange(true)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragHoverChange(false)
                val transferable = event.awtTransferable
                return try {
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        if (files.isNotEmpty()) {
                            onFilesDropped(files)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .background(backgroundColor)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragAndDropTarget
            ),
        contentAlignment = Alignment.Center
    ) {
        if (apkInfo != null) {
            ApkSelectedContent(
                apkInfo = apkInfo,
                onClearClick = onClearClick
            )
        } else {
            DropZoneEmptyContent(
                isDragHovering = isDragHovering,
                onBrowseClick = onBrowseClick
            )
        }
    }
}

@Composable
private fun DropZoneEmptyContent(
    isDragHovering: Boolean,
    onBrowseClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = if (isDragHovering) "Drop here" else "Drag & drop APK file here",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = "or",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onBrowseClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Browse Files")
        }
    }
}

@Composable
private fun ApkSelectedContent(
    apkInfo: ApkInfo,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = apkInfo.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = apkInfo.formattedSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = apkInfo.filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onClearClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear selection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
